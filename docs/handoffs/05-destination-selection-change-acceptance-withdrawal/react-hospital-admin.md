# 목적지 선택·변경 및 수락 철회 React 병원·관리자 웹 핸드오프

```text
Feature: destination-selection-change-acceptance-withdrawal
Backend Feature: docs/features/05-destination-selection-change-acceptance-withdrawal/
Related PR: NONE
Available After: MAIN_MERGE
Breaking Change: NO
Hospital Impact: YES
Admin Impact: NONE
```

## 변경 요약

- 병원이 수락한 제안은 목적지 확정 전까지 `ACTIVE`에 유지됩니다.
- 구급대원이 목적지를 선택하면 현재 목적지 병원의 수락 카드만 `ACTIVE`에 남고,
  선택되지 않은 수락 병원은 `HISTORY`의 최소 이력으로 이동합니다.
- 최소 이력에는 환자 임상정보·구급대원 연락처·거리·ETA가 없지만, 아직 수락
  상태라면 병원은 그 이력에서 수락을 철회할 수 있습니다.
- 병원은 필수 사유와 함께 자기 조직의 `ACCEPTED` 제안을 철회할 수 있습니다.
- 현재 목적지 병원이 철회하면 목적지가 해제되고 자동 재탐색이 시작됩니다.
  다른 병원이 현재 목적지인 상태에서 비목적지 병원이 철회하면 재탐색하지 않습니다.
- 수신 상태를 `OFF`로 바꾸면 새 요청 후보에서만 제외됩니다. 이미 수락했거나 현재
  목적지인 요청의 목록·상세·철회 권한은 유지됩니다.
- `SUPER_ADMIN`은 이 기능 API와 환자 임상정보에 접근할 수 없습니다.

## 역할별 사용자 흐름

| 역할 | 순서 | 사용자·웹 동작 | API | 성공 후 상태 |
|---|---:|---|---|---|
| 병원 | 1 | 활성 제안 조회 | `GET .../offers?view=ACTIVE` | 대기 제안·현재 표시 가능한 수락 카드 확인 |
| 병원 | 2 | 목적지 선정 SSE 수신 | 활성·이력 목록 재조회 | 목적지 병원은 ACTIVE 유지, 비목적지는 HISTORY 이동 |
| 병원 | 3 | 수락 철회 사유 선택 | `POST .../{offerId}/withdraw-acceptance` | `ACCEPTANCE_WITHDRAWN`, HISTORY 최소 이력 |
| 병원 | 4 | 철회 또는 목적지 변경 SSE 수신 | ACTIVE와 HISTORY 재조회 | 권위 상태로 카드 갱신 |
| 관리자 | - | 목적지·철회 API 호출 | 허용 안 됨 | `AUTH_003` |

## 공통 계약

- Base URL: `http://13.124.194.249`
- 인증: `Authorization: Bearer {accessToken}`
- 병원 역할: `HOSPITAL_STAFF`
- 시간: ISO-8601 UTC
- 다른 병원 조직의 `offerId`는 `TRANSPORT_005`로 숨깁니다.
- 구조적으로 기존 응답 필드가 삭제되지는 않았지만, 목적지 선택 뒤 `ACCEPTED`
  카드의 ACTIVE/HISTORY 소속이 달라지므로 두 목록을 함께 갱신해야 합니다.

공통 오류:

```json
{
  "code": "ERROR_CODE",
  "message": "사용자에게 표시 가능한 메시지",
  "fieldErrors": [],
  "traceId": "TRACE_ID"
}
```

## API 1. 병원 제안 목록 변경

### `GET /api/v1/hospitals/me/offers?view={view}&page={page}&size={size}`

- 성공: `200 OK`
- 기본값: `view=ACTIVE`, `page=0`, `size=20`
- `page`: 0 이상, `size`: 1~100
- 정렬: `offeredAt` 오래된 순, 같은 시각은 서버 내부 안정 순서

| `view` | 포함 제안 |
|---|---|
| `ACTIVE` | `PENDING`, 목적지 선택 전 `ACCEPTED`, 현재 목적지인 `ACCEPTED` |
| `HISTORY` | `REJECTED`, `NO_RESPONSE`, `ACCEPTANCE_WITHDRAWN`, 현재 목적지가 아닌 숨겨진 `ACCEPTED` |

목록 item에 추가된 필드:

| 필드 | 타입 | Nullable | 설명 |
|---|---|---:|---|
| `currentDestination` | boolean | NO | 자기 제안이 현재 목적지인지 표시 |
| `canWithdraw` | boolean | NO | 현재 상태에서 철회 API를 호출할 수 있는지 표시 |
| `respondedAt` | datetime | YES | 기존 수락·거절 응답 시각 |
| `withdrawalReason` | enum | YES | 수락 철회 사유 |
| `withdrawalDetail` | string | YES | `OTHER` 상세 |
| `withdrawnAt` | datetime | YES | 철회 서버 시각 |

### ACTIVE item 예시

```json
{
  "offerId": "OFFER_UUID",
  "transportRequestId": "REQUEST_UUID",
  "dispatchAttemptNumber": 1,
  "transportRequestStatus": "EN_ROUTE",
  "offerStatus": "ACCEPTED",
  "currentDestination": true,
  "canWithdraw": true,
  "ageStatus": "ESTIMATED",
  "ageYears": 45,
  "sex": "UNKNOWN",
  "preKtasClassificationStatus": "COMPLETED",
  "preKtasLevel": 2,
  "preKtasExceptionReason": null,
  "straightLineDistanceMeters": 5230,
  "routeEstimateStatus": "AVAILABLE",
  "routeDistanceMeters": 6840,
  "etaSeconds": 780,
  "offeredAt": "2026-08-04T03:09:00Z",
  "respondedAt": "2026-08-04T03:10:00Z",
  "withdrawalReason": null,
  "withdrawalDetail": null,
  "withdrawnAt": null
}
```

### 숨겨진 수락·철회 HISTORY item

비목적지 `ACCEPTED`와 `ACCEPTANCE_WITHDRAWN`은 최소 이력만 반환합니다.

```json
{
  "offerId": "OFFER_UUID",
  "transportRequestId": "REQUEST_UUID",
  "dispatchAttemptNumber": null,
  "transportRequestStatus": "EN_ROUTE",
  "offerStatus": "ACCEPTED",
  "currentDestination": false,
  "canWithdraw": true,
  "ageStatus": null,
  "ageYears": null,
  "sex": null,
  "preKtasClassificationStatus": null,
  "preKtasLevel": null,
  "preKtasExceptionReason": null,
  "straightLineDistanceMeters": null,
  "routeEstimateStatus": null,
  "routeDistanceMeters": null,
  "etaSeconds": null,
  "offeredAt": null,
  "respondedAt": "2026-08-04T03:10:00Z",
  "withdrawalReason": null,
  "withdrawalDetail": null,
  "withdrawnAt": null
}
```

- 숨겨진 `ACCEPTED`: `canWithdraw: true`일 수 있으며 철회 버튼을 표시할 수 있습니다.
- `ACCEPTANCE_WITHDRAWN`: `canWithdraw: false`, 철회 사유·상세·시각이 있습니다.
- 최소 이력에는 임상 요약·거리·ETA·제안 시각이 없습니다.
- `REJECTED`, `NO_RESPONSE`의 기존 HISTORY 데이터 계약은 유지됩니다.
- 철회 복구 탐색이 `CANDIDATES_EXHAUSTED`로 끝난 뒤에도 기존 `PENDING` 제안은
  수락할 수 있습니다. 성공 응답의 요청 상태는 `ACCEPTED_AVAILABLE`입니다.

## API 2. 병원 제안 상세 변경

### `GET /api/v1/hospitals/me/offers/{offerId}`

- `PENDING`, 목적지 선택 전 `ACCEPTED`, 현재 목적지 `ACCEPTED`, 기존
  `REJECTED`·`NO_RESPONSE`는 기존 상세 계약을 유지합니다.
- 응답에 다음 필드가 추가됩니다.

| 필드 | 설명 |
|---|---|
| `currentDestination` | 현재 목적지 여부 |
| `canWithdraw` | 지금 철회 가능한지 여부 |
| `withdrawalReason`, `withdrawalDetail`, `withdrawnAt` | 철회 정보; 일반 상세에서는 보통 `null` |

- 목적지 선택 뒤 숨겨진 비목적지 `ACCEPTED`와 `ACCEPTANCE_WITHDRAWN`의 상세를
  요청하면 `404 TRANSPORT_005`입니다.
- 이 두 상태는 HISTORY 최소 item만 표시하며 임상 상세를 다시 요청하지 않습니다.

## API 3. 병원 수락 철회

### `POST /api/v1/hospitals/me/offers/{offerId}/withdraw-acceptance`

- 성공·동일 재시도: `200 OK`
- 인증·조직: 로그인한 병원 조직에 전달된 자기 `ACCEPTED` 제안만 허용
- 헤더: `Idempotency-Key` 필수

```http
Authorization: Bearer ACCESS_TOKEN
Idempotency-Key: withdraw-acceptance-20260804-01
Content-Type: application/json
```

```json
{
  "reason": "SPECIALIST_UNAVAILABLE",
  "detail": null
}
```

철회 사유:

```text
BED_SHORTAGE
OPERATING_ROOM_SHORTAGE
SPECIALIST_UNAVAILABLE
EQUIPMENT_UNAVAILABLE
OTHER
```

- `reason`은 필수입니다.
- `OTHER`는 공백이 아닌 `detail`이 필수이며 trim 이후 최대 200자입니다.
- `OTHER`가 아닌 사유의 `detail`은 서버가 `null`로 정규화합니다.

응답:

```json
{
  "offerId": "OFFER_UUID",
  "offerStatus": "ACCEPTANCE_WITHDRAWN",
  "transportRequestId": "REQUEST_UUID",
  "transportRequestStatus": "SEARCHING",
  "currentDestinationOfferId": null,
  "reason": "SPECIALIST_UNAVAILABLE",
  "detail": null,
  "withdrawnAt": "2026-08-04T03:15:00Z",
  "searchRestarted": true,
  "idempotentReplay": false
}
```

### 응답 해석

| 조건 | `currentDestinationOfferId` | `transportRequestStatus` | `searchRestarted` |
|---|---|---|---:|
| 다른 병원이 현재 목적지인 비목적지 철회 | 기존 목적지 ID | `EN_ROUTE` | false |
| 현재 목적지 철회, 다른 수락 존재 | null | `ACCEPTED_AVAILABLE` | true |
| 현재 목적지 철회, 다른 수락 없음 | null | `SEARCHING` | true |
| 목적지 선택 전 수락 철회 | null | 남은 수락에 따라 `ACCEPTED_AVAILABLE` 또는 `SEARCHING` | true |

- 응답 유실 시 같은 `offerId`, 키, 사유·상세로 재시도하면 최초 결과와
  `idempotentReplay: true`를 반환합니다.
- 응답의 요청·목적지 상태는 철회가 처음 확정된 시점의 결과입니다. 성공 뒤
  ACTIVE와 HISTORY 목록을 다시 조회합니다.

### 철회 오류

| 코드 | HTTP | 조건 | 웹 처리 |
|---|---:|---|---|
| `COMMON_001` | 400 | 키·사유 형식 오류, `OTHER` 상세 누락·공백·길이 초과 | 입력 수정, 새 유효 키 사용 |
| `AUTH_001`, `AUTH_002` | 401 | 인증 없음·토큰 오류 | 갱신 후 같은 키·본문 재시도 |
| `AUTH_003` | 403 | 병원 역할 아님 | 접근 차단 |
| `USER_002` | 403 | 비활성 계정 | 운영자 확인 |
| `TRANSPORT_005` | 404 | 제안 없음·다른 병원 조직 | 목록 재조회, 존재 여부 추정 금지 |
| `TRANSPORT_006` | 409 | `ACCEPTED`가 아님·이미 다른 키로 철회됨 | ACTIVE·HISTORY 재조회 |
| `TRANSPORT_004` | 409 | 인계 요청 이후 등 철회 불가능한 요청 상태 | 목록 재조회, 철회 버튼 비활성화 |
| `COMMON_005` | 409 | 같은 철회 키로 다른 사유·상세 제출 | 최초 명령 복구 또는 새 키 사용 |

`Idempotency-Key`는 8~100자이며 `[A-Za-z0-9._:-]`만 사용할 수 있습니다.
버튼 클릭부터 응답 확정까지 키와 요청 내용을 유지합니다.

## 화면 상태 조건

| 대상 | 조건 | 웹 처리 |
|---|---|---|
| 활성 카드 유지 | `PENDING`, 목적지 선택 전 `ACCEPTED`, 현재 목적지 `ACCEPTED` | 기존 최소 임상정보 상세 허용 |
| 이력으로 이동 | 다른 병원이 목적지가 된 `ACCEPTED` | 최소 item만 표시, `canWithdraw`면 철회 가능 |
| 철회 이력 | `ACCEPTANCE_WITHDRAWN` | 사유·시각 표시, 상세 링크·철회 버튼 비활성화 |
| 이동 중 표시 | `currentDestination: true`, 요청 `EN_ROUTE` | 현재 목적지 카드로 표시 |
| 철회 명령 활성화 | `canWithdraw: true` | 사유 선택 뒤 API 호출 |
| 목적지/철회 이벤트 | SSE 수신 | ACTIVE와 HISTORY를 모두 첫 페이지부터 재조회 |

## 실시간 이벤트와 재조회

### `GET /api/v1/realtime/events`

병원 조직이 추가로 받을 수 있는 `type`:

```text
DESTINATION_SELECTED
DESTINATION_CHANGED
HOSPITAL_ACCEPTANCE_WITHDRAWN
```

| 이벤트 | 수신 대상 | 재조회 |
|---|---|---|
| `DESTINATION_SELECTED` | 선택된 병원과 선택되지 않은 수락 병원 조직 | ACTIVE와 HISTORY |
| `DESTINATION_CHANGED` | 이전 목적지와 새 목적지 병원 조직 | ACTIVE와 HISTORY |
| `HOSPITAL_ACCEPTANCE_WITHDRAWN` | 철회한 병원 조직 | ACTIVE와 HISTORY |

```text
id: EVENT_UUID
event: update
data: {"eventId":"EVENT_UUID","type":"DESTINATION_CHANGED","aggregateType":"TRANSPORT_DESTINATION","aggregateId":"COMMAND_UUID","occurredAt":"2026-08-04T03:14:00Z"}
```

- 이벤트에는 환자정보·구급대원 연락처·좌표·철회 상세가 없습니다.
- SSE 중복·누락을 허용하고 목록·허용된 상세 REST 응답을 최종 기준으로 사용합니다.
- 연결 재개·브라우저 복귀·Access Token 갱신 뒤 ACTIVE와 HISTORY를 재조회합니다.

## 관리자 접근

- `SUPER_ADMIN`이 병원 목록·상세·철회·SSE를 호출하면 `AUTH_003`입니다.
- 관리자는 목적지, 철회 사유, 환자 임상정보, 구급대원 연락처와 위치를 조회하지 않습니다.
- 이 기능으로 변경되는 관리자 전용 API는 없습니다.

## 웹 상태 복구

| 상황 | 서버 계약 | 웹 처리 |
|---|---|---|
| 철회 응답 유실 | 같은 키·같은 명령은 최초 결과 반환 | 같은 키·본문으로 재시도 |
| 목적지 선택과 철회 경합 | 서버가 순서대로 확정하고 나중 명령은 최신 상태 검증 | 성공·오류 뒤 두 목록 재조회 |
| 상세가 `TRANSPORT_005`로 바뀜 | 목적지 변경으로 임상 상세 권한이 끝날 수 있음 | 상세 닫기, HISTORY 최소 item 사용 |
| SSE 누락·중복 | SSE는 권위 상태가 아님 | 화면 진입·복귀 시 두 목록 재조회 |
| Access Token 만료 | REST·SSE 인증 실패 | 토큰 갱신, SSE 재연결, 두 목록 재조회 |

## 연동 확인

- [ ] 목적지 선택 전 수락 카드 ACTIVE 유지
- [ ] 목적지 선택 뒤 현재 목적지 ACTIVE, 비목적지 수락 HISTORY 이동
- [ ] 숨겨진 수락 HISTORY에서 임상·연락처·거리·ETA 미노출
- [ ] `canWithdraw`에 따른 철회 버튼 상태
- [ ] 모든 철회 사유와 `OTHER` 상세 검증
- [ ] 비목적지 철회에서 현재 목적지 유지·재탐색 없음
- [ ] 현재 목적지 철회 응답과 ACTIVE/HISTORY 재조회
- [ ] 숨겨진 수락·철회 상세 `TRANSPORT_005`
- [ ] 목적지·철회 SSE 뒤 두 목록 재조회
- [ ] 슈퍼 관리자 접근 차단
- [ ] 실제 환자정보·연락처가 아닌 테스트 데이터로 Dev 연동
