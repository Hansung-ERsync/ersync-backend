# 환자 평가 및 이송 요청 생성 React 병원·관리자 웹 핸드오프

```text
Feature: patient-assessment-transfer-request
Backend Feature: docs/features/03-patient-assessment-transfer-request/
Related PR: NONE
Available After: MAIN_MERGE
Breaking Change: YES
Hospital Impact: YES
Admin Impact: NONE
```

## 변경 요약

- 병원 가입 화면에 기존 응급실 연락처와 함께 연락처 수집·상대방 제공 동의 UI가
  필요합니다.
- 병원 가입 API에 동의 여부와 표시한 동의 문구 버전이 필수로 추가됩니다.
- 이번 기능에는 병원의 환자 요청 조회·응답 API가 없습니다.
- 슈퍼 관리자는 평가 프로토콜과 환자·위치·구급대원 연락처를 조회할 수 없습니다.

## 역할별 사용자 흐름

| 역할 | 순서 | 사용자·웹 동작 | API 호출 | 성공 후 상태 |
|---|---:|---|---|---|
| 병원 관계자 | 1 | 연락처 수집·제공 동의 문구 확인 | 화면 동작 | 동의 체크 가능 |
| 병원 관계자 | 2 | 가입 코드·병원 정보·공용 계정·응급실 연락처 제출 | `POST /api/v1/auth/signups/hospital` | 연락처·동의 이력이 있는 병원 공용 계정, 수신 `OFF` |
| 슈퍼 관리자 | - | 기존 조직·가입 코드 관리 | 기존 API | 변경 없음 |

## 인증과 접근 범위

| 역할 | 인증 | 허용 작업 | 조직·정보 접근 범위 |
|---|---|---|---|
| 병원 관계자 | 가입에는 인증 없음 | 병원 가입 | 가입 코드에 연결된 병원 조직 하나 |
| 슈퍼 관리자 | Bearer JWT | 기존 조직·가입 코드 관리 | 환자 평가·위치·회신 연락처 접근 불가 |

## 공통 계약

- Base URL: `http://13.124.194.249`
- Content-Type: `application/json`
- 연락처 원문과 동의 여부를 브라우저 로그에 출력하지 않습니다.
- 테스트 환경에서는 실제 전화번호 대신 테스트 번호를 사용합니다.

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

### `POST /api/v1/auth/signups/hospital`

- 목적: 병원 공용 계정·응급실 프로필과 연락처 제공 동의 이력 생성
- 인증: 없음
- 성공: `201 Created`
- 호환성: 기존 요청에 필수 필드 2개가 추가되는 breaking change

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
  "contact": "02-1234-5678",
  "contactSharingConsentAccepted": true,
  "contactSharingConsentVersion": "CONTACT_SHARING_DEV_1.0"
}
```

| 필드 | 타입 | 필수 | Nullable | 제약 |
|---|---|---:|---:|---|
| `invitationCode` | string | YES | NO | 미사용·미만료 `HOSPITAL_STAFF` 가입 코드 |
| `organizationName` | string | YES | NO | 코드에 연결된 조직명과 일치, 최대 100자 |
| `loginId` | string | YES | NO | `[a-z0-9]{4,30}` |
| `password` | string | YES | NO | 8~64자 |
| `address` | string | YES | NO | 최대 255자 |
| `latitude` | number | YES | NO | -90~90 |
| `longitude` | number | YES | NO | -180~180 |
| `contact` | string | YES | NO | 앞뒤 공백 제거 후 `[0-9+][0-9-]{7,29}` |
| `contactSharingConsentAccepted` | boolean | YES | NO | 반드시 `true` |
| `contactSharingConsentVersion` | string | YES | NO | 현재 `CONTACT_SHARING_DEV_1.0`과 정확히 일치 |

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

- 성공 응답에는 연락처와 동의 버전·시각이 포함되지 않습니다.
- 서버는 동의 시각을 요청값이 아니라 서버 시각으로 기록합니다.
- 가입 전체가 한 트랜잭션이므로 연락처·동의 검증 실패 시 계정·프로필을 만들지
  않고 가입 코드를 `AVAILABLE`로 유지합니다.

| 오류 | HTTP | 발생 조건 | 웹 처리 |
|---|---:|---|---|
| `COMMON_001` | 400 | 연락처 형식, 동의 누락·거짓·버전 불일치 또는 기존 입력 오류 | 필드별 입력과 최신 동의 문구 확인 |
| `INVITATION_001` | 400 | 가입 코드 검증 불가 | 코드 재확인 |
| `INVITATION_002` | 409 | 만료 코드 | 새 코드 요청 |
| `INVITATION_003` | 409 | 사용된 코드 | 기존 공용 계정 로그인 또는 관리자 확인 |
| `INVITATION_004` | 409 | 폐기된 코드 | 새 코드 요청 |
| `USER_003` | 409 | 로그인 ID 중복 | 다른 ID 입력 |
| `USER_004` | 409 | 병원 공용 계정이 이미 존재 | 기존 공용 계정 사용 |

## 동의 UI 계약

| 항목 | 처리 |
|---|---|
| 표시 문구 버전 | 현재 `CONTACT_SHARING_DEV_1.0` |
| 체크 상태 | 기본 미체크; 사용자가 명시적으로 체크해야 제출 가능 |
| 제출 값 | 체크되면 `contactSharingConsentAccepted: true`와 화면의 버전을 함께 전송 |
| 실패 처리 | `COMMON_001`이면 가입 성공으로 간주하지 않고 입력·동의 상태 유지 |
| 실제 운영 | 법적 동의 문구가 확정되면 백엔드와 같은 새 버전으로 함께 변경 필요 |

## 화면 상태와 기존 기능

| 대상 | 조건 | 웹 처리 |
|---|---|---|
| 가입 제출 버튼 | 필수 입력과 동의 체크 완료 | 활성화 |
| 가입 완료 | HTTP 201 | 로그인 화면 또는 기존 완료 흐름 |
| 병원 수신 ON/OFF | 기존 API | 변경 없음 |
| 환자 요청 목록·응답 | 이번 백엔드 기능에 API 없음 | 아직 연결하지 않음 |
| 관리자 화면 | 신규 환자정보 기능 없음 | 기존 조직·가입 코드 기능만 유지 |

## 접근 차단

- `HOSPITAL_STAFF`와 `SUPER_ADMIN`이
  `GET /api/v1/assessment-protocols/active` 또는
  `POST /api/v1/transport-requests`를 호출하면 `AUTH_003`입니다.
- 이번 기능에서 구급대원 회신 연락처를 웹에 반환하는 API는 없습니다.
- 병원별 요청 전달과 상태에 따른 연락처 원문·마스킹은 후속 병원 응답 기능에서
  별도 계약으로 제공합니다.

## 실시간 이벤트와 재조회

- 이벤트: 없음
- 환자 요청 목록·재조회 API: 없음
- 중복 클릭·멱등성 계약: 병원 가입에는 기존 가입 코드 단일 소비 정책 적용

## 연동 확인

- [ ] 동의 UI 기본 미체크와 필수 처리
- [ ] 연락처·동의 필드가 포함된 병원 가입 201
- [ ] 동의 false·버전 불일치 `COMMON_001`
- [ ] 가입 실패 후 같은 코드 재사용 가능
- [ ] 가입 성공 후 수신 상태 `OFF`
- [ ] 기존 로그인·수신 ON/OFF 회귀 확인
- [ ] 실제 개인번호가 아닌 테스트 연락처로 dev API 연결
