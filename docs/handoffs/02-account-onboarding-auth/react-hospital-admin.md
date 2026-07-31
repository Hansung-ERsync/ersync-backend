# 조직 가입 및 사용자 인증 React 병원·관리자 웹 핸드오프

```text
Feature: account-onboarding-auth
Backend Feature: docs/features/02-account-onboarding-auth/
Related PR: NONE
Available After: MAIN_MERGE
Breaking Change: NO
Hospital Impact: YES
Admin Impact: YES
```

## 변경 요약

- 새로 가능해진 병원 동작: 가입 코드로 병원 공용 계정·응급실 프로필 생성,
  로그인, 토큰 갱신, 자기 응급실 수신 `ON/OFF` 변경
- 새로 가능해진 관리자 동작: 병원·구급대 조직 등록·목록, 가입 코드
  발급·목록·폐기
- 기존 웹 영향: 기존 API 변경 없음

## 역할별 사용자 흐름

| 역할 | 순서 | 사용자·웹 동작 | API 호출 | 성공 후 상태 |
|---|---:|---|---|---|
| 슈퍼 관리자 | 1 | bootstrap 계정으로 로그인 | `POST /api/v1/auth/login` | `SUPER_ADMIN` 토큰 확보 |
| 슈퍼 관리자 | 2 | 병원·구급대 조직 등록 | `POST /api/v1/admin/organizations` | 조직 UUID 생성 |
| 슈퍼 관리자 | 3 | 조직·역할·만료로 코드 발급 | `POST /api/v1/admin/invitation-codes` | 원문 코드 한 번 표시 |
| 슈퍼 관리자 | 4 | 코드 조회 또는 사용 전 폐기 | `GET` 또는 `POST .../revoke` | 상태 확인·`REVOKED` 전환 |
| 병원 관계자 | 1 | 코드·공용 자격정보·응급실 정보 제출 | `POST /api/v1/auth/signups/hospital` | 공용 계정·프로필 생성, 수신 `OFF` |
| 병원 관계자 | 2 | 공용 계정 로그인 | `POST /api/v1/auth/login` | `HOSPITAL_STAFF` 토큰 확보 |
| 병원 관계자 | 3 | 정보 확인 후 수신 시작 | `PUT /api/v1/hospitals/me/receiving-status` | 수신 `ON` |

## 인증과 접근 범위

| 역할 | 인증 | 허용 작업 | 조직·정보 접근 범위 |
|---|---|---|---|
| 병원 관계자 | Bearer JWT | 자기 병원 수신 상태 변경 | 토큰에 연결된 병원 프로필만 접근 |
| 슈퍼 관리자 | Bearer JWT | 조직과 가입 코드 관리 | 조직·코드 관리정보만; 환자·위치정보 권한 없음 |

- Access Token 기본 만료는 15분, Refresh Token 기본 만료는 7일입니다.
- Refresh 성공 시 Access·Refresh Token을 모두 교체해야 하며 이전 Refresh
  Token은 다시 사용할 수 없습니다.
- 서버는 JWT의 계정·역할·조직을 현재 DB 계정과 매 요청 대조합니다.
- `AUTH_002`이면 Refresh를 한 번 시도하고, `AUTH_005` 또는 `USER_002`이면
  인증정보를 제거하고 로그인 화면으로 이동합니다.

## 공통

- Base URL: `http://13.124.194.249`
- Content-Type: `application/json`
- 보호 API 헤더: `Authorization: Bearer {accessToken}`
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

## 관리자 API

### `POST /api/v1/admin/organizations`

- 목적: 병원 또는 구급대 조직 등록
- 인증·역할: `SUPER_ADMIN`
- 성공 HTTP: `201 Created`

요청:

```json
{
  "name": "한성대학교병원",
  "type": "HOSPITAL"
}
```

| 필드 | 타입 | 필수 | 제약 |
|---|---|---:|---|
| `name` | string | YES | 공백 제외 1~100자 |
| `type` | enum | YES | `HOSPITAL`, `EMS_UNIT` |

성공 응답:

```json
{
  "organizationId": "ORGANIZATION_UUID",
  "name": "한성대학교병원",
  "type": "HOSPITAL",
  "createdAt": "2026-08-01T01:00:00Z"
}
```

### `GET /api/v1/admin/organizations?page=0&size=20`

- 목적: 등록된 조직을 생성 시각 역순으로 조회
- 인증·역할: `SUPER_ADMIN`
- 성공 HTTP: `200 OK`

| Query | 필수 | 기본값 | 제약 |
|---|---:|---|---|
| `page` | NO | `0` | 0 이상 |
| `size` | NO | `20` | 1~100 |

성공 응답:

```json
{
  "items": [
    {
      "organizationId": "ORGANIZATION_UUID",
      "name": "한성대학교병원",
      "type": "HOSPITAL",
      "createdAt": "2026-08-01T01:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

### `POST /api/v1/admin/invitation-codes`

- 목적: 선택한 조직에 사용할 일회용 가입 코드 발급
- 인증·역할: `SUPER_ADMIN`
- 성공 HTTP: `201 Created`
- 중요: `code` 원문은 이 응답에만 존재하며 다시 조회할 수 없음

요청 예시:

```json
{
  "organizationId": "ORGANIZATION_UUID",
  "role": "HOSPITAL_STAFF",
  "expiryOption": "THREE_DAYS",
  "customExpiresAt": null
}
```

| 필드 | 타입 | 필수 | 제약 |
|---|---|---:|---|
| `organizationId` | UUID string | YES | 등록된 조직 |
| `role` | enum | YES | 병원은 `HOSPITAL_STAFF`, 구급대는 `PARAMEDIC` |
| `expiryOption` | enum | YES | `THREE_DAYS`, `SEVEN_DAYS`, `CUSTOM` |
| `customExpiresAt` | datetime | 조건부 | `CUSTOM`일 때만 미래 UTC 시각, 나머지는 `null` 또는 생략 |

성공 응답:

```json
{
  "code": "URL_SAFE_ONE_TIME_CODE",
  "invitation": {
    "invitationCodeId": "INVITATION_UUID",
    "organizationId": "ORGANIZATION_UUID",
    "organizationName": "한성대학교병원",
    "organizationType": "HOSPITAL",
    "role": "HOSPITAL_STAFF",
    "status": "AVAILABLE",
    "expiresAt": "2026-08-04T01:00:00Z",
    "usedAt": null,
    "revokedAt": null,
    "createdAt": "2026-08-01T01:00:00Z"
  }
}
```

| 오류 코드 | HTTP | 발생 조건 |
|---|---:|---|
| `COMMON_001` | 400 | 필드 또는 조직 유형·역할 조합 오류 |
| `ORGANIZATION_001` | 404 | 조직 없음 |

### `GET /api/v1/admin/invitation-codes`

- 목적: 원문·다이제스트가 제외된 가입 코드 메타데이터 조회
- 인증·역할: `SUPER_ADMIN`
- 성공 HTTP: `200 OK`

| Query | 필수 | 설명 |
|---|---:|---|
| `status` | NO | `AVAILABLE`, `USED`, `EXPIRED`, `REVOKED` |
| `organizationId` | NO | 특정 조직 UUID |
| `page` | NO | 기본 0 |
| `size` | NO | 기본 20, 최대 100 |

응답의 각 `items`는 발급 응답의 `invitation`과 같은 구조이며 `code`와
`codeDigest` 필드는 존재하지 않습니다.

### `POST /api/v1/admin/invitation-codes/{invitationCodeId}/revoke`

- 목적: 아직 사용되지 않고 만료되지 않은 코드 폐기
- 인증·역할: `SUPER_ADMIN`
- 성공 HTTP: `200 OK`
- 성공 응답: 가입 코드 메타데이터, `status`는 `REVOKED`

| 오류 코드 | HTTP | 발생 조건 |
|---|---:|---|
| `INVITATION_001` | 400 | 가입 코드 UUID를 찾을 수 없음 |
| `INVITATION_005` | 409 | 이미 사용·만료·폐기되어 상태 변경 불가 |

## 병원 API

### `POST /api/v1/auth/signups/hospital`

- 목적: 병원별 공용 계정 하나와 응급실 프로필 생성
- 인증: 없음
- 성공 HTTP: `201 Created`

요청:

```json
{
  "invitationCode": "URL_SAFE_ONE_TIME_CODE",
  "organizationName": "한성대학교병원",
  "loginId": "hansung1",
  "password": "safe-password",
  "address": "서울특별시 성북구 삼선교로 16길",
  "latitude": 37.5821000,
  "longitude": 127.0105000,
  "contact": "02-1234-5678"
}
```

| 필드 | 타입 | 필수 | 제약 |
|---|---|---:|---|
| `invitationCode` | string | YES | 미사용·미만료 병원 코드 |
| `organizationName` | string | YES | 코드에 연결된 조직명과 일치 |
| `loginId` | string | YES | 소문자 영문·숫자 4~30자, 전역 고유 |
| `password` | string | YES | 8~64자 |
| `address` | string | YES | 최대 255자 |
| `latitude` | number | YES | -90~90 |
| `longitude` | number | YES | -180~180 |
| `contact` | string | YES | 최대 30자 |

성공 응답:

```json
{
  "accountId": "ACCOUNT_UUID",
  "organizationId": "ORGANIZATION_UUID",
  "organizationName": "한성대학교병원",
  "role": "HOSPITAL_STAFF",
  "hospitalId": "HOSPITAL_UUID",
  "receivingStatus": "OFF"
}
```

| 오류 코드 | HTTP | 발생 조건 | 웹 처리 |
|---|---:|---|---|
| `COMMON_001` | 400 | 입력·좌표·조직명·코드 역할 오류 | 필드 확인 |
| `INVITATION_001` | 400 | 코드 검증 불가 | 코드 재확인 |
| `INVITATION_002` | 409 | 코드 만료 | 새 코드 요청 |
| `INVITATION_003` | 409 | 코드 사용 완료 | 로그인 또는 관리자 확인 |
| `INVITATION_004` | 409 | 코드 폐기 완료 | 새 코드 요청 |
| `USER_003` | 409 | 로그인 ID 중복 | 다른 ID 입력 |
| `USER_004` | 409 | 해당 병원 공용 계정이 이미 존재 | 기존 공용 계정 사용 |

### `PUT /api/v1/hospitals/me/receiving-status`

- 목적: 인증된 자기 병원의 신규 요청 수신 상태 변경
- 인증·역할: `HOSPITAL_STAFF`
- 성공 HTTP: `200 OK`

요청:

```json
{
  "status": "ON"
}
```

성공 응답:

```json
{
  "hospitalId": "HOSPITAL_UUID",
  "organizationId": "ORGANIZATION_UUID",
  "status": "ON",
  "updatedAt": "2026-08-01T01:05:00Z"
}
```

| 오류 코드 | HTTP | 발생 조건 |
|---|---:|---|
| `AUTH_001` | 401 | Access Token 없음 |
| `AUTH_002` | 401 | Access Token 위조·만료·현재 계정 불일치 |
| `AUTH_003` | 403 | 병원 역할이 아님 |
| `USER_002` | 403 | 계정 비활성 |
| `COMMON_004` | 403 | 인증 조직과 병원 프로필 불일치 |
| `HOSPITAL_001` | 404 | 계정에 연결된 병원 프로필 없음 |

`OFF` 전환은 이후 구현될 신규 요청 후보에서 제외하는 상태이며 이미 생성된 요청을
철회하는 명령이 아닙니다.

## 공통 인증 API

### `POST /api/v1/auth/login`

- 인증: 없음
- 성공 HTTP: `200 OK`

```json
{
  "loginId": "hansung1",
  "password": "safe-password"
}
```

```json
{
  "tokenType": "Bearer",
  "accessToken": "JWT_ACCESS_TOKEN",
  "accessTokenExpiresAt": "2026-08-01T01:15:00Z",
  "refreshToken": "OPAQUE_REFRESH_TOKEN",
  "refreshTokenExpiresAt": "2026-08-08T01:00:00Z",
  "accountId": "ACCOUNT_UUID",
  "organizationId": "ORGANIZATION_UUID",
  "role": "HOSPITAL_STAFF"
}
```

- 슈퍼 관리자는 `organizationId`가 `null`, 병원은 자기 조직 UUID입니다.
- `AUTH_004`(401)는 ID·비밀번호 중 어느 값이 틀렸는지 구분하지 않습니다.
- `USER_002`(403)는 비활성 계정입니다.

### `POST /api/v1/auth/tokens/refresh`

- 인증: Bearer 헤더 없이 요청 본문 사용
- 성공 HTTP: `200 OK`

```json
{
  "refreshToken": "CURRENT_REFRESH_TOKEN"
}
```

- 성공 응답은 로그인 응답과 같습니다.
- `AUTH_005`(401)는 불일치·만료·폐기·재사용된 Refresh Token입니다.
- 성공 즉시 이전 Access·Refresh Token을 모두 새 값으로 교체합니다.

## 화면 상태 조건

| 대상 | 조건 | 웹 처리 |
|---|---|---|
| 가입 코드 원문 | 발급 성공 응답 직후 | 한 번 표시하고 복사 안내; 목록에서 재조회 불가 |
| 코드 폐기 버튼 | `AVAILABLE`이며 현재 시각이 `expiresAt` 이전 | 활성화 |
| 병원 수신 상태 | 가입 직후 | `OFF` 표시 |
| 병원 요청 후보 | 수신 `ON` | 이후 이송 요청 기능에서만 후보가 됨 |
| 목록 정렬·페이징 | 조직·코드 모두 | 생성 시각 역순, 서버 `page`·`size` 사용 |

## 상태와 Enum

| 값 | 의미 |
|---|---|
| `HOSPITAL`, `EMS_UNIT` | 조직 유형 |
| `SUPER_ADMIN`, `HOSPITAL_STAFF`, `PARAMEDIC` | 계정 역할 |
| `THREE_DAYS`, `SEVEN_DAYS`, `CUSTOM` | 코드 만료 입력 방식 |
| `AVAILABLE`, `USED`, `EXPIRED`, `REVOKED` | 가입 코드 상태 |
| `ON`, `OFF` | 병원 신규 요청 수신 상태 |

## 실시간 이벤트와 재조회

- 이벤트: 없음
- 재연결 후 재조회: 조직·가입 코드 목록 API 사용
- 중복 클릭: 가입 코드는 한 번만 사용·폐기되며 두 번째 명령은 상태 오류 반환

## 연동 확인

- [ ] 관리자 로그인과 조직 등록·목록
- [ ] 병원·구급대 역할 매핑별 코드 발급
- [ ] 코드 원문 1회 표시와 목록 비노출
- [ ] 코드 필터·페이징·폐기 상태
- [ ] 병원 공용 계정 가입과 중복 병원 처리
- [ ] 병원 로그인·토큰 갱신
- [ ] 수신 `ON/OFF` 및 병원 외 역할 차단
- [ ] dev API 연결
