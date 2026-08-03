# 환자 평가 및 이송 요청 생성 Flutter 구급대원 앱 핸드오프

```text
Feature: patient-assessment-transfer-request
Backend Feature: docs/features/03-patient-assessment-transfer-request/
Related PR: NONE
Available After: MAIN_MERGE
Breaking Change: YES
```

## 변경 요약

- 구급대원 가입에 병원 회신용 연락처와 연락처 제공 동의가 필수로 추가됩니다.
- 로그인한 `PARAMEDIC`은 현재 개발용 평가 프로토콜을 조회하고 최초 평가로
  `SEARCHING` 이송 요청을 생성할 수 있습니다.
- 이송 요청에는 `callbackContact`, 계정·조직·병원 ID를 보내지 않습니다.
  서버가 JWT 계정의 프로필 연락처와 EMS 조직을 사용합니다.
- 현재 프로토콜은 실제 의료 승인을 받은 기준이 아니라 `ERSYNC_MVP_1.0`
  개발 흐름 검증용입니다. 실제 환자정보와 실제 개인번호를 사용하지 않습니다.

## 사용자 흐름

| 순서 | 사용자·앱 동작 | API 호출 | 성공 후 상태 |
|---:|---|---|---|
| 1 | 연락처 제공 동의를 확인하고 코드·계정·연락처 입력 | `POST /api/v1/auth/signups/paramedic` | 연락처·동의 이력이 있는 `PARAMEDIC` 계정 |
| 2 | 로그인 | `POST /api/v1/auth/login` | Bearer Access Token 확보 |
| 3 | 지원 입력 계약 조회 | `GET /api/v1/assessment-protocols/active` | 버전·개발 상태·enum·단위 확인 |
| 4 | 최초 평가·위치와 새 멱등성 키 제출 | `POST /api/v1/transport-requests` | `SEARCHING` 요청 생성 |
| 5 | 응답을 받지 못한 경우 같은 키와 같은 본문 재전송 | 같은 생성 API | 기존 요청을 `200 OK`로 회수 |

## 인증과 접근 범위

| 항목 | 계약 |
|---|---|
| 인증 | 프로토콜 조회·요청 생성은 `Authorization: Bearer {accessToken}` 필수 |
| 역할 | `PARAMEDIC`만 허용; 다른 역할은 `AUTH_003` |
| 조직·소유권 | 토큰의 계정 ID를 현재 DB 계정·`EMS_UNIT` 조직과 다시 대조 |
| 회신 연락처 | 가입 프로필에서 서버가 읽어 요청 생성 당시 값으로 보관 |
| 응답 노출 | 생성 응답에는 연락처·환자 평가·정확한 좌표가 없음 |

## 공통 계약

- Base URL: `http://13.124.194.249`
- Content-Type: `application/json`
- 시간: ISO-8601 UTC 문자열
- 공통 오류 응답:

```json
{
  "code": "ERROR_CODE",
  "message": "사용자에게 표시 가능한 메시지",
  "fieldErrors": [],
  "traceId": "TRACE_ID"
}
```

## API 1. 구급대원 가입 변경

### `POST /api/v1/auth/signups/paramedic`

- 인증: 없음
- 성공: `201 Created`
- 기존 API와 호환되지 않는 필수 필드 3개가 추가됩니다.

```json
{
  "invitationCode": "URL_SAFE_ONE_TIME_CODE",
  "loginId": "medic01",
  "password": "safe-password",
  "contact": "010-1234-5678",
  "contactSharingConsentAccepted": true,
  "contactSharingConsentVersion": "CONTACT_SHARING_DEV_1.0"
}
```

| 필드 | 필수 | 제약 |
|---|---:|---|
| `invitationCode` | YES | 미사용·미만료 `PARAMEDIC` 가입 코드 |
| `loginId` | YES | `[a-z0-9]{4,30}` |
| `password` | YES | 8~64자 |
| `contact` | YES | 앞뒤 공백 제거 후 `[0-9+][0-9-]{7,29}` |
| `contactSharingConsentAccepted` | YES | 반드시 `true` |
| `contactSharingConsentVersion` | YES | 현재 `CONTACT_SHARING_DEV_1.0`과 정확히 일치 |

성공 응답은 기존 가입 응답과 같으며 연락처·동의 원문은 반환하지 않습니다.

| 오류 | HTTP | 처리 |
|---|---:|---|
| `COMMON_001` | 400 | 연락처·동의 버전·입력 형식을 확인; 가입 코드는 소비되지 않음 |
| `INVITATION_001` | 400 | 가입 코드 재확인 |
| `INVITATION_002`~`004` | 409 | 새 가입 코드 요청 또는 기존 계정 로그인 |
| `USER_003` | 409 | 다른 로그인 ID 사용 |

## API 2. 활성 평가 프로토콜 조회

### `GET /api/v1/assessment-protocols/active`

- 인증·역할: Bearer, `PARAMEDIC`
- 성공: `200 OK`

주요 응답 구조:

```json
{
  "version": "ERSYNC_MVP_1.0",
  "status": "DEVELOPMENT",
  "preKtasStandardVersion": "DEV_UNCONFIRMED",
  "requiredSections": [
    "origin", "patient", "incident", "preKtas",
    "consciousness", "vitalSigns", "treatments"
  ],
  "enumValues": {
    "ageStatus": ["EXACT", "ESTIMATED", "UNKNOWN"],
    "vitalSignType": [
      "BLOOD_PRESSURE", "PULSE", "RESPIRATORY_RATE", "TEMPERATURE", "SPO2"
    ]
  },
  "vitalSignUnits": {
    "BLOOD_PRESSURE": "mmHg",
    "PULSE": "beats/min",
    "RESPIRATORY_RATE": "breaths/min",
    "TEMPERATURE": "degC",
    "SPO2": "percent"
  },
  "conditionalRules": [
    "EXACT_OR_ESTIMATED_AGE_REQUIRES_VALUE",
    "NON_DISEASE_REQUIRES_INJURY_INFORMATION",
    "KNOWN_ONSET_REQUIRES_TIME",
    "PRE_KTAS_COMPLETED_OR_EMERGENCY_UNFINISHED",
    "ALL_FIVE_VITAL_SIGNS_EXACTLY_ONCE",
    "NONE_TREATMENT_MUST_BE_ALONE"
  ]
}
```

`enumValues`의 실제 전체 값은 다음과 같습니다.

| 키 | 값 |
|---|---|
| `ageStatus` | `EXACT`, `ESTIMATED`, `UNKNOWN` |
| `patientSex` | `MALE`, `FEMALE`, `UNKNOWN` |
| `originSource` | `GPS`, `MANUAL_CONFIRMED` |
| `occurrenceType` | `DISEASE`, `NON_DISEASE`, `OTHER`, `UNKNOWN` |
| `injuryMechanism` | `TRAFFIC`, `FALL`, `FALL_FROM_HEIGHT`, `BLUNT`, `PENETRATING`, `BURN`, `POISONING`, `DROWNING_ASPHYXIA`, `ASSAULT_SELF_HARM`, `MACHINERY_AGRICULTURAL`, `OTHER`, `UNKNOWN` |
| `injurySite` | `HEAD_FACE`, `NECK`, `CHEST`, `ABDOMEN_PELVIS`, `SPINE`, `UPPER_LIMB`, `LOWER_LIMB`, `MULTIPLE`, `UNKNOWN` |
| `symptom` | `ALTERED_CONSCIOUSNESS`, `DYSPNEA`, `RESPIRATORY_ARREST`, `CHEST_PAIN`, `CARDIAC_ARREST`, `SUSPECTED_STROKE`, `SEIZURE_SYNCOPE`, `TRAUMA`, `BLEEDING`, `GASTROINTESTINAL`, `POISONING`, `BURN`, `PREGNANCY_DELIVERY`, `BEHAVIORAL_SELF_HARM`, `FEVER_INFECTION`, `OTHER`, `UNKNOWN` |
| `onsetTimeStatus` | `EXACT`, `ESTIMATED`, `UNKNOWN` |
| `preKtasClassificationStatus` | `COMPLETED`, `EMERGENCY_UNFINISHED` |
| `preKtasExceptionReason` | `CPR_IN_PROGRESS`, `SCENE_DANGER`, `INSUFFICIENT_ASSESSMENT_TIME`, `OTHER` |
| `avpu` | `A`, `V`, `P`, `U`, `UNASSESSABLE` |
| `consciousnessUnassessableReason` | `SCENE_DANGER`, `PATIENT_INACCESSIBLE`, `OTHER` |
| `vitalSignType` | `BLOOD_PRESSURE`, `PULSE`, `RESPIRATORY_RATE`, `TEMPERATURE`, `SPO2` |
| `vitalSignState` | `VALUE`, `MEASUREMENT_UNAVAILABLE`, `PATIENT_REFUSED` |
| `vitalSignUnavailableReason` | `PATIENT_CONDITION`, `SCENE_DANGER`, `INJURY_SITE`, `DEVICE_ERROR`, `OTHER` |
| `treatmentType` | `NONE`, `OXYGEN`, `AIRWAY`, `CPR`, `DEFIBRILLATION_AED`, `IV_FLUID`, `MEDICATION`, `BLEEDING_WOUND`, `IMMOBILIZATION`, `ECG`, `WARMING_COOLING`, `DELIVERY`, `OTHER` |
| `treatmentAttemptResult` | `SUCCESS`, `FAILURE`, `ONGOING`, `NOT_APPLICABLE` |

## API 3. 이송 요청 생성

### `POST /api/v1/transport-requests`

- 인증·역할: Bearer, `PARAMEDIC`
- 헤더: `Idempotency-Key` 필수
- 최초 성공: `201 Created`, `Location: /api/v1/transport-requests/{id}`
- 동일 재시도 성공: `200 OK`

| 헤더 | 제약 |
|---|---|
| `Idempotency-Key` | 8~100자, `[A-Za-z0-9._:-]`; 요청 시작 전에 만들고 응답을 확정할 때까지 본문과 함께 보관 |

요청 예시:

```json
{
  "assessmentProtocolVersion": "ERSYNC_MVP_1.0",
  "origin": {
    "latitude": 37.5821000,
    "longitude": 127.0105000,
    "source": "GPS"
  },
  "patient": {
    "ageStatus": "ESTIMATED",
    "ageYears": 45,
    "sex": "UNKNOWN"
  },
  "incident": {
    "occurrenceType": "DISEASE",
    "mechanism": null,
    "occurrenceDetail": null,
    "injurySites": [],
    "primarySymptom": "CHEST_PAIN",
    "primarySymptomDetail": null,
    "secondarySymptoms": ["DYSPNEA"],
    "onsetTimeStatus": "ESTIMATED",
    "onsetAt": "2026-08-03T10:00:00Z",
    "enteredAt": "2026-08-03T10:01:00Z"
  },
  "preKtas": {
    "classificationStatus": "COMPLETED",
    "level": 2,
    "exceptionReason": null,
    "exceptionDetail": null,
    "assessedAt": "2026-08-03T10:00:00Z",
    "standardVersion": "DEV_UNCONFIRMED",
    "enteredAt": "2026-08-03T10:01:00Z"
  },
  "consciousness": {
    "avpu": "A",
    "unassessableReason": null,
    "unassessableDetail": null,
    "observedAt": "2026-08-03T10:00:00Z",
    "enteredAt": "2026-08-03T10:01:00Z"
  },
  "vitalSigns": {
    "measuredAt": "2026-08-03T10:00:00Z",
    "enteredAt": "2026-08-03T10:01:00Z",
    "measurements": [
      {"type": "BLOOD_PRESSURE", "state": "VALUE", "primaryValue": 120, "secondaryValue": 80},
      {"type": "PULSE", "state": "VALUE", "primaryValue": 80},
      {"type": "RESPIRATORY_RATE", "state": "VALUE", "primaryValue": 18},
      {"type": "TEMPERATURE", "state": "VALUE", "primaryValue": 36.5},
      {"type": "SPO2", "state": "VALUE", "primaryValue": 98}
    ]
  },
  "treatments": [
    {"type": "NONE", "attemptResult": null, "details": null, "performedAt": null,
      "enteredAt": "2026-08-03T10:01:00Z"}
  ]
}
```

### 조건부 입력 규칙

| 영역 | 규칙 |
|---|---|
| 나이 | `EXACT`·`ESTIMATED`는 `ageYears` 0 이상 필수, `UNKNOWN`은 `ageYears: null`; 서버가 임의의 최대 나이를 제한하지 않음 |
| 발생 | `NON_DISEASE`만 `mechanism`과 하나 이상의 `injurySites` 필수; 다른 유형은 둘 다 비움 |
| `OTHER` | 발생 유형과 주증상 `OTHER`는 각각 대응하는 detail 필수 |
| 발생 시각 | `EXACT`·`ESTIMATED`는 `onsetAt` 필수, `UNKNOWN`은 `null` |
| Pre-KTAS 완료 | `COMPLETED`: `level` 1~5와 `assessedAt` 필수, 예외 필드는 비움 |
| 긴급 미완료 | `EMERGENCY_UNFINISHED`: 단계·분류 시각 없이 `exceptionReason` 필수; `OTHER`는 detail 필수 |
| AVPU | `UNASSESSABLE`만 사유 필수; 사유 `OTHER`는 detail 필수. 그 외 AVPU는 사유·detail 비움 |
| 활력징후 | 다섯 종류를 중복 없이 정확히 한 번씩 제출 |
| 혈압 값 | `VALUE`이면 `primaryValue`=수축기, `secondaryValue`=이완기 |
| 다른 활력 값 | `VALUE`이면 `primaryValue`만 사용하고 `secondaryValue`는 비움 |
| 측정 불가 | 값 없이 `MEASUREMENT_UNAVAILABLE`과 사유; 사유 `OTHER`는 detail 필수 |
| 환자 거부 | 값·사유 없이 `PATIENT_REFUSED` |
| 처치 없음 | `NONE` 한 건만 가능하며 결과·details·시행 시각은 비움 |
| 실제 처치 | `attemptResult`, `performedAt`, `enteredAt`과 유형별 최소 details 필수 |

- `measurements`와 `treatments` 배열에는 `null` 요소를 넣을 수 없으며 잘못된 입력은 `COMMON_001`입니다.
- 임상·입력 시각은 원본 기록으로 보존하지만 최신 순서 판정은 서버 수신 시각을 사용합니다.

처치 유형별 최소 `details`:

| 유형 | 필수 detail |
|---|---|
| `OXYGEN` | `method`, `flowRateLpm` |
| `AIRWAY` | `device` |
| `CPR` | `startedAt`, `currentStatus` |
| `DEFIBRILLATION_AED` | `shockCount` 0 이상 |
| `IV_FLUID` | `fluidName`, `amountMl` |
| `MEDICATION` | `medicationName`, `dose`, `route` |
| `BLEEDING_WOUND`, `IMMOBILIZATION` | `method`, `site` |
| `ECG` | `leadType` |
| `WARMING_COOLING` | `method` |
| `DELIVERY` | `birthAt` 또는 `currentStatus` 중 하나 이상 |
| `OTHER` | `detail` |

사용 가능한 `details` 필드는 `method`, `device`, `flowRateLpm`, `startedAt`,
`success`, `currentStatus`, `rosc`, `roscAt`, `shockCount`, `fluidName`, `amountMl`,
`medicationName`, `dose`, `route`, `site`, `tourniquetUsed`,
`tourniquetAppliedAt`, `leadType`, `findings`, `transmitted`, `birthAt`, `detail`입니다.

성공 응답:

```json
{
  "transportRequestId": "TRANSPORT_REQUEST_UUID",
  "status": "SEARCHING",
  "assessmentProtocolVersion": "ERSYNC_MVP_1.0",
  "createdAt": "2026-08-03T10:02:00Z"
}
```

| 오류 | HTTP | 재시도와 처리 |
|---|---:|---|
| `COMMON_001` | 400 | 필드·조건 조합·멱등성 키 수정 후 새 요청 |
| `AUTH_001` | 401 | 로그인 필요 |
| `AUTH_002` | 401 | 토큰 갱신 후 같은 키·본문으로 한 번 재시도 |
| `AUTH_003` | 403 | 구급대원 계정인지 확인 |
| `COMMON_004` | 403 | 서버 계정·조직 불일치; 운영자 확인 |
| `USER_002` | 403 | 비활성 계정; 운영자 확인 |
| `USER_005` | 409 | 새 계약으로 연락처·동의를 등록한 계정 필요 |
| `PROTOCOL_002` | 409 | 활성 프로토콜 재조회 후 새 입력 작성 |
| `COMMON_005` | 409 | 같은 키에 다른 본문을 보냄; 원 요청 본문을 복구하거나 새 키 사용 |

## 모바일 상태 복구

| 상황 | 서버 계약 | 앱 처리 |
|---|---|---|
| 생성 응답 유실 | 같은 계정·키·본문이면 기존 결과를 `200`으로 반환 | 키와 완성 본문을 로컬에 유지하고 그대로 재전송 |
| 같은 키의 본문 변경 | `COMMON_005`, 기존 요청은 변경되지 않음 | 편집한 새 환자는 새 키로 제출 |
| Access Token 만료 | `AUTH_002` | 갱신 후 같은 키·본문으로 재전송 |
| 앱 재실행 | 생성 결과 조회 API는 아직 없음 | 확정 응답 전 요청 키·본문을 보존해야 함 |
| GPS 실패 | 요청 자체는 좌표가 필수 | 사용자가 지도에서 확인한 좌표와 `MANUAL_CONFIRMED` 사용 |

## 실시간 이벤트와 재조회

- 이벤트: 없음
- 이 기능에는 생성된 요청 조회·병원 응답 API가 아직 없습니다.
- 병원 탐색·응답, 목적지와 이송 상태는 후속 기능 계약입니다.

## 연동 확인

- [ ] 연락처·동의가 포함된 신규 구급대원 가입
- [ ] 활성 프로토콜 조회와 `DEVELOPMENT` 표시
- [ ] 완료 Pre-KTAS 생성
- [ ] 긴급 미완료 Pre-KTAS 생성
- [ ] 다섯 활력징후의 값·측정 불가·환자 거부
- [ ] `NONE`과 실제 처치 입력
- [ ] 같은 키·본문 재시도와 `COMMON_005`
- [ ] GPS 실패 후 `MANUAL_CONFIRMED`
- [ ] 실제 개인정보가 아닌 테스트 데이터로 dev API 연결
