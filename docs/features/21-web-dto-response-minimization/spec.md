# 웹 전용 응답 DTO 최소화 요구사항

```text
Feature: 21-web-dto-response-minimization
Owner: Backend
Related Issue: NONE
Frontend Impact: REACT_HOSPITAL_ADMIN
Policy Decision Status: RESOLVED
```

> 2026-08-20 웹 프론트가 제공한 DTO 사용 현황 감사 결과를 기준으로 합니다.
> 병원·슈퍼 관리자 웹 전용 응답에서 실제 실행 코드가 읽지 않는 필드만 제거합니다.

## 목적

- 병원 웹과 슈퍼 관리자 웹이 사용하지 않는 백엔드 응답 필드를 제거해 전송 JSON과 외부 계약을 간결하게 유지합니다.
- 자주 조회하는 병원 제안 목록·상세 응답의 불필요한 직렬화를 줄입니다.
- Flutter 구급대원 앱과 공용으로 사용하는 DTO·API 계약은 변경하지 않습니다.
- 사용자 기능, 권한, 상태 전이, DB 데이터와 HTTP 성공·오류 의미는 유지합니다.

## 정책 기준

- 적용할 MVP 요구사항:
  - 일반 API는 정상 부하에서 p95 1초 이내를 목표로 합니다.
  - 병원에는 역할·조직·제안 상태에 따라 허용된 환자정보만 제공합니다.
  - 슈퍼 관리자는 환자 임상정보와 위치정보를 조회할 수 없습니다.
  - 클라이언트는 실시간 신호 수신 뒤 권위 REST API를 다시 조회합니다.
- 웹 사용 현황 감사 기준:
  - 병원 웹과 슈퍼 관리자 웹의 실제 화면·상태·조건·후속 요청까지 추적해 `PASS_THROUGH` 또는 `UNUSED_FIELD`로 판정된 필드만 대상으로 합니다.
  - 타입 선언, 단순 상태 저장과 API Route 전달만으로는 사용 중으로 판단하지 않습니다.
- 기존 정책과 충돌:
  - 외부 응답 필드를 제거하는 호환되지 않는 변경이지만, 웹 프론트의 실제 미사용 조사와 팀의 DTO 최소화 결정에 따라 이번 범위로 확정했습니다.
  - 변경된 실제 응답 계약은 React 핸드오프에 기록합니다.

## 범위

### 포함

- `HOSPITAL_STAFF` 전용 API가 반환하는 웹 전용 DTO의 미사용 필드
- `SUPER_ADMIN` 전용 API가 반환하는 관리자 웹 전용 DTO의 미사용 필드
- 기존 DTO 생성 코드·테스트·핸드오프의 일치 작업

### 제외

- Flutter 구급대원 앱 전용 API와 응답 DTO 전체
- 앱과 웹이 공용으로 사용하는 DTO와 그 필드
- 공통 인증, 가입, 가입 코드 검증, 오류, SSE 응답
- `UNUSED_ENDPOINT`로 조사된 API의 삭제
- `TEST_ONLY` 또는 DTO 전체가 미사용으로 조사된 명령 응답의 본문 제거·`204 No Content` 전환
- DTO 분리, 새 API 추가, 요청 DTO 변경
- `supplementalAssessment`와 공용 `SupplementalAssessmentResponse`
- DB 스키마·저장 데이터·조회 권한·상태 전이 변경

## 제거할 응답 필드

### 병원 프로필·수신 상태

| API | DTO | 제거 필드 |
|---|---|---|
| `GET, PUT /api/v1/hospitals/me` | `HospitalProfileResponse` | `accountId`, `role`, `organizationId` |
| `PUT /api/v1/hospitals/me/receiving-status` | `HospitalReceivingStatusResponse` | `hospitalId`, `organizationId` |

### 병원 제안 목록·상세·철회

| API | DTO | 제거 필드 |
|---|---|---|
| `GET /api/v1/hospitals/me/offers` | `HospitalOfferListResponse` | `page`, `size`, `serverNow` |
| `GET /api/v1/hospitals/me/offers` | `HospitalOfferListResponse.Item` | `canConfirmHandoff` |
| `GET /api/v1/hospitals/me/offers/{offerId}` | `HospitalOfferDetailResponse` | `transportRequestId`, `hospitalOutcome`, `processedAt`, `completedAt`, `cancelledAt`, `cancellationReason` |
| 위 상세 API | `HospitalOfferDetailResponse.PreKtas` | `exceptionDetail`, `assessedAt`, `standardVersion` |
| 위 상세 API | `HospitalOfferDetailResponse.Consciousness` | `unassessableDetail`, `observedAt` |
| 위 상세 API | `HospitalOfferDetailResponse.Route` | `calculatedAt` |
| 위 상세 API | `HospitalOfferDetailResponse.Timing` | `offeredAt` |
| `POST /api/v1/hospitals/me/offers/{offerId}/withdraw-acceptance` | `HospitalAcceptanceWithdrawalResponse` | `offerId`, `offerStatus`, `transportRequestId`, `currentDestinationOfferId`, `idempotentReplay` |

- 제안 상세의 `supplementalAssessment`는 웹 감사에서 미사용으로 조사됐어도 공용 임상 DTO이자 기존 환자정보 공개 계약이므로 제거하지 않습니다.
- 제안 목록의 `cancellationReason`과 처리 결과·시각은 병원 이력 화면에 필요하므로 유지합니다.
- 수락·거절의 `HospitalOfferDecisionResponse`와 인계 확인의 `HospitalHandoffConfirmationResponse`는 이번 범위에서 변경하지 않습니다.

### 슈퍼 관리자 조직·가입 코드

| API | DTO | 제거 필드 |
|---|---|---|
| `GET /api/v1/admin/organizations` | `OrganizationListResponse` | `page`, `size`, `totalElements` |
| `GET, POST /api/v1/admin/organizations` | `OrganizationResponse` | `status` |
| `GET /api/v1/admin/invitation-codes` | `InvitationListResponse` | `page`, `size`, `totalElements` |
| 가입 코드 목록·폐기 응답 | `InvitationResponse` | `organizationId`, `organizationType`, `usedAt`, `revokedAt`, `createdAt` |
| `POST /api/v1/admin/invitation-codes` | `IssuedInvitationResponse` | `invitation` |

## 반드시 유지할 공용 계약

다음 DTO는 이번 기능에서 파일과 직렬화 계약을 변경하지 않습니다.

- `AuthTokenResponse`
- `SignupResponse`
- `InvitationValidationResponse`
- `RequiredConsentResponse`
- `ClinicalTimelineResponse`
- `TransportLocationResponse`
- `SupplementalAssessmentResponse`
- `RealtimeEventResponse`
- `ErrorResponse`
- `FieldErrorResponse`
- 구급대원 이송 요청·검색·상세·이력·임상 갱신·위치·목적지·취소·인계 응답 DTO

## 시나리오

| # | 상황 | 기대 결과 |
|---:|---|---|
| 1 | 병원 웹이 프로필·수신 상태와 제안 목록·상세를 조회 | 감사에서 사용 중으로 확인된 필드와 기존 HTTP 상태는 유지되고 제거 대상 필드는 JSON에 없음 |
| 2 | 병원이 수락을 철회 | 웹이 사용하는 최종 요청 상태·사유·상세·철회 시각·재검색 여부는 유지되고 미사용 식별자·반복 여부 필드는 없음 |
| 3 | 슈퍼 관리자가 조직·가입 코드를 생성·조회·폐기 | 웹이 표시·필터·후속 요청에 사용하는 필드는 유지되고 페이지 중복 메타데이터와 미사용 메타데이터는 없음 |
| 4 | 구급대원 앱이 로그인·가입·이송·임상·위치 API를 사용 | 이번 변경 전과 동일한 공용·앱 응답 JSON을 받음 |
| 5 | 인증·권한·검증 또는 시스템 오류가 발생 | 기존 공통 오류 본문과 `X-Trace-Id` 계약을 그대로 받음 |

## 외부 동작

| 행위 | 요청·응답 또는 상태 변화 |
|---|---|
| 웹 전용 조회·명령 API 호출 | Method, Endpoint, 요청 본문·Query, 성공 HTTP 상태는 유지하고 위 목록의 응답 필드만 제거 |
| 앱·공용 API 호출 | 요청과 응답을 모두 기존과 동일하게 유지 |
| 병원 제안 상태 변경 | 수락·거절·철회·목적지·인계 상태 전이와 멱등성은 변경하지 않음 |
| 관리자 조직·가입 코드 관리 | 생성·목록·폐기 동작과 가입 코드 원문 1회 반환 정책은 유지 |
| 실시간 갱신 | SSE 이벤트 종류와 payload를 변경하지 않음 |

## 권한

| 역할 | 허용 작업 | 접근 범위 |
|---|---|---|
| `HOSPITAL_STAFF` | 기존 자기 병원 프로필·수신 상태·병원 제안 API 사용 | JWT의 자기 병원과 자기 조직에 전달된 제안만 |
| `SUPER_ADMIN` | 기존 조직·가입 코드 관리 API 사용 | 임상정보·위치정보를 제외한 운영 관리 범위 |
| `PARAMEDIC` | 기존 앱 API 사용 | 자신의 계정·조직·이송 요청만, 응답 계약 변경 없음 |
| 미인증 사용자 | 없음 | 기존 인증 필요 |

- 이번 기능은 권한 판정, 조직 격리와 환자정보 공개 범위를 변경하지 않습니다.

## 오류

| 조건 | 기대 결과 또는 오류 코드 | HTTP |
|---|---|---:|
| 인증정보 없음 | 기존 `AUTH_001` | 401 |
| 토큰 오류·비활성 계정 | 기존 `AUTH_002` | 401 |
| API 대상과 다른 역할 | 기존 `AUTH_003` | 403 |
| 조직·제안 소유권 불일치 | 기존 표준 오류 | 기존 상태 유지 |
| 요청값 검증 실패 | 기존 `COMMON_001` | 400 |

- 응답 필드 제거 자체로 새 오류 코드나 실패 경로를 만들지 않습니다.
- 모든 실패는 기존 공통 오류 응답과 `X-Trace-Id` 계약을 유지합니다.

## 완료 조건

- [x] 명시한 웹 전용 응답 필드가 실제 JSON에서 제거됨
- [x] 웹이 사용하는 응답 필드, HTTP 상태와 상태 전이가 유지됨
- [x] 공용·Flutter 전용 DTO 파일과 직렬화 계약이 변경되지 않음
- [x] `UNUSED_ENDPOINT`, 명령 응답 전체와 `TEST_ONLY` 필드는 삭제하지 않음
- [x] 병원·관리자 역할과 조직 격리, 환자정보 공개 범위가 유지됨
- [x] DB migration 없이 기존 데이터와 기능이 정상 동작함
- [x] 변경된 React 응답 계약을 핸드오프에 기록함
- [x] 회귀 테스트와 `./gradlew clean check`, 로컬 readiness를 통과함

## 기능 내 결정 사항

| 쟁점 | 결정 | 이유·영향 |
|---|---|---|
| 1차 정리 범위 | 병원·슈퍼 관리자 웹 전용 DTO의 `UNUSED_FIELD`·`PASS_THROUGH` 필드만 제거 | Flutter 사용 현황 없이 공용·앱 계약을 추측해 변경하지 않음 |
| 공용 DTO | 변경·분리하지 않음 | 현재 공통 응답 구조와 앱 연동을 그대로 보존 |
| 앱 전용·웹 미호출 API | 삭제하지 않음 | 웹 미호출은 앱·운영 미사용을 의미하지 않음 |
| 명령 응답 전체 | 이번 범위에서 유지 | 응답 본문 제거와 `204` 전환은 JSON 파싱·테스트 계약을 바꿀 수 있음 |
| `supplementalAssessment` | 병원 상세에서 유지 | 기존 임상정보 공개 기능과 공용 DTO 계약을 보존 |
| 호환성 처리 | 별도 구버전 응답을 두지 않고 제거된 필드를 React 핸드오프에 명시 | 두 웹의 실제 미사용 감사와 팀의 DTO 최소화 결정에 근거 |
| 성능 효과 | JSON 전송량·직렬화 대상 감소로 한정 | 실제 응답시간 개선 폭은 부하·네트워크 검증 전 단정하지 않음 |

## 확인 필요 사항

- 없음

## 진행 기준

- `Policy Decision Status: RESOLVED`이며 공용·앱 DTO 제외와 웹 전용 미사용 필드 정리 범위가 확정됐습니다.
- 별도 정책 확인 없이 상세 구현 계획과 구현을 계속 진행할 수 있습니다.
