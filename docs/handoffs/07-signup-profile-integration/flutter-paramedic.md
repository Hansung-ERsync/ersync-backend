# 구급대원 회원가입·프로필 연동 보완 Flutter 핸드오프

```text
Feature: signup-profile-integration
Backend Feature: docs/features/07-signup-profile-integration/
Related PR: NONE
Available After: MAIN_MERGE
Breaking Change: YES
```

> 이 문서는 구급대원 가입 코드 확인, 회원가입, 로그인, 토큰 갱신과 내 프로필
> 조회의 최신 계약입니다. 기존 02·03 문서와 가입 요청 필드가 다르면 이 07
> 문서를 우선합니다. React 병원 회원가입 계약은 변경되지 않았습니다.

## 변경 요약

- 가입 코드를 입력한 뒤 소속 구급대와 `PARAMEDIC` 역할을 확인하는 API가 추가됐습니다.
- 사용자는 가입 코드를 1단계에서 한 번만 입력합니다. 앱은 원문을 메모리에 잠시 보관해 최종 가입 요청에 자동으로 포함합니다.
- 구급대원 가입에 표시 이름과 다음 필수 동의 2개가 추가됐습니다.
  - 전화번호 수집·이용 동의
  - 이송 요청을 받은 병원에 전화번호 제공 동의
- 로그인·앱 재실행 후 이름·소속·로그인 ID·연락처·동의를 복구하는 내 프로필 API가 추가됐습니다.
- 기존 로그인과 Access·Refresh Token 계약은 그대로입니다.

## 사용자 흐름

| 순서 | 사용자·앱 동작 | API 호출 | 성공 후 상태 |
|---:|---|---|---|
| 1 | 가입 코드 한 번 입력 후 확인 | `POST /api/v1/auth/invitations/validate` | 소속·역할 표시, 코드 원문은 앱 메모리에만 보관 |
| 2 | 이름·전화번호·ID·비밀번호 입력 후 동의 2개 승인 | `POST /api/v1/auth/signups/paramedic` | 개인 계정·프로필 생성, 가입 코드는 `USED` |
| 3 | ID·비밀번호 로그인 | `POST /api/v1/auth/login` | 새 Access·Refresh Token 쌍 저장 |
| 4 | 로그인 직후 또는 앱 상태 복구 | `GET /api/v1/paramedics/me` | 메인·설정 화면의 사용자 정보 구성 |
| 5 | Access Token 만료 | `POST /api/v1/auth/tokens/refresh` 후 프로필 재조회 | 새 토큰 쌍과 서버 프로필로 복구 |

## 공통 계약

- Base URL: `http://13.124.194.249`
- Content-Type: `application/json`
- 시간: ISO-8601 UTC
- 로그인 ID: `[a-z0-9]{4,30}`, 대문자 불가
- 비밀번호: 8~64자
- 표시 이름: 앞뒤 공백 제거 후 2~50자, 공백만 입력 불가
- 서버 연락처 형식: 앞뒤 공백 제거 후 `[0-9+][0-9-]{7,29}`
- 현재 Flutter 화면의 구급대원 전화번호 형식은 `010-0000-0000`으로 제한해도 됩니다.
- 가입 코드, 비밀번호, Access·Refresh Token을 로그·오류 화면·분석 도구에 기록하지 않습니다.
- 현재 Dev 서버는 HTTP이므로 실제 개인번호가 아닌 테스트 번호만 사용합니다.

공통 오류 응답:

```json
{
  "code": "ERROR_CODE",
  "message": "사용자에게 표시 가능한 메시지",
  "fieldErrors": [],
  "traceId": "TRACE_ID"
}
```

오류 문의에는 API 경로, HTTP 상태, `code`, `traceId`만 전달합니다. 가입 코드,
전화번호, 비밀번호와 토큰 원문은 전달하지 않습니다.

## API 1. 가입 코드 확인

### `POST /api/v1/auth/invitations/validate`

- 인증: 없음
- 성공: `200 OK`
- 코드를 소비하지 않으며 감사 이벤트도 만들지 않습니다.

요청:

```json
{
  "invitationCode": "URL_SAFE_CASE_SENSITIVE_CODE"
}
```

| 필드 | 타입 | 필수 | 제약 |
|---|---|---:|---|
| `invitationCode` | string | YES | 앞뒤 공백만 제거, 최대 200자, 대소문자 보존 |

성공 응답:

```json
{
  "organizationId": "EMS_ORGANIZATION_UUID",
  "organizationName": "강동소방서 3구급대",
  "role": "PARAMEDIC",
  "expiresAt": "2026-08-07T09:00:00Z",
  "requiredConsents": [
    {
      "type": "CONTACT_COLLECTION_USE",
      "policyVersion": "COLLECTION_USE_DEV_1.0"
    },
    {
      "type": "HOSPITAL_PROVISION",
      "policyVersion": "HOSPITAL_PROVISION_DEV_1.0"
    }
  ]
}
```

| 필드 | 설명 |
|---|---|
| `organizationId` | 코드에 고정된 소속 조직 공개 ID; 최종 가입에서 앱이 조직을 지정하지 않음 |
| `organizationName` | 2단계 `소속이 확인되었습니다` 카드에 표시 |
| `role` | Flutter에서는 `PARAMEDIC`일 때만 2단계로 진행 |
| `expiresAt` | 코드 만료 시각 |
| `requiredConsents[]` | 최종 가입에 그대로 제출할 동의 유형과 현재 문구 버전 |

중요:

- 가입 코드는 대소문자를 구분합니다. `textCapitalization: characters`,
  `toUpperCase()`와 `toLowerCase()`로 원문을 바꾸면 안 됩니다.
- 현재 목 데이터의 `ERSYNC-EMS-001` 표시·자동 입력 버튼은 실제 API 연동 시 제거합니다.
- 성공 응답에는 코드 원문이 없습니다. 앱은 사용자가 입력한 원문을 회원가입 흐름 동안 메모리에만 보관합니다.
- 회원가입 완료, 취소, 로그인 화면 복귀 또는 앱 종료 시 보관한 코드를 제거합니다.
- 기기 영구 저장소와 일반 로그에는 가입 코드를 저장하지 않습니다.
- 응답 `role`이 `HOSPITAL_STAFF`이면 구급대원용 코드가 아니므로 2단계로 진행하지 않습니다.

| 오류 코드 | HTTP | 조건 | 앱 처리 |
|---|---:|---|---|
| `COMMON_001` | 400 | 빈 값·형식 오류 또는 비활성 조직 | 코드 확인 안내 |
| `INVITATION_001` | 400 | 코드를 찾을 수 없음·대소문자 불일치 | 입력값 확인 |
| `INVITATION_002` | 409 | 만료된 코드 | 관리자에게 새 코드 요청 |
| `INVITATION_003` | 409 | 이미 사용된 코드 | 기존 계정 로그인 또는 새 코드 요청 |
| `INVITATION_004` | 409 | 폐기된 코드 | 관리자에게 새 코드 요청 |

## API 2. 구급대원 회원가입

### `POST /api/v1/auth/signups/paramedic`

- 인증: 없음
- 성공: `201 Created`
- 이 요청 형식이 구급대원 가입의 최신 계약입니다.

요청:

```json
{
  "invitationCode": "1단계에서 앱이 보관한 원문",
  "displayName": "김민준",
  "loginId": "paramedic01",
  "password": "safe-password",
  "contact": "010-0000-0000",
  "collectionUseConsentAccepted": true,
  "collectionUseConsentVersion": "COLLECTION_USE_DEV_1.0",
  "hospitalProvisionConsentAccepted": true,
  "hospitalProvisionConsentVersion": "HOSPITAL_PROVISION_DEV_1.0"
}
```

| 필드 | 타입 | 필수 | 제약·출처 |
|---|---|---:|---|
| `invitationCode` | string | YES | 1단계에서 사용자가 입력한 원문을 변경 없이 자동 포함 |
| `displayName` | string | YES | 앞뒤 공백 제거 후 2~50자 |
| `loginId` | string | YES | 소문자 영문·숫자 4~30자, 전체 시스템 고유 |
| `password` | string | YES | 8~64자 |
| `contact` | string | YES | 현재 화면에서는 `010-0000-0000` |
| `collectionUseConsentAccepted` | boolean | YES | 반드시 `true` |
| `collectionUseConsentVersion` | string | YES | 확인 응답의 `CONTACT_COLLECTION_USE.policyVersion` |
| `hospitalProvisionConsentAccepted` | boolean | YES | 반드시 `true` |
| `hospitalProvisionConsentVersion` | string | YES | 확인 응답의 `HOSPITAL_PROVISION.policyVersion` |

기존 `contactSharingConsentAccepted`, `contactSharingConsentVersion`만 보내면 가입할
수 없습니다. 새 동의 필드 4개를 사용해야 합니다.

성공 응답:

```json
{
  "accountId": "ACCOUNT_UUID",
  "organizationId": "EMS_ORGANIZATION_UUID",
  "organizationName": "강동소방서 3구급대",
  "role": "PARAMEDIC",
  "hospitalId": null,
  "receivingStatus": null
}
```

- 서버는 최종 가입 시 코드를 다시 잠그고 상태·만료·역할·조직을 확인합니다.
- 확인 화면에서 성공했더라도 그 사이 다른 사람이 코드를 사용하거나 관리자가
  폐기하면 최종 가입은 실패할 수 있습니다.
- 계정·프로필·동의 2개와 코드 소비는 한 트랜잭션이므로 일부만 저장되지 않습니다.

| 오류 코드 | HTTP | 조건 | 앱 처리 |
|---|---:|---|---|
| `COMMON_001` | 400 | 이름·ID·비밀번호·연락처·동의·버전 오류 또는 역할 불일치 | 해당 입력·동의 확인 |
| `INVITATION_001` | 400 | 코드를 찾을 수 없음 | 1단계로 이동해 다시 입력 |
| `INVITATION_002` | 409 | 가입 전에 만료됨 | 1단계로 이동, 새 코드 요청 |
| `INVITATION_003` | 409 | 다른 가입이 먼저 코드를 사용함 | 1단계로 이동, 기존 로그인 또는 새 코드 요청 |
| `INVITATION_004` | 409 | 가입 전에 폐기됨 | 1단계로 이동, 새 코드 요청 |
| `USER_003` | 409 | 로그인 ID 중복 | 코드는 소비되지 않으므로 ID만 변경해 다시 제출 가능 |

## API 3. 로그인

### `POST /api/v1/auth/login`

- 인증: 없음
- 성공: `200 OK`

```json
{
  "loginId": "paramedic01",
  "password": "safe-password"
}
```

성공 응답:

```json
{
  "tokenType": "Bearer",
  "accessToken": "JWT_ACCESS_TOKEN",
  "accessTokenExpiresAt": "2026-08-04T09:15:00Z",
  "refreshToken": "OPAQUE_REFRESH_TOKEN",
  "refreshTokenExpiresAt": "2026-08-11T09:00:00Z",
  "accountId": "ACCOUNT_UUID",
  "organizationId": "EMS_ORGANIZATION_UUID",
  "role": "PARAMEDIC"
}
```

| 오류 코드 | HTTP | 조건 | 앱 처리 |
|---|---:|---|---|
| `AUTH_004` | 401 | ID 또는 비밀번호 불일치 | 같은 메시지로 다시 입력 |
| `USER_002` | 403 | 비활성 계정 | 운영 담당자 문의 |

로그인 응답만으로 이름과 연락처를 만들지 말고, 토큰 저장 후 반드시 내 프로필을
조회합니다.

## API 4. 내 프로필 조회

### `GET /api/v1/paramedics/me`

- 인증: `Authorization: Bearer {accessToken}`
- 역할: `PARAMEDIC`
- 성공: `200 OK`
- 계정 ID를 Path·Query로 받지 않고 Access Token의 본인만 조회합니다.

성공 응답:

```json
{
  "accountId": "ACCOUNT_UUID",
  "loginId": "paramedic01",
  "displayName": "김민준",
  "organizationId": "EMS_ORGANIZATION_UUID",
  "organizationName": "강동소방서 3구급대",
  "role": "PARAMEDIC",
  "callbackContact": "010-0000-0000",
  "privacyConsent": {
    "collectionUsePolicyVersion": "COLLECTION_USE_DEV_1.0",
    "hospitalProvisionPolicyVersion": "HOSPITAL_PROVISION_DEV_1.0",
    "consentedAt": "2026-08-04T09:00:00Z",
    "legacyCombined": false
  }
}
```

| 필드 | Nullable | 설명 |
|---|---:|---|
| `accountId` | NO | 계정 공개 ID |
| `loginId` | NO | 설정 화면의 사용자 ID |
| `displayName` | NO | 메인·설정 화면 이름 |
| `organizationId` | NO | 소속 조직 공개 ID |
| `organizationName` | NO | 메인·설정 화면 소속명 |
| `role` | NO | 항상 `PARAMEDIC` |
| `callbackContact` | NO | 이송 요청 시 병원 회신용 연락처 |
| `privacyConsent` | NO | 현재 또는 기존 통합 동의 정보 |
| `legacyCombined` | NO | 기존 통합 동의 계정이면 `true` |

`legacyCombined: true`인 기존 Dev 계정은 두 버전 필드에
`CONTACT_SHARING_DEV_1.0`이 반환될 수 있습니다. 계정 오류가 아니므로 정상 프로필로
처리합니다.

| 오류 코드 | HTTP | 조건 | 앱 처리 |
|---|---:|---|---|
| `AUTH_001` | 401 | Access Token 없음 | 로그인 화면 이동 |
| `AUTH_002` | 401 | 토큰 만료·위조·비활성 계정·현재 계정 불일치 | Refresh Token 갱신 시도 |
| `AUTH_003` | 403 | 구급대원 역할이 아님 | 구급대원 앱 사용 차단 |
| `COMMON_004` | 403 | 토큰과 현재 조직 범위 불일치 | 인증정보 제거 후 다시 로그인 |
| `USER_001` | 404 | 계정 또는 구급대원 프로필 없음 | 운영 담당자 문의 |
| `USER_005` | 409 | 연락처 또는 필요한 동의 이력 없음 | 운영 담당자 문의 |

## API 5. 토큰 갱신

### `POST /api/v1/auth/tokens/refresh`

- 인증 헤더 없이 현재 Refresh Token을 본문으로 보냅니다.
- 성공: `200 OK`

```json
{
  "refreshToken": "CURRENT_REFRESH_TOKEN"
}
```

성공 응답은 로그인 응답과 같습니다. 성공 즉시 Access·Refresh Token을 모두 새
값으로 교체하며 이전 Refresh Token은 다시 사용할 수 없습니다.

| 오류 코드 | HTTP | 조건 | 앱 처리 |
|---|---:|---|---|
| `AUTH_005` | 401 | 만료·폐기·재사용·불일치 Refresh Token | 인증정보 제거 후 로그인 |
| `USER_002` | 403 | 계정 비활성 | 인증정보 제거 후 운영 담당자 문의 |

## 상태와 Enum

| 값 | 의미 | 앱 처리 |
|---|---|---|
| `PARAMEDIC` | 구급대원 개인 계정·가입 코드 | 2단계 가입과 구급대원 화면 허용 |
| `HOSPITAL_STAFF` | 병원 공용 계정·가입 코드 | Flutter 구급대원 가입 중단 |
| `CONTACT_COLLECTION_USE` | 전화번호 수집·이용 동의 | 첫 번째 필수 체크박스 버전 |
| `HOSPITAL_PROVISION` | 요청 수신 병원 제공 동의 | 두 번째 필수 체크박스 버전 |
| `CONTACT_COLLECTION_AND_PROVISION` | 기존 통합 동의 | 신규 구급대원 가입에서는 전송하지 않음 |

## 모바일 상태 복구

| 상황 | 서버 계약 | 앱 처리 |
|---|---|---|
| 코드 확인 통신 실패 | 코드 상태는 변하지 않음 | 같은 원문으로 다시 확인 |
| 코드 확인 후 앱 종료 | 코드는 소비되지 않음 | 메모리 값이 없으므로 1단계에서 다시 입력 |
| 최종 가입 전에 코드 상태 변경 | 가입에서 `INVITATION_002~004` | 1단계로 돌아가 새 코드 확인 |
| 가입 성공 응답 유실 | 계정과 코드 `USED` 상태가 이미 저장될 수 있음 | 입력한 ID로 로그인 시도, 실패하면 운영자 확인 |
| Access Token 만료 | 보호 API `AUTH_002` | Refresh 후 원 요청 한 번 재시도 |
| 토큰 갱신 성공 | 이전 Refresh Token 폐기 | 새 토큰 쌍 저장 후 `/api/v1/paramedics/me` 재조회 |
| 앱 재실행 | 서버 세션 없음 | 안전하게 저장한 토큰 복구 후 내 프로필 조회 |
| 프로필 조회 실패 | 표준 오류 응답 | 토큰·역할·계정 오류별 처리, 로그인 응답만으로 임의 프로필 생성 금지 |

## 실시간 이벤트와 재조회

- 이 기능의 SSE 이벤트: 없음
- 로그인·토큰 갱신·앱 재실행 후 재조회: `GET /api/v1/paramedics/me`

## 연동 확인

- [ ] 목 가입 코드 카드와 자동 입력 제거
- [ ] 가입 코드 대소문자를 변경하지 않음
- [ ] 사용자는 가입 코드를 1단계에서 한 번만 입력
- [ ] 확인 응답의 조직명·역할 표시
- [ ] `PARAMEDIC` 코드만 2단계 진행
- [ ] 이름·ID·비밀번호·전화번호 프론트 검증
- [ ] 필수 동의 2개와 확인 응답의 버전 전송
- [ ] 사용·만료·폐기 코드에서 1단계 복귀
- [ ] 회원가입 후 로그인
- [ ] 로그인·앱 재실행·토큰 갱신 후 내 프로필 조회
- [ ] Access·Refresh Token 원문을 로그에 남기지 않음
- [ ] 실제 개인정보가 아닌 테스트 데이터로 Dev API 연동
