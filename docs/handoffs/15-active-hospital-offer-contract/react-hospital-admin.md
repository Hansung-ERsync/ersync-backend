# 비목적지 병원 제안 활성 상태 React 핸드오프

```text
Feature: active-hospital-offer-contract
Backend Feature: docs/features/15-active-hospital-offer-contract/
Related PR: NONE
Available After: MAIN_MERGE
Breaking Change: YES (응답 스키마는 유지, 진행 중 카드 분류·상태 의미 변경)
Hospital Impact: YES
Admin Impact: NONE
```

> 이 문서는 진행 중 병원 제안에 대한 현재 백엔드 계약입니다.
> 기능 12의 진행 중 `NOT_SELECTED`·HISTORY 계약을 대체합니다.

## 변경 요약

- 목적지가 선택되어도 다른 병원의 `PENDING`·`ACCEPTED` 카드는 `ACTIVE`에 남습니다.
- `PENDING` 병원은 계속 수락·거절할 수 있고, `ACCEPTED` 병원은 계속 철회할 수 있습니다.
- 비목적지 병원은 목적지 선택 시점까지의 임상정보만 봅니다.
- 비목적지 병원은 정확한 구급차 위치와 동적 경로·ETA를 볼 수 없습니다.
- 슈퍼 관리자 API와 화면에는 변경이 없습니다.

## 화면 흐름

| 조건 | 카드 표시 | 가능한 명령 |
|---|---|---|
| `PENDING`, 목적지 없음 | 응답 대기 | 수락·거절 |
| `PENDING`, `EN_ROUTE`, `currentDestination=false` | 다른 병원으로 이동 중 · 응답 가능 | 수락·거절 |
| `ACCEPTED`, 목적지 없음 | 수락 완료 | 수락 철회 |
| `ACCEPTED`, `EN_ROUTE`, `currentDestination=false` | 수락 완료 · 다른 병원으로 이동 중 | 수락 철회 |
| `ACCEPTED`, `currentDestination=true` | 우리 병원으로 이동 중 | 수락 철회 |
| `HANDOFF_REQUESTED`, `currentDestination=true` | 인계 확인 대기 | 인계 확인 |
| `HANDOFF_REQUESTED`, `currentDestination=false` | 다른 병원 인계 진행 중 | 없음 |

`hospitalOutcome`, `offerStatus`, `transportRequestStatus`, `currentDestination`을 함께
사용합니다. 진행 중 비목적지 카드에 `NOT_SELECTED`를 사용하지 않습니다.

## 인증과 접근 범위

| 역할 | 인증 | 접근 범위 |
|---|---|---|
| 병원 관계자 | 웹 API Route가 Access Token을 Bearer로 전달 | 자기 병원 조직에 전달된 제안만 |
| 슈퍼 관리자 | 동일 | 이 기능 접근 불가 (`AUTH_003`) |

- Base URL: `http://13.124.194.249`
- Content-Type: `application/json`
- 시간: ISO-8601 UTC
- 브라우저에 Access Token을 저장하지 않고 기존 HttpOnly 쿠키·API Route 구조를 유지합니다.

## 조회 API

### `GET /api/v1/hospitals/me/offers?view=ACTIVE&page=0&size=20`

목적지 선택 뒤에도 자기 병원의 진행 중 `PENDING`·`ACCEPTED` 제안을 반환합니다.

```json
{
  "items": [
    {
      "offerId": "OFFER_UUID",
      "transportRequestId": "REQUEST_UUID",
      "transportRequestStatus": "EN_ROUTE",
      "offerStatus": "PENDING",
      "hospitalOutcome": "AWAITING_RESPONSE",
      "currentDestination": false,
      "canWithdraw": false,
      "ageStatus": "KNOWN",
      "ageYears": 54,
      "sex": "MALE",
      "preKtasClassificationStatus": "COMPLETED",
      "preKtasLevel": 2,
      "straightLineDistanceMeters": 2800,
      "lastClinicalUpdateAt": "2026-08-15T01:00:00Z",
      "offeredAt": "2026-08-15T00:59:00Z",
      "canConfirmHandoff": false
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "serverNow": "2026-08-15T01:05:00Z"
}
```

비목적지에서 다음 값은 `null`입니다. 이를 통신 실패로 처리하지 않습니다.

```text
routeEstimateStatus
routeDistanceMeters
etaSeconds
lastSuccessfulRouteDistanceMeters
lastSuccessfulEtaSeconds
lastSuccessfulEtaCalculatedAt
```

`GET ...?view=HISTORY`에는 거절·미응답·수락 철회 또는 완료·취소된 제안만
표시합니다. 진행 중 비목적지 제안은 HISTORY에 넣지 않습니다.

### `GET /api/v1/hospitals/me/offers/{offerId}`

- 현재 목적지: 최신 임상정보와 동적 경로·ETA를 반환합니다.
- 비목적지: 최초 목적지 선택 시점까지의 임상정보를 반환합니다.
- 목적지가 변경되면 이전 목적지는 변경 시점에 동결되고 새 목적지는 최신값을 봅니다.
- 비목적지 응답의 `route.status`와 동적 경로·ETA 필드는 `null`입니다.
- 실제 목적지 병원의 이름이나 식별자는 제공하지 않습니다.

### `GET /api/v1/hospitals/me/offers/{offerId}/clinical-timeline?page=0&size=50`

- 현재 목적지는 최신 snapshot과 전체 임상 이력을 조회합니다.
- 비목적지는 자기 병원의 공개 종료 시각 이전 snapshot과 이력만 조회합니다.
- 이후 임상정보가 추가돼도 `totalElements`와 `latestSnapshot.lastClinicalUpdateAt`은
  동결된 상태를 유지합니다.

### `GET /api/v1/hospitals/me/offers/{offerId}/location`

- 현재 목적지만 `200 OK`로 정확한 위치와 ETA를 조회합니다.
- 비목적지는 `404 TRANSPORT_005`를 받습니다.

## 명령 API

모든 명령은 `Idempotency-Key` 헤더가 필요합니다. 길이는 8~100자이며
`[A-Za-z0-9._:-]`만 사용합니다. 네트워크 재시도에는 같은 키와 요청을 사용합니다.

| API | 요청 본문 | 허용 조건 |
|---|---|---|
| `POST .../{offerId}/accept` | 없음 | `PENDING`, `HANDOFF_REQUESTED` 이전 |
| `POST .../{offerId}/reject` | `{"reason":"SPECIALIST_UNAVAILABLE","detail":null}` | `PENDING`, `HANDOFF_REQUESTED` 이전 |
| `POST .../{offerId}/withdraw-acceptance` | `{"reason":"BED_SHORTAGE","detail":null}` | `ACCEPTED`, `HANDOFF_REQUESTED` 이전 |
| `POST .../{offerId}/confirm-handoff` | 없음 | 현재 목적지이며 `HANDOFF_REQUESTED` |

거절 사유:

```text
ER_GENERAL_BED_SHORTAGE
ISOLATION_BED_SHORTAGE
OPERATING_ROOM_SHORTAGE
ICU_SHORTAGE
SPECIALIST_UNAVAILABLE
EQUIPMENT_UNAVAILABLE
OTHER
```

수락 철회 사유:

```text
BED_SHORTAGE
OPERATING_ROOM_SHORTAGE
SPECIALIST_UNAVAILABLE
EQUIPMENT_UNAVAILABLE
OTHER
```

`OTHER`는 공백이 아닌 `detail`이 필요하며 최대 200자입니다.
버튼 활성화는 응답의 `canWithdraw`, `canConfirmHandoff`를 우선합니다.

## 오류 처리

| 코드 | HTTP | 조건 | 웹 처리 |
|---|---:|---|---|
| `AUTH_001`, `AUTH_002` | 401 | 인증 실패 | 기존 토큰 갱신·로그인 흐름 |
| `AUTH_003` | 403 | 역할 불일치 | 접근 차단 |
| `TRANSPORT_005` | 404 | 다른 조직 제안 또는 비목적지 위치 | 목록 재조회, 위치 화면 미표시 |
| `TRANSPORT_006` | 409 | 이미 처리된 제안 | 목록·상세 재조회 |
| `TRANSPORT_004` | 409 | 인계 요청 뒤 명령 | 버튼 비활성화 후 목록 재조회 |
| `COMMON_005` | 409 | 같은 멱등 키에 다른 요청 | 최초 요청 복구 또는 새 키 사용 |

공통 오류 응답의 `code`, `message`, `fieldErrors`, `traceId`와 응답 헤더
`X-Trace-Id` 계약은 유지됩니다.

## SSE와 재조회

`GET /api/v1/realtime/events`를 기존 방식으로 연결합니다.

- `DESTINATION_SELECTED`: 활성 제안을 가진 병원은 `ACTIVE` 목록을 다시 조회합니다.
- `DESTINATION_CHANGED`: 이전 목적지와 새 목적지는 목록·상세를 다시 조회합니다.
- 임상 갱신 이벤트는 현재 목적지에만 전달됩니다.
- 재연결·브라우저 복귀 때는 `ACTIVE` 목록 전체를 다시 조회합니다.
- 목적지 이벤트의 `aggregateId`는 제안 ID가 아닐 수 있으므로 상세 ID로 사용하지 않습니다.
- 이벤트에는 환자정보, 좌표와 목적지 병원 식별정보가 없습니다.

## 연동 확인

- [ ] A 목적지 선택 뒤 B 수락·C 응답 대기 카드가 ACTIVE에 유지됨
- [ ] C의 늦은 수락·거절과 B의 수락 철회가 처리됨
- [ ] 비목적지 상세·timeline이 동결되고 위치가 404임
- [ ] 비목적지 동적 ETA가 빈 값이어도 오류 화면이 나오지 않음
- [ ] 목적지 변경 뒤 이전·새 목적지 표시가 전환됨
- [ ] HANDOFF_REQUESTED에서 비목적지 명령이 비활성화됨
- [ ] 슈퍼 관리자와 다른 병원 조직의 접근이 차단됨
- [ ] SSE 재연결 뒤 ACTIVE 목록으로 상태가 복구됨
