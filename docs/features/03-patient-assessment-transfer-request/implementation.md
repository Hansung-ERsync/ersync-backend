# 환자 평가 및 이송 요청 생성 구현 계획

```text
Feature: patient-assessment-transfer-request
Author: backend AI collaboration
Handoff Targets: BOTH
```

> `Policy Decision Status: RESOLVED`인 `spec.md`와 현재 인증·가입 코드를
> 기준으로 작성한 구현 계획입니다. 개발용 `ERSYNC_MVP_1.0`의 기본 평가와
> 테스트 연락처만 다루며 병원 탐색·응답은 포함하지 않습니다.

## 설계 요약

- 선택한 방식:
  - 병원 연락처는 기존 `HospitalProfile.contact`를 유지합니다.
  - 구급대원 연락처는 새 `ParamedicProfile`에 저장합니다.
  - 연락처 제공 동의는 역할별 프로필에 중복 저장하지 않고 계정 기준의 불변 `ContactSharingConsent` 이력으로 분리합니다.
  - 이송 요청 생성 시 JWT 주체를 DB 계정·조직과 다시 대조하고 구급대원 프로필의 연락처를 요청 스냅샷으로 복사합니다.
  - 환자 평가는 하나의 JSON이나 메모로 저장하지 않고 인구학·발생정보·Pre-KTAS·의식·활력징후·처치 기록으로 구조화합니다.
  - `CurrentPatientSnapshot`은 환자 기본·발생·프로토콜과 최신 Pre-KTAS·의식·활력·현재 처치 포인터를 모은 읽기 모델로 구성합니다.
  - 최신 임상 기록의 순서와 갱신 시각은 신뢰할 수 있는 서버 수신 시각으로 판정하고 클라이언트 임상·입력 시각은 원본 기록에 별도로 보존합니다.
  - 프로토콜은 코드에 포함되는 버전별 release artifact와 검증기 조합으로 제공하고 슈퍼 관리자 편집 기능은 만들지 않습니다.
  - 멱등성은 인증 계정과 `Idempotency-Key` 조합의 DB 고유 제약, 계정 행 잠금과 정규화된 요청 지문으로 보장합니다.
- 선택 이유:
  - 기존 병원 가입 계약을 최대한 유지하면서 구급대원 연락처와 동의 사실을 구분해 감사할 수 있습니다.
  - 요청마다 연락처를 다시 받지 않아 다른 계정의 번호를 임의로 제출하는 문제를 막습니다.
  - 임상 기록을 append-only 구조로 시작하면 이송 중 재평가 기능이 추가돼도 최초 기록을 덮어쓰지 않습니다.
  - 계정 단위 직렬화는 요청 생성 빈도가 낮은 MVP에서 단순하고 검증 가능한 동시성 제어를 제공합니다.
- 검토한 대안과 제외 이유:
  - 요청 본문의 `callbackContact`: 인증된 계정 정보와 연결되지 않고 매 요청 오입력·변조가 가능해 제외합니다.
  - `user_accounts`에 모든 역할의 연락처 저장: 기존 병원 프로필 연락처와 중복되고 역할별 의미가 흐려져 제외합니다.
  - 임상 전체 JSON 저장: 구조 검증, 이력과 조회 계약을 보장하기 어려워 제외합니다.
  - 기존 개발 계정에 임의 동의값을 migration으로 채우기: 실제 동의가 아닌 값을 만들게 되므로 제외합니다.
  - Redis 멱등성 키: DB 트랜잭션과 별도 상태가 생기며 현재 규모에 불필요해 제외합니다.

## 외부 API 계획

| 대상 | 동작 | 계획 |
|---|---|---|
| 병원 가입 | 기존 `POST /api/v1/auth/signups/hospital` 확장 | 기존 `contact` 유지, 연락처 제공 동의 여부·동의 문구 버전 필수 추가 |
| 구급대원 가입 | 기존 `POST /api/v1/auth/signups/paramedic` 확장 | 연락처·연락처 제공 동의 여부·동의 문구 버전 필수 추가 |
| 프로토콜 조회 | `GET /api/v1/assessment-protocols/active` 추가 | `PARAMEDIC`에게 활성 버전, 개발 상태, Pre-KTAS 기준 상태, enum·단위·필수 규칙 반환 |
| 요청 생성 | `POST /api/v1/transport-requests` 추가 | Bearer 인증과 `Idempotency-Key` 헤더, 구조화된 최초 평가·출발 좌표 입력 |

- 가입 클라이언트는 사용자가 동의 문구를 확인한 뒤 동의 여부와 표시한 문구 버전을 제출합니다.
- 서버는 현재 허용한 동의 버전과 일치하는지 확인하고 실제 동의 시각은 주입된 `Clock`으로 기록합니다.
- 이송 요청 본문에는 계정 ID, 조직 ID, 병원 ID와 회신 연락처를 포함하지 않습니다.
- `Idempotency-Key`는 8~100자의 영문·숫자와 `-`, `_`, `.`, `:`만 허용합니다.
- 연락처는 공통 정책에서 앞뒤 공백을 제거하고 8~30자의 숫자·`+`·`-` 형식을 검증하며, 실제 정규식은 양쪽 가입 핸드오프에 동일하게 기록합니다.
- 최초 생성은 `201 Created`와 `Location`을 반환하고, 같은 내용의 멱등 재시도는 기존 요청을 `200 OK`로 반환합니다.
- 생성 응답은 요청 공개 ID, `SEARCHING` 상태, 고정된 평가 버전과 서버 생성 시각만 포함하고 임상 원문·좌표·연락처를 다시 노출하지 않습니다.
- `Idempotency-Key`는 CORS 허용 헤더에 추가하고 계정별로 해석합니다.

## 프로토콜과 검증

- `AssessmentProtocolRegistry`가 활성 버전 `ERSYNC_MVP_1.0`과 개발 상태를 제공합니다.
- Pre-KTAS 기준 버전은 공식 값이 아니라는 사실이 드러나는 개발용 식별자로 설정하고 프로토콜 조회 응답에도 동일하게 표시합니다.
- Request DTO의 Bean Validation은 누락·문자열 길이·숫자 형식처럼 단순한 형태를 검사합니다.
- collection 내부의 `null` 요소도 Bean Validation에서 차단해 잘못된 입력이 항상 `COMMON_001`로 응답되게 합니다.
- 나이는 0 이상만 검증하며 MVP에 없는 임의의 최대값을 서버 정책으로 추가하지 않습니다.
- `AssessmentProtocolValidator`는 다음 교차 필드 규칙을 검사합니다.
  - `EXACT`·`ESTIMATED` 나이는 값 필수, `UNKNOWN`은 값 금지
  - 비질병 발생은 손상 기전과 손상 부위 필수
  - 정확·추정 발생 시각은 시각 필수, `UNKNOWN`은 시각 금지
  - 완료 Pre-KTAS와 긴급 미완료 예외는 동시에 제출할 수 없음
  - 긴급 예외·AVPU 평가 불가·`OTHER`는 정해진 사유와 조건부 상세 필수
  - 활력징후 다섯 종류가 정확히 한 번씩 존재하고 상태별 값·사유 조합이 유효함
  - 혈압은 하나의 활력 항목 안에 수축기·이완기 값을 함께 가짐
  - `NONE` 처치는 다른 처치와 함께 존재할 수 없음
  - 처치 유형별 문서에 명시된 최소 상세정보와 임상 시각이 존재함
- 공식 범위가 없는 의학적 정상·비정상 수치 판정은 추가하지 않고 숫자 형식과 상태 조합만 검증합니다.
- 상황별 추가 평가는 이번 생성 DTO에 넣지 않으며, 규칙과 typed DTO가 확정된 새 프로토콜 버전에서 추가합니다.
- 모든 enum과 단위는 서버 계약으로 고정하고 자유 문자열은 `OTHER` 상세처럼 허용된 위치에만 사용합니다.

## DB 변경

- 새 Flyway migration `V2__create_patient_assessment_transport_schema.sql`과 보완 migration
  `V3__complete_current_patient_snapshot.sql`을 추가하고 적용된 `V1`·`V2`는 수정하지 않습니다.
- 모든 외부 노출 ID는 UUID 문자열 `public_id`를 사용하고 내부 PK는 응답하지 않습니다.

| 테이블 | 주요 내용과 제약 |
|---|---|
| `paramedic_profiles` | 계정·구급대 조직, 연락처, 생성·수정 시각; 계정당 하나, `PARAMEDIC` 역할은 애플리케이션에서 재검증 |
| `contact_sharing_consents` | 계정, 동의 문구 버전, 서버 동의 시각, 생성 시각; 동의 사실을 append-only로 보관하고 연락처 원문은 넣지 않음 |
| `transport_requests` | 소유 계정·EMS 조직, `SEARCHING` 상태, 회신 연락처 스냅샷, 평가 버전, 출발 좌표·확인 방식, 멱등성 키·요청 지문, 서버 시각·버전 |
| `patient_demographics` | 요청당 하나의 나이 상태·조건부 값·성별 |
| `incident_assessments` | 요청당 하나의 발생 유형·손상 기전·주증상·발생 시각 상태와 입력·서버 시각 |
| `incident_injury_sites` | 요청의 손상 부위 enum 집합, 요청·부위 고유 제약 |
| `incident_secondary_symptoms` | 요청의 부증상 enum 집합, 요청·증상 고유 제약 |
| `pre_ktas_assessments` | 완료 단계 또는 긴급 미완료 사유, 분류·입력·서버 시각, 기준 버전, 생성자; append-only |
| `consciousness_assessments` | AVPU·평가 불가 사유, 관찰·입력·서버 시각, 생성자; append-only |
| `vital_sign_sets` | 한 번의 다섯 활력징후 묶음, 측정·입력·서버 시각, 생성자; append-only |
| `vital_sign_measurements` | 활력 종류·상태·수치·측정 불가 사유; 세트·종류 고유 제약과 상태별 CHECK |
| `treatment_events` | 처치 유형, 결과와 유형별 typed nullable 열, 시행·입력·서버 시각, 생성자; JSON 없이 append-only |
| `current_patient_snapshots` | 요청당 하나, 환자 기본·발생·프로토콜, 최신 Pre-KTAS·의식·활력 포인터와 서버 기준 마지막 임상 갱신 시각; 원본 기록은 별도 유지 |
| `current_patient_snapshot_treatments` | 현재 요약에 포함되는 처치 이벤트 포인터 집합; 처치 원본은 `treatment_events`에 유지 |

### migration과 기존 개발 계정

- `paramedic_profiles`와 동의 이력은 새 가입부터 생성합니다.
- 기존 구급대원 계정에 가짜 연락처나 가짜 동의를 자동으로 채우지 않습니다.
- 기존 계정에 프로필·유효 동의가 없으면 요청 생성 시 `USER_005`를 반환합니다.
- 개발 환경의 기존 계정은 새 가입 코드로 테스트 계정을 다시 만들어 검증하며 실제 번호를 사용하지 않습니다.
- 병원 기존 프로필의 연락처는 유지하되 동의 이력을 임의 생성하지 않습니다. 변경된 가입 계약은 새 가입부터 적용합니다.

## 트랜잭션과 멱등성

1. JWT의 계정 공개 ID로 계정과 조직을 잠금 조회합니다.
2. `PARAMEDIC`, 활성 계정, `EMS_UNIT`, 구급대원 프로필과 유효 동의를 검증합니다.
3. 입력을 정규화하고 순서 없는 enum 집합을 정렬해 SHA-256 요청 지문을 계산합니다.
4. 같은 계정·멱등성 키의 요청이 있으면 지문을 비교합니다.
5. 지문이 같으면 기존 응답을 반환하고, 다르면 `COMMON_005`를 반환합니다.
6. 새 키이면 요청·임상 원본·현재 스냅샷·감사 이벤트를 하나의 트랜잭션에 저장합니다.
7. DB의 `(owner_account_id, client_idempotency_key)` 고유 제약을 최종 중복 방어선으로 사용합니다.

- 연락처, 임상 원문과 좌표를 요청 지문 계산 외의 로그·감사 payload에 포함하지 않습니다.
- 감사 이벤트에는 `TRANSPORT_REQUEST_CREATED`, 행위자, 조직, 요청 공개 ID, 서버 시각과 trace ID만 저장합니다.
- 병원 검색과 외부 지도 API 호출이 없으므로 이 기능의 생성 트랜잭션 안에서 외부 호출을 하지 않습니다.

## 구현 Step

| Step | 작업 | 완료 기준 |
|---:|---|---|
| 1 | V2·V3 migration과 연락처·동의·이송·구조화 임상 Entity/Repository 작성 | MySQL 8.4 migration과 JPA `validate`가 일치하고 적용된 migration은 변경되지 않음 |
| 2 | 공통 연락처 검증과 동의 이력 추가, 병원·구급대원 가입 계약 확장 | 두 가입이 연락처·동의를 원자적으로 저장하고 누락·버전 불일치 시 가입 코드가 소비되지 않음 |
| 3 | `ERSYNC_MVP_1.0` registry·조회 API·교차 필드 validator 작성 | Flutter가 개발 상태·enum·단위·필수 규칙을 조회하고 잘못된 조합이 `COMMON_001`로 거절됨 |
| 4 | 이송 요청 생성 API와 계정·조직·프로필 권한 검증 작성 | `PARAMEDIC`만 등록 연락처를 자동 연결해 `SEARCHING` 요청을 생성함 |
| 5 | 최초 임상 원본과 현재 스냅샷 저장 흐름 작성 | 환자·발생·최신 평가·처치·프로토콜이 요약되고 서버 시각 기준으로 한 트랜잭션에 저장됨 |
| 6 | 멱등성·동시성·감사·오류 처리 작성 | 동일 재시도는 같은 요청, 다른 payload는 충돌, 동시 요청은 하나만 생성됨 |
| 7 | 단위·통합·권한·MySQL·비로그 테스트 작성 | 정상·실패·긴급 예외·모든 상태 조합과 보안 가드레일이 자동 검증됨 |
| 8 | 전체 검사·로컬 실행 후 review와 양쪽 핸드오프 작성 | `./gradlew clean check`, readiness와 실제 API 시나리오가 통과하고 문서가 코드와 일치함 |

## 변경 범위

| 패키지·파일 | 변경 내용 |
|---|---|
| `account/api`, `account/application` | 병원·구급대원 가입 연락처 동의 필드와 저장 흐름 추가 |
| `paramedic/**` | 구급대원 프로필·연락처 영속성과 조회 추가 |
| `privacy/**` | 연락처 제공 동의 버전 검증과 불변 동의 이력 추가 |
| `assessment/protocol/**` | 개발용 프로토콜 registry, 응답 모델과 교차 필드 검증기 추가 |
| `transport/api/**` | 이송 요청 생성 Controller, 중첩 Request DTO와 Response 추가 |
| `transport/application/**` | 인증 컨텍스트, 요청 지문, 멱등 생성과 snapshot 조립 추가 |
| `transport/domain/**`, `transport/infrastructure/**` | 요청·임상 원본·현재 snapshot Entity와 Repository 추가 |
| `global/exception/ErrorCode.java` | 연락처 미등록 `USER_005` 등 필요한 안전한 공개 오류 추가 |
| `global/security/SecurityConfig.java` | `Idempotency-Key` CORS 허용 헤더 추가; 기존 공개 가입 외 요청 API는 인증 유지 |
| `audit/domain/AuditAction.java` | 연락처 동의와 요청 생성 감사 행위 추가 |
| `application*.yaml` | 활성 개발 프로토콜·Pre-KTAS 상태·연락처 동의 문구 버전 설정 추가 |
| `db/migration/V2__*.sql`, `V3__*.sql` | 새 테이블, 완전한 snapshot 포인터, FK·CHECK·고유 제약과 인덱스 추가 |
| `src/test/**` | 가입 회귀, 프로토콜, 생성·권한·멱등성·동시성·MySQL 테스트 추가 |

## 테스트 계획

- [x] 병원 가입이 기존 연락처와 동의 버전·서버 시각을 함께 저장함
- [x] 구급대원 가입이 프로필 연락처와 동의 이력을 함께 저장함
- [x] 연락처·동의 누락 또는 버전 불일치 시 계정·프로필·동의가 생성되지 않고 가입 코드가 소비되지 않음
- [x] 기존 가입 코드 동시 사용 방지와 병원 공용 계정 하나 정책이 유지됨
- [x] 프로토콜 조회가 `ERSYNC_MVP_1.0`, 개발 상태, 고정 enum·단위를 반환함
- [x] 나이·발생·시각·Pre-KTAS·AVPU·활력징후·처치의 정상 및 잘못된 조건부 조합 검증
- [x] Pre-KTAS 완료 흐름과 긴급 미완료 흐름이 상호 배타적으로 저장됨
- [x] 활력징후 다섯 종류의 `VALUE`, `MEASUREMENT_UNAVAILABLE`, `PATIENT_REFUSED` 조합 검증
- [x] 활력징후·처치 배열 내부 `null` 요소가 500이 아니라 `COMMON_001`로 거절됨
- [x] 나이는 0 이상을 허용하고 문서에 없는 임의의 최대 나이 제한을 적용하지 않음
- [x] `NONE`과 유형별 처치 상세·성공 실패 기록 검증
- [x] 요청 소유자·조직·연락처가 JWT/DB에서 결정되고 요청 본문으로 조작되지 않음
- [x] `HOSPITAL_STAFF`, `SUPER_ADMIN`, 다른 조직과 미인증 접근이 차단됨
- [x] 같은 멱등성 키·같은 payload 재시도는 한 요청만 유지함
- [x] 같은 멱등성 키·다른 payload는 `COMMON_005`이고 원본 요청은 바뀌지 않음
- [x] 두 동시 생성이 DB에서 하나의 요청·임상 기록·감사 이벤트만 생성함
- [x] 현재 요약이 환자·발생·프로토콜·현재 처치와 최신 평가를 가리키고 마지막 갱신 순서는 서버 수신 시각을 사용함
- [x] H2는 빠른 회귀 검사에만 사용하고 Testcontainers MySQL 8.4에서 migration·CHECK·고유 제약·JPA validate를 검증함
- [x] 오류 응답과 `X-Trace-Id`가 유지되고 캡처 로그에 연락처·임상 원문·정확한 좌표가 없음
- [x] `./gradlew clean check`
- [x] local 프로필 MySQL 8.4 실행과 `/actuator/health/readiness`

## 프론트 핸드오프

- 대상: `BOTH`
- Flutter: `docs/handoffs/03-patient-assessment-transfer-request/flutter-paramedic.md`
  - 구급대원 가입 연락처·동의 변경
  - 프로토콜 조회와 최초 평가·멱등성 헤더·생성 응답
- React: `docs/handoffs/03-patient-assessment-transfer-request/react-hospital-admin.md`
  - 병원 가입 연락처 동의 변경
- 구현과 로컬 검증 후 실제 코드의 필드·enum·오류·HTTP 상태·전환 방법만 기록합니다.

## 유지할 계약

- `spec.md`의 연락처 출처·개인정보 제한·개발 프로토콜과 완료 조건
- 가입 코드 소비, 병원 공용 계정 하나와 가입 직후 수신 `OFF`
- JWT 역할·조직 검증과 `SUPER_ADMIN`의 환자·위치·연락처 접근 금지
- 공통 오류 응답, `X-Trace-Id`와 민감정보 비로그
- 기존 V1 migration 불변과 MySQL 8.4 호환성
- 병원 자동 탐색·응답·목적지 정책은 이번 기능에서 구현하거나 변경하지 않음

## 리스크

| 리스크 | 대응 |
|---|---|
| 구조화된 최초 평가의 테이블·조건 조합이 많아 부분 저장이 발생함 | 단일 생성 트랜잭션, DB FK·CHECK와 전체 통합 테스트로 원자성 검증 |
| 가입 필수 필드 추가로 기존 Flutter·React 요청이 실패함 | 양쪽 핸드오프에 전환 계약을 기록하고 같은 배포 주기에 가입 화면 갱신 |
| 기존 개발 계정에 연락처·실제 동의가 없음 | 가짜 backfill을 하지 않고 `USER_005`로 차단하며 테스트용 신규 가입 절차 안내 |
| 개발용 프로토콜이 공식 의료 규칙으로 오인됨 | 응답·설정·문서에 개발 상태를 표시하고 실제 데이터 사용을 금지 |
| 동시 재시도로 요청이 중복 생성됨 | 계정 잠금, 계정·멱등성 키 고유 제약과 요청 지문 비교를 함께 검증 |
