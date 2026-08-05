# 역할별 로그인 아이디 및 로그인 역할 지정 구현 계획

```text
Feature: role-scoped-login-id
Author: backend
Handoff Targets: BOTH
```

> 정책이 확정된 `spec.md`와 현재 계정·가입·인증 코드, V1~V8 migration을
> 기준으로 작성했습니다. 로그인은 호환되지 않는 외부 계약 변경이므로 DB와
> 백엔드만 바꾸지 않고 Flutter·React 전환 문서와 회귀 테스트까지 한 기능으로
> 완료합니다.

## 설계 요약

- 선택한 방식:
  - 기존 `user_accounts` 단일 테이블을 유지하고 새 V9 migration에서 단일
    `login_id` 고유 제약을 `(login_id, role)` 복합 고유 제약으로 교체합니다.
  - JPA 매핑과 `UserAccountRepository`도 같은 복합 고유성 및
    `loginId + role` 조회 계약으로 맞춥니다.
  - 회원가입은 가입 코드에서 서버가 확정한 역할을 중복 검사에 사용하고,
    로그인은 요청의 필수 `role`로 후보 계정을 찾은 뒤 실제 DB 계정의
    비밀번호·상태·역할·조직으로 토큰을 발급합니다.
  - Refresh Token과 JWT 보호 API는 계정 UUID로 식별하므로 변경하지 않습니다.
- 선택 이유:
  - 계정 상태, Refresh Token, 감사 행위자와 JWT 검증 구조를 중복하지 않으면서
    앱·웹의 같은 아이디를 명확하게 식별할 수 있습니다.
  - DB 복합 고유 제약이 애플리케이션 사전 검사와 동시 가입 경합의 최종
    무결성 방어선이 됩니다.
  - 역할을 로그인 요청에서 명시하면 같은 아이디·비밀번호를 여러 역할이
    우연히 공유해도 서버가 임의 계정을 추측하지 않습니다.
- 검토한 대안과 제외 이유:
  - 역할별 테이블 또는 DB 분리: 토큰·상태·권한·감사 구조가 중복되고 MVP
    변경 범위가 불필요하게 커집니다.
  - 앱·웹별 로그인 API 경로 분리: 내부 서비스는 분리할 수 있지만 공통 인증
    응답과 오류 계약을 중복하며 현재 단일 로그인 API 이점이 줄어듭니다.
  - 비밀번호가 맞는 계정을 역할 없이 탐색: 여러 역할에 같은 비밀번호가 있으면
    모호하고 계정 존재 여부를 더 많이 조회하게 되어 보안상 제외합니다.
  - 기존 요청을 임시 허용해 유일한 아이디만 로그인: 배포 후 생성되는 동일
    아이디 때문에 동작이 계정 상태에 따라 달라져 계약이 불명확해지므로 Dev
    단계에서는 필수 `role`로 한 번에 전환합니다.

## 구현 Step

| Step | 작업 | 완료 기준 |
|---:|---|---|
| 1 | V9 복합 고유 제약, JPA 매핑과 Repository 역할 복합 조회 추가 | 기존 데이터 보존, 역할 간 동일 아이디 허용, 같은 역할 중복 차단을 H2·MySQL에서 확인 |
| 2 | 병원·구급대원 가입과 슈퍼 관리자 bootstrap 중복 범위 변경 | 가입 코드 역할을 사용해 중복 검사하고 다른 역할 동일 아이디는 생성 가능 |
| 3 | 로그인 요청 필수 `role`과 역할 복합 인증 구현 | 올바른 역할만 로그인되고 누락·오역할·잘못된 비밀번호가 표준 오류를 반환 |
| 4 | 인증·가입·bootstrap 집중 통합 및 동시성 테스트 추가 | 같은 아이디의 역할별 토큰 격리, 같은 역할 동시 가입 단일 생성 검증 |
| 5 | 기존 테스트와 내부 조회를 역할 복합 계약으로 전환 | 단일 아이디 조회가 남지 않고 기존 MVP 로그인·보호 API 회귀 통과 |
| 6 | MySQL 8.4 migration·JPA 실제 제약 검증 | V8 기존 계정이 V9에서 유지되고 복합 unique가 실제 MySQL에서 동작 |
| 7 | Flutter·React 핸드오프와 review 작성 | 로그인은 11번이 최신, 나머지 인증 계약은 기존 문서 유지라고 대상별 명시 |
| 8 | 전체 정적·자동·로컬 실행 검증 | `./gradlew clean check`, MySQL migration, 로컬 readiness가 모두 성공 |

## 변경 범위

| 패키지·파일 | 변경 내용 |
|---|---|
| `db/migration/V9__scope_login_id_uniqueness_by_role.sql` | 단일 로그인 아이디 unique를 역할 복합 unique로 교체 |
| `account/domain/UserAccount` | JPA 단일 unique 제거 및 테이블 복합 unique 선언 |
| `account/infrastructure/UserAccountRepository` | `find/existsByLoginIdAndRole`로 모호하지 않은 조회 제공 |
| `account/application/AccountSignupService` | 가입 코드의 실제 역할을 로그인 아이디 중복 검사에 전달 |
| `account/application/SuperAdminBootstrap` | 다른 역할의 같은 아이디가 있어도 최초 관리자 생성 허용 |
| `auth/api/LoginRequest` | 필수 `UserRole role` 요청 필드 추가 |
| `auth/application/AuthService` | 정규화 아이디와 요청 역할로 계정 조회, 실제 계정으로 토큰 발급 |
| `account·auth` 통합/동시성 테스트 | 역할별 동일 아이디 생성·로그인·오역할·중복 경합 검증 |
| MySQL migration 테스트 | V8→V9 데이터 보존과 실제 복합 unique 검증 |
| 기존 로그인 사용 테스트 | 모든 로그인 요청에 해당 서비스 역할 추가 |
| `docs/handoffs/11-role-scoped-login-id/` | Flutter·React 최신 로그인 계약과 전환 방법 작성 |
| 기능 `review.md` | 실제 변경·검증·남은 리스크 기록 |

## DB 변경

- 새 migration: `V9__scope_login_id_uniqueness_by_role.sql`
- `user_accounts.uk_user_accounts_login_id`를 제거합니다.
- `(login_id, role)`에 `uk_user_accounts_login_id_role` 복합 고유 제약을 추가합니다.
- 기존 행은 전체 로그인 아이디가 이미 고유하므로 backfill과 데이터 수정이
  필요하지 않습니다.
- `login_id`와 `role`의 타입·값은 변경하지 않습니다.
- 기존 계정 PK·공개 UUID를 참조하는 Refresh Token, 프로필, 감사·이송 데이터의
  외래키는 변경하지 않습니다.
- MySQL 8.4에서 V8까지 적용된 기존 계정을 넣은 뒤 V9로 올려 행 보존과
  제약 동작을 직접 확인합니다.

## 트랜잭션·동시성

- 회원가입의 사전 `existsByLoginIdAndRole` 검사는 빠른 공개 오류를 위한 것이고,
  실제 경합의 최종 판정은 DB 복합 고유 제약과 `saveAndFlush`가 담당합니다.
- 같은 역할·아이디 동시 가입 중 하나만 커밋되며 다른 요청은 기존
  `USER_003`으로 변환합니다.
- 다른 역할의 같은 아이디 가입은 서로 다른 unique key이므로 각 가입 코드와
  병원 공용 계정 제약이 충족되면 모두 성공할 수 있습니다.
- 로그인은 읽은 계정 한 건에만 `lastLoginAt`을 기록합니다. 역할별 같은 아이디
  계정의 로그인 시각과 Refresh Token이 서로 변경되지 않는지 검증합니다.
- Refresh Token은 `account_id`로 연결돼 있어 역할 복합 조회를 다시 수행하지
  않습니다.

## 오류·보안

- 누락되거나 JSON enum으로 변환할 수 없는 `role`은 기존 공통 요청 검증
  `COMMON_001` 400을 사용합니다.
- 존재하지 않는 `loginId + role`, 다른 역할 선택과 잘못된 비밀번호는 모두
  `AUTH_004` 401을 사용합니다.
- 일치한 계정의 비활성 상태만 기존 `USER_002` 403을 사용합니다.
- JWT와 응답은 요청 DTO의 역할을 복사하지 않고 조회한 `UserAccount`에서
  생성합니다.
- 로그인 ID, 비밀번호, 토큰 원문과 Secret은 로그에 추가하지 않습니다.
- 역할 간 같은 아이디의 계정 UUID·조직·토큰·보호 API 권한이 섞이지 않는지
  검증합니다.

## 테스트 계획

- [x] `PARAMEDIC`, `HOSPITAL_STAFF`, `SUPER_ADMIN`이 같은 아이디로 각각 생성됨
- [x] 각 역할·비밀번호 로그인 응답의 계정 UUID·조직·역할이 정확함
- [x] 올바른 비밀번호라도 다른 역할로 로그인하면 `AUTH_004`
- [x] 존재하지 않는 아이디·역할과 잘못된 비밀번호가 같은 `AUTH_004`
- [x] `role` 누락·null·알 수 없는 enum은 `COMMON_001`
- [x] 비활성 계정은 정확한 역할로 조회된 뒤 `USER_002`
- [x] 같은 역할·아이디의 순차 및 동시 가입은 하나만 생성되고 `USER_003`
- [x] 다른 역할·같은 아이디의 가입은 둘 다 성공
- [x] 슈퍼 관리자와 다른 역할이 같은 아이디여도 bootstrap 성공
- [x] 기존 Access Token 검증과 Refresh Token 회전·재사용 차단 유지
- [x] 기존 가입·프로필·병원 수신·MVP 여정 회귀 테스트
- [x] 가입부터 완료·취소까지 5개 분기형 충돌 여정과 기존 동시성 회귀 테스트
- [x] V8→V9 MySQL 8.4 데이터 보존·복합 unique 검증
- [x] `./gradlew clean check`
- [x] `./scripts/dev-start.sh`와 로컬 readiness

## 프론트 핸드오프

- 대상: `BOTH`
- Flutter: `docs/handoffs/11-role-scoped-login-id/flutter-paramedic.md`
- React: `docs/handoffs/11-role-scoped-login-id/react-hospital-admin.md`
- 로그인 요청 예시는 11번 문서가 최신 기준입니다.
- 회원가입·가입 코드·토큰 갱신·프로필의 변경되지 않은 계약은 기존 02·07·09
  문서 경로를 대상별 핸드오프에 직접 연결합니다.
- 앱·웹 내부 구조를 지시하지 않고 전송할 고정 역할, 요청·응답, 오류와 전환
  조건만 기록합니다.

## 유지할 계약

- `spec.md`의 제품 동작과 완료 조건
- 공통 오류 응답과 `X-Trace-Id`
- 서버가 검증한 실제 역할·조직 기반 권한
- 병원 조직당 공용 병원 계정·프로필 하나
- 기존 회원가입 요청·응답과 가입 코드 역할
- 기존 JWT claim, Access Token 검증과 Refresh Token 회전
- 로그인 ID·비밀번호 형식과 공개 인증 오류

## 리스크

| 리스크 | 대응 |
|---|---|
| 기존 앱·웹이 `role` 없이 로그인하면 배포 직후 400 | 핸드오프에서 breaking change와 역할별 고정값을 최상단에 표시하고 프론트 전환 확인 |
| JPA와 DB 중 하나에 단일 unique가 남으면 다른 역할 가입 실패 | Entity 매핑·Flyway·H2·MySQL schema 및 실제 insert 테스트를 함께 수행 |
| 단일 `findByLoginId`가 남으면 다중 결과 또는 잘못된 계정 로그인 | Repository의 모호한 조회 제거 후 전체 코드 검색과 컴파일로 잔존 사용 차단 |
| 역할값을 권한으로 신뢰하면 권한 상승 가능 | 요청 역할은 조회 조건으로만 사용하고 JWT·응답·인가를 DB 계정 기준으로 유지 |
| 동시 가입의 사전 검사만 믿으면 중복 계정 가능 | DB 복합 unique와 `saveAndFlush` 예외 변환 및 실제 동시성 테스트 적용 |
