# 8자리 가입 코드 React 병원·관리자 웹 핸드오프

```text
Feature: eight-character-invitation-code
Backend Feature: docs/features/14-eight-character-invitation-code/
Related PR: NONE
Available After: MAIN_MERGE
Breaking Change: NO
Hospital Impact: YES
Admin Impact: YES
```

> 슈퍼 관리자 발급 화면에는 새 8자리 코드 원문을 한 번만 표시하고, 병원
> 회원가입 화면은 신규 8자리와 기존 긴 코드를 모두 입력할 수 있어야 합니다.

## 변경 요약

- 관리자 발급 API의 신규 `code`가 정확히 `[A-Za-z0-9_-]{8}`입니다.
- API 경로·요청 DTO·응답 구조·오류 코드는 변경하지 않았습니다.
- 관리자 목록의 `invitationCodeId`는 기존 UUID이며 8자리 코드 원문이 아닙니다.
- 기존 약 43자리 가입 코드도 상태와 유효기간이 허용하면 계속 사용할 수 있습니다.
- 가입 코드는 대소문자를 구분합니다.

## 관리자 사용자 흐름

| 순서 | 웹 동작 | API | 결과 |
|---:|---|---|---|
| 1 | 조직·역할·유효기간 선택 | 화면 입력 | 기존 계약 유지 |
| 2 | 가입 코드 발급 | `POST /api/v1/admin/invitation-codes` | `201`, 8자리 `code`를 한 번 반환 |
| 3 | 코드 표시·복사 | 발급 성공 화면 | 대소문자·기호를 그대로 전달 |
| 4 | 목록·폐기 | 목록·폐기 API | 관리용 `invitationCodeId` 사용, 원문 재조회 불가 |

## API 1. 관리자 가입 코드 발급

### `POST /api/v1/admin/invitation-codes`

기존 요청 예시:

```json
{
  "organizationId": "organization-uuid",
  "role": "HOSPITAL_STAFF",
  "expiryOption": "THREE_DAYS"
}
```

성공 응답의 변경된 값:

```json
{
  "code": "Ab12_-Z9",
  "invitation": {
    "invitationCodeId": "management-uuid",
    "status": "AVAILABLE"
  }
}
```

- `code`: 사용자에게 한 번 전달할 8자리 원문
- `invitation.invitationCodeId`: 목록·폐기에 사용하는 관리용 UUID
- 두 값을 서로 바꾸어 사용하면 안 됩니다.
- 새 원문을 발급 성공 모달에서 한 번 표시하고 복사 기능을 제공합니다.
- 새로고침·목록 재조회로 원문을 다시 받을 수 없습니다.
- 코드 원문을 브라우저 로그·분석 이벤트·오류 수집 도구에 전송하지 않습니다.

## API 2. 관리자 목록·폐기

```text
GET /api/v1/admin/invitation-codes
POST /api/v1/admin/invitation-codes/{invitationCodeId}/revoke
```

- 목록에는 기존처럼 코드 원문과 digest가 없습니다.
- 폐기 Path에는 8자리 `code`가 아니라 목록에서 받은 `invitationCodeId`를 넣습니다.
- 상태·필터·페이지·만료·폐기 계약은 변경하지 않았습니다.

## 병원 회원가입 흐름

```text
POST /api/v1/auth/invitations/validate
POST /api/v1/auth/signups/hospital
```

- 신규 8자리 코드와 배포 전에 발급된 기존 긴 코드를 모두 허용합니다.
- 전환 기간에는 입력창을 정확히 8자로만 제한하지 않습니다.
- 입력값을 대문자나 소문자로 자동 변환하지 않습니다.
- `-`, `_`를 제거하거나 바꾸지 않습니다.
- 사전 확인한 원문을 병원 회원가입 요청에도 그대로 보냅니다.
- 조직·병원 정보·연락처·동의·로그인 계약은 변경하지 않았습니다.

## 오류 처리

| 코드 | HTTP | 조건 | 웹 처리 |
|---|---:|---|---|
| `INVITATION_001` | 400 | 존재하지 않음, 오타, 대소문자 변경 | 원문 재확인 |
| `INVITATION_002` | 409 | 만료 | 새 코드 발급 |
| `INVITATION_003` | 409 | 이미 사용 | 새 코드 발급 또는 기존 계정 확인 |
| `INVITATION_004` | 409 | 폐기 | 새 코드 발급 |
| `INVITATION_005` | 409 | 사용·만료된 코드 폐기 시도 | 목록 재조회 |
| `AUTH_003` | 403 | 비관리자의 관리 API 호출 | 관리 화면 접근 차단 |

## 연동 확인

- [ ] 관리자 발급 성공 모달에 정확한 8자리 원문 표시·복사
- [ ] 목록에서 원문 재조회를 기대하지 않음
- [ ] `code`와 `invitationCodeId`를 구분
- [ ] 병원 가입 입력은 기존 긴 코드도 허용
- [ ] 대소문자 자동 변환과 기호 제거 없음
- [ ] 코드 원문을 로그·분석·오류 수집에 남기지 않음
- [ ] Refresh Token UI·저장 계약을 8자리로 바꾸지 않음
