# 조직 가입 및 사용자 인증 구현 검수

```text
Feature: account-onboarding-auth
Implemented By: Codex
Related PR: NONE
Frontend Impact: BOTH
Flutter Handoff: docs/handoffs/02-account-onboarding-auth/flutter-paramedic.md
React Handoff: docs/handoffs/02-account-onboarding-auth/react-hospital-admin.md
```

## 구현 요약

- 슈퍼 관리자 bootstrap과 병원·구급대 조직 등록·목록 API를 구현했습니다.
- 조직·역할·만료에 묶인 가입 코드 발급·목록·폐기·자동 만료를 구현했습니다.
- 가입 코드 원문은 발급 응답에서 한 번만 반환하고 DB에는 SHA-256
  다이제스트만 저장합니다.
- 병원 공용 계정·응급실 프로필·수신 `OFF`와 구급대원 개인 계정 가입을
  일회용 코드 소비와 같은 트랜잭션으로 구현했습니다.
- BCrypt 비밀번호, HS256 JWT Access Token, 다이제스트 저장 및 일회성 회전
  Refresh Token 로그인을 구현했습니다.
- 병원 역할만 자기 응급실의 신규 요청 수신 상태를 변경하도록 구현했습니다.
- 가입 코드와 병원 수신 상태 변경의 필수 감사 이벤트를 저장합니다.

## 요구사항 체크

| 요구사항 | 결과 | 근거 |
|---|---|---|
| 관리자가 병원·구급대 조직을 등록·조회함 | PASS | `AdminOrganizationController`, 관리자 권한 통합 테스트 |
| 조직·역할·만료별 가입 코드를 발급·조회·폐기함 | PASS | `InvitationService`, `AdminInvitationIntegrationTest` |
| 가입 코드 원문 1회 노출 및 다이제스트 저장 | PASS | 발급·목록 응답 및 DB 다이제스트 비교 테스트 |
| 가입 코드 상태와 감사 기록 | PASS | `AVAILABLE`·`USED`·`EXPIRED`·`REVOKED` 전이 및 감사 테스트 |
| 동일 코드 동시 가입 하나만 성공 | PASS | 두 스레드 동시 가입 통합 테스트 |
| 병원 공용 계정 하나와 최초 수신 `OFF` | PASS | 병원 가입 및 서로 다른 코드 동시 가입 테스트 |
| 구급대원 개인 계정과 코드의 조직·역할 연결 | PASS | 구급대원 가입 통합 테스트 |
| 활성 계정 로그인과 역할·조직 JWT | PASS | 로그인·보호 API·비활성 계정 JWT 테스트 |
| Refresh Token 일회성 회전 | PASS | 정상 갱신 후 이전 토큰 재사용 거절 테스트 |
| 병원이 자기 응급실 수신 상태만 변경 | PASS | 병원 JWT 성공 및 구급대원 권한 거절 테스트 |
| 공통 오류와 `X-Trace-Id` 유지 | PASS | 기존 공통 API 기반 테스트와 신규 실패 경로 테스트 |

## 변경 API·DB

| 구분 | 변경 내용 | 호환성 |
|---|---|---|
| API | 조직 2개, 가입 코드 3개, 가입 2개, 로그인·갱신 2개, 병원 수신 상태 1개 추가 | 기존 API 변경 없음 |
| DB | `V1__create_account_onboarding_schema.sql`로 6개 기능 테이블과 제약·인덱스 추가 | 신규 migration |
| 보안 | Spring Security Resource Server와 HS256 JWT 검증 추가 | 기존 공개 상태 API 유지 |
| 배포 설정 | AWS Secret에서 JWT와 최초 관리자 자격정보를 런타임 YAML로 연결 | Secret 키 추가 필요 |

## Spec 이후 변경

- 제품 정책 변경: 없음
- 구현 중 추가된 설계 결정:
  - 로그인 ID는 앞뒤 공백 제거 후 소문자 영문·숫자 4~30자로 제한
  - 비밀번호는 8~64자, BCrypt 해시 사용
  - Access Token 기본 15분, Refresh Token 기본 7일
  - 가입 코드와 Refresh Token은 256비트 임의 문자열 및 SHA-256 다이제스트 사용
  - 외부 식별자는 UUID 문자열, 시간은 UTC `Instant` 사용
  - 병원별 공용 계정 하나는 조직 행 잠금과 병원 프로필 고유 제약으로 이중 보장

## 범위 확인

- spec 밖 추가 작업: 인증과 최초 관리자 사용에 필요한 런타임 Secret 연결
- 의도적으로 제외한 작업:
  - 로그아웃·비밀번호 변경·계정 비활성화 관리 API
  - 구급대 회신 연락처 저장과 이송 요청 기능
  - 프론트 상태관리·화면 구조

## 테스트 결과

| 테스트 | 결과 | 근거 |
|---|---|---|
| `./gradlew clean check` | PASS | 2026-08-01 로컬 실행, 31개 모두 통과 |
| H2 migration·JPA validate | PASS | 컨텍스트별 격리된 MySQL 호환 모드 H2 |
| 가입·인증·권한·감사 시나리오 | PASS | Web MVC 및 서비스 통합 테스트 |
| 동일 코드·병원 공용 계정 동시성 | PASS | 두 스레드 경합 테스트 |
| Testcontainers MySQL 8.4 | PASS | Flyway migration, JPA validate, 6개 테이블과 readiness `UP` 확인 |
| local 실행·readiness | PASS | 검증 전용 MySQL DB와 18080 포트에서 실제 HTTP `{"status":"UP"}` 확인 |
| 로컬 슈퍼 관리자 로그인 | PASS | HTTP 200, `SUPER_ADMIN`, Access·Refresh Token 존재 확인 후 토큰 파일 삭제 |

## 프론트 핸드오프

| 대상 | 문서 | 실제 코드와 일치 |
|---|---|---|
| Flutter 구급대원 앱 | `docs/handoffs/02-account-onboarding-auth/flutter-paramedic.md` | YES |
| React 병원·관리자 웹 | `docs/handoffs/02-account-onboarding-auth/react-hospital-admin.md` | YES |

## 배포 전 준비

AWS Secrets Manager의 `ersync/dev/backend` JSON에 다음 문자열 키가 추가되어야
새 컨테이너가 기동됩니다.

- `jwtSecretBase64`: Base64로 인코딩한 32바이트 이상의 임의 JWT 키
- `superAdminLoginId`: 소문자 영문·숫자 4~30자의 최초 관리자 ID
- `superAdminPassword`: 8~64자의 최초 관리자 비밀번호

2026-08-01 운영 담당자로부터 세 키의 등록 완료를 전달받았습니다. 실제 값은
저장소와 문서에 기록하지 않았으며, AWS에서의 설정 적용 여부는 `main` 배포 후
readiness와 로그인 시나리오로 확인합니다.

## 남은 리스크

| 리스크 | 영향 | 대응 |
|---|---|---|
| 기존 `ersync` 로컬 DB에 현재 main과 다른 V1~V3가 적용됨 | 기본 `scripts/dev-start.sh`는 Flyway 체크섬 불일치로 기동 실패 | 기존 DB는 보존함; 필요 시 데이터 소유자 확인 후 별도 백업·초기화 |
| AWS Secret 신규 키가 실제 배포에서 아직 검증되지 않음 | 형식이나 값이 잘못되면 새 버전 readiness 실패 후 기존 이미지로 복구됨 | 운영 담당자의 등록 완료를 전달받음; `main` 배포 후 readiness와 슈퍼 관리자 로그인을 확인 |
