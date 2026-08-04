# 이송 취소·인계 완료 및 이력 Flutter 구급대원 앱 핸드오프

```text
Feature: transport-cancellation-handoff-history
Backend Feature: docs/features/08-transport-cancellation-handoff-history/
Related PR: NONE
Available After: MAIN_MERGE
Breaking Change: NO
```

> 구현과 로컬 검증이 끝난 실제 백엔드 계약입니다. 취소·인계 명령의 성공
> 응답을 받지 못하면 최초 `Idempotency-Key`와 본문을 그대로 유지해 재시도하고,
> 화면 복구와 SSE 수신 뒤에는 목록 REST 응답을 최종 상태로 사용합니다.

## 변경 요약

- 구급대원은 인계 요청 전까지 자기 이송 요청을 필수 사유와 함께 취소할 수 있습니다.
- `EN_ROUTE`에서 목적지 병원에 인계 확인을 요청할 수 있습니다.
- 본인의 활성·종료·홈 최근 이송을 페이지로 조회할 수 있습니다.
- 취소·인계 요청·완료 SSE 신호가 추가됐습니다.
- 기존 API는 제거하거나 호환되지 않게 변경하지 않았습니다.
- 앱 연동 시 다음 화면 보완이 필요합니다.
  - `OTHER` 취소 사유 선택 시 200자 이하 상세 입력
  - 병원으로 이동 중인 화면에도 인계 요청 전 취소 동작 제공
  - 최근 이송에 `CANCELLED` 상태 표시
  - 목적지 선택 전 취소를 위해 `hospitalName`을 nullable로 처리

## 사용자 흐름

| 순서 | 사용자·앱 동작 | API 호출 | 성공 후 상태 |
|---:|---|---|---|
| 1 | 활성 이송 또는 최근 이송 복구 | `GET /api/v1/transport-requests?view=...` | 서버의 최신 요청 상태 표시 |
| 2-A | 취소 사유 선택 후 이송 취소 | `POST .../{requestId}/cancel` | `CANCELLED`, 활성 화면에서 제거 |
| 2-B | 목적지 도착 후 인계 요청 | `POST .../{requestId}/handoff-request` | `HANDOFF_REQUESTED`, 인계 대기 표시 |
| 3 | 취소·인계 SSE 수신 | 목록 API 재조회 | 최신 종료·대기 상태 반영 |
| 4 | 목적지 병원이 인계 확인 | 앱의 직접 호출 없음 | `COMPLETED`, 최근 이력에 인계 완료 표시 |

## 인증과 접근 범위

| 항목 | 계약 |
|---|---|
| 인증 | `Authorization: Bearer {accessToken}` |
| 역할 | 활성 `PARAMEDIC` 계정 |
| 조직·소유권 | 서버가 EMS 조직과 요청 생성 계정을 검증하며 자기 요청만 명령·조회 가능 |
| 민감정보 | 목록과 SSE에 환자 임상정보·회신 연락처·정확한 좌표·내부 DB PK 없음 |

## API

공통:

- Base URL: `http://13.124.194.249`
- Content-Type: `application/json`
- 시간: ISO-8601 UTC
- 명령 API의 `Idempotency-Key`: 8~100자, `[A-Za-z0-9._:-]`
- JSON의 nullable 필드는 서버 설정에 따라 값이 `null`이면 생략될 수 있습니다.

### `POST /api/v1/transport-requests/{requestId}/cancel`

- 목적: 인계 요청 전 자기 이송 취소
- 인증·역할: Bearer, `PARAMEDIC`, 요청 소유자
- 성공 HTTP: `200 OK`

#### 파라미터

| 위치 | 이름 | 타입 | 필수 | Nullable | 제약 |
|---|---|---|---:|---:|---|
| Path | `requestId` | string | O | X | 이송 요청 공개 ID |
| Header | `Authorization` | string | O | X | Bearer Access Token |
| Header | `Idempotency-Key` | string | O | X | 8~100자, 허용 문자만 사용 |

#### 요청

```json
{
  "reason": "OTHER",
  "detail": "현장 처치 후 이송이 필요하지 않음"
}
```

| 필드 | 타입 | 필수 | Nullable | 제약 |
|---|---|---:|---:|---|
| `reason` | enum | O | X | 아래 취소 사유 4개 중 하나 |
| `detail` | string | 조건부 | O | `OTHER`일 때 trim 후 1~200자 필수, 다른 사유면 보내지 않음 |

#### 성공 응답

```json
{
  "transportRequestId": "REQUEST_UUID",
  "status": "CANCELLED",
  "reason": "OTHER",
  "detail": "현장 처치 후 이송이 필요하지 않음",
  "cancelledAt": "2026-08-05T01:00:00Z",
  "idempotentReplay": false
}
```

| 필드 | 타입 | Nullable | 설명 |
|---|---|---:|---|
| `transportRequestId` | string | X | 취소된 요청 공개 ID |
| `status` | enum | X | 항상 `CANCELLED` |
| `reason` | enum | X | 저장된 취소 사유 |
| `detail` | string | O | `OTHER` 상세, 다른 사유면 없음 |
| `cancelledAt` | string(datetime) | X | 서버 취소 시각 |
| `idempotentReplay` | boolean | X | 같은 명령 재시도 응답이면 `true` |

#### 오류

| 오류 코드 | HTTP | 발생 조건 | 재시도 | 앱에서 필요한 처리 |
|---|---:|---|---|---|
| `COMMON_001` | 400 | 사유 누락·잘못된 `OTHER` 상세·키 형식 오류 | 입력 수정 후 새 키 | 필드 오류 표시 |
| `AUTH_001`, `AUTH_002` | 401 | 인증 없음·토큰 오류 | 토큰 갱신 후 같은 키·본문 | 로그인 또는 토큰 갱신 |
| `AUTH_003`, `COMMON_004`, `USER_002` | 403 | 역할·조직·계정 상태 오류 | 자동 재시도 X | 접근 차단·운영자 확인 |
| `TRANSPORT_001` | 404 | 요청 없음 또는 다른 구급대원 소유 | 자동 재시도 X | 타 요청 정보를 표시하지 않고 목록 복구 |
| `TRANSPORT_004` | 409 | 취소 불가 상태 또는 경합에서 다른 전이가 먼저 확정 | 자동 반복 X | 목록 재조회 후 화면 전환 |
| `COMMON_005` | 409 | 같은 키를 다른 lifecycle 명령·본문에 재사용 | 최초 명령 복구 또는 새 키 | 새 동작이면 새 키 발급 |

허용 상태는 `SEARCHING`, `CANDIDATES_EXHAUSTED`, `ACCEPTED_AVAILABLE`,
`EN_ROUTE`입니다. 취소 완료 뒤 요청을 재개할 수 없습니다.

### `POST /api/v1/transport-requests/{requestId}/handoff-request`

- 목적: 목적지 병원 도착 후 인계 확인 요청
- 인증·역할: Bearer, `PARAMEDIC`, 요청 소유자
- 성공 HTTP: `200 OK`
- 요청 본문: 없음

#### 파라미터

| 위치 | 이름 | 타입 | 필수 | Nullable | 제약 |
|---|---|---|---:|---:|---|
| Path | `requestId` | string | O | X | 이송 요청 공개 ID |
| Header | `Authorization` | string | O | X | Bearer Access Token |
| Header | `Idempotency-Key` | string | O | X | 8~100자 |

#### 성공 응답

```json
{
  "transportRequestId": "REQUEST_UUID",
  "status": "HANDOFF_REQUESTED",
  "destinationOfferId": "OFFER_UUID",
  "destinationHospitalName": "한양대학교병원",
  "handoffRequestedAt": "2026-08-05T01:20:00Z",
  "idempotentReplay": false
}
```

| 필드 | 타입 | Nullable | 설명 |
|---|---|---:|---|
| `transportRequestId` | string | X | 요청 공개 ID |
| `status` | enum | X | 항상 `HANDOFF_REQUESTED` |
| `destinationOfferId` | string | X | 현재 목적지 제안 공개 ID |
| `destinationHospitalName` | string | X | 목적지 병원명 snapshot |
| `handoffRequestedAt` | string(datetime) | X | 서버 요청 시각 |
| `idempotentReplay` | boolean | X | 같은 명령 재시도 응답 여부 |

#### 오류

| 오류 코드 | HTTP | 발생 조건 | 재시도 | 앱에서 필요한 처리 |
|---|---:|---|---|---|
| `COMMON_001` | 400 | 키 형식 오류 | 키 수정 | 요청 전 검증 |
| `AUTH_001`, `AUTH_002` | 401 | 인증 없음·토큰 오류 | 토큰 갱신 후 같은 키 | 인증 복구 |
| `AUTH_003`, `COMMON_004`, `USER_002` | 403 | 역할·조직·계정 상태 오류 | 자동 재시도 X | 접근 차단 |
| `TRANSPORT_001` | 404 | 요청 없음 또는 다른 구급대원 소유 | 자동 재시도 X | 목록 재조회 |
| `TRANSPORT_004` | 409 | `EN_ROUTE`가 아니거나 현재 목적지 수락이 유효하지 않음 | 자동 반복 X | 목록·병원 탐색 현황 재조회 |
| `COMMON_005` | 409 | 같은 키를 다른 lifecycle 명령에 재사용 | 최초 명령 복구 또는 새 키 | 새 동작이면 새 키 |

성공한 뒤에는 취소·목적지 변경·병원 철회·새 임상·위치 갱신이 차단됩니다.

### `GET /api/v1/transport-requests`

- 목적: 본인의 활성·종료·홈 최근 이송 복구
- 인증·역할: Bearer, `PARAMEDIC`
- 성공 HTTP: `200 OK`

#### 파라미터

| 위치 | 이름 | 타입 | 필수 | Nullable | 제약 |
|---|---|---|---:|---:|---|
| Query | `view` | enum | X | X | 기본 `ACTIVE`, `ACTIVE`·`HISTORY`·`RECENT` |
| Query | `page` | integer | X | X | 기본 0, 0 이상 |
| Query | `size` | integer | X | X | 기본 20, 1~100 |
| Header | `Authorization` | string | O | X | Bearer Access Token |

#### 성공 응답

```json
{
  "items": [
    {
      "transportRequestId": "REQUEST_UUID",
      "status": "HANDOFF_REQUESTED",
      "hospitalName": "한양대학교병원",
      "createdAt": "2026-08-05T00:00:00Z",
      "statusUpdatedAt": "2026-08-05T01:20:00Z",
      "handoffRequestedAt": "2026-08-05T01:20:00Z",
      "completedAt": null,
      "cancelledAt": null,
      "cancellationReason": null
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

| 필드 | 타입 | Nullable | 설명 |
|---|---|---:|---|
| `items` | array | X | 본인 요청 요약 |
| `items[].transportRequestId` | string | X | 요청 공개 ID |
| `items[].status` | enum | X | 현재 요청 상태 |
| `items[].hospitalName` | string | O | 현재·최종 목적지 병원명, 목적지 전 취소면 없음 |
| `items[].createdAt` | string(datetime) | X | 최초 요청 생성 시각 |
| `items[].statusUpdatedAt` | string(datetime) | X | 현재 상태 기준 최신 시각 |
| `items[].handoffRequestedAt` | string(datetime) | O | 인계 요청 시각 |
| `items[].completedAt` | string(datetime) | O | 인계 완료 시각 |
| `items[].cancelledAt` | string(datetime) | O | 취소 시각 |
| `items[].cancellationReason` | enum | O | 취소 사유 |
| `page`, `size` | integer | X | 현재 페이지·크기 |
| `totalElements` | integer | X | 전체 항목 수 |
| `totalPages` | integer | X | 전체 페이지 수 |

#### View별 상태

| `view` | 포함 상태 | 사용 예 |
|---|---|---|
| `ACTIVE` | `SEARCHING`, `CANDIDATES_EXHAUSTED`, `ACCEPTED_AVAILABLE`, `EN_ROUTE`, `HANDOFF_REQUESTED` | 진행 중 요청 복구 |
| `HISTORY` | `COMPLETED`, `CANCELLED` | 종료 이력 |
| `RECENT` | `HANDOFF_REQUESTED`, `COMPLETED`, `CANCELLED` | 현재 홈의 최근 이송 |

#### 오류

| 오류 코드 | HTTP | 발생 조건 | 앱에서 필요한 처리 |
|---|---:|---|---|
| `COMMON_001` | 400 | view·page·size 오류 | 입력값 수정 |
| `AUTH_001`, `AUTH_002` | 401 | 인증 없음·토큰 오류 | 토큰 갱신 후 재조회 |
| `AUTH_003`, `COMMON_004`, `USER_002` | 403 | 역할·조직·계정 상태 오류 | 접근 차단 |

## 상태와 Enum

### 취소 사유

| 값 | 의미 | 앱에서 필요한 처리 |
|---|---|---|
| `PATIENT_REFUSED_TRANSPORT` | 환자가 이송을 거부함 | 상세 입력 없음 |
| `GUARDIAN_SELF_TRANSPORT` | 보호자가 직접 이송함 | 상세 입력 없음 |
| `SCENE_RESOLVED` | 현장에서 상황이 해결됨 | 상세 입력 없음 |
| `OTHER` | 기타 | 상세 입력창과 1~200자 검증 |

### 종료 관련 요청 상태

| 값 | 의미 | 앱에서 필요한 처리 |
|---|---|---|
| `HANDOFF_REQUESTED` | 구급대원이 인계를 요청했고 병원 확인 대기 중 | `인계 대기 중`, 변경 명령 중지 |
| `COMPLETED` | 목적지 병원이 인계를 확인함 | `인계 완료`, 종료 이력 표시 |
| `CANCELLED` | 구급대원이 이송을 취소함 | `이송 취소`, 사유 표시 |

## 오류 처리

공통 오류 응답:

```json
{
  "code": "TRANSPORT_004",
  "message": "변경할 수 없는 이송 요청 상태입니다.",
  "fieldErrors": [],
  "traceId": "TRACE_ID"
}
```

- 오류 문의 시 API 경로, HTTP 상태, `code`, `traceId`를 전달합니다.
- 환자정보·연락처·정확한 위치·토큰 원문은 공유하지 않습니다.

## 모바일 상태 복구

| 상황 | 서버 계약 | 앱에서 필요한 처리 |
|---|---|---|
| 명령 응답 유실 | 같은 키·같은 명령은 최초 결과 재생 | 성공 확인 전까지 키와 본문 유지 |
| 같은 키로 내용 변경 | `COMMON_005`, 기존 결과 유지 | 최초 내용 복구 또는 새 동작에 새 키 |
| 취소·인계 경합 | 요청 잠금으로 한 전이만 최종 확정 | 성공·409 모두 목록 재조회 |
| 앱 재실행 | 상태와 종료 snapshot을 DB에 보존 | `ACTIVE`와 필요 시 `RECENT` 재조회 |
| SSE 누락·중복 | SSE는 권위 상태가 아님 | 목록 REST 응답으로 덮어쓰기 |
| Access Token 만료 | REST·SSE 인증 실패 | 토큰 갱신, SSE 재연결, 목록 재조회 |

## 실시간 이벤트와 재조회

### `GET /api/v1/realtime/events`

구급대원 계정이 추가로 받을 수 있는 `type`:

```text
TRANSPORT_CANCELLED
HANDOFF_REQUESTED
HANDOFF_COMPLETED
```

```text
id: EVENT_UUID
event: update
data: {"eventId":"EVENT_UUID","type":"HANDOFF_COMPLETED","aggregateType":"TRANSPORT_LIFECYCLE","aggregateId":"COMMAND_UUID","occurredAt":"2026-08-05T01:22:00Z"}
```

- 세 이벤트를 받으면 `GET /api/v1/transport-requests?view=ACTIVE...`와 화면에 필요한
  `RECENT` 또는 `HISTORY`를 다시 조회합니다.
- 이벤트에는 취소 상세·임상정보·연락처·정확한 좌표가 없습니다.
- 연결은 약 14분 뒤 종료될 수 있습니다. Access Token을 확인해 재연결하고 목록을 재조회합니다.

## 연동 확인

- [ ] 네 취소 사유와 `OTHER` 상세 1~200자 검증
- [ ] 탐색·수락 후보·이동 중 상태에서 취소 성공과 최근 `CANCELLED` 표시
- [ ] 목적지 전 취소의 nullable `hospitalName`
- [ ] `EN_ROUTE` 인계 요청 후 `HANDOFF_REQUESTED` 표시와 변경 명령 중지
- [ ] 병원 확인 뒤 SSE 수신과 `COMPLETED` 표시
- [ ] 명령 응답 유실 시 같은 키·같은 본문 재시도
- [ ] 409 경합 뒤 권위 목록 재조회
- [ ] 앱 재실행·SSE 재연결 뒤 활성·최근 상태 복구
- [ ] 실제 환자정보·개인 연락처·정확한 실제 위치가 아닌 테스트 데이터로 Dev 연동
