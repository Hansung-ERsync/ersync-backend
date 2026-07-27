# 계정·조직·가입 코드·인증 구현 검수

```text
Feature: account-organization-invitation-security
Implemented By: Codex
Related PR:
Frontend Impact: YES
Frontend Contract: docs/contracts/02-account-organization-invitation-security.md
```

## 구현 요약

- 조직 등록·조회 API와 가입 코드 발급·조회·폐기 API를 추가했습니다.
- 가입 코드 확인, 회원가입, 로그인, 내 계정 조회 API를 추가했습니다.
- Access Token은 HMAC 서명 JWT로 발급하고 Refresh Token과 가입 코드는 BCrypt 해시로 저장합니다.
- `V1__account_organization_invitation_security.sql`로 계정 기반 테이블을 추가했습니다.

## 요구사항 체크

| 요구사항 | 결과 | 근거 |
|---|---|---|
| 관리자 조직 등록·조회 | PASS | 통합 테스트에서 `SUPER_ADMIN` 토큰으로 조직 등록 |
| 가입 코드 발급·조회·폐기 | PASS | 원문 1회 응답, 목록 원문 제외, 폐기 API 구현 |
| 코드 기반 회원가입 | PASS | 통합 테스트에서 코드 확인 후 가입 성공과 코드 `USED` 확인 |
| 로그인과 Bearer 인증 | PASS | 로그인 토큰으로 `/api/v1/auth/me` 조회 |

## 변경 API·DB

| 구분 | 변경 내용 | 호환성 |
|---|---|---|
| API | `POST /api/v1/auth/login`, `POST /api/v1/auth/invitation-code/verify`, `POST /api/v1/auth/signup`, `GET /api/v1/auth/me`, 관리자 조직·가입 코드 API | 기존 API 변경 없는 추가 API |
| DB | `organizations`, `user_accounts`, `invitation_codes`, `refresh_tokens` | 새 Flyway migration |

## 프론트엔드 전달

| 영향 | 계약 |
|---|---|
| `YES` | `docs/contracts/02-account-organization-invitation-security.md` |

## Spec 이후 정책 변경

- 없음

## 범위 확인

- spec 범위를 넘어 추가한 작업: 없음
- 의도적으로 제외한 후속 작업: 병원 ER 프로필, 수신 ON/OFF, 환자 이송 도메인

## 테스트 결과

| 테스트 | 결과 | 근거 |
|---|---|---|
| `./gradlew clean check` | PASS | 17개 테스트, 실패·스킵 없음 |
| local 실행·readiness | PASS | Docker MySQL healthy, 8081 검증 실행 readiness `UP`, Flyway V1 적용 확인 |

## 남은 리스크

| 리스크 | 영향 | 대응 |
|---|---|---|
| 없음 |  |  |

## 다음 작업 추천

1. 병원 프로필과 응급실 수신 ON/OFF 기능을 구현합니다.
