# 계정·조직·가입 코드·인증 프론트엔드 연동 계약

```text
Feature: account-organization-invitation-security
Backend Feature: docs/features/02-account-organization-invitation-security/
Available After: MAIN_MERGE
Breaking Change: NO
Updated: 2026-07-27
```

## 변경 요약

- 추가된 사용자 동작: 관리자 조직 등록, 가입 코드 발급·조회·폐기, 가입 코드 확인, 회원가입, 로그인, 내 계정 조회
- 기존 프론트 영향: 보호 API는 `Authorization: Bearer {accessToken}` 필요

## API

공통:

- Content-Type: `application/json`
- 인증: 공개 API 제외 `Authorization: Bearer {accessToken}`
- 시간: ISO-8601 UTC

| 행위 | Method | Path | 성공 HTTP | 인증 역할 |
|---|---|---|---:|---|
| 로그인 | POST | `/api/v1/auth/login` | 200 | 공개 |
| 가입 코드 확인 | POST | `/api/v1/auth/invitation-code/verify` | 200 | 공개 |
| 회원가입 | POST | `/api/v1/auth/signup` | 200 | 공개 |
| 내 계정 조회 | GET | `/api/v1/auth/me` | 200 | 인증 사용자 |
| 조직 등록 | POST | `/api/v1/admin/organizations` | 200 | `SUPER_ADMIN` |
| 조직 목록 | GET | `/api/v1/admin/organizations` | 200 | `SUPER_ADMIN` |
| 가입 코드 발급 | POST | `/api/v1/admin/organizations/{organizationId}/invitation-codes` | 200 | `SUPER_ADMIN` |
| 가입 코드 목록 | GET | `/api/v1/admin/invitation-codes` | 200 | `SUPER_ADMIN` |
| 가입 코드 폐기 | POST | `/api/v1/admin/invitation-codes/{invitationCodeId}/revoke` | 200 | `SUPER_ADMIN` |

## 주요 요청·응답

로그인 요청:

```json
{"loginId":"admin","password":"password"}
```

로그인 응답:

```json
{
  "accessToken": "jwt",
  "tokenType": "Bearer",
  "expiresInSeconds": 900,
  "refreshToken": "opaque-token",
  "refreshTokenId": "uuid",
  "refreshTokenExpiresAt": "2026-08-03T00:00:00Z",
  "account": {
    "accountId": "uuid",
    "organizationId": null,
    "role": "SUPER_ADMIN",
    "loginId": "admin"
  }
}
```

가입 코드 확인 요청:

```json
{"invitationCode":"code-shown-once"}
```

가입 코드 확인 응답:

```json
{
  "organizationId": "uuid",
  "organizationName": "EMS Alpha",
  "organizationType": "EMS_UNIT",
  "targetRole": "PARAMEDIC",
  "expiresAt": "2026-07-30T00:00:00Z"
}
```

회원가입 요청:

```json
{
  "invitationCode": "code-shown-once",
  "loginId": "paramedic.one",
  "password": "password"
}
```

조직 등록 요청:

```json
{"type":"EMS_UNIT","name":"EMS Alpha"}
```

가입 코드 발급 요청:

```json
{"targetRole":"PARAMEDIC","expiresInDays":3}
```

가입 코드 발급 응답에는 `plaintextCode`가 포함됩니다. 목록 응답에는 원문 코드가 없습니다.

## 상태와 Enum

| 값 | 의미 | 프론트 처리 |
|---|---|---|
| `SUPER_ADMIN` | 관리자 | 관리자 화면 진입 |
| `PARAMEDIC` | 구급대원 | 구급대원 앱 진입 |
| `HOSPITAL_STAFF` | 병원 공용 계정 | 병원 웹 진입 |
| `HOSPITAL` | 병원 조직 | 병원 가입 코드에는 `HOSPITAL_STAFF`만 사용 |
| `EMS_UNIT` | 구급대 조직 | 구급대 가입 코드에는 `PARAMEDIC`만 사용 |
| `AVAILABLE` | 사용 가능 코드 | 가입 가능 |
| `USED` | 사용된 코드 | 가입 불가 |
| `EXPIRED` | 만료 코드 | 가입 불가 |
| `REVOKED` | 폐기 코드 | 가입 불가 |

## 오류 처리

| 오류 코드 | HTTP | 발생 조건 | 프론트 처리 |
|---|---:|---|---|
| `COMMON_001` | 400 | 필수값, enum, 형식 오류 | 입력값 확인 표시 |
| `COMMON_005` | 409 | 중복 로그인 ID 또는 조직명 | 중복 안내 |
| `AUTH_001` | 401 | 인증 누락 | 로그인 화면 이동 |
| `AUTH_002` | 401 | 토큰 만료·위조 | 재로그인 |
| `AUTH_003` | 403 | 관리자 권한 없음 | 접근 불가 표시 |
| `AUTH_004` | 401 | 로그인 실패 | 아이디·비밀번호 오류 표시 |
| `ORGANIZATION_001` | 404 | 조직 없음 | 관리자 목록 재조회 |
| `ORGANIZATION_002` | 409 | 비활성 조직 | 조직 상태 확인 안내 |
| `INVITATION_001` | 409 | 코드 없음·만료·사용·폐기 | 새 가입 코드 요청 안내 |
| `INVITATION_002` | 409 | 조직 유형과 역할 불일치 | 관리자 입력 수정 |

## 실시간 이벤트

- 없음

## 프론트엔드 처리 순서

1. 사용자가 가입 코드를 입력하면 `POST /api/v1/auth/invitation-code/verify`로 조직과 역할을 확인합니다.
2. 사용자가 아이디와 비밀번호를 입력하면 `POST /api/v1/auth/signup`을 호출합니다.
3. 로그인 성공 후 `accessToken`을 보호 API의 Bearer 토큰으로 사용합니다.
4. 관리자 화면은 `SUPER_ADMIN` 계정만 조직과 가입 코드 API를 호출합니다.

## 호환성

- 기존 API 영향: 없음
- 필수 동시 배포: 프론트가 계정 기능을 사용할 때 필요
- 중복 요청·멱등성: 회원가입은 코드 1회 사용과 로그인 ID unique 제약으로 중복 생성 방지
- 페이징·정렬: 이번 목록 API는 MVP 초기 목록이며 페이징 없음

## 연동 확인

- [x] 정상 요청과 응답
- [x] 권한 없는 요청
- [x] 주요 오류별 화면 처리
- [x] 상태 또는 실시간 갱신
- [x] dev API 연결은 main 병합·배포 후 확인

## 미결정 사항

- 없음
