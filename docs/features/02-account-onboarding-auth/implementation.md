# 조직 가입 및 사용자 인증 구현 계획

```text
Feature: account-onboarding-auth
Author: Codex
Handoff Targets: BOTH
```

> 이 문서는 `spec.md`에 확정된 MVP 동작을 현재 코드에 구현하기 위한 기술
> 계획입니다. 새로운 제품 기능을 추가하지 않으며 API·DB·인증·동시성·테스트
> 선택만 구체화합니다.

## 설계 요약

- 선택한 방식:
  - 조직, 계정, 가입 코드, 병원 프로필을 하나의 기능 흐름으로 구현합니다.
  - Access Token은 짧은 수명의 JWT, Refresh Token은 서버에서 폐기할 수 있는
    고난도 임의 문자열로 발급합니다.
  - 가입 코드와 Refresh Token 원문은 응답에서만 전달하고 DB에는 SHA-256
    다이제스트만 저장합니다.
  - 비밀번호는 Spring Security의 `PasswordEncoder`를 통해 BCrypt로 단방향
    해시합니다.
  - 가입 코드 소비와 계정 생성을 하나의 트랜잭션으로 처리하고 가입 코드 행을
    잠가 동시 사용을 방지합니다.
  - 병원 가입은 계정과 병원 프로필을 같은 트랜잭션에서 생성하고, 병원 프로필의
    조직 고유 제약으로 병원별 공용 계정 하나를 보장합니다.
- 선택 이유:
  - 현재 `SecurityConfig`가 무상태 API로 구성되어 있어 Bearer JWT와 맞습니다.
  - Flutter와 React가 같은 인증 응답을 사용할 수 있고 Access Token 탈취 시
    노출 시간을 제한할 수 있습니다.
  - 가입 코드·Refresh Token을 서버에서 상태 관리하면 일회용 사용, 조기 폐기와
    재발급 후 이전 토큰 무효화를 검증할 수 있습니다.
  - DB 제약, 행 잠금과 트랜잭션을 함께 사용해야 서버가 여러 대여도 가입 코드와
    병원 공용 계정의 경합을 막을 수 있습니다.
- 검토한 대안과 제외 이유:
  - 서버 세션은 모바일·웹 공통 무상태 API 및 현재 보안 구성과 맞지 않아 제외합니다.
  - Refresh Token까지 JWT로만 발급하는 방식은 즉시 폐기와 재사용 탐지가 어려워
    제외합니다.
  - 가입 코드 원문 암호화 저장은 원문 재조회가 필요하지 않고 유출 범위만
    넓히므로 제외합니다.
  - H2만 사용하는 검증은 MySQL 잠금과 고유 제약 동작을 증명하지 못하므로 최종
    통합 테스트에는 Testcontainers MySQL 8.4를 사용합니다.

## 기술 계약

### 계정과 역할

- 가입 코드의 역할은 클라이언트 입력이 아니라 서버에 저장된 값으로 결정합니다.
- `HOSPITAL` 조직은 `HOSPITAL_STAFF`, `EMS_UNIT` 조직은 `PARAMEDIC` 가입
  코드만 발급할 수 있습니다.
- `SUPER_ADMIN` 가입 코드는 발급하지 않습니다. 슈퍼 관리자 한 계정은 런타임
  Secret을 읽는 멱등적인 bootstrap 구성으로 최초 생성합니다.
- 로그인 ID는 전체 시스템에서 고유하며 앞뒤 공백을 제거한 뒤
  `[a-z0-9]{4,30}` 형식만 허용합니다. 대문자는 자동 변환하지 않고 검증 오류로
  반환합니다.
- 비밀번호는 8~64자로 제한하고 원문, 해시와 인증 토큰을 로그에 남기지 않습니다.
- 서버는 인증 객체의 계정 ID·역할·조직 ID만 권한 판단에 사용합니다. 요청 본문의
  사용자 ID나 조직 ID로 소유권을 결정하지 않습니다.

### 인증 토큰

- 로그인과 토큰 갱신 성공 응답은 `accessToken`, `refreshToken`, 각 만료 시각,
  계정 공개 ID, 역할과 조직 공개 ID를 반환합니다.
- Access Token은 기본 15분 만료의 서명된 JWT로 발급합니다. `sub`, `role`,
  `organizationId`, `jti`, `iat`, `exp`만 포함하고 개인정보는 넣지 않습니다.
- 보호 API는 `Authorization: Bearer {accessToken}`을 사용하고 JWT 서명, 만료,
  계정 활성 상태를 서버에서 확인합니다.
- Refresh Token은 기본 7일 만료로 발급하고 DB에는 다이제스트만 저장합니다.
  갱신할 때마다 기존 토큰을 폐기하고 새 토큰으로 교체합니다. 이미 사용했거나
  폐기된 토큰은 재사용할 수 없습니다.
- 토큰 수명과 JWT 서명 키는 환경 설정으로 주입합니다. 개발·배포 환경의 Secret은
  소스와 로그에 기록하지 않습니다.

### 가입 코드

- `SecureRandom`으로 256비트 값을 생성하고 URL-safe Base64 원문을 발급
  응답에서 한 번만 반환합니다.
- 발급 요청은 3일, 7일 또는 미래의 직접 지정 만료 시각 중 하나를 받습니다.
- 목록 응답은 코드 공개 ID, 조직, 역할, 상태, 발급·만료·사용·폐기 시각만
  반환하며 원문과 다이제스트는 반환하지 않습니다.
- 가입 트랜잭션에서 다이제스트로 코드를 조회하고 해당 행을 잠근 뒤 상태, 만료
  시각, 조직 유형과 역할을 검증합니다. 계정·병원 프로필 생성과 `USED` 전환 중
  하나라도 실패하면 모두 롤백합니다.
- 조회 시각에 만료된 `AVAILABLE` 코드는 즉시 `EXPIRED`로 전환합니다. 별도
  스케줄 작업도 같은 전환 서비스를 호출해 미조회 코드를 정리하며, 상태 전환은
  멱등적으로 처리합니다.
- `USED`, `EXPIRED`, `REVOKED` 코드는 폐기할 수 없습니다.

### API 초안

| HTTP | Path | 권한 | 핵심 동작 |
|---|---|---|---|
| `POST` | `/api/v1/admin/organizations` | `SUPER_ADMIN` | 병원·구급대 조직 등록 |
| `GET` | `/api/v1/admin/organizations` | `SUPER_ADMIN` | 조직 목록 조회 |
| `POST` | `/api/v1/admin/invitation-codes` | `SUPER_ADMIN` | 조직·역할·만료를 지정해 코드 발급 |
| `GET` | `/api/v1/admin/invitation-codes` | `SUPER_ADMIN` | 상태·조직 조건으로 코드 메타데이터 조회 |
| `POST` | `/api/v1/admin/invitation-codes/{invitationCodeId}/revoke` | `SUPER_ADMIN` | 사용 전 코드 폐기 |
| `POST` | `/api/v1/auth/signups/hospital` | 공개 | 병원 공용 계정과 프로필 생성 |
| `POST` | `/api/v1/auth/signups/paramedic` | 공개 | 구급대원 개인 계정 생성 |
| `POST` | `/api/v1/auth/login` | 공개 | 자격정보 검증 후 토큰 발급 |
| `POST` | `/api/v1/auth/tokens/refresh` | 공개 | Refresh Token 회전 후 토큰 재발급 |
| `PUT` | `/api/v1/hospitals/me/receiving-status` | `HOSPITAL_STAFF` | 자기 병원의 `ON`·`OFF` 변경 |

- 조직과 가입 코드 목록은 생성 시각 역순의 페이지 응답으로 구현합니다.
- 병원 가입 요청의 병원명은 사용자가 선택한 조직 확인용으로만 받고, 저장할
  조직은 가입 코드에서 결정합니다. 요청값과 코드의 조직명이 다르면 검증 오류를
  반환합니다.
- 병원 가입 요청은 주소, 위도, 경도, 응급실 연락처를 필수로 받습니다. 위도는
  -90~90, 경도는 -180~180 범위를 검증하고 최초 수신 상태는 항상 `OFF`로
  저장합니다.
- 구급대원 가입 요청은 가입 코드, 로그인 ID와 비밀번호만 받습니다.
- 실제 DTO 필드명, 성공 상태와 JSON 예시는 구현 및 테스트 후 프론트 핸드오프에
  확정합니다.

### 오류 코드

현재 `ErrorCode`의 다음 번호를 사용합니다.

| 오류 코드 | HTTP | 사용 조건 |
|---|---:|---|
| `AUTH_004` | 401 | 로그인 ID 또는 비밀번호 불일치 |
| `AUTH_005` | 401 | Refresh Token이 유효하지 않거나 만료·폐기·재사용됨 |
| `USER_003` | 409 | 로그인 ID 중복 |
| `USER_004` | 409 | 병원 조직에 공용 계정이 이미 존재함 |
| `ORGANIZATION_001` | 404 | 조직을 찾을 수 없음 |
| `INVITATION_001` | 400 | 가입 코드를 찾거나 검증할 수 없음 |
| `INVITATION_002` | 409 | 가입 코드 만료 |
| `INVITATION_003` | 409 | 가입 코드 사용 완료 |
| `INVITATION_004` | 409 | 가입 코드 폐기 완료 |
| `INVITATION_005` | 409 | 현재 상태에서 가입 코드 상태를 변경할 수 없음 |

- 형식 검증과 조직 유형·역할 조합 오류는 `COMMON_001`, 권한 부족은
  `AUTH_003` 또는 `COMMON_004`, 비활성 계정은 `USER_002`를 재사용합니다.
- 로그인 실패 응답은 계정 존재 여부를 구분하지 않습니다.

## DB 변경

새 Flyway migration 하나에 다음 테이블과 인덱스를 추가합니다. 실제 파일명은
저장소의 다음 사용 가능한 버전을 확인한 뒤 결정합니다.

| 테이블 | 주요 컬럼·제약 |
|---|---|
| `organizations` | 내부 PK, 외부 공개 UUID 고유값, 이름, `HOSPITAL`·`EMS_UNIT` 유형, 생성·수정 시각 |
| `user_accounts` | 내부 PK, 공개 UUID, 조직 FK(슈퍼 관리자는 `NULL`), 전역 고유 로그인 ID, 비밀번호 해시, 역할, `ACTIVE`·`INACTIVE` 상태, 최근 로그인 시각, 생성·수정 시각 |
| `invitation_codes` | 공개 UUID, 조직 FK, 역할, 고유 다이제스트, 상태, 만료·사용·폐기 시각, 사용 계정 FK, 낙관적 버전 |
| `hospital_profiles` | 공개 UUID, 조직 FK 고유값, 계정 FK 고유값, 주소, `DECIMAL` 위도·경도, 연락처, 수신 상태, 낙관적 버전 |
| `refresh_tokens` | 계정 FK, 고유 다이제스트, 만료·사용·폐기 시각, 교체 토큰 식별자, 생성 시각 |
| `audit_events` | 행위 종류, 행위자 계정·조직, 대상 종류·공개 ID, 발생 시각, 추적 ID |

- 외부 API에는 내부 순차 PK를 노출하지 않고 UUID만 사용합니다.
- `hospital_profiles.organization_id` 고유 제약과 가입 트랜잭션 롤백으로 병원별
  공용 계정 하나를 보장합니다.
- 가입 코드 상태·만료 시각, 조직 유형, 로그인 ID, Refresh Token 만료 조회에
  필요한 인덱스를 추가합니다.
- 감사 이벤트에는 가입 코드 원문·다이제스트, 비밀번호, 토큰과 병원 연락처를
  저장하지 않습니다.

## 패키지와 변경 범위

기능 중심 패키지를 사용하되 공통 인증 처리만 `global.security`에 둡니다.

| 패키지·파일 | 변경 내용 |
|---|---|
| `build.gradle` | JWT 검증·발급에 필요한 Spring Security 모듈 추가 |
| `src/main/resources/db/migration/` | 조직·계정·가입 코드·병원 프로필·Refresh Token·감사 테이블 migration 추가 |
| `organization/` | 조직 Entity, Repository, 관리자 등록·목록 API |
| `invitation/` | 가입 코드 생성·조회·폐기·소비·만료와 감사 처리 |
| `account/` | 계정 Entity, 병원·구급대원 가입 유스케이스와 DTO |
| `hospital/` | 병원 프로필과 수신 상태 변경 유스케이스 |
| `auth/` | 로그인, 토큰 발급·회전, 계정 활성 상태 확인 |
| `audit/` | 가입 코드와 수신 상태 감사 이벤트 저장 |
| `global/security/` | JWT 인증 변환, 공개 경로, 역할 기반 접근 제어, PasswordEncoder 구성 |
| `global/exception/ErrorCode.java` | 확정한 인증·사용자·조직·가입 코드 오류 추가 |
| `application*.yaml` | 토큰 수명, 서명 키와 bootstrap 환경 변수 연결 |
| `src/test/` | 단위·Web MVC·MySQL 통합·권한·동시성 테스트 추가 |

## 구현 Step

| Step | 작업 | 완료 기준 |
|---:|---|---|
| 1 | migration과 도메인 모델 작성 | 여섯 테이블, FK·고유 제약·인덱스와 JPA `validate`가 MySQL 8.4에서 일치함 |
| 2 | 슈퍼 관리자 bootstrap과 조직 관리 구현 | 관리자만 조직을 등록·조회하고 환자·위치 API 권한은 부여받지 않음 |
| 3 | 가입 코드 관리 구현 | 원문 1회 반환, 다이제스트 저장, 목록·폐기·만료와 감사 기록이 동작함 |
| 4 | 병원·구급대원 가입 구현 | 코드의 조직·역할을 사용해 각 계정 정책대로 생성되고 병원은 프로필과 `OFF` 상태가 함께 저장됨 |
| 5 | 로그인과 JWT·Refresh Token 구현 | 활성 계정만 로그인하며 JWT 검증과 Refresh Token 1회 회전·재사용 거절이 동작함 |
| 6 | 역할·조직 권한과 병원 수신 상태 구현 | 서버 인증정보로 자기 조직만 접근하고 병원만 자기 수신 상태를 변경함 |
| 7 | 실패·권한·동시성·MySQL 통합 테스트 작성 | 동일 코드 동시 가입 하나만 성공, 병원 중복 계정과 토큰 재사용이 차단됨 |
| 8 | 전체 검증, `review.md`와 핸드오프 작성 | `clean check`, readiness 결과와 실제 Flutter·React API 계약이 문서에 기록됨 |

## 테스트 계획

- [x] 슈퍼 관리자만 조직과 가입 코드를 관리함
- [x] `HOSPITAL`↔`HOSPITAL_STAFF`, `EMS_UNIT`↔`PARAMEDIC` 조합만 허용함
- [x] 가입 코드 원문은 발급 응답에만 있고 DB·목록·로그·감사 기록에는 없음
- [x] 3일·7일·직접 지정 만료와 `AVAILABLE`·`USED`·`EXPIRED`·`REVOKED` 전이를 검증함
- [x] 동일 가입 코드를 동시에 소비하면 하나의 가입 트랜잭션만 성공함
- [x] 병원 가입 시 계정·프로필·수신 `OFF`가 원자적으로 생성되고 조직당 하나만 존재함
- [x] 구급대원 계정은 코드의 구급대 조직과 `PARAMEDIC` 역할에 연결됨
- [x] 로그인 ID 중복, 잘못된 자격정보, 비활성 계정과 필드 검증 오류를 확인함
- [x] JWT의 서명·만료·역할·조직을 검증하고 위조·만료 토큰을 `AUTH_002`로 거절함
- [x] Refresh Token 정상 회전, 만료·폐기·재사용 거절을 검증함
- [x] 다른 조직과 다른 역할의 API 접근이 차단되고 슈퍼 관리자가 임상·위치 권한을 갖지 않음
- [x] 병원 공용 계정만 자기 응급실 수신 상태를 변경하며 변경 감사 기록이 남음
- [x] 모든 오류가 공통 응답과 `X-Trace-Id` 계약을 유지함
- [x] Testcontainers MySQL 8.4에서 migration, 고유 제약과 잠금 동작을 확인함
- [x] `./gradlew clean check`
- [x] 로컬 실행 후 `/actuator/health/readiness`

## 프론트 핸드오프

- 대상: `BOTH`
- Flutter: `docs/handoffs/02-account-onboarding-auth/flutter-paramedic.md`
- React: `docs/handoffs/02-account-onboarding-auth/react-hospital-admin.md`
- 구현과 로컬 검증 후 실제 요청·응답, 권한, 오류와 토큰 갱신 계약을 대상별로 작성합니다.

## 유지할 계약

- `spec.md`의 제품 동작과 완료 조건
- 공통 오류 응답과 `X-Trace-Id`
- 역할, 조직과 요청 소유권의 서버 검증
- 환자정보, 비밀번호, 가입 코드, 토큰, Secret과 정확한 GPS 비로그 정책
- Flyway 새 migration 및 MySQL 8.4 호환성
- 기존 공개 API 호환성 또는 명시된 전환 계획

## 리스크

| 리스크 | 대응 |
|---|---|
| 가입 코드가 동시에 두 번 사용됨 | 가입 코드 행 잠금, 상태 재검증, 계정 생성과 코드 소비를 한 트랜잭션으로 묶고 MySQL 동시성 테스트 추가 |
| 병원 공용 계정이 둘 이상 생성됨 | 병원 프로필 조직 고유 제약과 가입 트랜잭션 롤백을 함께 검증 |
| Access·Refresh Token 또는 Secret이 노출됨 | 짧은 Access Token 수명, Refresh Token 다이제스트·회전, 로그 금지와 외부 Secret 주입 적용 |
| JWT 정보와 현재 계정 상태가 달라짐 | 매 요청에서 토큰 서명·만료를 확인하고 계정 활성 상태가 필요한 권한 경계에서 현재 계정을 조회 |
| 가입 코드 오류가 코드 존재 여부를 과도하게 노출함 | 잘못된 원문은 하나의 안전한 오류로 응답하고 상태는 정상적으로 식별된 코드에만 반환 |
| 긴 기능 범위로 회귀 지점이 늘어남 | Step별 테스트를 먼저 통과시키고 최종 `clean check`와 전체 사용자 흐름 통합 테스트 수행 |
