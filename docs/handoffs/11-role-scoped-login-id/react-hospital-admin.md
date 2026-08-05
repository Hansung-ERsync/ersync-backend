# 역할별 로그인 아이디 React 병원·관리자 웹 핸드오프

```text
Feature: role-scoped-login-id
Backend Feature: docs/features/11-role-scoped-login-id/
Related PR: NONE
Available After: MAIN_MERGE
Breaking Change: YES
Hospital Impact: YES
Admin Impact: YES
```

> 로그인 요청 형식은 이 문서가 React 병원·관리자 웹의 최신 기준입니다. 기존
> 02 문서의 `loginId + password` 로그인 예시는 더 이상 사용하지 않습니다.
> 회원가입, 가입 코드 관리, 병원 프로필·수신 상태와 토큰 갱신은 기존 문서를
> 계속 사용합니다.

## 변경 요약

- `POST /api/v1/auth/login` 요청에 `role`이 필수로 추가됐습니다.
- 병원 로그인 진입 화면은 `role: HOSPITAL_STAFF`를 고정합니다.
- 슈퍼 관리자 로그인 진입 화면은 `role: SUPER_ADMIN`을 고정합니다.
- 일반 사용자가 로그인 역할을 임의로 선택하는 UI는 사용하지 않습니다.
- 같은 `loginId`를 구급대원 앱 계정 또는 다른 역할 계정이 사용하고 있어도
  병원·관리자 계정을 별도로 식별할 수 있습니다.
- 같은 역할 안에서는 조직이 달라도 동일 로그인 아이디를 중복 사용할 수
  없습니다.
- Refresh Token 요청과 기존 보호 API는 변경되지 않았습니다.

## 적용 범위

| 기능 | 최신 참고 문서 |
|---|---|
| 병원·관리자 로그인 요청 | 이 11번 문서 |
| 병원 회원가입·조직·가입 코드·토큰 갱신 | 기존 02 문서 |
| 병원 내 정보·수신 상태 조회 | 기존 09 문서 |
| 병원 제안·응답·목적지 업무 | 각 기능의 기존 핸드오프 |

기존 참고 링크:

- 02 인증·관리: `docs/handoffs/02-account-onboarding-auth/react-hospital-admin.md`
- 09 병원 프로필·수신 상태: `docs/handoffs/09-hospital-profile-receiving-status/react-hospital-admin.md`

## 공통 계약

- Base URL: `http://13.124.194.249`
- Content-Type: `application/json`
- 로그인 인증: 없음
- 시간: ISO-8601 UTC 문자열
- 로그인 ID: 앞뒤 공백 제거 후 `[a-z0-9]{4,30}`, 대문자 불가
- 비밀번호: 8~64자
- 병원 고정 역할: `HOSPITAL_STAFF`
- 관리자 고정 역할: `SUPER_ADMIN`

공통 오류 응답:

```json
{
  "code": "ERROR_CODE",
  "message": "사용자에게 표시 가능한 메시지",
  "fieldErrors": [],
  "traceId": "TRACE_ID"
}
```

## API. 역할 지정 로그인

### `POST /api/v1/auth/login`

- 성공 HTTP: `200 OK`
- `Authorization` 헤더: 없음
- `role`은 로그인 진입 경로가 넣는 고정값입니다.

### 병원 로그인 요청

```json
{
  "loginId": "shared01",
  "password": "hospital-password",
  "role": "HOSPITAL_STAFF"
}
```

### 슈퍼 관리자 로그인 요청

```json
{
  "loginId": "admin",
  "password": "admin-password",
  "role": "SUPER_ADMIN"
}
```

| 필드 | 타입 | 필수 | 병원 화면 | 관리자 화면 |
|---|---|---:|---|---|
| `loginId` | string | YES | 입력값 | 입력값 |
| `password` | string | YES | 입력값 | 입력값 |
| `role` | enum string | YES | `HOSPITAL_STAFF` 고정 | `SUPER_ADMIN` 고정 |

병원 성공 응답 예시:

```json
{
  "tokenType": "Bearer",
  "accessToken": "JWT_ACCESS_TOKEN",
  "accessTokenExpiresAt": "2026-08-05T08:15:00Z",
  "refreshToken": "OPAQUE_REFRESH_TOKEN",
  "refreshTokenExpiresAt": "2026-08-12T08:00:00Z",
  "accountId": "HOSPITAL_ACCOUNT_UUID",
  "organizationId": "HOSPITAL_ORGANIZATION_UUID",
  "role": "HOSPITAL_STAFF"
}
```

슈퍼 관리자 성공 응답은 같은 구조이고 다음 값이 다릅니다.

```json
{
  "accountId": "SUPER_ADMIN_ACCOUNT_UUID",
  "organizationId": null,
  "role": "SUPER_ADMIN"
}
```

- 병원 로그인 성공 후 응답 `role`은 반드시 `HOSPITAL_STAFF`여야 합니다.
- 관리자 로그인 성공 후 응답 `role`은 반드시 `SUPER_ADMIN`이어야 합니다.
- 고정 역할과 응답 역할이 다르면 토큰을 저장하지 않습니다.
- JWT와 응답은 요청값을 복사하지 않고 DB의 실제 계정으로 생성됩니다.

## 오류

| 오류 코드 | HTTP | 발생 조건 | 웹 처리 |
|---|---:|---|---|
| `COMMON_001` | 400 | `role` 누락·오타·알 수 없는 값 또는 다른 필드 형식 오류 | 진입 경로별 고정 역할과 요청 형식 확인 |
| `AUTH_004` | 401 | `loginId + role` 계정 없음 또는 비밀번호 불일치 | 어떤 값이 틀렸는지 구분하지 않고 동일한 로그인 실패 안내 |
| `USER_002` | 403 | 일치한 병원·관리자 계정이 비활성 | 토큰 저장 금지, 운영 담당자 확인 안내 |

- 같은 아이디의 구급대원 계정만 존재하면 병원·관리자 로그인은 `AUTH_004`입니다.
- 병원 비밀번호와 `role: SUPER_ADMIN`을 보내도 관리자 계정으로 로그인되지
  않습니다.
- 다른 역할 계정의 존재 여부는 오류로 구분하지 않습니다.

## 병원 회원가입 아이디 중복 의미

- 병원 회원가입의 `USER_003`은 다른 `HOSPITAL_STAFF` 계정이 같은 아이디를
  사용 중이라는 뜻입니다.
- 구급대원이나 슈퍼 관리자 계정이 같은 아이디를 사용하는 것은 병원 가입 실패
  원인이 아닙니다.
- 병원 조직당 공용 계정·프로필 하나인 기존 `USER_004` 정책은 유지됩니다.
- 병원 회원가입 요청 필드와 가입 코드 소비 계약은 기존 02 문서와 같습니다.

## 토큰 갱신

기존 `POST /api/v1/auth/tokens/refresh` 요청에는 `role`을 추가하지 않습니다.

```json
{
  "refreshToken": "CURRENT_REFRESH_TOKEN"
}
```

- Refresh Token은 이미 병원 또는 관리자 계정 UUID에 연결돼 있습니다.
- 갱신 응답의 역할·조직도 실제 계정 기준입니다.
- 성공하면 Access·Refresh Token을 모두 새 값으로 교체합니다.
- 기존 `AUTH_005`, `USER_002` 처리 방식은 유지합니다.

## 전환과 복구

| 상황 | 서버 계약 | 웹 처리 |
|---|---|---|
| 기존 형식으로 로그인 | `role` 누락으로 `COMMON_001` | 병원·관리자 진입 경로별 고정 역할 추가 |
| 병원과 관리자 아이디가 같음 | 요청 역할에 맞는 계정만 조회 | 각 진입 경로의 계정 비밀번호 사용 |
| 응답 역할이 고정 역할과 다름 | 정상 구현에서는 발생하지 않음 | 토큰 저장 금지, `traceId`와 함께 문의 |
| 기존 Access Token 보유 | 만료 전까지 기존 보호 API 사용 가능 | 현재 인증 복구 유지 |
| 기존 Refresh Token 보유 | 기존 요청으로 회전 가능 | 성공 후 새 토큰 쌍 저장 |
| Refresh 실패 | 기존 `AUTH_005` | 인증정보 제거 후 역할 지정 로그인 |

## 연동 확인

- [ ] 병원 로그인 요청에 `role: HOSPITAL_STAFF` 포함
- [ ] 관리자 로그인 요청에 `role: SUPER_ADMIN` 포함
- [ ] 일반 사용자가 역할을 임의 선택하는 UI가 없음
- [ ] 로그인 성공 응답 역할과 진입 경로 역할이 같은지 확인
- [ ] 같은 아이디의 구급대원 계정이 있어도 병원 로그인 성공
- [ ] 다른 역할 비밀번호·없는 역할 계정은 `AUTH_004` 처리
- [ ] `role` 누락 시 `COMMON_001` 확인
- [ ] Refresh Token 요청에는 `role`을 추가하지 않음
- [ ] 비밀번호·Access·Refresh Token 원문을 로그·노션·카카오톡에 남기지 않음
- [ ] HTTP Dev에서는 테스트 계정과 가짜 데이터만 사용
