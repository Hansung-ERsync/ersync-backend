# 조직 가입 및 사용자 인증 Flutter 구급대원 앱 핸드오프

```text
Feature: account-onboarding-auth
Backend Feature: docs/features/02-account-onboarding-auth/
Related PR: NONE
Available After: MAIN_MERGE
Breaking Change: NO
```

> 로그인 요청과 로그인 아이디 고유성은 11번 역할별 로그인 아이디 문서가 최신
> 기준입니다. 이 문서는 회원가입, 토큰 의미와 갱신 계약을 참고할 때 사용합니다.

## 변경 요약

- 새로 가능해진 구급대원 동작: 가입 코드로 개인 계정 생성, 로그인, Access
  Token 갱신
- 기존 앱 영향: 기존 API 변경 없음

## 사용자 흐름

| 순서 | 사용자·앱 동작 | API 호출 | 성공 후 상태 |
|---:|---|---|---|
| 1 | 전달받은 가입 코드와 개인 ID·비밀번호 입력 | `POST /api/v1/auth/signups/paramedic` | `PARAMEDIC` 계정 생성, 코드는 `USED` |
| 2 | ID·비밀번호와 앱 고정 `PARAMEDIC` 역할로 로그인 | `POST /api/v1/auth/login` | Access·Refresh Token과 조직 범위 저장 |
| 3 | 보호 API 호출 | `Authorization: Bearer {accessToken}` | 서버가 계정·역할·조직 검증 |
| 4 | Access Token 만료 | `POST /api/v1/auth/tokens/refresh` | 새 토큰 쌍으로 교체, 이전 Refresh Token 폐기 |

## 인증과 접근 범위

| 항목 | 계약 |
|---|---|
| 인증 | Bearer JWT Access Token, 기본 만료 15분 |
| 갱신 | 고난도 Refresh Token, 기본 만료 7일, 갱신할 때마다 1회 교체 |
| 역할 | 가입 코드를 발급할 때 서버에 저장된 `PARAMEDIC` |
| 조직·소유권 | 가입 코드의 `EMS_UNIT`; 앱이 보낸 사용자·조직 ID로 권한을 결정하지 않음 |

- 토큰과 비밀번호를 로그에 출력하지 않습니다.
- `AUTH_002` 수신 시 Refresh Token으로 한 번 갱신하고 원래 요청을 다시 시도할
  수 있습니다.
- 갱신이 `AUTH_005` 또는 `USER_002`이면 저장한 인증정보를 제거하고 다시
  로그인해야 합니다.

## 공통

- Base URL: `http://13.124.194.249`
- Content-Type: `application/json`
- 시간: ISO-8601 UTC
- 로그인 ID: 앞뒤 공백 제거 후 `[a-z0-9]{4,30}`, 대문자 불가
- 비밀번호: 8~64자

공통 오류 응답:

```json
{
  "code": "ERROR_CODE",
  "message": "사용자에게 표시 가능한 메시지",
  "fieldErrors": [],
  "traceId": "TRACE_ID"
}
```

## API

### `POST /api/v1/auth/signups/paramedic`

- 목적: 가입 코드의 구급대 조직에 개인 구급대원 계정 생성
- 인증: 없음
- 성공 HTTP: `201 Created`

요청:

```json
{
  "invitationCode": "URL_SAFE_ONE_TIME_CODE",
  "loginId": "medic01",
  "password": "safe-password"
}
```

| 필드 | 타입 | 필수 | 제약 |
|---|---|---:|---|
| `invitationCode` | string | YES | 슈퍼 관리자가 전달한 미사용·미만료 구급대원 코드 |
| `loginId` | string | YES | 소문자 영문·숫자 4~30자, 같은 `PARAMEDIC` 역할에서 고유 |
| `password` | string | YES | 8~64자 |

성공 응답:

```json
{
  "accountId": "ACCOUNT_UUID",
  "organizationId": "EMS_ORGANIZATION_UUID",
  "organizationName": "성북소방서 구급대",
  "role": "PARAMEDIC",
  "hospitalId": null,
  "receivingStatus": null
}
```

| 오류 코드 | HTTP | 발생 조건 | 처리 |
|---|---:|---|---|
| `COMMON_001` | 400 | 입력 형식 또는 코드 역할·조직 유형 불일치 | 입력값 확인 |
| `INVITATION_001` | 400 | 코드를 찾거나 검증할 수 없음 | 코드 재확인 |
| `INVITATION_002` | 409 | 만료된 코드 | 새 코드 요청 |
| `INVITATION_003` | 409 | 이미 사용된 코드 | 로그인하거나 새 코드 요청 |
| `INVITATION_004` | 409 | 폐기된 코드 | 새 코드 요청 |
| `USER_003` | 409 | 같은 `PARAMEDIC` 역할의 로그인 ID 중복 | 다른 ID 입력 |

### `POST /api/v1/auth/login`

- 목적: 활성 계정 로그인과 토큰 쌍 발급
- 인증: 없음
- 성공 HTTP: `200 OK`

요청:

```json
{
  "loginId": "medic01",
  "password": "safe-password",
  "role": "PARAMEDIC"
}
```

성공 응답:

```json
{
  "tokenType": "Bearer",
  "accessToken": "JWT_ACCESS_TOKEN",
  "accessTokenExpiresAt": "2026-08-01T01:15:00Z",
  "refreshToken": "OPAQUE_REFRESH_TOKEN",
  "refreshTokenExpiresAt": "2026-08-08T01:00:00Z",
  "accountId": "ACCOUNT_UUID",
  "organizationId": "EMS_ORGANIZATION_UUID",
  "role": "PARAMEDIC"
}
```

| 오류 코드 | HTTP | 발생 조건 | 처리 |
|---|---:|---|---|
| `AUTH_004` | 401 | ID 또는 비밀번호 불일치 | 같은 메시지로 자격정보 재입력 |
| `USER_002` | 403 | 비활성 계정 | 운영 담당자 문의 |

### `POST /api/v1/auth/tokens/refresh`

- 목적: Access Token 만료 전후 새 토큰 쌍 발급
- 인증: Bearer 헤더 없이 요청 본문의 Refresh Token 사용
- 성공 HTTP: `200 OK`

요청:

```json
{
  "refreshToken": "CURRENT_REFRESH_TOKEN"
}
```

성공 응답은 로그인 응답과 같습니다. 성공 즉시 저장된 Access·Refresh Token을
둘 다 새 값으로 교체해야 하며 이전 Refresh Token은 다시 사용할 수 없습니다.

| 오류 코드 | HTTP | 발생 조건 | 처리 |
|---|---:|---|---|
| `AUTH_005` | 401 | 토큰 불일치·만료·폐기·재사용 | 인증정보 제거 후 로그인 |
| `USER_002` | 403 | 계정 비활성 | 인증정보 제거 후 운영 담당자 문의 |

## 상태와 Enum

| 값 | 의미 | 앱 처리 |
|---|---|---|
| `PARAMEDIC` | 구급대원 개인 계정 | 구급대원 기능 노출 |
| `AUTH_001` | Bearer 인증 없음 | 로그인 필요 |
| `AUTH_002` | Access Token 위조·만료·현재 계정 불일치 | 토큰 갱신 시도 |
| `AUTH_003` | 다른 역할 전용 API | 해당 기능 사용 불가 |

## 모바일 상태 복구

| 상황 | 서버 계약 | 앱 처리 |
|---|---|---|
| 가입 응답 유실 | 코드는 성공 시 이미 `USED`일 수 있음 | 같은 ID로 로그인 시도, 실패하면 운영자 확인 |
| 로그인 응답 유실 | 새 토큰이 DB에 남을 수 있으나 계정은 정상 | 다시 로그인 가능 |
| Access Token 만료 | 보호 API `AUTH_002` | Refresh 후 원 요청 한 번 재시도 |
| Refresh Token 재사용 | `AUTH_005` | 인증정보 제거 후 로그인 |
| 앱 재실행 | 서버 세션 없음 | 안전하게 저장한 토큰으로 복구하거나 로그인 |

## 실시간 이벤트와 재조회

- 이벤트: 없음
- 재연결 후 재조회 API: 없음

## 연동 확인

- [ ] 구급대원 가입 정상·만료·사용 코드
- [ ] 로그인과 잘못된 자격정보
- [ ] Access Token 보호 API
- [ ] Refresh Token 회전과 이전 토큰 재사용 차단
- [ ] 비활성 계정 처리
- [ ] dev API 연결
