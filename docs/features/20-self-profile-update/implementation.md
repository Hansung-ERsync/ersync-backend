# 병원·구급대원 자기 프로필 수정 구현 계획

```text
Feature: 20-self-profile-update
Author: Codex
Handoff Targets: BOTH
```

## 설계 요약

- 선택한 방식:
  - 기존 자기 프로필 경로에 전체 수정 `PUT`을 추가합니다.
    - `PUT /api/v1/hospitals/me`
    - `PUT /api/v1/paramedics/me`
  - 병원과 구급대원별 Command Service가 인증 계정·조직·역할을 DB와 대조하고 자기 프로필 행을 비관적 쓰기 잠금으로 조회합니다.
  - 요청 DTO의 Bean Validation과 기존 도메인 정책을 함께 사용해 모든 값을 먼저 검증한 뒤 Entity의 의미 있는 변경 메서드로 한 번에 반영합니다.
  - 병원은 위치 묶음과 연락처, 구급대원은 표시 이름과 회신 연락처를 각각 한 트랜잭션에서 갱신합니다.
  - 성공 응답은 기존 `GET /me`와 같은 전체 프로필 DTO를 사용해 저장 결과를 즉시 반환합니다.
  - `HOSPITAL_PROFILE_UPDATED`, `PARAMEDIC_PROFILE_UPDATED` 감사 행위를 추가하되 감사 이벤트에는 연락처·주소·좌표 원문을 저장하지 않습니다.
- 선택 이유:
  - 전체 수정 `PUT`은 주소만 바뀌고 좌표는 이전 값으로 남는 부분 갱신 오류를 막습니다.
  - 프로필 행 잠금은 동시에 들어온 두 요청의 필드가 섞이거나 JPA 낙관적 잠금 오류가 외부 500으로 노출되는 것을 막습니다.
  - 기존 `TransportRequest.callbackContact`와 `HospitalOffer` 위치·연락처 스냅샷을 수정하지 않으면 이미 전달된 정보와 과거 이력의 의미를 보존할 수 있습니다.
  - 현재 테이블에 필요한 필드와 `updated_at`이 모두 있으므로 불필요한 스키마 변경 없이 구현할 수 있습니다.
- 검토한 대안과 제외 이유:
  - `PATCH` 부분 수정: 병원 주소·좌표가 서로 다른 시점 값으로 저장될 수 있어 제외합니다.
  - 프로필 수정 시 기존 이송 요청·제안 일괄 갱신: 진행 중·과거 기록을 소급 변경하고 이미 본 정보와 DB가 달라질 수 있어 제외합니다.
  - 서버 Geocoding으로 주소·좌표 재계산: 현재 가입 계약과 다른 외부 의존성·오류 정책이 추가되므로 이번 범위에서 제외합니다.
  - 수정마다 새 연락처 동의 생성: 동의 목적과 버전은 유지되고 값만 최신화되므로 제외합니다.
  - 프로필 변경 이력 전용 테이블: MVP에서는 기존 감사 이벤트로 행위자·대상·시각을 추적할 수 있어 제외합니다.

## 구현 Step

| Step | 작업 | 완료 기준 |
|---:|---|---|
| 1 | 병원·구급대원 수정 요청 계약과 공통 정규화 정책 구성 | 두 `PUT` 요청 DTO에 필수값·길이·좌표 검증을 선언하고 주소·상세주소·이름·연락처 정규화가 가입 계약과 일치함 |
| 2 | 프로필 도메인 변경 메서드와 잠금 조회 추가 | `HospitalProfile`은 위치·연락처, `ParamedicProfile`은 이름·연락처를 Setter 없이 한 번에 변경하고 두 Repository가 자기 프로필을 쓰기 잠금으로 조회함 |
| 3 | 병원 자기 프로필 수정 유스케이스 연결 | 활성 `HOSPITAL_STAFF`와 병원 조직·프로필 소유권을 확인하고 저장 후 전체 `HospitalProfileResponse`를 반환하며 `receivingStatus`를 유지함 |
| 4 | 구급대원 자기 프로필 수정 유스케이스 연결 | 활성 `PARAMEDIC`과 EMS 조직·프로필 소유권·기존 연락처 동의를 확인하고 저장 후 동의 포함 전체 `ParamedicProfileResponse`를 반환함 |
| 5 | 감사 기록과 트랜잭션 원자성 적용 | 성공한 수정만 역할별 감사 이벤트 한 건을 남기고 검증·권한·동의 실패에서는 프로필과 감사 이벤트가 모두 변경되지 않음 |
| 6 | API·권한·동시성·스냅샷 회귀 테스트 작성 | 정상 수정, 상세주소 제거, 실패 롤백, 다른 역할 차단, 동시 전체 수정, 기존/신규 이송 요청·병원 제안 값 분리를 자동 검증함 |
| 7 | 전체 검사와 로컬 실행 검증 | 대상 테스트, MySQL 8.4 호환 검사, `./gradlew clean check`, Docker MySQL 실행과 readiness가 통과함 |
| 8 | 실제 구현 기준 review와 양쪽 핸드오프 작성 | `review.md`, Flutter·React 핸드오프에 최종 요청·응답·검증·오류·스냅샷 적용 시점을 기록함 |

## 변경 범위

| 패키지·파일 | 변경 내용 |
|---|---|
| `hospital/api/HospitalProfileController` | 기존 `GET`과 같은 경로에 병원 프로필 `PUT` 연결 |
| `hospital/api/UpdateHospitalProfileRequest` | 주소·상세주소·위도·경도·연락처 입력과 Bean Validation 정의 |
| `hospital/application/HospitalProfileCommandService` | 병원 인증·조직·소유권 검증, 정규화, 잠금, 저장, 감사와 응답 조립 |
| `hospital/application` 프로필 정책 | 기본주소 trim·상세주소 nullable 변환을 가입과 수정에서 공통 사용하도록 분리 |
| `account/application/AccountSignupService` | 병원 가입도 분리한 공통 주소·상세주소 정규화 정책을 사용하도록 정렬 |
| `hospital/domain/HospitalProfile` | 수신 상태를 건드리지 않는 위치·연락처 변경 메서드 추가 |
| `hospital/infrastructure/HospitalProfileRepository` | 기존 계정 기준 비관적 잠금 조회를 수정 유스케이스에서도 사용 |
| `paramedic/api/ParamedicProfileController` | 기존 `GET`과 같은 경로에 구급대원 프로필 `PUT` 연결 |
| `paramedic/api/UpdateParamedicProfileRequest` | 표시 이름·회신 연락처 입력과 Bean Validation 정의 |
| `paramedic/application/ParamedicProfileCommandService` | 구급대원 인증·조직·소유권·동의 검증, 잠금, 저장, 감사와 응답 조립 |
| `paramedic/application/ParamedicProfilePolicy` | 기존 이름 trim·2~50자·제어문자 금지 정책을 수정에서도 재사용 |
| `privacy/application/ContactPolicy` | 기존 전화번호 trim·형식 검증을 두 수정 유스케이스에서 재사용 |
| `paramedic/application` 응답 조립 구성 | 조회와 수정이 같은 동의 해석 및 `ParamedicProfileResponse` 계약을 사용하도록 중복 책임 분리 |
| `paramedic/domain/ParamedicProfile` | 이름·연락처를 함께 변경하는 도메인 메서드 추가 |
| `paramedic/infrastructure/ParamedicProfileRepository` | 계정 기준 비관적 쓰기 잠금 조회 추가 |
| `audit/domain/AuditAction` | 병원·구급대원 프로필 수정 감사 행위 두 종류 추가 |
| `HospitalProfileIntegrationTest`, `ParamedicProfileIntegrationTest` | 정상·검증·권한·동의·감사 API 시나리오 추가 |
| `AccountSignupIntegrationTest` | 공통 정책 분리 뒤 기존 병원 가입 정규화·검증 계약 회귀 확인 |
| 프로필 동시성 통합 테스트 | 두 전체 수정과 병원 수신 상태 변경 경합에서 완전한 상태만 저장되는지 검증 |
| 이송 요청·병원 검색 통합 테스트 | 수정 전 스냅샷 유지와 수정 후 새 데이터의 최신 프로필 사용 검증 |
| `docs/handoffs/20-self-profile-update/*` | Flutter·React가 단독 연동할 수 있는 실제 API 계약 작성 |
| `docs/features/20-self-profile-update/review.md` | 실제 변경·테스트·남은 리스크 기록 |

## 요청 처리 상세

### 병원 프로필 수정

1. Controller가 `@Valid`로 필수값·길이·좌표 범위를 검사합니다.
2. 기본주소와 선택 상세주소를 공통 병원 프로필 정책으로 정규화하고 연락처는 `ContactPolicy`로 검증합니다.
3. JWT 역할이 `HOSPITAL_STAFF`인지 확인하고 계정·병원 조직이 활성 상태이며 JWT 조직과 일치하는지 확인합니다.
4. `findLockedByAccountPublicId`로 자기 병원 프로필을 잠그고 계정·조직 연결을 다시 검증합니다.
5. 도메인 메서드로 주소·상세주소·좌표·연락처만 교체합니다. 수신 상태와 공개 ID는 유지합니다.
6. `saveAndFlush` 뒤 `HOSPITAL_PROFILE_UPDATED` 감사 이벤트를 같은 트랜잭션에 저장합니다.
7. 저장된 프로필을 `HospitalProfileResponse`로 반환합니다.

### 구급대원 프로필 수정

1. Controller가 `@Valid`로 이름·연락처 필수값과 길이를 검사합니다.
2. 이름은 `ParamedicProfilePolicy`, 연락처는 `ContactPolicy`로 정규화·검증합니다.
3. JWT 역할이 `PARAMEDIC`인지 확인하고 계정·EMS 조직이 활성 상태이며 JWT 조직과 일치하는지 확인합니다.
4. 현재 계정에 연락처 수집·이용과 병원 제공 동의 또는 허용된 기존 통합 동의가 있는지 기존 조회 규칙으로 확인합니다.
5. 계정 기준 쓰기 잠금으로 자기 프로필을 읽고 계정·조직 연결을 다시 검증합니다.
6. 도메인 메서드로 이름·연락처를 함께 교체하고 `PARAMEDIC_PROFILE_UPDATED` 감사를 같은 트랜잭션에 저장합니다.
7. 기존 동의 정보를 함께 조립한 `ParamedicProfileResponse`를 반환합니다.

## 동시성·트랜잭션

- 두 수정 API는 각각 단일 트랜잭션에서 프로필 잠금, 필드 변경, 감사 저장, 응답용 flush까지 수행합니다.
- 동일 프로필에 동시에 들어온 두 전체 수정은 프로필 행 잠금 순서대로 처리합니다.
- 최종 프로필은 마지막으로 적용된 요청의 완전한 필드 묶음이며, 첫 요청의 주소와 둘째 요청의 좌표가 섞인 상태를 허용하지 않습니다.
- 병원 프로필 수정과 기존 수신 상태 변경도 같은 병원 프로필 잠금을 사용합니다. 정보 수정은 잠금 획득 시점의 `receivingStatus`를 변경하지 않습니다.
- 동일한 `PUT` 본문을 재전송해도 프로필이나 이송 데이터가 추가 생성되지 않으므로 별도 `Idempotency-Key`는 요구하지 않습니다.
- 검증·권한·프로필·동의 확인 또는 감사 저장이 실패하면 트랜잭션 전체를 롤백합니다.

## 기존 데이터 보존

- `TransportRequest.callbackContact`는 요청 생성 때 구급대원 프로필 연락처를 복사한 값이므로 프로필 수정 서비스에서 조회·갱신하지 않습니다.
- `HospitalOffer`의 주소·상세주소·위도·경도·연락처는 제안 생성 때 복사한 값이므로 프로필 수정 서비스에서 조회·갱신하지 않습니다.
- 수정 후 생성되는 새 `TransportRequest`와 새 `HospitalOffer`는 기존 생성 서비스가 최신 프로필을 읽으므로 별도 전파 작업 없이 수정값을 사용합니다.
- 구급대원 표시 이름 수정은 계정 공개 ID, 이송 요청 소유자와 기존 감사 이벤트의 행위자를 바꾸지 않습니다.

## DB 변경

- 새 Flyway migration 없음.
- `hospital_profiles`에는 주소·상세주소·좌표·연락처·`updated_at`·`version`이 이미 존재합니다.
- `paramedic_profiles`에는 표시 이름·연락처·`updated_at`이 이미 존재하며 비관적 잠금으로 동시 수정을 직렬화합니다.
- `audit_events.action`은 문자열 컬럼이며 DB CHECK enum 제약이 없으므로 `AuditAction` 값 추가만으로 저장할 수 있습니다.
- MySQL 8.4와 Flyway V1~V13 전체 적용 및 JPA validate를 회귀 검사합니다.

## 테스트 계획

### 정상 흐름

- [x] 병원 주소·상세주소·좌표·연락처를 수정하면 `PUT` 응답과 이후 `GET`이 같은 최신값을 반환함
- [x] 병원 상세주소에 `null` 또는 공백을 보내면 `null`로 저장·반환됨
- [x] 병원 프로필 수정 전후 `receivingStatus`, 계정·조직·병원 공개 ID가 유지됨
- [x] 구급대원 이름·연락처를 수정하면 `PUT` 응답과 이후 `GET`이 같은 최신값과 기존 동의를 반환함
- [x] 두 역할 모두 앞뒤 공백은 제거되고 같은 값 재전송으로 중복 행이 생성되지 않음

### 주요 실패 흐름

- [x] 병원 기본주소 누락·255자 초과, 상세주소 200자 초과, 좌표 범위 이탈, 연락처 형식 오류를 `COMMON_001`로 차단함
- [x] 구급대원 이름 2자 미만·50자 초과·제어문자, 연락처 형식 오류를 `COMMON_001`로 차단함
- [x] 한 필드라도 실패하면 다른 프로필 필드와 감사 이벤트가 변경되지 않음
- [x] 존재하지 않는 병원·구급대원 프로필을 각각 `HOSPITAL_001`, `USER_001`로 처리함
- [x] 연락처 동의가 없는 구급대원은 `USER_005`로 차단하고 이름도 일부 저장하지 않음

### 권한·조직·소유권

- [x] 미인증 요청은 `AUTH_001`, 비활성 계정·유효하지 않은 인증은 `AUTH_002`로 차단함
- [x] 병원 API의 구급대원·관리자와 구급대원 API의 병원·관리자를 `AUTH_003`으로 차단함
- [x] JWT 조직과 계정·프로필 조직이 다르면 `COMMON_004`로 차단함
- [x] 요청에 계정·조직·프로필 ID를 받지 않고 JWT 본인 외 프로필을 수정할 수 없음

### 동시성·멱등성

- [x] 같은 병원 프로필에 서로 다른 두 위치 묶음을 동시에 저장해 최종값이 둘 중 하나의 완전한 묶음인지 확인함
- [x] 같은 구급대원 프로필에 서로 다른 두 이름·연락처 묶음을 동시에 저장해 혼합값이 없는지 확인함
- [x] 병원 프로필 수정과 수신 상태 변경이 겹쳐도 두 요청이 성공하고 정보·수신 상태가 각각 보존됨
- [x] 동일 요청 재전송이 프로필·동의·이송 데이터 중복을 만들지 않고 성공 감사만 요청 횟수만큼 기록함

### 스냅샷·회귀

- [x] 구급대원 연락처 수정 전 생성된 이송 요청은 기존 회신 연락처를, 수정 후 새 요청은 최신 연락처를 유지함
- [x] 병원 프로필 수정 전 생성된 제안은 기존 위치·연락처를, 수정 후 새 제안은 최신 프로필을 스냅샷함
- [x] 기존 병원 탐색 거리·ETA와 수락 후 주소 공개 계약이 회귀하지 않음
- [x] 감사 이벤트에는 역할별 action, 행위자·조직·대상 ID·시각·traceId만 있고 수정값 원문이 없음

### 전체 검증

- [x] 대상 통합·동시성 테스트
- [x] 실제 MySQL 8.4, Flyway V1~V13, JPA validate
- [x] `./gradlew clean check`
- [x] `./scripts/dev-start.sh`
- [x] `curl http://127.0.0.1:8080/actuator/health/readiness`
- [x] `git diff --check`

## 프론트 핸드오프

- 대상: `BOTH`
- Flutter: `docs/handoffs/20-self-profile-update/flutter-paramedic.md`
  - 설정 화면 초기 `GET`, 이름·회신 연락처 전체 `PUT`, 성공 응답 반영, 검증·동의 오류와 기존 요청 적용 시점 기록
- React: `docs/handoffs/20-self-profile-update/react-hospital-admin.md`
  - 설정 화면 초기 `GET`, 주소·상세주소·지도 좌표·응급실 연락처 전체 `PUT`, 수신 상태 분리, 성공 응답 반영과 기존 제안 적용 시점 기록
- 구현과 로컬 검증 후 실제 코드 기준으로 작성합니다.

## 유지할 계약

- `spec.md`의 제품 동작과 완료 조건
- 기존 `GET /api/v1/hospitals/me`, `GET /api/v1/paramedics/me` 응답 계약
- 기존 `PUT /api/v1/hospitals/me/receiving-status`와 병원 수신 상태 동작
- 가입 시 사용하는 병원 주소·좌표·연락처와 구급대원 이름·연락처 검증 정책
- 기존 이송 요청의 구급대원 회신 연락처 스냅샷
- 기존 병원 제안의 주소·상세주소·좌표·연락처 스냅샷과 공개 범위
- 구급대원 연락처 동의 이력과 버전
- 공통 오류 응답과 `X-Trace-Id`
- 역할, 조직과 요청 소유권
- 환자정보·연락처·정확한 GPS·Secret 비로그 원칙

## 리스크

| 리스크 | 대응 |
|---|---|
| 병원 웹이 주소와 맞지 않는 좌표를 제출 | 네이버 지도에서 위치 확인 후 전체 묶음으로 제출하도록 핸드오프하고 서버는 형식·범위를 검증 |
| 프로필 수정값이 진행 중 이송에도 즉시 반영된다고 프론트가 오해 | 기존 요청·제안은 스냅샷 유지, 새 요청·제안부터 반영됨을 양쪽 핸드오프와 회귀 테스트에 명시 |
| 병원 정보 수정과 수신 상태 변경이 겹쳐 한쪽 값이 사라짐 | 두 명령이 같은 프로필 비관적 잠금을 사용하고 각 도메인 메서드가 자기 필드만 변경하도록 검증 |
| 구급대원 연락처만 바뀌고 동의가 없는 상태로 사용됨 | 수정 전 기존 동의 해석을 재사용하고 동의 누락 시 전체 요청을 롤백 |
| 감사·오류 로그에 주소·좌표·연락처가 노출 | 요청·응답 본문을 로그에 남기지 않고 감사 이벤트에는 action과 공개 식별자만 저장하는 테스트 유지 |
