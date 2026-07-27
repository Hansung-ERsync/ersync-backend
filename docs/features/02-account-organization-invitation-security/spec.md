# 계정·조직·가입 코드·인증 요구사항

```text
Feature: account-organization-invitation-security
Domain: auth, organization, invitation
Owner: backend
Related Issue: NONE
Frontend Impact: YES
```

> 관리자 조직 등록부터 가입 코드 발급, 코드 기반 회원가입, 로그인까지의
> 계정 기반 흐름을 한 PR에서 완성합니다.

## 목적

- 슈퍼 관리자가 병원 또는 구급대 조직을 등록합니다.
- 슈퍼 관리자가 조직과 역할에 묶인 일회용 가입 코드를 발급·조회·폐기합니다.
- 사용자는 가입 코드로 계정을 만들고 로그인해 보호 API를 호출할 수 있습니다.

## 시나리오

| # | 상황 | 기대 결과 |
|---:|---|---|
| 1 | 슈퍼 관리자가 조직을 등록함 | 조직 ID, 유형, 이름과 활성 상태가 저장됨 |
| 2 | 슈퍼 관리자가 가입 코드를 발급함 | 코드 원문은 응답에 한 번만 포함되고 DB에는 해시만 저장됨 |
| 3 | 사용자가 유효한 코드로 가입함 | 계정이 생성되고 가입 코드는 `USED`가 됨 |
| 4 | 사용자가 로그인함 | Access Token과 Refresh Token을 발급받음 |
| 5 | 만료·사용·폐기된 코드로 가입함 | 계정이 생성되지 않고 표준 오류가 반환됨 |

## API

| 행위 | Method·Path | 요청·응답 핵심 |
|---|---|---|
| 로그인 | `POST /api/v1/auth/login` | loginId/password, accessToken/refreshToken |
| 가입 코드 확인 | `POST /api/v1/auth/invitation-code/verify` | invitationCode, organization/targetRole |
| 회원가입 | `POST /api/v1/auth/signup` | invitationCode/loginId/password, accountId/role |
| 내 계정 조회 | `GET /api/v1/auth/me` | 인증 계정의 ID, 조직, 역할 |
| 조직 등록 | `POST /api/v1/admin/organizations` | type/name, organizationId |
| 조직 목록 | `GET /api/v1/admin/organizations` | 조직 목록 |
| 가입 코드 발급 | `POST /api/v1/admin/organizations/{organizationId}/invitation-codes` | targetRole/expiresInDays 또는 expiresAt, plaintextCode |
| 가입 코드 목록 | `GET /api/v1/admin/invitation-codes` | 원문 없이 상태 목록 |
| 가입 코드 폐기 | `POST /api/v1/admin/invitation-codes/{invitationCodeId}/revoke` | 폐기된 코드 상태 |

프론트엔드 영향이 `YES`이므로 구현과 검증 후
`docs/contracts/02-account-organization-invitation-security.md`를 작성합니다.

## 권한

| 역할 | 허용 작업 | 접근 범위 |
|---|---|---|
| 공개 | 로그인, 회원가입 | 유효한 자격 증명 또는 가입 코드 |
| `SUPER_ADMIN` | 조직 등록·조회, 가입 코드 발급·조회·폐기 | 모든 조직과 가입 코드 |
| 인증 사용자 | 내 계정 조회 | 본인 계정 |

## 오류

| 조건 | 오류 코드 | HTTP |
|---|---|---:|
| 요청값 검증 실패 | `COMMON_001` | 400 |
| 로그인 ID 또는 비밀번호 불일치 | `AUTH_004` | 401 |
| 중복 로그인 ID | `COMMON_005` | 409 |
| 조직 없음 | `ORGANIZATION_001` | 404 |
| 조직 비활성 | `ORGANIZATION_002` | 409 |
| 가입 코드 없음·만료·사용·폐기 | `INVITATION_001` | 409 |
| 가입 코드 대상 역할과 조직 유형 불일치 | `INVITATION_002` | 409 |
| 토큰 없음·만료·위조 | `AUTH_001` 또는 `AUTH_002` | 401 |
| 슈퍼 관리자 권한 없음 | `AUTH_003` | 403 |

## 완료 조건

- [x] 관리자 조직 등록·조회 API를 구현하고 권한을 검증합니다.
- [x] 가입 코드 발급·조회·폐기를 구현하고 원문은 발급 응답에서만 반환합니다.
- [x] 가입 코드 회원가입과 로그인 토큰 발급을 구현합니다.
- [x] 보호 API에서 Bearer Access Token 인증을 적용합니다.
- [x] Flyway migration과 단위·통합·권한 테스트를 추가합니다.
- [x] 프론트엔드 계약 문서를 작성합니다.

## 확정 정책

| 쟁점 | 최종 결정 | 결정 이유·영향 |
|---|---|---|
| 슈퍼 관리자 초기 생성 | `ersync.bootstrap.super-admin.*` 설정이 있을 때만 서버 시작 시 생성 | 기본 비밀번호 커밋을 피하면서 최초 운영 계정 생성 경로 확보 |
| 가입 코드 해시 방식 | BCrypt 해시 저장, 원문은 발급 응답에서만 반환 | 원문 조회를 막고 DB 유출 시 코드 재사용 위험 축소 |
| 로그인 토큰 | HMAC 서명 JWT Access Token과 랜덤 Refresh Token 발급 | 프론트 연동 가능한 인증을 제공하고 Refresh Token은 해시로 저장 |
| 병원 프로필 | 이번 기능에서 제외 | 병원 ER 주소·좌표·수신 상태는 다음 hospital profile 기능에서 구현 |

## 결정 필요 사항

- 없음

## 구현 전 확인

- [x] AI 또는 작성자가 기존 요구사항과의 충돌·미확정 정책을 검토함
- [x] 팀에서 목적, 시나리오, API, 권한, 오류와 완료 조건을 검토함
- [x] 최종 결정을 `확정 정책`에 반영했고 `결정 필요 사항`이 없음

세 항목을 모두 확인했으므로 `implementation.md` 계획에 따라 구현합니다.
