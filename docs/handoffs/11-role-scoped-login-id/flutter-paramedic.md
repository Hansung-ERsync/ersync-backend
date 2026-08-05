# 역할별 로그인 아이디 Flutter 구급대원 앱 핸드오프

```text
Feature: role-scoped-login-id
Backend Feature: docs/features/11-role-scoped-login-id/
Related PR: NONE
Available After: MAIN_MERGE
Breaking Change: YES
```

> 로그인 요청 형식은 이 문서가 Flutter의 최신 기준입니다. 기존 02 문서의
> `loginId + password` 로그인 예시는 더 이상 사용하지 않습니다. 회원가입,
> 가입 코드 확인, 프로필 조회와 토큰 갱신은 기존 문서를 계속 사용합니다.

## 변경 요약

- `POST /api/v1/auth/login` 요청에 `role`이 필수로 추가됐습니다.
- Flutter 구급대원 앱은 항상 `role: PARAMEDIC`을 전송합니다.
- 같은 `loginId`를 병원 웹 계정이 사용하고 있어도 구급대원 계정을 만들고
  로그인할 수 있습니다.
- 같은 `PARAMEDIC` 역할 안에서는 기존처럼 로그인 아이디를 중복 사용할 수
  없습니다.
- 서버는 요청 역할을 권한으로 신뢰하지 않고, `loginId + role`로 조회한 실제
  DB 계정의 UUID·조직·역할로 JWT와 응답을 생성합니다.
- Refresh Token 요청과 기존 Access Token 보호 API는 변경되지 않았습니다.

## 적용 범위

| 기능 | 최신 참고 문서 |
|---|---|
| 로그인 요청 | 이 11번 문서 |
| 가입 코드 확인·구급대원 회원가입·프로필 | 기존 07 문서 |
| Access·Refresh Token 의미와 갱신 | 기존 02 문서 |
| 보호 API 역할·조직 검증 | 각 기능의 기존 핸드오프 |

기존 참고 링크:

- 02 인증: `docs/handoffs/02-account-onboarding-auth/flutter-paramedic.md`
- 07 가입·프로필: `docs/handoffs/07-signup-profile-integration/flutter-paramedic.md`

## 공통 계약

- Base URL: `http://13.124.194.249`
- Content-Type: `application/json`
- 로그인 인증: 없음
- 시간: ISO-8601 UTC 문자열
- 로그인 ID: 앞뒤 공백 제거 후 `[a-z0-9]{4,30}`, 대문자 불가
- 비밀번호: 8~64자
- 앱 고정 역할: `PARAMEDIC`

공통 오류 응답:

```json
{
  "code": "ERROR_CODE",
  "message": "사용자에게 표시 가능한 메시지",
  "fieldErrors": [],
  "traceId": "TRACE_ID"
}
```

## API. 구급대원 역할 지정 로그인

### `POST /api/v1/auth/login`

- 성공 HTTP: `200 OK`
- `Authorization` 헤더: 없음
- 요청의 `role`은 화면 입력값이 아니라 앱이 넣는 고정값입니다.

요청:

```json
{
  "loginId": "shared01",
  "password": "safe-password",
  "role": "PARAMEDIC"
}
```

| 필드 | 타입 | 필수 | Flutter 계약 |
|---|---|---:|---|
| `loginId` | string | YES | 사용자가 입력한 소문자 영문·숫자 4~30자 |
| `password` | string | YES | 사용자가 입력한 8~64자 비밀번호 |
| `role` | enum string | YES | 항상 `PARAMEDIC` |

성공 응답:

```json
{
  "tokenType": "Bearer",
  "accessToken": "JWT_ACCESS_TOKEN",
  "accessTokenExpiresAt": "2026-08-05T08:15:00Z",
  "refreshToken": "OPAQUE_REFRESH_TOKEN",
  "refreshTokenExpiresAt": "2026-08-12T08:00:00Z",
  "accountId": "PARAMEDIC_ACCOUNT_UUID",
  "organizationId": "EMS_ORGANIZATION_UUID",
  "role": "PARAMEDIC"
}
```

| 응답 필드 | Flutter 처리 |
|---|---|
| `tokenType` | `Bearer` 확인 |
| `accessToken` | 보호 API의 `Authorization: Bearer {accessToken}`에 사용 |
| `accessTokenExpiresAt` | Access Token 갱신 판단에 사용 |
| `refreshToken` | 기존 보안 저장 방식 유지, 원문 로그 금지 |
| `refreshTokenExpiresAt` | 재로그인 판단에 사용 |
| `accountId` | 로그인된 실제 구급대원 계정 UUID |
| `organizationId` | 가입 코드로 연결된 실제 `EMS_UNIT` 조직 UUID |
| `role` | 반드시 `PARAMEDIC`; 다르면 토큰을 저장하지 않음 |

## 오류

| 오류 코드 | HTTP | 발생 조건 | 앱 처리 |
|---|---:|---|---|
| `COMMON_001` | 400 | `role` 누락·오타·알 수 없는 값 또는 다른 필드 형식 오류 | 앱 요청에 `PARAMEDIC`이 포함됐는지 확인하고 입력값 점검 |
| `AUTH_004` | 401 | `loginId + PARAMEDIC` 계정 없음 또는 비밀번호 불일치 | 어떤 값이 틀렸는지 구분하지 않고 동일한 로그인 실패 안내 |
| `USER_002` | 403 | 일치한 구급대원 계정이 비활성 | 토큰을 저장하지 않고 운영 담당자 문의 안내 |

- 같은 아이디의 병원 계정이 존재해도 구급대원 계정이 없으면 `AUTH_004`입니다.
- 병원 계정 비밀번호를 입력해도 `role: PARAMEDIC`이면 병원 계정으로 로그인되지
  않습니다.
- 계정 존재 여부와 어떤 자격정보가 틀렸는지는 응답으로 구분하지 않습니다.

## 회원가입 아이디 중복 의미

- 구급대원 가입의 `USER_003`은 같은 `PARAMEDIC` 역할에 이미 같은 아이디가
  있다는 뜻입니다.
- 병원 계정이 같은 아이디를 사용하는 것은 구급대원 가입 실패 원인이 아닙니다.
- 회원가입 요청 필드와 가입 코드 소비 방식은 07 문서에서 변경되지 않았습니다.

## 토큰 갱신

기존 `POST /api/v1/auth/tokens/refresh` 요청에는 `role`을 추가하지 않습니다.

```json
{
  "refreshToken": "CURRENT_REFRESH_TOKEN"
}
```

- Refresh Token은 발급할 때 이미 한 계정 UUID에 연결됩니다.
- 갱신 응답의 `role`과 `organizationId`도 실제 계정 기준입니다.
- 성공하면 Access·Refresh Token을 모두 새 값으로 교체하고 이전 Refresh Token을
  다시 사용하지 않습니다.
- 기존 `AUTH_005`, `USER_002` 처리 방식은 유지합니다.

## 전환과 복구

| 상황 | 서버 계약 | 앱 처리 |
|---|---|---|
| 기존 형식으로 로그인 | `role`이 없으므로 `COMMON_001` | 로그인 요청에 `PARAMEDIC` 고정값 추가 |
| 같은 아이디의 병원 계정도 존재 | `PARAMEDIC` 계정만 조회 | 구급대원 비밀번호로 로그인 |
| 응답 역할이 예상과 다름 | 정상 구현에서는 발생하지 않음 | 토큰 저장 금지, `traceId`와 함께 문의 |
| 기존 Access Token 보유 | 만료 전까지 기존 보호 API 사용 가능 | 현재 복구 흐름 유지 |
| 기존 Refresh Token 보유 | 기존 요청으로 회전 가능 | 성공 후 새 토큰 쌍 저장 |
| Refresh 실패 | 기존 `AUTH_005` | 인증정보 제거 후 역할 지정 로그인 |

## 연동 확인

- [ ] 모든 로그인 요청에 `role: PARAMEDIC`을 포함함
- [ ] 사용자가 역할을 선택하거나 수정하는 UI가 없음
- [ ] 로그인 성공 응답 `role`이 `PARAMEDIC`인지 확인함
- [ ] 병원과 같은 아이디를 가진 구급대원 계정으로 로그인 성공
- [ ] 병원 비밀번호·없는 구급대원 계정은 `AUTH_004`로 처리
- [ ] `role` 누락 시 `COMMON_001` 확인
- [ ] Refresh Token 요청에는 `role`을 추가하지 않음
- [ ] 비밀번호·Access·Refresh Token 원문을 로그·노션·카카오톡에 남기지 않음
- [ ] HTTP Dev에서는 테스트 계정과 가짜 데이터만 사용
