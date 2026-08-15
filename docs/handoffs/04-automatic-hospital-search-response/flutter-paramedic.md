# 자동 병원 탐색 및 병원 응답 Flutter 구급대원 앱 핸드오프

> **사용 중지:** 현재 백엔드의 이전 계약 기록입니다. 2026-08-13 개정 정책 구현과 핸드오프 갱신 전에는 후보 소진·무응답·전체 재전송·전화 연결을 새 프론트 기능으로 구현하지 않습니다.

```text
Feature: automatic-hospital-search-response
Backend Feature: docs/features/04-automatic-hospital-search-response/
Related PR: NONE
Available After: MAIN_MERGE
Breaking Change: NO
```

## 변경 요약

- 기존 기능 3의 이송 요청을 만들면 백엔드가 별도 명령 없이 병원 탐색 회차를
  자동 시작합니다.
- 앱은 검색 현황 API로 현재 반경, 후보 부족, 다음 확대 시각과 병원별 응답을
  조회할 수 있습니다.
- 첫 수락이 발생해도 목적지가 확정된 것은 아닙니다. 요청 상태는
  `ACCEPTED_AVAILABLE`이며 목적지 선택은 별도 기능입니다.
- 최대 100km까지 수락이 없으면 `CANDIDATES_EXHAUSTED`가 되고 같은 요청에 새
  탐색 회차를 만들 수 있습니다.
- SSE는 갱신 신호만 제공합니다. 이벤트 수신·재연결 뒤 검색 현황 API 결과를
  최종 상태로 사용합니다.
- 이 문서는 탐색 이후 계약입니다. 환자 평가와 요청 생성 본문은 기능 3
  `docs/handoffs/03-patient-assessment-transfer-request/flutter-paramedic.md`를 함께 사용합니다.

## 사용자 흐름

| 순서 | 앱·사용자 동작 | API | 성공 후 상태 |
|---:|---|---|---|
| 1 | 환자 평가와 위치로 이송 요청 생성 | 기존 `POST /api/v1/transport-requests` | 응답 `SEARCHING`, 서버가 자동 탐색 예약 |
| 2 | 검색 화면 진입·복구 | `GET .../{requestId}/hospital-search` | 현재 회차·반경·병원별 상태 표시 |
| 3 | 실시간 갱신 연결 | `GET /api/v1/realtime/events` | 자기 요청의 변경 신호 수신 |
| 4 | SSE 변경 신호 수신 | 검색 현황 다시 조회 | 수락·거절·무응답·ETA 갱신 반영 |
| 5 | 후보 소진 확인 | 검색 현황 조회 | 소진 사유와 전화 가능한 병원 연락처 표시 |
| 6 | 같은 요청 재전송 | `POST .../{requestId}/dispatch-attempts` | 요청 `SEARCHING`, 회차 번호 1 증가 |

## 공통 계약

- Base URL: `http://13.124.194.249`
- 인증: `Authorization: Bearer {accessToken}`
- 역할: `PARAMEDIC`
- 시간: ISO-8601 UTC
- `requestId`는 기능 3 생성 응답의 `transportRequestId`입니다.
- 다른 구급대원 또는 다른 EMS 조직의 요청은 `TRANSPORT_001`로 숨깁니다.
- 정확한 출발 좌표와 환자 직접 식별정보는 아래 응답과 SSE에 포함되지 않습니다.

공통 오류:

```json
{
  "code": "ERROR_CODE",
  "message": "사용자에게 표시 가능한 메시지",
  "fieldErrors": [],
  "traceId": "TRACE_ID"
}
```

## API 1. 병원 탐색 현황 조회

### `GET /api/v1/transport-requests/{requestId}/hospital-search`

- 성공: `200 OK`
- 인증·소유권: 로그인한 구급대원이 생성한 요청만 허용
- 앱 시작, 화면 복귀, SSE 수신, SSE 재연결 뒤 다시 호출합니다.

응답 예시:

```json
{
  "transportRequestId": "REQUEST_UUID",
  "status": "SEARCHING",
  "currentAttempt": {
    "dispatchAttemptId": "ATTEMPT_UUID",
    "number": 1,
    "status": "SEARCHING",
    "currentRadiusKm": 30,
    "candidateShortage": false,
    "nextExpansionAt": "2026-08-04T03:10:00Z",
    "startedAt": "2026-08-04T03:09:00Z",
    "endedAt": null
  },
  "exhaustionReason": null,
  "offers": [
    {
      "offerId": "OFFER_UUID",
      "dispatchAttemptNumber": 1,
      "hospitalName": "테스트병원",
      "hospitalContact": null,
      "status": "PENDING",
      "straightLineDistanceMeters": 5230,
      "routeEstimateStatus": "AVAILABLE",
      "routeDistanceMeters": 6840,
      "etaSeconds": 780,
      "etaCalculatedAt": "2026-08-04T03:09:03Z",
      "rejectionReason": null,
      "rejectionDetail": null,
      "offeredAt": "2026-08-04T03:09:00Z",
      "respondedAt": null,
      "closedAt": null
    }
  ],
  "serverNow": "2026-08-04T03:09:10Z"
}
```

### 요청 상태

| `status` | 의미 | 앱 표시 |
|---|---|---|
| `SEARCHING` | 자동 탐색·병원 응답 대기 중 | 현재 반경·다음 확대·병원별 상태 |
| `ACCEPTED_AVAILABLE` | 한 곳 이상 수락, 자동 확대 중단 | 수락 병원 선택 가능 표시 |
| `CANDIDATES_EXHAUSTED` | 최대 반경에서 수락 없음 | 소진 사유·전화·재전송 제공 |
| `EN_ROUTE`, `HANDOFF_REQUESTED`, `COMPLETED`, `CANCELLED` | 다른 기능의 후속 상태 | 해당 기능 계약 사용 |

### 탐색 회차 상태

| 값 | 의미 |
|---|---|
| `SEARCHING` | 반경 확대 또는 응답 대기 가능 |
| `STOPPED_ON_ACCEPTANCE` | 첫 수락으로 새 반경 확대 중단 |
| `EXHAUSTED` | 최대 반경 탐색 종료 |

### 병원 제안 상태

| 값 | 의미 | 연락처 노출 |
|---|---|---|
| `PENDING` | 병원 응답 대기 | `null` |
| `ACCEPTED` | 병원이 현재 수용 가능하다고 응답 | 병원 응급실 연락처 원문 |
| `REJECTED` | 병원이 사유와 함께 거절 | 검색 중 `null`, 요청 소진 뒤 원문 |
| `NO_RESPONSE` | 마지막 응답 창까지 미응답 | 요청 소진 뒤 원문 |

### 후보 부족·소진

- `candidateShortage: true`: 최대 반경까지 후보가 최소 3곳보다 적다는 뜻입니다.
  요청 상태가 `SEARCHING`이면 발견된 병원의 응답을 계속 기다릴 수 있습니다.
- `exhaustionReason`은 요청이 소진된 경우에만 다음 중 하나입니다.

| 값 | 의미 |
|---|---|
| `NO_CANDIDATES` | 100km 안에 적격 병원이 한 곳도 없음 |
| `ALL_REJECTED` | 전달받은 병원이 모두 거절 |
| `NO_RESPONSE_INCLUDED` | 마지막 응답 창까지 응답하지 않은 병원이 포함됨 |

### ETA 상태

| 값 | 필드 계약 |
|---|---|
| `CALCULATING` | 도로 거리·ETA·계산 시각은 `null`; 직선거리는 사용 가능 |
| `AVAILABLE` | `routeDistanceMeters`, `etaSeconds`, `etaCalculatedAt` 존재 |
| `UNAVAILABLE` | 네이버 계산 실패; 세 필드는 `null`, 병원 응답 흐름은 정상 |

`etaSeconds`는 교통상황에 따른 예상값이며 목적지 확정이나 이송 중 ETA가 아닙니다.

### 거절 사유

```text
ER_GENERAL_BED_SHORTAGE
ISOLATION_BED_SHORTAGE
OPERATING_ROOM_SHORTAGE
ICU_SHORTAGE
SPECIALIST_UNAVAILABLE
EQUIPMENT_UNAVAILABLE
OTHER
```

`OTHER`이면 `rejectionDetail`이 있으며 다른 사유의 상세는 `null`일 수 있습니다.

### 오류

| 코드 | HTTP | 조건 | 앱 처리 |
|---|---:|---|---|
| `AUTH_001`, `AUTH_002` | 401 | 인증 없음·토큰 오류 | 로그인 또는 토큰 갱신 후 재조회 |
| `AUTH_003` | 403 | 구급대원 역할 아님 | 접근 차단 |
| `USER_002` | 403 | 비활성 계정 | 운영자 확인 |
| `TRANSPORT_001` | 404 | 요청 없음 또는 소유권 불일치 | 현재 요청 ID 확인, 다른 요청 정보 표시 금지 |

## API 2. 후보 소진 요청 재전송

### `POST /api/v1/transport-requests/{requestId}/dispatch-attempts`

- 헤더: `Idempotency-Key` 필수
- 최초 성공: `201 Created`
- 같은 키 재시도: `200 OK`, 같은 `dispatchAttemptId`
- 허용 상태: `CANDIDATES_EXHAUSTED`만
- 요청 본문: 없음

```http
Idempotency-Key: retry-request-20260804-01
```

응답:

```json
{
  "transportRequestId": "REQUEST_UUID",
  "transportRequestStatus": "SEARCHING",
  "dispatchAttemptId": "NEW_ATTEMPT_UUID",
  "attemptNumber": 2,
  "attemptStatus": "SEARCHING",
  "currentRadiusKm": 0,
  "nextExpansionAt": "2026-08-04T03:20:00Z",
  "idempotentReplay": false
}
```

| 항목 | 계약 |
|---|---|
| 키 형식 | 8~100자, `[A-Za-z0-9._:-]` |
| 응답 유실 | 같은 요청 ID와 같은 키로 다시 호출 |
| 성공 뒤 | 검색 현황 API를 재조회하고 SSE 연결 유지 |
| 이전 결과 | 이전 회차의 거절·무응답은 삭제되지 않음 |
| 새 회차 | 이전 병원도 다시 후보가 될 수 있음 |

| 코드 | HTTP | 조건 | 앱 처리 |
|---|---:|---|---|
| `COMMON_001` | 400 | 멱등성 키 형식 오류 | 새 유효 키 사용 |
| `AUTH_001`, `AUTH_002` | 401 | 인증 오류 | 갱신 후 같은 키로 재시도 |
| `TRANSPORT_001` | 404 | 요청 없음·소유권 불일치 | 요청 ID 확인 |
| `TRANSPORT_004` | 409 | 후보 소진 상태가 아님 | 현황 재조회, 재전송 버튼 비활성화 |
| `COMMON_005` | 409 | 같은 키 충돌 | 원래 명령을 복구하거나 새 키 사용 |

## API 3. 실시간 갱신 신호

### `GET /api/v1/realtime/events`

- 요청 헤더: `Authorization: Bearer {accessToken}`
- 응답: `200 OK`, `Content-Type: text/event-stream`
- 토큰을 query string으로 보내는 계약은 없습니다.
- `Authorization` 헤더를 보낼 수 있는 스트리밍 HTTP 연결을 사용합니다.
- 서버 연결은 약 14분 뒤 종료될 수 있습니다. 새 Access Token으로 재연결합니다.
- 응답 헤더: `Cache-Control: no-cache`, `X-Accel-Buffering: no`

갱신 이벤트 예시:

```text
id: EVENT_UUID
event: update
data: {"eventId":"EVENT_UUID","type":"HOSPITAL_OFFER_ACCEPTED","aggregateType":"HOSPITAL_OFFER","aggregateId":"OFFER_UUID","occurredAt":"2026-08-04T03:12:00Z"}
```

구급대원이 받을 수 있는 `type`:

```text
HOSPITAL_OFFER_ACCEPTED
HOSPITAL_OFFER_REJECTED
HOSPITAL_OFFER_NO_RESPONSE
HOSPITAL_SEARCH_EXHAUSTED
HOSPITAL_SEARCH_RETRY_STARTED
ETA_UPDATED
```

- 이벤트에는 환자정보·연락처·좌표가 없습니다.
- 같은 `eventId`가 다시 와도 검색 현황을 한 번 갱신하는 신호로 취급할 수 있습니다.
- heartbeat 또는 `connected` 이벤트에는 상태 데이터가 없습니다.
- 연결 실패·종료·앱 재실행 뒤 재연결하고 반드시 검색 현황을 다시 조회합니다.

## 모바일 상태 복구

| 상황 | 서버 계약 | 앱 처리 |
|---|---|---|
| 요청 생성 직후 | 탐색 작업은 DB에 저장됨 | 생성 응답의 요청 ID로 현황 polling 또는 SSE 연결 |
| SSE보다 조회가 먼저 옴 | `currentRadiusKm: 0`일 수 있음 | 잠시 뒤 갱신 신호 또는 재조회 |
| SSE 누락·중복 | 이벤트는 권위 상태가 아님 | 검색 현황 조회 결과로 덮어씀 |
| 재전송 응답 유실 | 같은 키는 같은 회차 반환 | 같은 키로 재시도 |
| ETA 실패 | 제안 상태와 독립적으로 `UNAVAILABLE` | 직선거리와 병원 응답은 계속 표시 |
| Access Token 만료 | SSE·REST 인증 실패 | 토큰 갱신, SSE 재연결, 현황 재조회 |

## 연동 확인

- [ ] 기능 3 요청 생성 뒤 별도 시작 API 없이 현황 조회
- [ ] 0km 초기 작업 상태와 실제 선택 반경 갱신
- [ ] `PENDING`·`ACCEPTED`·`REJECTED`·`NO_RESPONSE` 표시
- [ ] 후보 부족과 세 가지 소진 사유 표시
- [ ] 수락 병원 연락처와 후보 소진 후 전화 연결
- [ ] ETA 세 상태와 `etaSeconds` 표시
- [ ] 재전송 201, 응답 유실 뒤 같은 키 200
- [ ] SSE Authorization, 재연결, 현황 재조회
- [ ] 실제 환자정보·개인번호가 아닌 테스트 데이터로 Dev 연동
