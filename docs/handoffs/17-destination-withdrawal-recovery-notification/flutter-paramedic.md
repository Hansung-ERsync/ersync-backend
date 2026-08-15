# 목적지 철회 복구 재알림 Flutter 구급대원 앱 핸드오프

```text
Feature: destination-withdrawal-recovery-notification
Backend Feature: docs/features/17-destination-withdrawal-recovery-notification/
Related PR: NONE
Available After: MAIN_MERGE
Breaking Change: NO
```

> 수락 병원의 주소·상세주소·좌표 필드는
> `docs/handoffs/19-hospital-detail-address/flutter-paramedic.md`가 최신 기준입니다.

## 변경 요약

- 이동 중인 목적지 병원이 수락을 철회하면 목적지가 즉시 해제됩니다.
- 기존 수락 병원은 계속 선택할 수 있고, 복구 검색에서 수락한 새 병원도 직접 선택할 수 있습니다.
- 서버는 목적지를 자동 선택하지 않습니다.
- 후보 소진·전체 재전송·전화 연결 상태는 추가되지 않았습니다.

## 사용자 흐름

| 순서 | 앱 동작 | API·신호 | 권위 상태 |
|---:|---|---|---|
| 1 | A병원으로 이동 중 | 기존 탐색 조회 | `EN_ROUTE`, 목적지 A |
| 2 | A병원 긴급 철회 수신 | `HOSPITAL_ACCEPTANCE_WITHDRAWN` | 탐색 현황 재조회 필요 |
| 3 | 목적지 해제 화면 표시 | 탐색 현황 GET | 목적지 `null`, 복구 회차 |
| 4 | B·C 등 수락 병원 확인 | 같은 GET 반복 | `ACCEPTED` 카드 유지·추가 |
| 5 | 구급대원이 새 병원 선택 | 기존 목적지 선택 POST | 선택 병원으로 `EN_ROUTE` |

철회 뒤 권장 문구는 `새로운 목적지를 찾고 있습니다`입니다. 별도
`현재 수락한 병원이 없습니다` 상태는 사용하지 않습니다.

## 인증과 접근

| 항목 | 계약 |
|---|---|
| Base URL | `http://13.124.194.249` |
| 인증 | `Authorization: Bearer {accessToken}` |
| 역할 | `PARAMEDIC` |
| 소유권 | 로그인한 구급대원이 자기 EMS 조직에서 생성한 요청만 조회·선택 |

## 탐색 현황

### `GET /api/v1/transport-requests/{requestId}/hospital-search`

- 성공: `200 OK`
- 호출 시점: 화면 진입, SSE 수신, 재연결, 앱 복구 뒤

철회 복구 예시:

> 화면 분기에 필요한 일부 필드만 표시한 예시입니다. `offers` 각 항목에는 기존
> 거리·ETA·거절·철회·응답 시각 필드도 함께 반환됩니다.

```json
{
  "transportRequestId": "REQUEST_UUID",
  "status": "ACCEPTED_AVAILABLE",
  "currentDestinationOfferId": null,
  "currentAttempt": {
    "dispatchAttemptId": "ATTEMPT_UUID",
    "number": 2,
    "status": "SEARCHING",
    "triggerType": "ACCEPTANCE_WITHDRAWAL",
    "currentRadiusKm": 10,
    "candidateShortage": false,
    "nextExpansionAt": "2026-08-15T05:01:00Z",
    "startedAt": "2026-08-15T05:00:00Z",
    "endedAt": null
  },
  "exhaustionReason": null,
  "offers": [
    {
      "offerId": "B_OFFER_UUID",
      "hospitalName": "B병원",
      "status": "ACCEPTED",
      "currentDestination": false
    },
    {
      "offerId": "C_OFFER_UUID",
      "hospitalName": "C병원",
      "status": "PENDING",
      "currentDestination": false
    }
  ]
}
```

| 필드·조건 | 앱 처리 |
|---|---|
| `currentDestinationOfferId=null` | 이동 중 목적지 해제, 새 목적지 선택 화면 표시 |
| `currentAttempt.triggerType=ACCEPTANCE_WITHDRAWAL` | 현재 회차가 목적지 철회 복구임을 구분 |
| `status=ACCEPTED_AVAILABLE` | 남은 수락 병원 카드를 즉시 선택 가능 |
| `status=SEARCHING` | 병원 응답을 계속 기다림 |
| 제안 `ACCEPTED` | 선택 가능한 병원 카드 표시 |
| 제안 `PENDING` | 응답 대기 상태 유지 |
| 철회한 A 제안 | 상태·철회 사유 표시, 다시 선택 금지 |

## 목적지 선택

### `POST /api/v1/transport-requests/{requestId}/destination`

```json
{
  "offerId": "ACCEPTED_OFFER_UUID"
}
```

| 항목 | 계약 |
|---|---|
| 필수 헤더 | `Idempotency-Key: {unique-key}` |
| 성공 | `200 OK` |
| 허용 대상 | 같은 요청의 `ACCEPTED` 제안 |
| 재시도 | 응답 유실 시 같은 key·같은 본문 사용 |

성공 응답:

```json
{
  "transportRequestId": "REQUEST_UUID",
  "transportRequestStatus": "EN_ROUTE",
  "selectedDestinationOfferId": "B_OFFER_UUID",
  "previousDestinationOfferId": null,
  "resultType": "SELECTED",
  "changedAt": "2026-08-15T05:02:00Z",
  "idempotentReplay": false
}
```

`previousDestinationOfferId`만 최초 선택에서 nullable입니다. `resultType`은 `SELECTED`,
`CHANGED`, `UNCHANGED` 중 하나입니다. 나머지 필드는 null이 아닙니다.

## SSE와 상태 복구

```text
GET /api/v1/realtime/events
Authorization: Bearer {accessToken}
Content-Type: text/event-stream
SSE event name: update
```

목적지 A 철회 신호의 data 예시:

```json
{
  "eventId": "EVENT_UUID",
  "type": "HOSPITAL_ACCEPTANCE_WITHDRAWN",
  "aggregateType": "HOSPITAL_OFFER",
  "aggregateId": "A_OFFER_UUID",
  "occurredAt": "2026-08-15T05:00:00Z"
}
```

이벤트에는 임상정보와 좌표가 없습니다. 이벤트의 aggregate만으로 화면 상태를 확정하지
말고 탐색 현황 API를 다시 호출합니다. SSE 중복·유실·재연결에도 같은 재조회 규칙을 사용합니다.

## 오류

| 코드 | HTTP | 앱 처리 |
|---|---:|---|
| `AUTH_001`, `AUTH_002` | 401 | 로그인 또는 토큰 갱신 뒤 재조회 |
| `AUTH_003`, `USER_002` | 403 | 접근 차단·운영 확인 |
| `COMMON_001` | 400 | `offerId`와 멱등 키 형식 확인 |
| `COMMON_004` | 403 | 계정·EMS 조직 권한 확인 |
| `TRANSPORT_001` | 404 | 요청 정보 제거, 다른 요청 추정 금지 |
| `TRANSPORT_004` | 409 | 최신 탐색 상태 재조회 |
| `TRANSPORT_002` | 409 | 해당 병원이 아직 수락 상태인지 재조회 |
| `COMMON_005` | 409 | 같은 멱등 키 본문 확인 뒤 새 키 또는 권위 조회 |

## 연동 확인

- [ ] A 목적지 철회 뒤 전체 화면 경고와 목적지 해제
- [ ] B 기존 수락 카드 즉시 선택 가능
- [ ] C·신규 병원 수락 뒤 자동 선택되지 않음
- [ ] 새 목적지 직접 선택 후 이동 중 상태 복구
- [ ] SSE 재연결·앱 재실행 뒤 권위 조회 복구
- [ ] 후보 소진·전체 재전송·전화 연결 UI 미사용
- [ ] dev API 연결
