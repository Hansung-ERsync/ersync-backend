# 병원 내 정보·수신 상태 조회 React 병원·관리자 웹 핸드오프

```text
Feature: hospital-profile-receiving-status
Backend Feature: docs/features/09-hospital-profile-receiving-status/
Related PR: NONE
Available After: MAIN_MERGE
Breaking Change: NO
Hospital Impact: YES
Admin Impact: NONE
```

> 병원 공용 계정이 로그인·새로고침 뒤 자기 병원 정보와 서버에 저장된 실제
> 신규 요청 수신 상태를 조회할 수 있습니다. 기존 병원 가입·로그인·수신 상태
> 변경 계약은 그대로 유지됩니다.

## 변경 요약

- 새로 가능해진 병원 동작: 자기 계정·조직·응급실 정보와 실제 수신 `ON/OFF`를 한 번에 조회
- 새로 가능해진 관리자 동작: 없음
- 기존 웹 영향:
  - 기존 `PUT /api/v1/hospitals/me/receiving-status`는 변경되지 않습니다.
  - 브라우저에 저장한 마지막 값이 아니라 GET 응답의 `receivingStatus`를 현재 서버 상태로 사용합니다.
  - 로그인·새로고침·다른 브라우저 접속 뒤에도 같은 서버 상태를 복구할 수 있습니다.

## 역할별 사용자 흐름

| 역할 | 순서 | 사용자·웹 동작 | API 호출 | 성공 후 상태 |
|---|---:|---|---|---|
| 병원 | 1 | 병원 공용 계정 로그인·세션 복구 | 기존 로그인·토큰 갱신 | `HOSPITAL_STAFF` Access Token 확보 |
| 병원 | 2 | 대시보드 또는 계정 정보 화면 진입 | `GET /api/v1/hospitals/me` | 병원 정보와 서버 수신 상태 표시 |
| 병원 | 3 | 신규 요청 수신을 ON 또는 OFF로 변경 | `PUT /api/v1/hospitals/me/receiving-status` | 응답의 `status`로 즉시 화면 갱신 |
| 병원 | 4 | 새로고침·재로그인·다른 브라우저 접속 | `GET /api/v1/hospitals/me` | DB의 최신 `receivingStatus`로 다시 복구 |
| 관리자 | - | 변경 없음 | 없음 | 병원 본인 프로필 조회 불가 |

## 인증과 접근 범위

| 역할 | 인증 | 허용 작업 | 조직·정보 접근 범위 |
|---|---|---|---|
| 병원 관계자 | Bearer Access Token, 활성 `HOSPITAL_STAFF` | 자기 병원 프로필 조회, 자기 병원 수신 상태 변경 | JWT 계정·조직과 DB에 연결된 자기 병원 한 건만 |
| 슈퍼 관리자 | Bearer Access Token | 이 기능의 병원 API 사용 불가 | 병원 공용 계정 프로필 조회 불가 |

- API는 병원·계정·조직 ID를 Path·Query·Body로 받지 않습니다.
- 서버가 JWT 계정·역할·조직을 현재 DB와 다시 대조합니다.
- 환자 임상정보, 구급대원 연락처와 위치정보는 이 응답에 없습니다.
- 비밀번호·해시, Access·Refresh Token과 가입 코드도 반환하지 않습니다.

## 공통 계약

- Base URL: `http://13.124.194.249`
- Content-Type: `application/json`
- 보호 API 헤더: `Authorization: Bearer {accessToken}`
- 시간: ISO-8601 UTC
- 현재 Dev 서버는 HTTP이므로 실제 개인정보가 아닌 테스트 데이터만 사용합니다.

공통 오류 응답:

```json
{
  "code": "ERROR_CODE",
  "message": "사용자에게 표시 가능한 메시지",
  "fieldErrors": [],
  "traceId": "TRACE_ID"
}
```

## API 1. 자기 병원 프로필 조회

### `GET /api/v1/hospitals/me`

- 목적: 로그인한 병원 공용 계정의 계정·조직·응급실 정보와 실제 수신 상태 복구
- 인증·역할: Bearer Access Token, `HOSPITAL_STAFF`
- 조직·정보 접근 범위: Access Token에 연결된 자기 병원 프로필 한 건
- 성공 HTTP: `200 OK`
- Path·Query·요청 본문: 없음
- 읽기만 수행하며 수신 상태와 감사 기록을 변경하지 않습니다.

#### 성공 응답

```json
{
  "accountId": "ACCOUNT_UUID",
  "loginId": "hospital01",
  "role": "HOSPITAL_STAFF",
  "organizationId": "ORGANIZATION_UUID",
  "organizationName": "한성대학교병원",
  "hospitalId": "HOSPITAL_UUID",
  "address": "서울특별시 성북구 삼선교로 16길",
  "latitude": 37.5821000,
  "longitude": 127.0105000,
  "contact": "02-1234-5678",
  "receivingStatus": "ON",
  "updatedAt": "2026-08-05T01:00:00Z"
}
```

| 필드 | 타입 | Nullable | 설명 |
|---|---|---:|---|
| `accountId` | string(UUID) | X | 병원 공용 계정 공개 ID |
| `loginId` | string | X | 로그인 ID |
| `role` | enum | X | 항상 `HOSPITAL_STAFF` |
| `organizationId` | string(UUID) | X | 병원 조직 공개 ID |
| `organizationName` | string | X | 병원 조직명 |
| `hospitalId` | string(UUID) | X | 응급실 프로필 공개 ID |
| `address` | string | X | 가입 때 등록한 응급실 주소 |
| `latitude` | number | X | 가입 때 확인한 응급실 위도 |
| `longitude` | number | X | 가입 때 확인한 응급실 경도 |
| `contact` | string | X | 응급실 연락처 |
| `receivingStatus` | enum | X | 서버에 저장된 실제 `ON` 또는 `OFF` |
| `updatedAt` | string(datetime) | X | 병원 프로필 최종 변경 서버 시각 |

#### 오류

| 오류 코드 | HTTP | 발생 조건 | 웹에서 필요한 처리 |
|---|---:|---|---|
| `AUTH_001` | 401 | Access Token 없음 | 로그인 화면 이동 |
| `AUTH_002` | 401 | 만료·위조 토큰 또는 비활성 계정 | Refresh Token 갱신 후 GET 한 번 재시도 |
| `AUTH_003` | 403 | 병원 관계자 역할이 아님 | 병원 웹 사용 차단 |
| `USER_002` | 403 | 현재 계정이 비활성 | 인증정보 제거 후 운영 담당자 확인 |
| `COMMON_004` | 403 | JWT·계정·조직·프로필 연결 불일치 | 인증정보 제거 후 다시 로그인, 계속되면 운영 담당자 확인 |
| `HOSPITAL_001` | 404 | 병원 계정에 응급실 프로필이 없음 | 운영 담당자 확인 |

## API 2. 자기 병원 수신 상태 변경

### `PUT /api/v1/hospitals/me/receiving-status`

- 기존 API 계약이며 변경 사항이 없습니다.
- 인증·역할: Bearer Access Token, `HOSPITAL_STAFF`
- 성공 HTTP: `200 OK`

#### 요청

```json
{
  "status": "ON"
}
```

| 필드 | 타입 | 필수 | Nullable | 제약 |
|---|---|---:|---:|---|
| `status` | enum | O | X | `ON`, `OFF` |

#### 성공 응답

```json
{
  "hospitalId": "HOSPITAL_UUID",
  "organizationId": "ORGANIZATION_UUID",
  "status": "ON",
  "updatedAt": "2026-08-05T01:00:00Z"
}
```

| 필드 | 타입 | Nullable | 설명 |
|---|---|---:|---|
| `hospitalId` | string(UUID) | X | 변경한 응급실 프로필 공개 ID |
| `organizationId` | string(UUID) | X | 자기 병원 조직 공개 ID |
| `status` | enum | X | 변경된 `ON` 또는 `OFF` |
| `updatedAt` | string(datetime) | X | 변경 서버 시각 |

#### 오류

| 오류 코드 | HTTP | 발생 조건 | 웹에서 필요한 처리 |
|---|---:|---|---|
| `COMMON_001` | 400 | `status` 누락 또는 지원하지 않는 값 | 선택값 확인 |
| `AUTH_001`, `AUTH_002` | 401 | 인증 없음·토큰 오류 | 토큰 갱신 또는 로그인 |
| `AUTH_003`, `COMMON_004`, `USER_002` | 403 | 역할·조직·계정 상태 오류 | 접근 차단·운영 담당자 확인 |
| `HOSPITAL_001` | 404 | 자기 병원 프로필 없음 | 운영 담당자 확인 |

## 화면 상태 조건

| 대상 | 조건 | 웹에서 필요한 처리 |
|---|---|---|
| 최초 프로필 조회 중 | GET 응답 전 | 이전 브라우저 저장값을 실제 상태로 확정하지 않고 로딩 또는 확인 중 표시 |
| 신규 요청 수신 중 | `receivingStatus: ON` | 신규 요청 수신 상태 표시 |
| 신규 요청 수신 중지 | `receivingStatus: OFF` | 신규 후보 제외 상태 표시 |
| 상태 변경 성공 | PUT 응답의 `status` 수신 | 응답값으로 즉시 표시 갱신 |
| 여러 화면의 동시 상태 변경 | 두 PUT 모두 성공 가능 | 각 응답은 해당 요청의 처리 결과이며 최종 공용 상태가 필요하면 GET으로 재조회 |
| 초기 GET과 사용자 PUT 응답 순서가 뒤바뀜 | 둘 다 정상 응답 가능 | 늦게 도착한 이전 GET이 PUT 성공 상태를 덮지 않도록 요청 순서를 관리하거나 GET 재조회 |
| 새로고침·재로그인 | GET 성공 | GET의 `receivingStatus`로 화면 복구 |
| GET 실패 | 인증·서버 오류 | 임의의 `ON/OFF`로 덮지 않고 오류 유형에 맞게 복구 |
| 계정 정보 화면 | GET 성공 | 조직명·로그인 ID·주소·연락처 등 필요한 필드 표시 |

`OFF`는 새 병원 요청 후보에서만 제외합니다. 이미 수락했거나 이동 중인 요청을
철회하거나 숨기는 명령이 아닙니다.

## 상태와 Enum

| 값 | 의미 | 웹에서 필요한 처리 |
|---|---|---|
| `HOSPITAL_STAFF` | 병원 응급실 공용 계정 | 병원 화면 허용 |
| `ON` | 새 이송 요청 후보에 포함 | 신규 요청 수신 중 표시 |
| `OFF` | 새 이송 요청 후보에서 제외 | 수신 일시 중지 표시, 기존 진행 요청 유지 |

## 인증 복구

| 상황 | 서버 계약 | 웹에서 필요한 처리 |
|---|---|---|
| Access Token 만료 | 보호 API에서 `AUTH_002` | Refresh 후 원 GET 또는 PUT 한 번 재시도 |
| Refresh 성공 | Access·Refresh Token 모두 교체 | 새 토큰으로 자기 병원 GET 재조회 |
| Refresh 실패 | `AUTH_005` | 인증정보 제거 후 로그인 이동 |
| 다른 브라우저 접속 | 브라우저 저장값과 무관한 DB 조회 | 로그인 뒤 GET으로 실제 상태 구성 |
| PUT 응답 유실 | 서버 상태 변경 여부를 클라이언트가 확정할 수 없음 | GET으로 현재 상태 재조회 |

## 실시간 이벤트와 재조회

- 이 기능의 SSE 이벤트: 없음
- 로그인·새로고침·토큰 갱신 뒤 재조회: `GET /api/v1/hospitals/me`
- PUT 응답 유실 또는 상태가 불명확할 때 재조회: `GET /api/v1/hospitals/me`
- 중복 클릭·동시 변경: 같은 병원 행의 PUT은 서버에서 순서대로 처리되며, 동일 값과 반대 값 동시 요청 모두 영속성 충돌 없이 완료됩니다.
- 최종 공용 상태: 여러 브라우저의 응답 도착 순서는 다를 수 있으므로 상태가 불명확하면 GET 결과를 기준으로 표시합니다.

## 관리자 접근

- `SUPER_ADMIN`이 두 병원 API를 호출하면 `AUTH_003`입니다.
- 이 기능으로 변경되는 관리자 API나 관리자 화면은 없습니다.
- 관리자에게 환자정보·병원 공용 계정 자격정보가 추가로 노출되지 않습니다.

## 연동 확인

- [ ] 병원 로그인 직후 자기 병원 GET 성공
- [ ] 조직명·로그인 ID·주소·좌표·연락처 표시
- [ ] 가입 직후 `receivingStatus: OFF` 표시
- [ ] PUT으로 `ON` 변경 후 즉시 `status: ON` 표시
- [ ] 새로고침·재로그인·다른 브라우저에서 GET으로 `ON` 복구
- [ ] 브라우저 저장값을 서버의 실제 수신 상태로 사용하지 않음
- [ ] PUT 응답 유실 시 GET으로 현재 상태 복구
- [ ] 같은 계정의 두 화면에서 동시에 상태를 바꾼 뒤 GET 결과로 두 화면 상태 일치
- [ ] 초기 GET 처리 중 토글을 비활성화하거나, 늦게 도착한 초기 GET이 PUT 성공 상태를 덮지 않게 처리
- [ ] 미인증·구급대원·관리자·조직 불일치 접근 차단
- [ ] 비밀번호·토큰·가입 코드가 화면·로그·오류 문의에 포함되지 않음
- [ ] 실제 개인정보가 아닌 테스트 데이터로 Dev API 연동
