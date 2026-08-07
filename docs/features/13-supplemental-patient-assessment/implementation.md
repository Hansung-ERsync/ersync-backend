# 조건부 추가 환자 평가 저장·조회 구현 계획

```text
Feature: supplemental-patient-assessment
Author: backend AI
Handoff Targets: BOTH
Status: IMPLEMENTED_AND_VERIFIED
```

> `Policy Decision Status: RESOLVED`인 `spec.md`, 백엔드 `main`의
> `63217c7`, Flutter `main`의 `463c86b`를 기준으로 작성했습니다.

## 설계 요약

- 선택한 방식:
  - 기존 이송 요청 생성 DTO에 nullable `supplementalAssessment`를 추가합니다.
  - 공통 메타데이터를 가진 append-only `SupplementalAssessmentRecord`와 현재
    여섯 항목을 가진 `GeneralSupplementalAssessment` 상세를 분리합니다.
  - `CurrentPatientSnapshot`은 nullable 최신 추가 평가 포인터를 가집니다.
  - 구급대원 상세와 병원 제안 상세는 한 공유 응답 mapper를 사용합니다.
- 선택 이유:
  - 현재 화면의 여섯 항목은 즉시 구조화 저장하면서 심정지·외상·뇌졸중 등
    미확정 타입은 나중에 공통 record 아래 별도 상세 테이블로 확장할 수 있습니다.
  - 기존 요청에는 추가 평가가 없으므로 nullable 포인터와 nullable 응답으로
    migration·API 하위 호환성을 유지합니다.
  - 기존 임상 timeline·SSE를 변경하지 않고 필요한 두 상세 조회에서만 현재값을
    제공해 공개 범위를 최소화합니다.
- 검토한 대안과 제외 이유:
  - 모든 추가 평가를 자유 메모 하나로 저장: 구조화 검증·타입별 확장이 불가능해 제외합니다.
  - 미확정 5.8 타입까지 nullable 컬럼·빈 테이블로 생성: 의료 계약을 임의로 고정하므로 제외합니다.
  - unversioned JSON payload: DB 무결성과 타입별 API 계약을 검증하기 어려워 제외합니다.
  - 기존 `current_patient_snapshots`에 여섯 값을 직접 저장: append-only 원본과 snapshot을 혼합하므로 제외합니다.

## API·DTO 설계

### 생성 요청

`CreateTransportRequestRequest`의 마지막에 다음 nullable record를 추가합니다.

```text
supplementalAssessment: SupplementalAssessmentInput?

SupplementalAssessmentInput
  assessedAt: Instant, required when object exists
  enteredAt: Instant, required when object exists
  glucoseMgDl: Integer?, 0..1000
  leftPupil: PupilResponse?
  rightPupil: PupilResponse?
  medicalHistory: String?, raw length 1..120 including spaces
  allergies: String?, raw length 1..120 including spaces
  medications: String?, raw length 1..120 including spaces
  isolationConcern: Boolean?
```

- `@Valid`, `@NotNull`, `@Min`, `@Max`, `@Size`로 단일 필드 형식을 검사합니다.
- `AssessmentProtocolValidator`가 객체 비어 있음, 공백 문자열, 좌우 동공 쌍을
  교차 검증하고 실패 시 기존 `COMMON_001`을 사용합니다.
- `PupilResponse` enum은 `NORMAL`, `SLUGGISH`, `FIXED`, `UNASSESSABLE`입니다.
- 객체가 없으면 기존 요청과 같은 저장 흐름을 유지합니다.
- `TransportRequestFingerprint`는 record component를 재귀적으로 읽으므로 새
  객체를 자동으로 지문에 포함합니다. 별도 민감정보 저장 없이 동일·충돌 재시도
  테스트로 이를 증명합니다.
- 문자열의 최대 120자는 입력 전체의 공백을 포함해 검사합니다. 지문과 저장값은
  앞뒤 공백을 제거하며, 공백만 있는 값은 저장 전에 검증 오류가 되므로 `null`과
  빈 문자열의 모호한 멱등 결과를 만들지 않습니다.

### 공통 응답

새 `SupplementalAssessmentResponse`를 구급대원·병원 상세에서 공유합니다.

```text
assessedAt
enteredAt
serverReceivedAt
glucoseMgDl
leftPupil
rightPupil
medicalHistory
allergies
medications
isolationConcern
```

- 내부 PK, record public ID, 생성자 계정 ID와 환자 직접 식별정보는 반환하지 않습니다.
- `TransportRequestDetailResponse` 최상위에 nullable
  `supplementalAssessment`를 추가합니다.
- `HospitalOfferDetailResponse` 최상위에 nullable
  `supplementalAssessment`를 추가합니다.
- 기존 병원 목록 DTO, 종료 이력 최소 카드, `ClinicalTimelineResponse`, 생성
  성공 응답과 SSE payload는 변경하지 않습니다.

## 도메인·영속성 설계

### 공통 append-only record

`SupplementalAssessmentRecord`는 다음 공통 속성을 가집니다.

```text
publicId
transportRequest
assessmentType: GENERAL
assessmentProtocolVersion
assessedAt
enteredAt
serverReceivedAt
createdBy
```

- 수정·삭제 메서드를 제공하지 않고 정적 factory로만 생성합니다.
- `assessmentProtocolVersion`은 요청에 고정된 버전을 서버가 복사하며 클라이언트가
  추가로 보내지 않습니다.
- 이번 migration의 허용 type은 `GENERAL` 하나입니다. 후속 의료 타입은 새
  상세 테이블과 함께 check constraint를 명시적으로 확장합니다.

### 현재 여섯 항목 상세

`GeneralSupplementalAssessment`는 `@MapsId` 기반 1:1 상세로 다음 값을 가집니다.

```text
supplementalAssessmentRecord
glucoseMgDl
leftPupil
rightPupil
medicalHistory
allergies
medications
isolationConcern
```

- 서비스는 문자열을 trim한 뒤 빈 값은 허용하지 않고 nullable 값은 그대로
  `기록 없음`으로 보존합니다.
- `false`인 `isolationConcern`은 `null`과 구분해 저장합니다.
- 공통 record와 GENERAL 상세는 같은 요청 생성 트랜잭션에서 함께 저장합니다.

### 현재 snapshot

- `CurrentPatientSnapshot`에 nullable `latestSupplementalAssessment` 연관관계를
  추가합니다.
- 최초 요청에 추가 평가가 있으면 생성된 공통 record를 가리키고, 없으면
  `null`을 유지합니다.
- 기존 `lastClinicalUpdateAt`은 모든 최초 임상 기록의 동일한
  `serverReceivedAt`을 계속 사용하므로 의미를 바꾸지 않습니다.
- repository `EntityGraph`에 최신 record와 GENERAL 상세를 포함해 OSIV가 꺼진
  상태에서도 두 상세 응답을 추가 쿼리 누락 없이 만듭니다.
- 응답 mapper는 포인터가 없으면 `null`, GENERAL인데 상세가 없으면 DB 불변식
  손상으로 판단해 `COMMON_003`을 발생시킵니다.

## DB 변경

새 Flyway `V10__create_supplemental_patient_assessment.sql`을 추가하고 V1~V9는 수정하지 않습니다.

### `supplemental_assessment_records`

| 컬럼·제약 | 내용 |
|---|---|
| PK·public ID | `BIGINT AUTO_INCREMENT`, `CHAR(36)` unique |
| 요청·생성자 | `transport_request_id`, `created_by_account_id` FK |
| 타입·버전 | `assessment_type VARCHAR(40)`, `assessment_protocol_version VARCHAR(50)` |
| 세 시각 | `assessed_at`, `entered_at`, `server_received_at` 모두 `DATETIME(6)` |
| 타입 check | 이번 버전은 `GENERAL`만 허용 |
| 조회 index | `(transport_request_id, assessment_type, assessed_at, server_received_at)` |

### `general_supplemental_assessments`

| 컬럼·제약 | 내용 |
|---|---|
| 1:1 PK/FK | `supplemental_assessment_id`가 공통 record를 참조 |
| 혈당 | `INT NULL`, 값이 있으면 0~1000 check |
| 동공 | 좌·우 `VARCHAR(30) NULL`, enum check와 둘 다 null 또는 둘 다 non-null check |
| 문자열 | 과거력·알레르기·복용약 각각 `VARCHAR(120) NULL` |
| 격리 | `BOOLEAN NULL`; false와 null 구분 |
| payload check | 여섯 종류 중 하나 이상 존재해야 함 |

### `current_patient_snapshots`

- nullable `latest_supplemental_assessment_id`와 FK를 추가합니다.
- 기존 행은 모두 `null`로 유지하므로 backfill과 가짜 의료 데이터 생성을 하지 않습니다.
- JPA `ddl-auto=validate`와 MySQL 8.4 실제 migration에서 두 테이블·FK·check·시각
  정밀도를 검증합니다.

## 생성·조회 흐름

```text
PARAMEDIC 생성 요청
→ Bean Validation
→ 프로토콜·추가 평가 교차 검증
→ 추가 평가 포함 전체 요청 SHA-256 지문 계산
→ 기존 멱등 요청 확인
→ 이송 요청과 기존 임상 원본 저장
→ supplementalAssessment가 있으면 공통 record + GENERAL 상세 저장
→ snapshot에 nullable 최신 추가 평가 포인터 저장
→ 기존 병원 자동 탐색·감사 기록
```

```text
구급대원 진행 상세
→ 활성 계정·EMS 조직·직접 소유권 검증
→ snapshot과 최신 추가 평가를 함께 조회
→ 추가 평가 응답 또는 null

병원 제안 상세
→ 병원 계정·조직·제안 소유권 검증
→ 기존 상세 조회 가능 여부 유지
→ 기존 clinical timeline과 같은 상태별 임상 공개 정책 확인
→ 허용 시 추가 평가 응답, 임상 접근 종료 시 null
```

## 구현 Step

| Step | 작업 | 완료 기준 |
|---:|---|---|
| 1 | API 입력·enum·교차 검증 추가 | nullable 기존 요청, 전체 정상 입력, 빈 객체·공백·혈당·동공 오류가 계약대로 동작 |
| 2 | V10 migration과 append-only 공통·GENERAL 도메인 구현 | 두 테이블, FK·CHECK·index와 JPA mapping이 H2·MySQL 8.4에서 일치 |
| 3 | 생성 트랜잭션·snapshot 포인터 연결 | 추가 평가가 요청과 원자적으로 한 번 저장되고 없는 요청은 nullable 상태 유지 |
| 4 | 멱등성·동시 생성 연결 | 같은 키·같은 payload는 한 record, 추가 평가가 다른 payload는 `COMMON_005`, 동시 재시도도 중복 없음 |
| 5 | 공유 응답 mapper와 구급대원 상세 확장 | 소유자만 추가 평가·세 시각을 복구하고 기존 데이터는 null이며 민감 필드 없음 |
| 6 | 병원 상세 확장과 상태별 임상 공개 정책 재사용 | 후보·현재 목적지만 값을 보고 임상 접근 종료 병원은 null; 목록·이력·관리자는 원문 없음 |
| 7 | 단위·통합·권한·MySQL·회귀 검증 | 대상 테스트와 `./gradlew clean check`, 로컬 readiness 통과 |
| 8 | review와 실제 프론트 핸드오프 작성 | Flutter 요청·복구와 React 상세 표시 계약이 실제 코드·테스트와 일치 |

## 변경 범위

| 패키지·파일 | 변경 내용 |
|---|---|
| `transport/api/CreateTransportRequestRequest.java` | nullable 추가 평가 입력과 Bean Validation |
| `transport/api/SupplementalAssessmentResponse.java` | 두 클라이언트가 공유하는 추가 평가 응답 |
| `transport/api/TransportRequestDetailResponse.java` | 구급대원 복구 응답 필드 추가 |
| `hospital/search/api/HospitalOfferDetailResponse.java` | 병원 제안 상세 응답 필드 추가 |
| `transport/domain/SupplementalAssessment*.java`, `PupilResponse.java` | 공통 record, GENERAL 상세, 타입·동공 enum |
| `transport/domain/CurrentPatientSnapshot.java` | nullable 최신 추가 평가 포인터 |
| `transport/infrastructure/SupplementalAssessment*.java` | 공통·상세 repository와 조회 mapping |
| `assessment/protocol/application/AssessmentProtocolValidator.java` | 선택 객체의 비어 있음·공백·동공 쌍 검증 |
| `transport/application/TransportRequestService.java` | 추가 평가 저장과 snapshot 연결 |
| `transport/application/SupplementalAssessmentResponseMapper.java` | 공통 응답 변환과 불변식 검사 |
| `transport/application/TransportRequestDetailQueryService.java` | 소유자 상세에 추가 평가 반환 |
| `hospital/search/application/HospitalClinicalAccessPolicy.java` | 기존 timeline 임상 공개 규칙을 재사용 가능한 정책으로 추출 |
| `transport/application/ClinicalTimelineQueryService.java` | 추출한 병원 임상 공개 정책 사용, 기존 동작 유지 |
| `hospital/search/application/HospitalOfferService.java` | 병원 상세에 권한별 추가 평가 또는 null 반환 |
| `transport/infrastructure/CurrentPatientSnapshotRepository.java` | 최신 추가 평가와 상세 EntityGraph 로딩 |
| `db/migration/V10__*.sql` | 두 테이블과 snapshot FK 추가 |
| `src/test/**` | 검증·생성·멱등성·동시성·복구·병원 권한·MySQL 테스트 |
| `docs/handoffs/13-*`, `review.md` | 실제 구현 계약과 검증 결과 |

## 테스트 계획

- [x] 추가 평가가 없는 기존 생성 요청이 동일하게 `201`, 재시도 `200`
- [x] 여섯 항목 전체 및 각 항목만 단독으로 입력할 수 있고 false 격리 값이 보존됨
- [x] 빈 객체, 공백 문자열, 120자 초과, -1·1001 혈당, 한쪽 동공과 잘못된 enum이 `COMMON_001`
- [x] 같은 키·같은 추가 평가 재시도는 요청·공통 record·GENERAL 상세 각각 한 건
- [x] 같은 키에서 추가 평가가 달라지면 `COMMON_005`
- [x] 두 동시 생성이 요청·추가 평가·snapshot·감사 이벤트를 각각 한 번만 만듦
- [x] 구급대원 본인 상세는 추가 평가와 세 시각을 반환하고 다른 구급대원·병원·관리자는 차단
- [x] 기존 데이터와 추가 평가 없는 새 요청의 구급대원 상세는 `supplementalAssessment: null`
- [x] 목적지 선택 전 허용 병원과 현재 목적지 병원 상세는 추가 평가를 반환
- [x] 거절·철회·선택되지 않은 병원 또는 종료 요청에서는 추가 평가 원문을 반환하지 않음
- [x] 병원 목록·최소 이력·clinical timeline·SSE·감사·생성 응답에 추가 평가 원문이 없음
- [x] 응답에 내부 PK·생성자 계정·환자 직접 식별정보·정확한 위치가 추가되지 않음
- [x] MySQL 8.4에서 V10, JPA validate, FK·CHECK·DATETIME(6), snapshot projection이 통과
- [x] 전체 기존 자동 테스트와 `./gradlew clean check`
- [x] `./scripts/dev-start.sh`와 `/actuator/health/readiness`

## 프론트 핸드오프

- 대상: `BOTH`
- Flutter: `docs/handoffs/13-supplemental-patient-assessment/flutter-paramedic.md`
  - 최신 앱 `ApiPatientAssessmentRepository._requestBody`에 현재 누락된 객체 추가
  - `PupilResponse`의 API 값 매핑 추가
  - 추가 평가가 하나라도 있으면 `assessedAt`, `enteredAt` 전송
  - 진행 이송 상세 복구 시 nullable 추가 평가 반영
- React: `docs/handoffs/13-supplemental-patient-assessment/react-hospital-admin.md`
  - 병원 제안 상세의 nullable 추가 평가와 enum·단위·시각
  - 임상 접근이 끝나면 null이고 목록·최소 이력에는 필드가 없다는 계약
- 프론트 코드는 이번 백엔드 브랜치에서 수정하지 않습니다.
- 구현과 로컬 검증 후 예정값이 아닌 실제 JSON·오류·상태별 결과로 작성합니다.

## 유지할 계약

- `spec.md`의 선택 입력·기록 없음 의미와 미확정 5.8 타입 제외
- 기존 요청 생성 `201`, 멱등 재시도 `200`, 충돌 `COMMON_005`
- 요청 생성 뒤 자동 병원 탐색과 기존 상태 전이
- 구급대원 직접 소유권, 병원 조직·상태별 임상 접근과 슈퍼 관리자 차단
- 기존 clinical timeline·SSE·감사·로그의 임상 원문 비포함
- 환자 직접 식별정보 비수집, 정확한 위치 공개 범위와 공통 오류·Trace ID
- V1~V9 migration 불변과 MySQL 8.4 호환성

## 리스크

| 리스크 | 대응 |
|---|---|
| 공통 record와 GENERAL 상세 중 하나만 저장돼 불완전한 임상 기록 발생 | 같은 트랜잭션, 1:1 PK/FK, mapper 불변식 검사와 실패 rollback 테스트 |
| 기존 데이터에는 snapshot 포인터가 없어 조회·JPA validate 실패 | nullable FK·연관관계로 추가하고 backfill 없이 null 응답 회귀 테스트 |
| 거절·철회·비목적지 병원에 추가 임상정보가 노출됨 | timeline의 상태별 정책을 별도 component로 추출해 새 응답에도 재사용하고 상태 전이 권한 테스트 |
| Flutter 화면 값이 로컬에는 남지만 실제 요청에 계속 누락됨 | 실제 코드 기준 핸드오프에 요청 mapper·동공 API 값·시각 필드를 명시하고 dev 연동 시 payload 확인 |
| 미확정 5.8 타입을 현재 GENERAL 구조에 억지로 추가함 | 이번 enum·DB check를 GENERAL로 제한하고 후속 타입은 정책 확정과 새 migration으로만 확장 |
