# 구급대원 이송 상세 복구 Flutter 앱 핸드오프

```text
Feature: transport-request-detail-recovery
Backend Feature: docs/features/10-transport-request-detail-recovery/
Related PR: NONE
Available After: MAIN_MERGE
Breaking Change: NO
```

> 앱 재실행·인증 복구 뒤 진행 중 이송의 최초 환자·발생정보와 최신 임상
> 상태를 서버 데이터로 다시 구성하는 조회 계약입니다. 기존 API의 요청·응답은
> 바뀌지 않았습니다.

## 변경 요약

- 새 API `GET /api/v1/transport-requests/{requestId}`가 추가됩니다.
- 활성 구급대원이 자신이 생성한 진행 중 요청만 조회할 수 있습니다.
- 최초 입력한 나이·성별·발생·증상정보와 현재 최신 Pre-KTAS·의식·활력징후·
  처치를 한 응답으로 복구할 수 있습니다.
- 전체 임상 이력, 병원 후보·목적지·ETA와 위치는 기존 전용 API를 계속
  사용합니다.
- 조회는 이송 상태, 임상정보, 감사 기록과 SSE 이벤트를 변경하지 않습니다.

## 앱 재실행 복구 순서

| 순서 | 앱 동작 | API | 복구하는 정보 |
|---:|---|---|---|
| 1 | 로그인 또는 토큰 갱신 | 기존 인증 API | 유효한 Access Token |
| 2 | 진행 중 요청 확인 | `GET /api/v1/transport-requests?view=ACTIVE` | 요청 ID·상태·목적지 요약 |
| 3 | 환자 화면 상세 복구 | `GET /api/v1/transport-requests/{requestId}` | 최초 환자·발생정보와 최신 임상 snapshot |
| 4 | 병원 탐색 화면 복구 | `GET /api/v1/transport-requests/{requestId}/hospital-search` | 후보·응답·목적지·거리·ETA |
| 5 | 마지막 위치 복구 | `GET /api/v1/transport-requests/{requestId}/location` | 위치 수신 여부·최신성·현재 목적지 ETA |
| 6 | 이후 변경 신호 구독 | `GET /api/v1/realtime/events` | SSE 수신 뒤 관련 REST API 재조회 |

- `ACTIVE`가 비어 있으면 진행 중 환자 화면을 복구하지 않습니다.
- 2번과 3번 사이에 이송이 종료되면 3번은 `TRANSPORT_001`을 반환할 수
  있습니다. 이때 환자 화면을 유지하지 말고 `ACTIVE`와 `RECENT`를 다시
  조회합니다.
- 전체 시간순 임상 기록 화면은 기존 `clinical-timeline`을 별도로 조회합니다.

## 인증과 접근 범위

| 항목 | 계약 |
|---|---|
| 인증 | `Authorization: Bearer {accessToken}` |
| 역할 | 활성 `PARAMEDIC` |
| 소유권 | Access Token 계정이 직접 생성한 요청 한 건만 |
| 조직 | JWT 조직과 현재 DB의 활성 `EMS_UNIT` 조직이 일치해야 함 |
| 조회 상태 | `SEARCHING`, `CANDIDATES_EXHAUSTED`, `ACCEPTED_AVAILABLE`, `EN_ROUTE`, `HANDOFF_REQUESTED` |

- 같은 구급대 조직의 다른 계정도 조회할 수 없습니다.
- 병원 관계자와 슈퍼 관리자는 이 API를 사용할 수 없습니다.
- 요청 ID, 계정·조직, 상태 중 어떤 조건이 맞지 않는지 구분해 노출하지 않습니다.

## API. 진행 중 자기 이송 상세 조회

### `GET /api/v1/transport-requests/{requestId}`

- Base URL: `http://13.124.194.249`
- 성공 HTTP: `200 OK`
- 요청 Body·Query·`Idempotency-Key`: 없음
- 시간: ISO-8601 UTC 문자열

#### Path

| 이름 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `requestId` | string(UUID) | O | 이송 요청 생성 또는 `ACTIVE` 목록에서 받은 `transportRequestId` |

#### 성공 응답

```json
{
  "transportRequestId": "REQUEST_UUID",
  "status": "EN_ROUTE",
  "assessmentProtocolVersion": "ERSYNC_MVP_1.0",
  "patient": {
    "ageStatus": "ESTIMATED",
    "ageYears": 45,
    "sex": "UNKNOWN"
  },
  "incident": {
    "occurrenceType": "DISEASE",
    "occurrenceDetail": null,
    "injuryMechanism": null,
    "injurySites": [],
    "primarySymptom": "CHEST_PAIN",
    "primarySymptomDetail": null,
    "secondarySymptoms": ["DYSPNEA"],
    "onsetTimeStatus": "ESTIMATED",
    "onsetAt": "2026-08-03T10:00:00Z"
  },
  "latestSnapshot": {
    "preKtas": {
      "classificationStatus": "COMPLETED",
      "level": 2,
      "exceptionReason": null,
      "exceptionDetail": null,
      "assessedAt": "2026-08-03T10:00:00Z",
      "standardVersion": "DEV_UNCONFIRMED"
    },
    "consciousness": {
      "avpu": "A",
      "unassessableReason": null,
      "unassessableDetail": null,
      "observedAt": "2026-08-03T10:00:00Z"
    },
    "vitalSigns": {
      "measuredAt": "2026-08-03T10:00:00Z",
      "measurements": [
        {"type":"BLOOD_PRESSURE","state":"VALUE","primaryValue":120,"secondaryValue":80,"unavailableReason":null,"unavailableDetail":null},
        {"type":"PULSE","state":"VALUE","primaryValue":80,"secondaryValue":null,"unavailableReason":null,"unavailableDetail":null},
        {"type":"RESPIRATORY_RATE","state":"VALUE","primaryValue":18,"secondaryValue":null,"unavailableReason":null,"unavailableDetail":null},
        {"type":"TEMPERATURE","state":"VALUE","primaryValue":36.5,"secondaryValue":null,"unavailableReason":null,"unavailableDetail":null},
        {"type":"SPO2","state":"VALUE","primaryValue":98,"secondaryValue":null,"unavailableReason":null,"unavailableDetail":null}
      ]
    },
    "treatments": [
      {
        "type": "NONE",
        "attemptResult": null,
        "performedAt": null,
        "details": null
      }
    ],
    "lastClinicalUpdateAt": "2026-08-03T10:01:00Z"
  },
  "createdAt": "2026-08-03T10:01:00Z",
  "serverNow": "2026-08-05T07:00:00Z"
}
```

### 최상위 필드

| 필드 | 타입 | Nullable | 설명 |
|---|---|---:|---|
| `transportRequestId` | string(UUID) | X | 조회한 이송 요청 공개 ID |
| `status` | enum | X | 현재 진행 상태 |
| `assessmentProtocolVersion` | string | X | 요청 생성 때 사용한 평가 프로토콜 버전 |
| `patient` | object | X | 최초 입력 환자 기본정보 |
| `incident` | object | X | 최초 입력 발생·증상정보 |
| `latestSnapshot` | object | X | 서버가 확정한 현재 최신 임상 요약 |
| `createdAt` | datetime | X | 요청 생성 서버 시각 |
| `serverNow` | datetime | X | 응답을 만든 서버 시각 |

### `patient`·`incident`

| 필드 | 타입 | Nullable | 설명 |
|---|---|---:|---|
| `patient.ageStatus` | enum | X | `EXACT`, `ESTIMATED`, `UNKNOWN` |
| `patient.ageYears` | integer | O | `UNKNOWN`이면 `null` |
| `patient.sex` | enum | X | `MALE`, `FEMALE`, `UNKNOWN` |
| `incident.occurrenceType` | enum | X | `DISEASE`, `NON_DISEASE`, `OTHER`, `UNKNOWN` |
| `incident.occurrenceDetail` | string | O | 조건부 최초 입력 상세 |
| `incident.injuryMechanism` | enum | O | 비질병 손상 기전, 아니면 `null` 가능 |
| `incident.injurySites` | string[] | X | 손상 부위가 없으면 빈 배열 |
| `incident.primarySymptom` | enum | X | 최초 주증상 |
| `incident.primarySymptomDetail` | string | O | 조건부 증상 상세 |
| `incident.secondarySymptoms` | string[] | X | 부증상이 없으면 빈 배열 |
| `incident.onsetTimeStatus` | enum | X | `EXACT`, `ESTIMATED`, `UNKNOWN` |
| `incident.onsetAt` | datetime | O | 발생 시각을 모르면 `null` |

손상 기전 값:

```text
TRAFFIC, FALL, FALL_FROM_HEIGHT, BLUNT, PENETRATING, BURN, POISONING,
DROWNING_ASPHYXIA, ASSAULT_SELF_HARM, MACHINERY_AGRICULTURAL, OTHER, UNKNOWN
```

손상 부위 값:

```text
HEAD_FACE, NECK, CHEST, ABDOMEN_PELVIS, SPINE, UPPER_LIMB, LOWER_LIMB,
MULTIPLE, UNKNOWN
```

주·부증상 값:

```text
ALTERED_CONSCIOUSNESS, DYSPNEA, RESPIRATORY_ARREST, CHEST_PAIN,
CARDIAC_ARREST, SUSPECTED_STROKE, SEIZURE_SYNCOPE, TRAUMA, BLEEDING,
GASTROINTESTINAL, POISONING, BURN, PREGNANCY_DELIVERY,
BEHAVIORAL_SELF_HARM, FEVER_INFECTION, OTHER, UNKNOWN
```

### `latestSnapshot`

| 필드 | 타입 | Nullable | 설명 |
|---|---|---:|---|
| `preKtas` | object | X | 최신 Pre-KTAS; 기존 timeline과 같은 형태 |
| `consciousness` | object | X | 최신 AVPU; 기존 timeline과 같은 형태 |
| `vitalSigns` | object | X | 최신 다섯 활력징후 세트 |
| `treatments` | object[] | X | 현재 처치 목록, 없으면 빈 배열 가능 |
| `lastClinicalUpdateAt` | datetime | X | 서버가 마지막 임상 갱신을 받은 시각 |

- `preKtas.classificationStatus`: `COMPLETED`, `EMERGENCY_UNFINISHED`
- `preKtas.exceptionReason`: `CPR_IN_PROGRESS`, `SCENE_DANGER`,
  `INSUFFICIENT_ASSESSMENT_TIME`, `OTHER` 또는 `null`
- `consciousness.avpu`: `A`, `V`, `P`, `U`, `UNASSESSABLE`
- `consciousness.unassessableReason`: `SCENE_DANGER`, `PATIENT_INACCESSIBLE`,
  `OTHER` 또는 `null`
- 활력징후 종류: `BLOOD_PRESSURE`, `PULSE`, `RESPIRATORY_RATE`,
  `TEMPERATURE`, `SPO2`
- 활력징후 상태: `VALUE`, `MEASUREMENT_UNAVAILABLE`, `PATIENT_REFUSED`
- 처치 종류: `NONE`, `OXYGEN`, `AIRWAY`, `CPR`, `DEFIBRILLATION_AED`,
  `IV_FLUID`, `MEDICATION`, `BLEEDING_WOUND`, `IMMOBILIZATION`, `ECG`,
  `WARMING_COOLING`, `DELIVERY`, `OTHER`
- 실제 처치가 추가되면 최초 `NONE`은 현재 처치 목록에서 제거됩니다.
- `details`의 전체 필드와 임상 enum 의미는 기존 06 임상 갱신 핸드오프와
  같습니다.
- 늦게 서버에 도착한 과거 측정은 원본 timeline에는 남지만 `latestSnapshot`을
  과거 값으로 되돌리지 않습니다.

## 오류

공통 오류 응답:

```json
{
  "code": "ERROR_CODE",
  "message": "사용자에게 표시 가능한 메시지",
  "fieldErrors": [],
  "traceId": "TRACE_ID"
}
```

| 오류 코드 | HTTP | 발생 조건 | 앱에서 필요한 처리 |
|---|---:|---|---|
| `AUTH_001` | 401 | Access Token 없음 | 로그인 또는 인증 복구 |
| `AUTH_002` | 401 | 만료·변조 토큰 또는 토큰 발급 뒤 계정 비활성화 | Refresh 후 한 번 재시도, 계속되면 로그인 |
| `AUTH_003` | 403 | 구급대원 역할이 아님 | 구급대원 화면 사용 차단 |
| `COMMON_004` | 403 | 비활성 조직 또는 계정·조직 연결 불일치 | 인증정보 제거 후 재로그인, 계속되면 운영 담당자 확인 |
| `TRANSPORT_001` | 404 | 요청 없음·타인 소유·완료·취소 | 환자 화면 유지 금지, `ACTIVE`·`RECENT` 재조회 |
| `COMMON_003` | 500 | 서버 저장 불변식 오류 | 환자정보를 임의 조립하지 말고 `traceId`와 함께 문의 |

## API별 책임 구분

| 필요한 데이터 | 사용할 API |
|---|---|
| 진행 중 요청 ID·상태·목적지 병원명 요약 | `GET /api/v1/transport-requests?view=ACTIVE` |
| 최초 환자·발생정보와 최신 임상 상태 | 새 `GET /api/v1/transport-requests/{requestId}` |
| 전체 임상 원본 이력 | `GET /api/v1/transport-requests/{requestId}/clinical-timeline` |
| 병원 후보·응답·목적지·거리·ETA | `GET /api/v1/transport-requests/{requestId}/hospital-search` |
| 마지막 구급차 위치 | `GET /api/v1/transport-requests/{requestId}/location` |
| 완료·취소 최소 이력 | `GET /api/v1/transport-requests?view=RECENT` 또는 `HISTORY` |

새 상세에는 다음 값이 없습니다.

- 구급대원 회신 연락처
- 환자 이름·주민등록번호·환자 연락처·정확한 생년월일·상세 주소
- 최초·최신 좌표와 전체 이동경로
- 병원 제안·목적지·거리·ETA
- 전체 임상 원본 배열
- 내부 DB ID, 비밀번호·토큰·가입 코드·멱등성 fingerprint

## 통신·경합 복구

| 상황 | 서버 계약 | 앱에서 필요한 처리 |
|---|---|---|
| 상세 GET 통신 실패 | 조회 상태는 변경되지 않음 | 네트워크 복구 뒤 같은 GET 재시도 |
| 상세 GET 중 임상 갱신 | 이전 또는 새 완전한 snapshot 반환 | 갱신 성공 또는 SSE 뒤 상세 재조회 |
| 상세 GET 중 완료·취소 | 활성 상세 또는 `TRANSPORT_001` 가능 | `ACTIVE`·`RECENT` 재조회로 최종 상태 확인 |
| 상세 GET·임상 갱신·취소가 동시에 발생 | 부분 데이터 없이 활성 상세 또는 `TRANSPORT_001`, 최종 상태는 취소 | 응답 하나만으로 추측하지 말고 `ACTIVE`·`RECENT` 재조회 |
| SSE 연결 해제 | REST 저장 상태는 유지 | Access Token 확인, REST 재조회 후 SSE 재연결 |
| Access Token 만료 | `AUTH_002` | Refresh Token 회전 뒤 `ACTIVE`부터 복구 재시작 |
| Refresh 실패 | 기존 `AUTH_005` | 인증정보 제거 후 로그인 |

## 연동 확인

- [ ] 앱 재실행 뒤 `ACTIVE`의 요청 ID로 상세 GET 성공
- [ ] 나이·성별·주증상과 최초 발생정보가 입력 화면에 복구됨
- [ ] 최신 Pre-KTAS·의식·다섯 활력징후·현재 처치가 복구됨
- [ ] 임상 갱신 뒤 재조회하면 최신 snapshot이 표시됨
- [ ] 병원 후보·ETA·위치는 상세 응답이 아닌 기존 전용 API에서 복구함
- [ ] `TRANSPORT_001`이면 환자 화면을 유지하지 않고 목록을 재조회함
- [ ] 병원·관리자·다른 구급대원 접근이 차단됨
- [ ] 응답·로그·오류 문의에 환자정보·정확한 위치·토큰 원문을 남기지 않음
- [ ] 현재 HTTP Dev 서버에서는 실제 환자·연락처·정확한 GPS 대신 가짜 데이터 사용
