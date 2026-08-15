# 병원 상세주소 React 병원·관리자 웹 핸드오프

```text
Feature: hospital-detail-address
Backend Feature: docs/features/19-hospital-detail-address/
Related PR: NONE
Available After: MAIN_MERGE
Breaking Change: NO
Hospital Impact: YES
Admin Impact: NONE
```

## 변경 요약

- 병원 가입 요청에 선택 필드 `detailAddress`가 추가됩니다.
- 자기 병원 프로필 응답에도 nullable `detailAddress`가 추가됩니다.
- 기존 병원 정보 수정 API는 없습니다.
- 슈퍼 관리자 API와 화면 계약은 변경되지 않습니다.

## 사용자 흐름

| 순서 | 병원 웹 동작 | API | 결과 |
|---:|---|---|---|
| 1 | 기본주소와 선택 상세주소 입력 | `POST /api/v1/auth/signups/hospital` | 병원 프로필 생성, 수신 `OFF` |
| 2 | 로그인 후 자기 정보 조회 | `GET /api/v1/hospitals/me` | 저장된 기본주소·상세주소 표시 |

## 병원 가입

### `POST /api/v1/auth/signups/hospital`

- 인증: 없음. 유효한 병원용 가입 코드가 필요합니다.
- Content-Type: `application/json`
- 성공: `201 Created`

```json
{
  "invitationCode": "A1B2C3D4",
  "organizationName": "한성대학교병원",
  "loginId": "hospital01",
  "password": "safe-password",
  "address": "서울특별시 성북구 삼선교로16길 116",
  "detailAddress": "본관 1층 응급의료센터",
  "latitude": 37.5821,
  "longitude": 127.0105,
  "contact": "02-1234-5678",
  "contactSharingConsentAccepted": true,
  "contactSharingConsentVersion": "CONTACT_SHARING_DEV_1.0"
}
```

| 필드 | 타입 | 필수 | Nullable | 제약 |
|---|---|---:|---:|---|
| `address` | string | YES | NO | 공백 제외, 최대 255자 |
| `detailAddress` | string | NO | YES | 최대 200자, 앞뒤 공백 제거, 공백만 입력하면 `null` |
| `latitude` | number | YES | NO | -90~90 |
| `longitude` | number | YES | NO | -180~180 |

나머지 가입 필드와 응답은 기존 계약을 유지합니다. 상세주소 입력이 비어 있으면
필드를 생략하거나 `null`로 보내는 방식을 권장합니다.

## 자기 병원 프로필

### `GET /api/v1/hospitals/me`

- 인증: `Authorization: Bearer {accessToken}`
- 역할: 활성 `HOSPITAL_STAFF`
- 성공: `200 OK`
- 접근 범위: 로그인한 계정에 연결된 자기 병원 한 건

```json
{
  "accountId": "ACCOUNT_UUID",
  "loginId": "hospital01",
  "role": "HOSPITAL_STAFF",
  "organizationId": "ORGANIZATION_UUID",
  "organizationName": "한성대학교병원",
  "hospitalId": "HOSPITAL_UUID",
  "address": "서울특별시 성북구 삼선교로16길 116",
  "detailAddress": "본관 1층 응급의료센터",
  "latitude": 37.5821,
  "longitude": 127.0105,
  "contact": "02-1234-5678",
  "receivingStatus": "OFF",
  "updatedAt": "2026-08-15T08:00:00Z"
}
```

기존 병원은 `detailAddress`가 `null`일 수 있습니다. 화면에서는 기본주소만
표시하고 빈 줄이나 `null` 문자열을 출력하지 않습니다.

## 오류 처리

| 오류 코드 | HTTP | 조건 | 웹 처리 |
|---|---:|---|---|
| `COMMON_001` | 400 | 상세주소 200자 초과 또는 기존 입력 검증 실패 | 해당 입력 오류 표시 |
| `INVITATION_001`~`INVITATION_004` | 400/404/409 | 코드 오류·만료·사용·폐기 | 가입 코드 재확인 |
| `AUTH_001`, `AUTH_002` | 401 | 프로필 조회 인증 실패 | 토큰 갱신 또는 로그인 |
| `AUTH_003` | 403 | 병원 역할 아님 | 병원 화면 접근 차단 |
| `HOSPITAL_001` | 404 | 자기 병원 프로필 없음 | 운영 담당자 확인 |

공통 오류 응답과 `X-Trace-Id` 계약은 기존과 같습니다.

## 연동 확인

- [ ] 상세주소 입력값이 가입 요청에 포함됨
- [ ] 상세주소 생략·공백 입력 가입 성공
- [ ] 200자 초과 시 입력 오류 표시
- [ ] 자기 프로필의 nullable 상세주소 표시
- [ ] 기존 가입·로그인·수신 상태 기능 회귀 없음
