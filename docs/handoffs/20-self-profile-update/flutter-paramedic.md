# 구급대원 자기 프로필 수정 Flutter 핸드오프

```text
Feature: 20-self-profile-update
Backend Feature: docs/features/20-self-profile-update/
Related PR: NONE
Available After: MAIN_MERGE
Breaking Change: NO
```

## 변경 요약

- 새로 가능해진 구급대원 동작: 설정 화면에서 자기 표시 이름과 병원 회신 연락처를 함께 수정할 수 있습니다.
- 기존 앱 영향: 기존 `GET /api/v1/paramedics/me` 계약은 유지되고 같은 경로의 `PUT`만 추가됩니다.
- 로그인 ID, 역할, 소속 조직과 기존 연락처 동의는 수정되지 않습니다.
- 연락처 변경 전에 생성된 이송 요청은 당시 연락처를 유지하고, 변경 후 새로 생성한 이송 요청부터 새 연락처를 사용합니다.

## 사용자 흐름

| 순서 | 사용자·앱 동작 | API 호출 | 성공 후 상태 |
|---:|---|---|---|
| 1 | 설정 화면 진입 | `GET /api/v1/paramedics/me` | 서버의 최신 이름·회신 연락처·동의 표시 |
| 2 | 이름과 연락처 편집 | 호출 없음 | 로컬 입력값만 변경 |
| 3 | 저장 | `PUT /api/v1/paramedics/me` | HTTP 200 응답 전체를 화면의 최신 프로필로 교체 |
| 4 | 이후 새 이송 요청 생성 | 기존 이송 요청 생성 API | 서버가 수정된 프로필 연락처를 새 요청에 스냅샷 |

## 인증과 접근 범위

| 항목 | 계약 |
|---|---|
| 인증 | `Authorization: Bearer {accessToken}` |
| 역할 | 활성 `PARAMEDIC`만 허용 |
| 조직·소유권 | Access Token의 계정·EMS 조직과 DB 프로필을 대조하며 본인 프로필 한 건만 조회·수정 |

- 계정·조직·프로필 ID를 Path, Query 또는 요청 본문으로 보내지 않습니다.
- 병원 계정과 슈퍼 관리자 토큰은 사용할 수 없습니다.

## API 1. 자기 프로필 조회

### `GET /api/v1/paramedics/me`

- 목적: 설정 화면 진입·앱 재실행 뒤 서버의 최신 프로필 복구
- 인증·역할: Bearer Access Token, `PARAMEDIC`
- 성공 HTTP: `200 OK`
- 요청 본문: 없음

#### 성공 응답

```json
{
  "accountId": "ACCOUNT_UUID",
  "loginId": "paramedic01",
  "displayName": "김민준",
  "organizationId": "EMS_ORGANIZATION_UUID",
  "organizationName": "강동소방서 3구급대",
  "role": "PARAMEDIC",
  "callbackContact": "010-1234-5678",
  "privacyConsent": {
    "collectionUsePolicyVersion": "COLLECTION_USE_DEV_1.0",
    "hospitalProvisionPolicyVersion": "HOSPITAL_PROVISION_DEV_1.0",
    "consentedAt": "2026-08-04T09:00:00Z",
    "legacyCombined": false
  }
}
```

## API 2. 자기 프로필 수정

### `PUT /api/v1/paramedics/me`

- 목적: 표시 이름과 병원 회신 연락처 전체 수정
- 인증·역할: Bearer Access Token, `PARAMEDIC`
- 성공 HTTP: `200 OK`
- Content-Type: `application/json`
- `Idempotency-Key`: 필요 없음

#### 요청

```json
{
  "displayName": "김민준",
  "callbackContact": "010-1234-5678"
}
```

| 필드 | 타입 | 필수 | Nullable | 제약 |
|---|---|---:|---:|---|
| `displayName` | string | O | X | 앞뒤 공백 제거 후 2~50자, 제어문자 금지 |
| `callbackContact` | string | O | X | 앞뒤 공백 제거, 8~30자, 첫 글자는 숫자 또는 `+`, 이후 숫자·`-`만 허용 |

- 두 필드를 항상 함께 전송합니다. 한 필드만 보내는 부분 수정은 지원하지 않습니다.
- 사용자가 한 필드만 바꿨다면 다른 필드는 직전 `GET` 값으로 함께 전송합니다.
- 기존 동의 목적 안에서 연락처 값만 최신화하므로 동의 UI를 다시 표시하거나 새 동의 버전을 보낼 필요가 없습니다.

#### 성공 응답

- `GET /api/v1/paramedics/me`와 같은 전체 응답을 반환합니다.
- 응답의 `displayName`, `callbackContact`는 정규화·저장된 값입니다.
- `accountId`, `loginId`, 조직·역할·`privacyConsent`는 기존 값을 유지합니다.
- 앱은 요청값을 그대로 확정하지 말고 HTTP 200 응답 전체를 최신 프로필로 사용합니다.

#### 오류

| 오류 코드 | HTTP | 발생 조건 | 재시도 | 앱에서 필요한 처리 |
|---|---:|---|---|---|
| `COMMON_001` | 400 | 필수값 누락, 이름 길이·제어문자, 연락처 형식 오류 | 입력 수정 후 가능 | 해당 입력값 안내 |
| `AUTH_001` | 401 | Access Token 없음 | 로그인 후 가능 | 로그인 화면 이동 |
| `AUTH_002` | 401 | 만료·위조 토큰 또는 비활성 계정 | 토큰 갱신 1회 | 실패하면 인증정보 제거 후 로그인 |
| `AUTH_003` | 403 | 구급대원 역할이 아님 | 불가 | 구급대원 앱 사용 차단 |
| `COMMON_004` | 403 | 토큰·계정·조직·프로필 연결 불일치 | 임의 재시도 금지 | 인증정보 제거 후 운영 담당자 확인 |
| `USER_001` | 404 | 본인 구급대원 프로필 없음 | 임의 재시도 금지 | 운영 담당자 확인 |
| `USER_005` | 409 | 필요한 기존 연락처 동의 없음 | 임의 재시도 금지 | 프로필은 바뀌지 않으므로 운영 담당자 확인 |

- 실패하면 이름과 연락처 어느 것도 저장되지 않습니다.

## 기존 이송 요청 적용 시점

| 대상 | 연락처 |
|---|---|
| 수정 전에 이미 생성된 이송 요청 | 요청 생성 당시 연락처 유지 |
| 수정 후 새로 생성하는 이송 요청 | 수정된 최신 연락처 사용 |

- 진행 중 요청의 병원 회신 연락처를 바꾸기 위한 API가 아닙니다.
- 기존 이송을 취소하거나 다시 생성하지 않습니다.

## 오류 처리

공통 오류 응답:

```json
{
  "code": "COMMON_001",
  "message": "요청값 검증에 실패했습니다.",
  "fieldErrors": [],
  "traceId": "TRACE_ID"
}
```

- 오류 문의 시 API 경로, HTTP 상태, `code`, `traceId`를 전달합니다.
- Access·Refresh Token과 실제 전화번호 원문은 메신저·노션·로그에 공유하지 않습니다.

## 모바일 상태 복구

| 상황 | 서버 계약 | 앱에서 필요한 처리 |
|---|---|---|
| 통신 실패·응답 불명 | 같은 전체 `PUT`을 재전송해도 프로필 행이나 이송 요청이 중복 생성되지 않음 | 저장 성공으로 추정하지 말고 재시도하거나 `GET`으로 확인 |
| 저장 성공 | 전체 최신 프로필 반환 | HTTP 200 응답으로 설정·메인 화면 이름과 연락처 갱신 |
| 앱 재실행 | 서버 프로필이 기준 | 토큰 복구 후 `GET /api/v1/paramedics/me` 재조회 |
| 입력 검증 실패 | 프로필 전체 미변경 | 기존 서버값은 유지하고 해당 입력만 수정하도록 안내 |

## 실시간 이벤트와 재조회

- 이벤트: 없음
- 재연결 후 재조회 API: `GET /api/v1/paramedics/me`
- 프로필 수정은 기존 이송 SSE를 발생시키지 않습니다.

## 연동 확인

- [ ] 설정 진입 시 최신 프로필 조회
- [ ] 이름·연락처 전체 수정과 성공 응답 반영
- [ ] 잘못된 이름·연락처 입력 오류 표시
- [ ] 인증 만료 후 토큰 갱신과 재조회
- [ ] 통신 실패 뒤 `PUT` 재시도 또는 `GET` 확인
- [ ] 기존 이송과 새 이송의 연락처 적용 시점 확인
- [ ] Dev API 연결
