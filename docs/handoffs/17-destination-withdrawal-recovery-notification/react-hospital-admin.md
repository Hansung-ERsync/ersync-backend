# 목적지 철회 복구 재알림 React 병원 웹 핸드오프

> **부분 대체:** `REJECTED` HISTORY의 환자정보 공개 범위와 상세 조회 계약은
> [기능 18 핸드오프](../18-rejected-hospital-history-privacy/react-hospital-admin.md)를 따릅니다.

```text
Feature: destination-withdrawal-recovery-notification
Backend Feature: docs/features/17-destination-withdrawal-recovery-notification/
Related PR: NONE
Available After: MAIN_MERGE
Breaking Change: NO
Hospital Impact: YES
Admin Impact: NONE
```

## 변경 요약

- 목적지 철회 뒤 기존 `PENDING` 병원은 같은 카드로 다시 요청받습니다.
- 재요청 카드는 `reRequested=true`와 `lastRequestedAt`으로 구분합니다.
- 다른 `ACCEPTED` 병원은 수락 상태를 유지하며 상태 변경 신호 뒤 목록을 재조회합니다.
- `REJECTED`, `ACCEPTANCE_WITHDRAWN`, 취소·완료 제안은 재요청되지 않습니다.
- 슈퍼 관리자 API와 화면에는 영향이 없습니다.

## 병원 사용자 흐름

| 병원 상태 | 수신 이벤트 | 재조회 | 결과 |
|---|---|---|---|
| C `PENDING` | `TRANSPORT_REQUEST_RECEIVED` | ACTIVE 목록·상세 | 같은 카드에 재요청 표시 |
| B `ACCEPTED` | `HOSPITAL_ACCEPTANCE_WITHDRAWN` | ACTIVE 목록 | 수락 유지, 다른 목적지 철회 확인 |
| A 철회 병원 | `HOSPITAL_ACCEPTANCE_WITHDRAWN` | HISTORY 목록 | 수용 불가 고지 이력 |
| D `REJECTED` | 없음 | 필요 없음 | 기존 거절 이력 유지 |

## 인증과 접근

| 역할 | 인증 | 허용 범위 |
|---|---|---|
| `HOSPITAL_STAFF` | `Authorization: Bearer {accessToken}` | 자기 병원 조직에 전달된 제안만 |
| `SUPER_ADMIN` | 동일 JWT 방식 | 이 기능 접근 불가, 환자 임상·위치 조회 금지 |

- Base URL: `http://13.124.194.249`
- 시간: ISO-8601 UTC
- 다른 조직의 `offerId`는 `TRANSPORT_005`로 숨깁니다.

## 활성 목록

### `GET /api/v1/hospitals/me/offers?view=ACTIVE&page={page}&size={size}`

추가 필드:

| 필드 | 타입 | Nullable | 의미 |
|---|---|---:|---|
| `reRequested` | boolean | 아니요 | `renotificationCount > 0` |
| `lastRequestedAt` | ISO-8601 string | 아니요 | 최초 또는 가장 최근 요청 시각 |

> 아래 JSON은 재요청 분기에 필요한 일부 필드만 표시합니다. 실제 목록 응답에는 기존
> 임상 요약·거리·ETA·처리 결과·철회·인계 시각 필드도 함께 반환됩니다.

```json
{
  "items": [
    {
      "offerId": "C_OFFER_UUID",
      "transportRequestId": "REQUEST_UUID",
      "transportRequestStatus": "ACCEPTED_AVAILABLE",
      "offerStatus": "PENDING",
      "currentDestination": false,
      "offeredAt": "2026-08-15T04:50:00Z",
      "reRequested": true,
      "lastRequestedAt": "2026-08-15T05:00:00Z"
    }
  ]
}
```

최초 요청은 `reRequested=false`, `lastRequestedAt=offeredAt`입니다. 재요청에서도
`offerId`와 `offeredAt`은 바뀌지 않습니다.

## 제안 상세

### `GET /api/v1/hospitals/me/offers/{offerId}`

`timing` 추가 필드:

```json
{
  "timing": {
    "requestReceivedAt": "2026-08-15T04:49:59Z",
    "offeredAt": "2026-08-15T04:50:00Z",
    "reRequested": true,
    "lastRequestedAt": "2026-08-15T05:00:00Z",
    "lastClinicalUpdateAt": "2026-08-15T04:59:50Z"
  }
}
```

- 재요청된 `PENDING`은 재요청 시점까지의 최소 임상정보를 조회합니다.
- 재요청 뒤 추가된 임상정보는 목적지 선택 전까지 보이지 않습니다.
- 정확한 구급차 위치는 현재 목적지 병원만 조회할 수 있습니다.
- 거리·ETA는 재요청 시 최신 저장 위치를 기준으로 다시 계산될 수 있습니다.

## 기존 응답 API

| 동작 | API | 성공 | 요청 본문 |
|---|---|---:|---|
| 수락 | `POST /api/v1/hospitals/me/offers/{offerId}/accept` | 200 | 없음 |
| 거절 | `POST /api/v1/hospitals/me/offers/{offerId}/reject` | 200 | `reason`, `detail` |
| 수락 철회 | `POST /api/v1/hospitals/me/offers/{offerId}/withdraw-acceptance` | 200 | `reason`, `detail` |

재요청된 `PENDING`도 같은 `offerId`와 API로 응답합니다. 응답 유실 시 같은 멱등 키와
같은 본문으로 재시도합니다.

공통 헤더:

```text
Authorization: Bearer {accessToken}
Idempotency-Key: {unique-key}
Content-Type: application/json
```

거절 본문:

```json
{
  "reason": "ER_GENERAL_BED_SHORTAGE",
  "detail": null
}
```

`reason`은 `ER_GENERAL_BED_SHORTAGE`, `ISOLATION_BED_SHORTAGE`,
`OPERATING_ROOM_SHORTAGE`, `ICU_SHORTAGE`, `SPECIALIST_UNAVAILABLE`,
`EQUIPMENT_UNAVAILABLE`, `OTHER`입니다. `OTHER`일 때 `detail`은 1~200자 필수입니다.

철회 본문:

```json
{
  "reason": "BED_SHORTAGE",
  "detail": null
}
```

철회 `reason`은 `BED_SHORTAGE`, `OPERATING_ROOM_SHORTAGE`,
`SPECIALIST_UNAVAILABLE`, `EQUIPMENT_UNAVAILABLE`, `OTHER`입니다.

수락·거절 성공 응답은 `offerId`, `offerStatus`, `transportRequestId`,
`transportRequestStatus`, `respondedAt`, `idempotentReplay`를 반환하며 모두 null이 아닙니다.

철회 성공 응답은 별도 계약입니다.

```json
{
  "offerId": "A_OFFER_UUID",
  "offerStatus": "ACCEPTANCE_WITHDRAWN",
  "transportRequestId": "REQUEST_UUID",
  "transportRequestStatus": "ACCEPTED_AVAILABLE",
  "currentDestinationOfferId": null,
  "reason": "BED_SHORTAGE",
  "detail": null,
  "withdrawnAt": "2026-08-15T05:00:00Z",
  "searchRestarted": true,
  "idempotentReplay": false
}
```

철회 응답에서 `currentDestinationOfferId`와 `detail`만 nullable이며 `respondedAt`은
반환하지 않습니다.

## SSE와 재조회

```text
GET /api/v1/realtime/events
Authorization: Bearer {accessToken}
Content-Type: text/event-stream
SSE event name: update
```

모든 `update` data 형식:

```json
{
  "eventId": "EVENT_UUID",
  "type": "TRANSPORT_REQUEST_RECEIVED",
  "aggregateType": "HOSPITAL_OFFER",
  "aggregateId": "C_OFFER_UUID",
  "occurredAt": "2026-08-15T05:00:00Z"
}
```

| 이벤트 | aggregate | 대상 | 처리 |
|---|---|---|---|
| `TRANSPORT_REQUEST_RECEIVED` | `HOSPITAL_OFFER`, 자기 `offerId` | 재요청 `PENDING` | ACTIVE 목록·상세 재조회 |
| `HOSPITAL_ACCEPTANCE_WITHDRAWN` | `TRANSPORT_REQUEST`, `requestId` | 다른 `ACCEPTED` | ACTIVE 목록 재조회 |
| `HOSPITAL_ACCEPTANCE_WITHDRAWN` | `HOSPITAL_OFFER`, 자기 `offerId` | 철회 병원 | HISTORY 목록 재조회 |
| `ETA_UPDATED` | `HOSPITAL_OFFER`, 자기 `offerId` | ETA 대상 병원 | 카드·상세 재조회 |

SSE payload에는 환자정보와 좌표가 없습니다. REST 응답을 권위 상태로 사용합니다.

## 화면 상태

| 조건 | 웹 처리 |
|---|---|
| `PENDING`, `reRequested=true` | 같은 ACTIVE 카드에 재요청 표시, 수락·거절 유지 |
| `ACCEPTED`, 목적지 없음 | 수락 유지, `다른 병원으로 이동 중` 표시 해제 |
| 현재 병원이 새 목적지 | 최신 임상·현재 위치·인계 흐름 사용 |
| 다른 병원이 새 목적지 | ACTIVE 상태 유지, 최신 임상·정확한 위치 숨김 |
| `REJECTED` | HISTORY에 거절 사유·처리 시각만 표시하고 상세 링크 제거 |
| `ACCEPTANCE_WITHDRAWN` | HISTORY에 철회 사유 표시 |

## 오류

| 코드 | HTTP | 웹 처리 |
|---|---:|---|
| `AUTH_001`, `AUTH_002` | 401 | 로그인 또는 토큰 갱신 |
| `AUTH_003`, `USER_002` | 403 | 접근 차단·운영 확인 |
| `HOSPITAL_001`, `TRANSPORT_005` | 404 | 목록 복귀, 다른 조직 정보 추정 금지 |
| `TRANSPORT_004`, `TRANSPORT_006` | 409 | 최신 목록·상세 재조회 |
| `COMMON_001`, `COMMON_005` | 400/409 | 입력·멱등 키 확인 후 재조회 |

## 연동 확인

- [ ] C 재요청 카드가 같은 `offerId`로 갱신됨
- [ ] B 기존 수락 카드가 유지됨
- [ ] D 거절 카드에 재요청 신호·최신 임상정보 없음
- [ ] 재요청 C 수락·거절과 멱등 재시도
- [ ] 목적지 변경 뒤 정보 노출 범위 변경
- [ ] SSE 재연결 뒤 ACTIVE·상세 복구
- [ ] 슈퍼 관리자 영향 없음
- [ ] dev API 연결
