# 웹 전용 응답 DTO 최소화 구현 계획

```text
Feature: 21-web-dto-response-minimization
Author: Codex
Handoff Targets: REACT_HOSPITAL_ADMIN
```

> 정책이 확정된 `spec.md`, 2026-08-20 웹 DTO 사용 현황 감사와 현재 백엔드 코드를 기준으로 작성합니다.
> 구현 Step은 최대 8개, 리스크는 최대 5개로 제한합니다.

## 설계 요약

- 선택한 방식:
  - 병원·슈퍼 관리자 웹 전용 Java record에서 스펙에 확정된 미사용 component만 제거합니다.
  - DTO의 정적 팩터리와 `HospitalOfferService`의 생성 인자를 같은 변경에서 축소합니다.
  - 기존 통합 테스트에 사용 필드 유지와 제거 필드 부재 assertion을 함께 추가합니다.
  - 과거 핸드오프를 수정하지 않고 이번 기능 번호의 새 React 핸드오프에 최신 전체 변경 계약을 기록합니다.
- 선택 이유:
  - 공용·Flutter DTO를 건드리지 않으면서 실제 직렬화 JSON만 줄일 수 있습니다.
  - 새 API나 DTO 분리 없이 현재 역할 전용 Endpoint의 응답 계약을 가장 단순하게 축소합니다.
  - 기존 권한·상태·멱등성·DB 로직은 그대로 두고 외부 JSON 모양만 검증할 수 있습니다.
- 검토한 대안과 제외 이유:
  - 공용 DTO를 병원용·앱용으로 분리: 사용자가 공용 계약 유지를 확정했고 불필요한 구조 증가가 생겨 제외합니다.
  - `HospitalOfferDecisionResponse`·`HospitalHandoffConfirmationResponse`를 삭제하고 `204` 반환: 웹 API client의 JSON 파싱과 테스트 계약을 바꾸므로 이번 1차 범위에서 제외합니다.
  - 웹 미호출 Endpoint 삭제: Flutter·배포·모니터링 사용 가능성이 있으므로 제외합니다.
  - `supplementalAssessment` 제거: 기존 임상 공개 요구사항과 공용 DTO 계약을 훼손하므로 제외합니다.
  - Jackson annotation으로 필드만 숨김: Java 타입에는 불필요한 component가 남아 DTO 최소화 목적에 맞지 않아 사용하지 않습니다.

## 구현 Step

| Step | 작업 | 완료 기준 |
|---:|---|---|
| 1 | 병원 프로필·수신 상태 DTO와 정적 팩터리 축소 | `HospitalProfileResponse`에서 `accountId`, `role`, `organizationId`, `HospitalReceivingStatusResponse`에서 `hospitalId`, `organizationId`가 제거되고 나머지 프로필·상태 응답은 동일 |
| 2 | 병원 제안 목록 DTO와 목록 매핑 축소 | 목록의 `page`, `size`, `serverNow`, item의 `canConfirmHandoff`를 제거하고 `items`, `totalElements`, `totalPages` 및 병원별 상태·시각은 유지 |
| 3 | 병원 제안 상세 DTO와 상세 매핑 축소 | 스펙의 최상위·Pre-KTAS·의식·경로·시각 미사용 필드를 제거하고 환자정보, 처치, `supplementalAssessment`, 회신 연락처, ETA와 명령 가능 여부는 유지 |
| 4 | 병원 수락 철회 응답 축소 | 미사용 식별자·제안 상태·목적지 ID·재사용 표시를 제거하고 `transportRequestStatus`, 사유·상세·시각·`searchRestarted`는 유지하며 저장·재검색·멱등 동작은 변경하지 않음 |
| 5 | 슈퍼 관리자 조직·가입 코드 응답 축소 | 조직·가입 코드 페이지 중복 메타데이터, 조직 상태, 가입 코드 미사용 메타데이터와 발급 응답의 `invitation`을 제거하고 화면 사용 필드는 유지 |
| 6 | API 계약·권한·상태 회귀 테스트 정비 | 변경 Endpoint마다 제거 필드 `doesNotExist`, 유지 필드 값, 기존 HTTP 상태·권한 실패를 검증하고 철회 중복 요청은 응답 표시가 아닌 저장 상태·이벤트 중복 방지로 검증 |
| 7 | 공용·Flutter 계약 비변경 검증과 전체 로컬 검사 | 공용·앱 DTO가 diff에 없고 관련 기존 테스트, `./gradlew clean check`, 로컬 readiness가 통과 |
| 8 | 실제 변경 기준 React 핸드오프와 review 작성 | 제거·유지 필드, 전환 주의사항, 테스트 결과가 `docs/handoffs/21-*`와 `review.md`에 실제 코드와 일치하게 기록됨 |

## 변경 범위

| 패키지·파일 | 변경 내용 |
|---|---|
| `hospital/api/HospitalProfileResponse.java` | 계정·역할·조직 공개 ID component와 `from` 매핑 제거 |
| `hospital/api/HospitalReceivingStatusResponse.java` | 병원·조직 공개 ID component와 `from` 매핑 제거 |
| `hospital/search/api/HospitalOfferListResponse.java` | 페이지 중복 메타데이터와 목록 item의 `canConfirmHandoff` 제거 |
| `hospital/search/api/HospitalOfferDetailResponse.java` | 스펙에 열거한 미사용 최상위·중첩 component와 불필요 import 제거 |
| `hospital/search/api/HospitalAcceptanceWithdrawalResponse.java` | 웹 미사용 component 제거, 웹 사용 결과 component 유지 |
| `hospital/search/application/HospitalOfferService.java` | 축소된 목록·상세·철회 DTO 생성 인자 정리, 상세에서 더는 필요 없는 결과 계산 제거 검토 |
| `organization/api/OrganizationListResponse.java` | `items`, `totalPages`만 반환하도록 `from(Page)` 매핑 축소 |
| `organization/api/OrganizationResponse.java` | `status` component·매핑·import 제거 |
| `invitation/api/InvitationListResponse.java` | `items`, `totalPages`만 반환하도록 `from(Page)` 매핑 축소 |
| `invitation/api/InvitationResponse.java` | 미사용 조직·사용·폐기·생성 메타데이터 component와 매핑·import 제거 |
| `invitation/api/IssuedInvitationResponse.java` | 가입 코드 원문 `code`만 반환하도록 축소 |
| `invitation/application/InvitationService.java` | 발급 응답 생성 시 중첩 `InvitationResponse` 생성 제거 |
| `src/test/java/.../hospital/*` | 프로필·수신 상태 응답 필드 부재와 기존 권한·저장 결과 검증 |
| `src/test/java/.../hospital/search/*`, `transport/*` | 제안 목록·상세·철회 응답 축소와 상태·멱등·재검색 회귀 검증 |
| `src/test/java/.../organization/*`, `invitation/*` | 관리자 응답 필드 부재, 사용 필드와 기존 권한·감사 검증 |
| `docs/handoffs/21-web-dto-response-minimization/react-hospital-admin.md` | 변경된 React 병원·관리자 웹 응답 계약과 전환 사항 작성 |
| `docs/features/21-web-dto-response-minimization/review.md` | 실제 구현 범위와 전체 검증 결과 기록 |

### 변경하지 않을 파일·계약

- `auth/api/AuthTokenResponse.java`
- `account/api/SignupResponse.java`
- `invitation/api/InvitationValidationResponse.java`
- `invitation/api/RequiredConsentResponse.java`
- `transport/api/ClinicalTimelineResponse.java`
- `transport/api/TransportLocationResponse.java`
- `transport/api/SupplementalAssessmentResponse.java`
- `realtime/api/RealtimeEventResponse.java`
- `global/exception/ErrorResponse.java`, `FieldErrorResponse.java`
- 구급대원 전용 Controller·요청·응답 DTO
- `HospitalOfferDecisionResponse`, `HospitalHandoffConfirmationResponse`
- 모든 Endpoint, 요청 DTO, HTTP 성공 상태, 오류 코드와 SSE payload

## 응답 매핑 원칙

- DTO component 제거와 생성 인자 제거를 같은 Step에서 수행해 컴파일 가능한 상태를 유지합니다.
- 제거된 값을 계산하기 위한 조회·조립이 다른 유지 필드에도 필요하지 않을 때만 함께 제거합니다.
- Entity, projection과 저장 모델은 응답 DTO 때문에 변경하지 않습니다.
- 목록 페이징 입력 `page`, `size`와 Repository의 `Pageable`은 그대로 유지합니다. 응답 JSON에서 중복 메타데이터만 제거합니다.
- `totalPages` 계산은 기존 Spring Data `Page` 결과를 사용합니다.
- 가입 코드 발급은 원문을 한 번만 반환하고 해시만 저장하는 기존 정책을 그대로 유지합니다.

## DB 변경

- 없음
- Entity, Repository query, Flyway migration과 기존 데이터는 변경하지 않습니다.

## 테스트 계획

### 병원 프로필·수신 상태

- [x] `GET, PUT /api/v1/hospitals/me`가 사용 필드를 반환하고 `accountId`, `role`, `organizationId`를 반환하지 않음
- [x] `PUT /api/v1/hospitals/me/receiving-status`가 `status`, `updatedAt`을 반환하고 병원·조직 ID를 반환하지 않음
- [x] 역할 불일치, 자기 조직 제한과 프로필 저장·감사는 기존대로 동작

### 병원 제안

- [x] ACTIVE·HISTORY 목록에서 `items`, `totalElements`, `totalPages`와 병원별 상태·사유·처리 시각 유지
- [x] 목록에서 `page`, `size`, `serverNow`, item `canConfirmHandoff` 부재
- [x] 상세에서 스펙의 제거 필드는 부재하고 환자 요약·현재 공개 정보·명령 가능 필드는 유지
- [x] 상세 `supplementalAssessment` 공개·차단 정책은 기존 테스트 그대로 통과
- [x] 수락 철회 응답은 최종 요청 상태·사유·상세·시각·재검색 여부를 반환하고 제거 필드는 부재
- [x] 철회 재시도·동시성은 동일한 저장 결과와 이벤트 중복 방지로 검증

### 슈퍼 관리자

- [x] 조직 생성·목록에서 사용 필드와 `totalPages` 유지, 제거 메타데이터 부재
- [x] 가입 코드 발급 응답은 8자리 `code`만 포함하고 원문 비저장·감사 기록 유지
- [x] 가입 코드 목록·폐기에서 화면 사용 필드 유지, 제거 메타데이터 부재
- [x] `PARAMEDIC`, `HOSPITAL_STAFF`의 관리자 API 접근은 기존대로 403

### 공용·앱 계약 가드

- [x] `git diff --name-only`에 스펙의 공용·Flutter DTO가 포함되지 않음
- [x] 로그인·가입 코드 검증·구급대원 가입·이송 생성·상세·임상 갱신·위치·취소·목적지·인계 관련 기존 테스트 통과
- [x] 공통 오류 본문과 `X-Trace-Id` 관련 기존 테스트 통과

### 전체 검사

- [x] 정상 흐름
- [x] 주요 실패 흐름
- [x] 권한·조직·소유권
- [x] 동시성·멱등성 회귀
- [x] MySQL 호환성: DB 변경 없음, 기존 MySQL 통합 테스트 통과
- [x] `./gradlew clean check`
- [x] `./scripts/dev-start.sh`
- [x] `curl http://127.0.0.1:8080/actuator/health/readiness`

## 프론트 핸드오프

- 대상: `REACT_HOSPITAL_ADMIN`
- Flutter: `NONE`
- React: `docs/handoffs/21-web-dto-response-minimization/react-hospital-admin.md`
- 새 문서는 다른 백엔드 문서를 읽지 않아도 변경된 Endpoint별 제거·유지 필드와 HTTP 계약을 확인할 수 있게 작성합니다.
- 과거 핸드오프의 응답 예시에 제거된 필드가 있더라도 이번 21번 문서를 최신 계약으로 안내합니다.

## 유지할 계약

- `spec.md`의 공용·앱 DTO 비변경 범위와 완료 조건
- 기존 Endpoint, 요청 필드, Query, 권한과 HTTP 상태
- 공통 오류 응답과 `X-Trace-Id`
- 역할, 조직과 병원 제안 소유권
- 환자정보·회신 연락처·정확한 위치 공개 범위
- 수락·거절·철회·목적지·취소·인계 상태 전이
- 가입 코드 원문 1회 반환·해시 저장·감사 정책

## 리스크

| 리스크 | 대응 |
|---|---|
| 웹 감사 이후 숨은 소비 코드가 추가됐을 수 있음 | 감사 기준 커밋과 제거 필드를 핸드오프에 명시하고 Ready PR에서 React 담당자가 최종 계약을 확인 |
| record component 제거로 생성 인자 순서나 테스트가 어긋남 | DTO와 모든 생성 지점을 같은 Step에서 수정하고 타깃 테스트 후 전체 컴파일 수행 |
| 철회 응답의 `idempotentReplay` 제거가 내부 멱등성 검증 약화로 이어짐 | 외부 표시 필드 대신 저장 상태·이벤트 수·재검색 회차가 한 번만 생성되는지 검증 |
| 과거 핸드오프 예시와 최신 응답이 다름 | 21번 React 핸드오프를 최신 전환 문서로 제공하고 제거 목록을 명시 |
| 응답 축소의 체감 성능 효과가 작을 수 있음 | 성능 향상을 단정하지 않고 JSON 필드·직렬화 감소와 전체 회귀 검증 결과만 기록 |
