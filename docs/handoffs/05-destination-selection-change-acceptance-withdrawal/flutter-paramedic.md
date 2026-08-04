# 목적지 선택·변경 및 수락 철회 Flutter 구급대원 앱 핸드오프

```text
Feature: destination-selection-change-acceptance-withdrawal
Backend Feature: docs/features/05-destination-selection-change-acceptance-withdrawal/
Related PR: NONE
Available After: MAIN_MERGE
Breaking Change: NO
```

## 변경 요약

- 구급대원은 자기 이송 요청을 수락한 병원 중 한 곳을 현재 목적지로 선택하고,
  아직 수락 상태인 다른 병원으로 변경할 수 있습니다.
- 기존 병원 탐색 현황 응답에 현재 목적지와 병원별 목적지 여부, 수락 철회
  사유·시각이 추가됩니다.
- 병원이 수락을 철회하면 해당 병원은 더 이상 목적지로 선택할 수 없습니다.
- 현재 목적지 병원이 철회하면 목적지가 즉시 해제되고 같은 이송 요청으로 병원
  탐색이 다시 시작됩니다. 다른 수락 병원이 남아 있으면 바로 선택할 수도 있습니다.
- SSE는 변경 사실만 알립니다. 이벤트 또는 목적지 명령 응답을 받은 뒤 병원 탐색
  현황 API를 다시 조회한 결과를 최종 상태로 사용합니다.
- 환자 평가·이송 요청 생성은 기능 2, 최초 병원 탐색·응답은 기능 3 핸드오프를
  함께 사용합니다.

## 사용자 흐름

| 순서 | 앱·사용자 동작 | API | 성공 후 상태 |
|---:|---|---|---|
| 1 | 수락 병원과 현재 목적지 조회 | `GET .../{requestId}/hospital-search` | `ACCEPTED` 병원과 `currentDestinationOfferId` 표시 |
| 2 | 수락 병원 한 곳을 목적지로 선택 | `POST .../{requestId}/destination` | 요청 `EN_ROUTE`, 선택 병원이 유일한 현재 목적지 |
| 3 | 다른 수락 병원으로 변경 | 같은 목적지 API에 다른 `offerId` | 이전 병원 수락 이력 유지, 새 병원이 현재 목적지 |
| 4 | SSE 변경 신호 수신 | 병원 탐색 현황 재조회 | 병원 철회·목적지 변경의 최신 상태 반영 |
| 5 | 현재 목적지 병원 철회 확인 | 병원 탐색 현황 재조회 | 목적지 해제, `ACCEPTED_AVAILABLE` 또는 `SEARCHING` |
| 6 | 남은 수락 병원 선택 또는 재탐색 대기 | 목적지 API 또는 현황 재조회 | 선택 시 `EN_ROUTE`, 탐색 중이면 새 후보 응답 대기 |

## 공통 계약

- Base URL: `http://13.124.194.249`
- 인증: `Authorization: Bearer {accessToken}`
- 역할: `PARAMEDIC`
- 시간: ISO-8601 UTC
- `requestId`는 이송 요청 생성 응답의 `transportRequestId`입니다.
- 목적지 조회·명령은 요청을 만든 구급대원 계정과 EMS 조직에만 허용됩니다.
- 다른 구급대원의 요청은 `TRANSPORT_001`로 숨깁니다.

공통 오류:

```json
{
  "code": "ERROR_CODE",
  "message": "사용자에게 표시 가능한 메시지",
  "fieldErrors": [],
  "traceId": "TRACE_ID"
}
```

## API 1. 수락 병원·현재 목적지 조회

### `GET /api/v1/transport-requests/{requestId}/hospital-search`

- 성공: `200 OK`
- 기존 기능 3 응답에 아래 목적지·철회 필드가 추가됩니다.
- 화면 진입, 앱 복귀, 목적지 명령 성공, SSE 수신·재연결 뒤 호출합니다.

```json
{
  "transportRequestId": "REQUEST_UUID",
  "status": "EN_ROUTE",
  "currentDestinationOfferId": "OFFER_B_UUID",
  "currentAttempt": {
    "dispatchAttemptId": "ATTEMPT_UUID",
    "number": 1,
    "status": "STOPPED_ON_ACCEPTANCE",
    "currentRadiusKm": 20,
    "candidateShortage": true,
    "nextExpansionAt": null,
    "startedAt": "2026-08-04T03:09:00Z",
    "endedAt": "2026-08-04T03:10:00Z"
  },
  "exhaustionReason": null,
  "offers": [
    {
      "offerId": "OFFER_A_UUID",
      "dispatchAttemptNumber": 1,
      "hospitalName": "A병원",
      "hospitalContact": "02-0000-0001",
      "status": "ACCEPTED",
      "currentDestination": false,
      "straightLineDistanceMeters": 5230,
      "routeEstimateStatus": "AVAILABLE",
      "routeDistanceMeters": 6840,
      "etaSeconds": 780,
      "etaCalculatedAt": "2026-08-04T03:09:03Z",
      "rejectionReason": null,
      "rejectionDetail": null,
      "withdrawalReason": null,
      "withdrawalDetail": null,
      "offeredAt": "2026-08-04T03:09:00Z",
      "respondedAt": "2026-08-04T03:10:00Z",
      "withdrawnAt": null,
      "closedAt": null
    },
    {
      "offerId": "OFFER_B_UUID",
      "dispatchAttemptNumber": 1,
      "hospitalName": "B병원",
      "hospitalContact": "02-0000-0002",
      "status": "ACCEPTED",
      "currentDestination": true,
      "withdrawalReason": null,
      "withdrawalDetail": null,
      "respondedAt": "2026-08-04T03:10:10Z",
      "withdrawnAt": null
    }
  ],
  "serverNow": "2026-08-04T03:11:00Z"
}
```

### 목적지 필드

| 필드 | 타입 | Nullable | 처리 |
|---|---|---:|---|
| `currentDestinationOfferId` | string | YES | `null`이면 현재 목적지 없음 |
| `offers[].currentDestination` | boolean | NO | `true`인 제안은 최대 한 개 |
| `offers[].withdrawalReason` | enum | YES | 철회 상태에서 사유 표시 |
| `offers[].withdrawalDetail` | string | YES | `OTHER` 철회 상세, 최대 200자 |
| `offers[].withdrawnAt` | datetime | YES | 서버가 기록한 철회 시각 |

`ACCEPTANCE_WITHDRAWN` 병원은 응답 이력에는 남지만 선택 가능한 병원 목록에서는
제외합니다. 해당 병원의 `hospitalContact`는 반환하지 않습니다.

### 추가 상태

| 구분 | 값 | 의미 |
|---|---|---|
| 병원 제안 | `ACCEPTANCE_WITHDRAWN` | 병원이 기존 수락을 철회함; 목적지 선택 불가 |
| 탐색 회차 | `STOPPED_ON_DESTINATION` | 철회 후 재탐색 중 다른 수락 병원을 목적지로 선택해 확대 중단 |

수락 철회 사유:

```text
BED_SHORTAGE
OPERATING_ROOM_SHORTAGE
SPECIALIST_UNAVAILABLE
EQUIPMENT_UNAVAILABLE
OTHER
```

## API 2. 목적지 선택·변경

### `POST /api/v1/transport-requests/{requestId}/destination`

- 성공·동일 목적지·동일 재시도: `200 OK`
- 헤더: `Idempotency-Key` 필수
- 허용 요청 상태: `ACCEPTED_AVAILABLE`, `EN_ROUTE`
- 대상: 같은 요청에 속한 `ACCEPTED` 제안

```http
Authorization: Bearer ACCESS_TOKEN
Idempotency-Key: destination-20260804-01
Content-Type: application/json
```

```json
{
  "offerId": "OFFER_UUID"
}
```

응답:

```json
{
  "transportRequestId": "REQUEST_UUID",
  "transportRequestStatus": "EN_ROUTE",
  "selectedDestinationOfferId": "OFFER_B_UUID",
  "previousDestinationOfferId": "OFFER_A_UUID",
  "resultType": "CHANGED",
  "changedAt": "2026-08-04T03:12:00Z",
  "idempotentReplay": false
}
```

| `resultType` | 의미 | 앱 처리 |
|---|---|---|
| `SELECTED` | 현재 목적지가 없던 요청의 최초 선택 | 선택 결과 표시 후 현황 재조회 |
| `CHANGED` | 기존 목적지에서 다른 수락 병원으로 변경 | 이전·새 목적지 표시 갱신 후 재조회 |
| `UNCHANGED` | 이미 현재 목적지인 병원을 새 키로 다시 선택 | 상태 변화 없이 현황 재조회 |

- `previousDestinationOfferId`는 최초 선택이면 `null`입니다.
- 같은 요청 ID·같은 키·같은 `offerId` 재시도는 최초 결과와
  `idempotentReplay: true`를 반환합니다.
- 응답 유실 시 같은 키와 같은 본문으로 재시도합니다.
- 같은 키로 다른 `offerId`를 보내지 않습니다.
- 명령 응답은 최초 처리 결과입니다. 다른 병원의 동시 철회 가능성이 있으므로
  성공 직후에도 병원 탐색 현황을 다시 조회합니다.

### 목적지 오류

| 코드 | HTTP | 조건 | 앱 처리 |
|---|---:|---|---|
| `COMMON_001` | 400 | 키 형식 오류·키 누락·빈 `offerId` | 요청 수정, 유효한 새 키 사용 |
| `AUTH_001`, `AUTH_002` | 401 | 인증 없음·토큰 오류 | 토큰 갱신 후 같은 키·본문 재시도 |
| `AUTH_003`, `COMMON_004` | 403 | 구급대원 역할·활성 조직 조건 불일치 | 접근 차단, 운영자 확인 |
| `USER_002` | 403 | 비활성 계정 | 운영자 확인 |
| `TRANSPORT_001` | 404 | 요청 없음·다른 구급대원 소유 | 요청 ID 확인, 다른 요청 정보 표시 금지 |
| `TRANSPORT_002` | 409 | 다른 요청 제안 또는 `ACCEPTED`가 아닌 병원 | 현황 재조회, 해당 병원 선택 해제 |
| `TRANSPORT_004` | 409 | 선택·변경할 수 없는 요청 상태 | 현황 재조회, 목적지 버튼 상태 갱신 |
| `COMMON_005` | 409 | 같은 키로 다른 목적지 명령 | 최초 명령 복구 또는 새 키 사용 |

`Idempotency-Key`는 8~100자이며 `[A-Za-z0-9._:-]`만 사용할 수 있습니다.

## 현재 목적지 병원 철회 후 복구

| 조회 결과 | 의미 | 앱 처리 |
|---|---|---|
| `currentDestinationOfferId: null`, `status: ACCEPTED_AVAILABLE` | 목적지는 철회됐지만 다른 수락 병원이 남음 | 남은 `ACCEPTED` 병원을 선택 가능하게 표시 |
| `currentDestinationOfferId: null`, `status: SEARCHING` | 다른 수락 병원이 없어 자동 재탐색 중 | 탐색 현황과 새 제안 응답 대기 |
| `status: CANDIDATES_EXHAUSTED` | 철회 복구 탐색에도 새 수락 후보가 없음 | 기존 후보 소진 계약 사용 |
| `currentAttempt.status: STOPPED_ON_DESTINATION` | 복구 탐색 중 목적지를 다시 선택함 | 새 반경 확대가 끝난 것으로 표시 |

- 철회 복구는 같은 `transportRequestId`를 유지합니다.
- 철회 병원과 이 요청에서 이미 연락한 병원에는 복구 탐색 요청을 다시 보내지 않습니다.
- `CANDIDATES_EXHAUSTED` 뒤에도 기존 `PENDING` 병원이 늦게 수락할 수 있습니다.
  이 경우 요청은 `ACCEPTED_AVAILABLE`로 복구되므로 수락 병원 목록과 선택 버튼을 다시 표시합니다.
- 수동 재탐색 중 기존 `PENDING` 병원이 늦게 수락하면 요청은
  `ACCEPTED_AVAILABLE`로 바뀌고 진행 중인 수동 회차는 `STOPPED_ON_ACCEPTANCE`로
  종료됩니다. 현황을 재조회해 새 수락 병원 선택 화면으로 전환합니다.
- 비목적지 수락 병원이 철회해도 현재 목적지가 유지되면 `EN_ROUTE`를 유지하고
  재탐색하지 않습니다.
- 남은 수락 병원의 목적지 선택과 해당 병원의 철회가 동시에 들어오면 선택이 먼저
  성공한 뒤 곧 철회되거나, 선택 요청이 `TRANSPORT_002` 또는 `TRANSPORT_004`로
  거절될 수 있습니다. 두 경우 모두 최종 목적지는 비워지고 진행 중인 재탐색은
  하나만 유지되므로 현황 API 결과로 화면을 갱신합니다.

## 실시간 이벤트와 재조회

### `GET /api/v1/realtime/events`

구급대원 계정이 추가로 받는 `type`:

```text
DESTINATION_SELECTED
DESTINATION_CHANGED
HOSPITAL_ACCEPTANCE_WITHDRAWN
```

```text
id: EVENT_UUID
event: update
data: {"eventId":"EVENT_UUID","type":"HOSPITAL_ACCEPTANCE_WITHDRAWN","aggregateType":"HOSPITAL_OFFER","aggregateId":"OFFER_UUID","occurredAt":"2026-08-04T03:15:00Z"}
```

- 세 이벤트 모두 `GET .../{requestId}/hospital-search`를 다시 조회하는 신호입니다.
- 이벤트에는 환자정보·연락처·좌표·철회 상세가 없습니다.
- 중복·누락을 허용하고 REST 응답을 최종 기준으로 사용합니다.
- 연결 종료·앱 복귀·Access Token 갱신 뒤 SSE를 재연결하고 현황을 다시 조회합니다.

## 모바일 상태 복구

| 상황 | 서버 계약 | 앱 처리 |
|---|---|---|
| 목적지 명령 응답 유실 | 같은 키·같은 본문은 최초 결과 반환 | 키와 본문을 유지해 재시도 |
| 목적지 명령과 철회 경합 | 서버가 순서대로 확정하고 뒤 명령은 최신 상태 검증 | 오류 또는 성공 뒤 현황 재조회 |
| 목적지 병원 철회 | 목적지 해제와 재탐색 회차가 한 트랜잭션으로 저장 | SSE가 없어도 화면 복귀 시 현황으로 복구 |
| SSE 누락·중복 | SSE는 권위 상태가 아님 | 현황 API 결과로 화면 상태 덮어쓰기 |
| 앱 재실행 | 요청·목적지·철회·탐색 이력은 DB에 보존 | 저장한 `requestId`로 현황 조회 후 SSE 연결 |

## 연동 확인

- [ ] 수락 병원 중 최초 목적지 선택과 `SELECTED`
- [ ] 다른 수락 병원으로 변경과 `CHANGED`
- [ ] 같은 목적지 재선택 `UNCHANGED`, 같은 키 재시도 `idempotentReplay`
- [ ] `currentDestinationOfferId`와 병원별 `currentDestination` 일치
- [ ] 철회 병원을 선택 목록에서 제외하고 철회 사유·시각 표시
- [ ] 현재 목적지 철회 후 남은 수락 선택 또는 자동 재탐색 표시
- [ ] 비목적지 철회에서는 현재 목적지와 `EN_ROUTE` 유지
- [ ] 목적지·철회 SSE 수신 뒤 병원 탐색 현황 재조회
- [ ] 실제 환자정보·개인 연락처·정확한 위치가 아닌 테스트 데이터로 Dev 연동
