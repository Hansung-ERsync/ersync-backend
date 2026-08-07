# 조건부 추가 환자 평가 Flutter 구급대원 앱 핸드오프

```text
Feature: supplemental-patient-assessment
Backend Feature: docs/features/13-supplemental-patient-assessment/
Related PR: NONE
Available After: MAIN_MERGE
Breaking Change: NO
```

> 환자 입력 마지막 화면의 개발용 여섯 항목을 최초 이송 요청에 선택적으로
> 전송하고, 앱 재실행 뒤 진행 중 상세에서 복구하는 계약입니다.

## 변경 요약

- 기존 `POST /api/v1/transport-requests`에 nullable
  `supplementalAssessment` 객체가 추가됐습니다.
- 기존 `GET /api/v1/transport-requests/{requestId}`에 같은 이름의 nullable
  응답이 추가됐습니다.
- 추가 평가를 전혀 입력하지 않은 기존 요청은 그대로 성공합니다.
- 생성 성공 응답, clinical timeline, 목록과 SSE payload는 바뀌지 않았습니다.
- 심정지·중증 외상·심혈관·뇌졸중·임신·선호 병원 상세는 이번 계약에 없습니다.

## 사용자 흐름

| 순서 | 앱 동작 | API | 결과 |
|---:|---|---|---|
| 1 | 환자 기본 평가 뒤 추가 항목 선택 입력 | 화면 입력 | 미입력은 `기록 없음` |
| 2 | 기존 최초 요청 본문에 추가 객체를 합쳐 제출 | `POST /api/v1/transport-requests` | 요청과 추가 평가가 한 번에 저장 |
| 3 | 통신 결과가 불명확하면 같은 키·같은 본문 재전송 | 같은 API | `200 OK`, 기존 요청 반환 |
| 4 | 앱 재실행 뒤 진행 요청 상세 복구 | `GET /api/v1/transport-requests/{requestId}` | 저장된 추가 평가 또는 `null` |

## 인증과 공통 계약

- Base URL: `http://13.124.194.249`
- 인증: `Authorization: Bearer {accessToken}`
- 역할: 활성 `PARAMEDIC`
- 시간: ISO-8601 UTC
- JSON nullable 필드는 서버 설정에 따라 `null`이면 생략될 수 있습니다.
- 실제 환자정보·개인 연락처·정확한 실제 위치가 아닌 테스트 데이터를 사용합니다.

## API 1. 최초 이송 요청 생성 변경

### `POST /api/v1/transport-requests`

- 헤더: 기존처럼 `Idempotency-Key` 필수, 8~100자
- 최초 성공: `201 Created`
- 같은 키·같은 전체 본문 재시도: `200 OK`
- 기존 필수 `origin`, `patient`, `incident`, `preKtas`, `consciousness`,
  `vitalSigns`, `treatments` 계약은 그대로 유지합니다.

기존 요청의 최상위에 다음 객체를 추가합니다.

```json
{
  "supplementalAssessment": {
    "assessedAt": "2026-08-07T10:00:00Z",
    "enteredAt": "2026-08-07T10:01:00Z",
    "glucoseMgDl": 85,
    "leftPupil": "NORMAL",
    "rightPupil": "SLUGGISH",
    "medicalHistory": "고혈압",
    "allergies": "확인된 알레르기 없음",
    "medications": "혈압약",
    "isolationConcern": false
  }
}
```

| 필드 | 타입 | 필수 조건 | 제약·의미 |
|---|---|---:|---|
| `supplementalAssessment` | object | NO | 객체 전체 생략·`null`이면 추가 기록 없음 |
| `assessedAt` | datetime | 객체가 있으면 YES | 실제 확인·측정 시각 |
| `enteredAt` | datetime | 객체가 있으면 YES | 앱 입력 시각, `assessedAt`보다 빠를 수 없음 |
| `glucoseMgDl` | integer | NO | 0~1000, 단위 mg/dL |
| `leftPupil` | enum | 동공 입력 시 YES | 좌우를 반드시 함께 전송 |
| `rightPupil` | enum | 동공 입력 시 YES | 좌우를 반드시 함께 전송 |
| `medicalHistory` | string | NO | 입력 전체(공백 포함) 1~120자, 저장 시 앞뒤 공백 제거 |
| `allergies` | string | NO | 입력 전체(공백 포함) 1~120자, 저장 시 앞뒤 공백 제거 |
| `medications` | string | NO | 입력 전체(공백 포함) 1~120자, 저장 시 앞뒤 공백 제거 |
| `isolationConcern` | boolean | NO | `false`는 우려 없음 기록, `null`은 기록 없음 |

- 객체를 보냈다면 여섯 종류 중 하나 이상 값이 있어야 합니다.
- 빈 문자열·공백 문자열은 보내지 않습니다.
- 좌우 동공 중 한쪽만 보내면 안 됩니다.
- `null`은 정상·없음·확인 불가가 아니라 해당 항목을 기록하지 않았다는 뜻입니다.
- 같은 멱등 키에 추가 평가 하나라도 다른 본문을 보내면 `COMMON_005`입니다.

### PupilResponse

| 값 | 의미 |
|---|---|
| `NORMAL` | 정상 반응 |
| `SLUGGISH` | 느린 반응 |
| `FIXED` | 고정 |
| `UNASSESSABLE` | 평가 불가 |

## API 2. 진행 중 이송 상세 복구 변경

### `GET /api/v1/transport-requests/{requestId}`

- 성공: `200 OK`
- 소유권: 로그인 구급대원이 직접 생성한 진행 중 요청만
- 기존 `patient`, `incident`, `latestSnapshot` 옆에 다음 값이 추가됩니다.

```json
{
  "supplementalAssessment": {
    "assessedAt": "2026-08-07T10:00:00Z",
    "enteredAt": "2026-08-07T10:01:00Z",
    "serverReceivedAt": "2026-08-07T10:01:02.123456Z",
    "glucoseMgDl": 85,
    "leftPupil": "NORMAL",
    "rightPupil": "SLUGGISH",
    "medicalHistory": "고혈압",
    "allergies": "확인된 알레르기 없음",
    "medications": "혈압약",
    "isolationConcern": false
  }
}
```

- 저장된 추가 평가가 없으면 `supplementalAssessment`는 `null` 또는 생략입니다.
- `serverReceivedAt`은 서버가 생성한 수신 시각이며 생성 요청으로 보내지 않습니다.
- 내부 record ID와 생성 계정 ID는 응답에 없습니다.

## 오류와 복구

| 코드 | HTTP | 조건 | 앱 처리 |
|---|---:|---|---|
| `COMMON_001` | 400 | 빈 객체·공백·범위·동공 쌍·시각·형식 오류 | 입력 수정 후 새 요청 |
| `COMMON_005` | 409 | 같은 멱등 키에 다른 추가 평가 포함 | 원 본문 복구 또는 새 키 사용 |
| `AUTH_001`, `AUTH_002` | 401 | 인증 없음·토큰 오류 | 토큰 갱신 후 같은 키·본문 재시도 |
| `AUTH_003`, `COMMON_004`, `USER_002` | 403 | 역할·조직·계정 상태 오류 | 접근 중단·운영자 확인 |
| `TRANSPORT_001` | 404 | 다른 소유자 또는 이미 종료된 요청 상세 | ACTIVE·RECENT 재조회 |
| `PROTOCOL_002` | 409 | 비활성 평가 프로토콜 | 프로토콜 재조회 후 새 입력 |

## 실시간 이벤트와 연동 확인

- 새 SSE 이벤트는 없습니다.
- 기존 SSE는 변경 신호이며 앱 재연결 뒤 진행 목록과 상세을 다시 조회합니다.
- [ ] 추가 정보가 하나도 없으면 객체 전체를 보내지 않음
- [ ] 하나라도 있으면 두 시각과 함께 전송
- [ ] 좌우 동공 API enum 변환과 쌍 검증
- [ ] `false`와 `null`을 구분
- [ ] 요청 응답 확정 전까지 멱등 키와 전체 본문을 함께 보존
- [ ] 앱 재실행 때 nullable 추가 평가 복구
- [ ] 토큰·실제 환자정보를 로그에 남기지 않음
