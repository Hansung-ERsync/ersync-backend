# 병원 프로필·응급실 수신 상태 프론트엔드 연동 계약

```text
Feature: hospital-profile-receiving-state
Backend Feature: docs/features/03-hospital-profile-receiving-state/
Available After: MAIN_MERGE
Breaking Change: NO
Updated: 2026-07-28
```

## 변경 요약

- 추가된 사용자 동작: 병원 공용 계정의 응급실 프로필 등록·조회, 응급실 수신 ON/OFF 변경
- 기존 프론트 영향: 병원 웹은 로그인 후 프로필이 없으면 프로필 등록 화면으로 유도해야 함

## API

공통:

- Content-Type: `application/json`
- 인증: `Authorization: Bearer {accessToken}`
- 시간: ISO-8601 UTC

| 행위 | Method | Path | 성공 HTTP | 인증 역할 |
|---|---|---|---:|---|
| 내 병원 프로필 등록·수정 | PUT | `/api/v1/hospital/profile` | 200 | `HOSPITAL_STAFF` |
| 내 병원 프로필 조회 | GET | `/api/v1/hospital/profile` | 200 | `HOSPITAL_STAFF` |
| 수신 상태 변경 | PUT | `/api/v1/hospital/receiving-status` | 200 | `HOSPITAL_STAFF` |

## 프로필 등록·수정

요청:

```json
{
  "erAddress": "Seoul Mapo Emergency Center",
  "latitude": 37.55,
  "longitude": 126.91,
  "erContact": "02-1234-5678"
}
```

| 필드 | 타입 | 필수 | Nullable | 제약 |
|---|---|---:|---:|---|
| `erAddress` | string | yes | no | 255자 이하 |
| `latitude` | number | yes | no | -90 이상 90 이하 |
| `longitude` | number | yes | no | -180 이상 180 이하 |
| `erContact` | string | yes | no | 40자 이하 |

성공 응답:

```json
{
  "organizationId": "uuid",
  "organizationName": "Hospital Gamma",
  "erAddress": "Seoul Mapo Emergency Center",
  "latitude": 37.55,
  "longitude": 126.91,
  "erContact": "02-1234-5678",
  "receivingStatus": "OFF",
  "locationVerifiedAt": "2026-07-28T14:54:32Z",
  "updatedAt": "2026-07-28T14:54:32Z",
  "version": 0
}
```

## 수신 상태 변경

요청:

```json
{"status":"ON"}
```

성공 응답은 병원 프로필 응답과 같습니다.

## 상태와 Enum

| 값 | 의미 | 프론트 처리 |
|---|---|---|
| `ON` | 새 이송 요청 후보에 포함 가능 | 병원 화면에 수신 중 표시 |
| `OFF` | 새 이송 요청 후보에서 제외 | 병원 화면에 수신 중지 표시 |

## 오류 처리

| 오류 코드 | HTTP | 발생 조건 | 프론트 처리 |
|---|---:|---|---|
| `COMMON_001` | 400 | 필수값 누락, 좌표 범위 오류, enum 오류 | 입력값 수정 안내 |
| `AUTH_001` | 401 | 인증 누락 | 로그인 화면 이동 |
| `AUTH_002` | 401 | 토큰 만료·위조 | 재로그인 |
| `AUTH_003` | 403 | 병원 계정이 아닌 사용자 | 접근 불가 표시 |
| `ORGANIZATION_001` | 404 | 계정 조직 없음 | 재로그인 또는 관리자 문의 |
| `HOSPITAL_001` | 404 | 병원 프로필 없음 | 프로필 등록 화면 표시 |
| `HOSPITAL_002` | 409 | 병원 조직이 아니거나 비활성 | 관리자 문의 |

## 실시간 이벤트

- 없음

## 프론트엔드 처리 순서

1. 병원 로그인 후 `GET /api/v1/hospital/profile`을 호출합니다.
2. `HOSPITAL_001`이면 프로필 등록 화면을 보여줍니다.
3. 프로필 등록 성공 직후 기본 상태는 `OFF`이므로, 병원 사용자가 확인 후 `ON`으로 변경하게 합니다.
4. 수신 상태 토글은 `PUT /api/v1/hospital/receiving-status`로 처리합니다.
5. `OFF` 전환은 수락 철회가 아니므로 기존 수락·이송 중 요청을 닫는 화면 처리를 하지 않습니다.

## 호환성

- 기존 API 영향: 없음
- 필수 동시 배포: 병원 프로필 화면 사용 시 필요
- 중복 요청·멱등성: 프로필 등록 API는 같은 병원 조직에 대해 upsert로 동작
- 페이징·정렬: 해당 없음

## 연동 확인

- [x] 정상 요청과 응답
- [x] 권한 없는 요청
- [x] 주요 오류별 화면 처리
- [x] 상태 또는 실시간 갱신
- [x] dev API 연결은 main 병합·배포 후 확인

## 미결정 사항

- 없음
