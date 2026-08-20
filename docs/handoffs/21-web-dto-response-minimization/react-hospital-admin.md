# 웹 전용 응답 DTO 최소화 React 병원·관리자 웹 핸드오프

```text
Feature: 21-web-dto-response-minimization
Backend Feature: docs/features/21-web-dto-response-minimization/
Related PR: NONE
Available After: MAIN_MERGE
Breaking Change: YES
Hospital Impact: YES
Admin Impact: YES
```

> 병원 웹과 슈퍼 관리자 웹의 실제 미사용 조사 결과를 반영해 응답 JSON의 일부 필드를 제거했습니다.
> Endpoint, 요청, Query, 권한, 성공 HTTP 상태와 오류 계약은 변경하지 않았습니다.

## 변경 요약

- 새 병원·관리자 기능은 추가하지 않았습니다.
- 병원 프로필·수신 상태, 병원 제안 목록·상세·수락 철회, 관리자 조직·가입 코드 응답만 간결해졌습니다.
- 이 문서에 적은 제거 필드는 더 이상 JSON에 존재하지 않으므로 프론트 타입에서도 필수 필드로 선언하지 않습니다.
- 병원 제안 상태 전이, 철회 멱등 처리, 관리자 생성·폐기 동작과 DB 데이터는 기존과 같습니다.
- 인증·가입·가입 코드 사전 확인·공통 오류·SSE·Flutter 구급대원 API 응답은 변경하지 않았습니다.

## 역할별 사용자 흐름

| 역할 | 순서 | 사용자·웹 동작 | API 호출 | 성공 후 상태 |
|---|---:|---|---|---|
| 병원 관계자 | 1 | 로그인 뒤 자기 병원 정보와 수신 상태 복구 | `GET /api/v1/hospitals/me` | 유지 필드로 기존 화면 표시 |
| 병원 관계자 | 2 | 제안 목록·상세 확인 | `GET /api/v1/hospitals/me/offers`, `GET /api/v1/hospitals/me/offers/{offerId}` | 기존 카드·상세·명령 상태 표시 |
| 병원 관계자 | 3 | 수락 철회 | `POST /api/v1/hospitals/me/offers/{offerId}/withdraw-acceptance` | 유지된 최종 상태·사유·재검색 여부로 결과 처리 후 목록 재조회 |
| 슈퍼 관리자 | 1 | 조직·가입 코드 관리 | 기존 관리자 API | 유지된 목록·생성·폐기 필드로 화면 갱신 |

## 인증과 접근 범위

| 역할 | 인증 | 허용 작업 | 조직·정보 접근 범위 |
|---|---|---|---|
| 병원 관계자 | Bearer Access Token, `HOSPITAL_STAFF` | 자기 병원 프로필·수신 상태·전달된 제안 조회와 기존 명령 | JWT의 자기 병원과 자기 조직에 전달된 제안만 |
| 슈퍼 관리자 | Bearer Access Token, `SUPER_ADMIN` | 기존 조직·가입 코드 관리 | 임상정보·위치정보를 제외한 운영 관리 범위 |

## 공통 계약

- Base URL: `http://13.124.194.249`
- Content-Type: `application/json`
- 시간: ISO-8601 UTC
- 목록 요청의 `page`, `size`, `view`, `status` Query는 기존과 동일합니다.
- 목록 응답에서 일부 페이징 필드가 제거돼도 요청 페이지 번호는 프론트가 계속 관리합니다.

## 병원 프로필·수신 상태

### `GET /api/v1/hospitals/me`

### `PUT /api/v1/hospitals/me`

- 성공 HTTP: `200 OK`
- 요청: 기존 계약과 동일
- 최신 성공 응답:

```json
{
  "loginId": "hospital01",
  "organizationName": "한성대학교병원",
  "hospitalId": "HOSPITAL_UUID",
  "address": "서울특별시 성북구 삼선교로 16길",
  "detailAddress": "본관 1층 응급의료센터",
  "latitude": 37.5821000,
  "longitude": 127.0105000,
  "contact": "02-1234-5678",
  "receivingStatus": "ON",
  "updatedAt": "2026-08-20T10:00:00Z"
}
```

- 제거: `accountId`, `role`, `organizationId`
- 유지: 위 예시의 모든 필드

### `PUT /api/v1/hospitals/me/receiving-status`

- 성공 HTTP: `200 OK`
- 요청: 기존 `{ "status": "ON" }` 또는 `{ "status": "OFF" }`
- 최신 성공 응답:

```json
{
  "status": "ON",
  "updatedAt": "2026-08-20T10:00:00Z"
}
```

- 제거: `hospitalId`, `organizationId`
- 토글 성공 여부는 `status`, 서버 반영 시각은 `updatedAt`으로 확인합니다.

## 병원 제안 목록

### `GET /api/v1/hospitals/me/offers`

- 성공 HTTP: `200 OK`
- 요청 Query와 ACTIVE·HISTORY 동작: 기존과 동일
- 최상위 유지 필드: `items`, `totalElements`, `totalPages`
- 최상위 제거 필드: `page`, `size`, `serverNow`
- `items[]` 제거 필드: `canConfirmHandoff`
- `items[]` 유지 필드:
  - 식별·상태: `offerId`, `transportRequestId`, `dispatchAttemptNumber`, `transportRequestStatus`, `offerStatus`, `hospitalOutcome`, `processedAt`
  - 동작: `currentDestination`, `canWithdraw`
  - 환자 요약: `ageStatus`, `ageYears`, `sex`, `preKtasClassificationStatus`, `preKtasLevel`, `preKtasExceptionReason`
  - 거리·ETA: `straightLineDistanceMeters`, `routeEstimateStatus`, `routeDistanceMeters`, `etaSeconds`, `lastSuccessfulRouteDistanceMeters`, `lastSuccessfulEtaSeconds`, `lastSuccessfulEtaCalculatedAt`
  - 시각·재요청: `lastClinicalUpdateAt`, `offeredAt`, `reRequested`, `lastRequestedAt`, `respondedAt`
  - 거절·철회·종료: `rejectionReason`, `rejectionDetail`, `withdrawalReason`, `withdrawalDetail`, `withdrawnAt`, `handoffRequestedAt`, `completedAt`, `cancelledAt`, `cancellationReason`

`canConfirmHandoff`는 목록에서 읽지 말고 상세 응답의 같은 이름 필드를 사용합니다.

## 병원 제안 상세

### `GET /api/v1/hospitals/me/offers/{offerId}`

- 성공 HTTP: `200 OK`
- 유지되는 최상위 필드:
  - `offerId`, `dispatchAttemptNumber`, `transportRequestStatus`, `offerStatus`
  - `currentDestination`, `canWithdraw`, `canConfirmHandoff`
  - `patient`, `incident`, `preKtas`, `consciousness`, `vitalSigns`, `treatments`, `supplementalAssessment`
  - `requester`, `route`, `timing`
  - `rejectionReason`, `rejectionDetail`, `withdrawalReason`, `withdrawalDetail`
  - `respondedAt`, `withdrawnAt`, `handoffRequestedAt`, `serverNow`
- 제거되는 최상위 필드:
  - `transportRequestId`, `hospitalOutcome`, `processedAt`, `completedAt`, `cancelledAt`, `cancellationReason`
- 제거되는 중첩 필드:
  - `preKtas.exceptionDetail`, `preKtas.assessedAt`, `preKtas.standardVersion`
  - `consciousness.unassessableDetail`, `consciousness.observedAt`
  - `route.calculatedAt`
  - `timing.offeredAt`

`supplementalAssessment`와 나머지 환자·처치·회신 연락처·ETA 공개 규칙은 기존 계약을 유지합니다.

## 병원 수락 철회

### `POST /api/v1/hospitals/me/offers/{offerId}/withdraw-acceptance`

- 성공 HTTP: `200 OK`
- 요청과 `Idempotency-Key`: 기존과 동일
- 최신 성공 응답:

```json
{
  "transportRequestStatus": "ACCEPTED_AVAILABLE",
  "reason": "BED_SHORTAGE",
  "detail": null,
  "withdrawnAt": "2026-08-20T10:00:00Z",
  "searchRestarted": true
}
```

- 제거: `offerId`, `offerStatus`, `transportRequestId`, `currentDestinationOfferId`, `idempotentReplay`
- 동일 키 재시도 시 서버의 멱등 처리는 유지되지만, 응답에서 재시도 여부를 구분하지 않습니다.
- 성공 뒤 목록·상세 REST API를 다시 조회해 화면을 확정합니다.

## 슈퍼 관리자 조직

### `GET /api/v1/admin/organizations`

- 성공 HTTP: `200 OK`
- 유지: `items`, `totalPages`
- 제거: `page`, `size`, `totalElements`
- `items[]` 유지: `organizationId`, `name`, `type`, `createdAt`
- `items[]` 제거: `status`

### `POST /api/v1/admin/organizations`

- 성공 HTTP: `201 Created`
- 요청: 기존과 동일
- 응답 유지: `organizationId`, `name`, `type`, `createdAt`
- 응답 제거: `status`

## 슈퍼 관리자 가입 코드

### `POST /api/v1/admin/invitation-codes`

- 성공 HTTP: `201 Created`
- 요청: 기존과 동일
- 최신 응답은 `{ "code": "A1b2_C3d" }`처럼 원문 `code`만 포함합니다.
- 제거: 중첩 객체 `invitation` 전체
- 가입 코드 원문을 한 번만 반환하고 DB에는 해시만 저장하는 정책은 그대로입니다.

### `GET /api/v1/admin/invitation-codes`

- 성공 HTTP: `200 OK`
- 유지: `items`, `totalPages`
- 제거: `page`, `size`, `totalElements`
- `items[]` 유지: `invitationCodeId`, `organizationName`, `role`, `status`, `expiresAt`
- `items[]` 제거: `organizationId`, `organizationType`, `usedAt`, `revokedAt`, `createdAt`

### `POST /api/v1/admin/invitation-codes/{invitationCodeId}/revoke`

- 성공 HTTP: `200 OK`
- 응답 유지: `invitationCodeId`, `organizationName`, `role`, `status`, `expiresAt`
- 응답 제거: `organizationId`, `organizationType`, `usedAt`, `revokedAt`, `createdAt`

## 변경하지 않은 명령 응답

- 수락·거절 `HospitalOfferDecisionResponse`의 기존 6개 필드는 유지됩니다.
- 인계 확인 `HospitalHandoffConfirmationResponse`의 기존 5개 필드는 유지됩니다.
- 응답 본문을 없애거나 `204 No Content`로 바꾸지 않았습니다.

## 오류 처리

공통 오류 응답과 `X-Trace-Id`는 변경하지 않았습니다.

```json
{
  "code": "ERROR_CODE",
  "message": "사용자에게 표시 가능한 메시지",
  "fieldErrors": [],
  "traceId": "TRACE_ID"
}
```

| 오류 코드 | HTTP | 웹에서 필요한 처리 |
|---|---:|---|
| `COMMON_001` | 400 | 기존 입력 오류 처리 유지 |
| `AUTH_001` | 401 | 로그인 화면 이동 |
| `AUTH_002` | 401 | 토큰 갱신 1회 후 실패 시 로그인 |
| `AUTH_003` | 403 | 역할에 맞지 않는 화면·명령 차단 |
| 기존 소유권·상태 오류 | 기존 HTTP | 기존 메시지와 재조회 처리 유지 |

## 실시간 이벤트와 재조회

- SSE 이벤트 종류와 payload는 변경하지 않았습니다.
- 이벤트 수신 후 기존 REST API 재조회 원칙을 유지합니다.
- 수락 철회 성공 후 제안 목록·상세를 재조회합니다.
- 관리자 생성·폐기 성공 후 조직·가입 코드 목록을 재조회할 수 있습니다.

## 연동 확인

- [ ] 제거 필드를 프론트 타입의 필수 속성에서 제거
- [ ] 병원 프로필 조회·수정과 수신 상태 토글 정상 동작
- [ ] ACTIVE·HISTORY 목록 페이징이 `totalPages`로 정상 동작
- [ ] 인계 가능 여부는 제안 상세의 `canConfirmHandoff`로 확인
- [ ] 수락 철회 뒤 목록·상세 재조회
- [ ] 조직·가입 코드 목록 페이징이 `totalPages`로 정상 동작
- [ ] 가입 코드 발급 성공 시 최상위 `code` 사용
- [ ] 인증·오류·SSE 기존 처리 유지
- [ ] Dev API 연결
