# 이송 중 환자·위치 갱신 Flutter 구급대원 앱 핸드오프

```text
Feature: in-transit-patient-location-updates
Backend Feature: docs/features/06-in-transit-patient-location-updates/
Related PR: NONE
Available After: MAIN_MERGE
Breaking Change: NO
```

## 변경 요약

- 구급대원은 자기 활성 이송 요청에 새 활력징후·의식·Pre-KTAS·처치 기록을
  추가할 수 있습니다. 기존 기록은 수정하지 않고 이력에 남습니다.
- 자기 요청의 최신 임상 snapshot과 전체 임상 이력을 시간순으로 조회할 수 있습니다.
- 최신 구급차 위치를 전송하고 마지막 위치·수신 경과시간·현재 목적지 거리와 ETA를
  조회할 수 있습니다.
- 서버는 요청별 정확한 위치 한 건만 유지합니다. 30초 이상 새 위치가 없으면 마지막
  좌표를 유지하면서 `STALE`을 반환합니다.
- 현재 목적지가 있으면 새 위치 기준 ETA를 비동기로 다시 계산합니다. 느리게 끝난
  과거 계산 결과는 버리고 지도 API 실패가 위치 저장을 취소하지 않습니다.
- 현재 목적지 철회 뒤 재탐색은 최초 요청 좌표보다 마지막 저장 위치를 우선 사용합니다.
- 기존 병원 탐색 응답에 마지막 성공 거리·ETA 필드가 추가됩니다.

## 사용자 흐름

| 순서 | 앱·사용자 동작 | API | 성공 후 상태 |
|---:|---|---|---|
| 1 | 활성 요청 화면 복구 | 임상 timeline, 위치, 병원 탐색 현황 GET | 현재 snapshot·위치·목적지 복구 |
| 2 | 새 환자 상태 입력 | 해당 임상 POST | 원본 1건 추가, snapshot 반영 여부 확인 |
| 3 | 이동 중 최신 위치 전송 | 위치 PUT | 최신 위치 한 건 유지, 목적지 ETA 계산 예약 |
| 4 | 위치·ETA 갱신 신호 수신 | 위치 GET과 병원 탐색 현황 GET | 서버 권위 상태로 화면 갱신 |
| 5 | 연결 복구·앱 재실행 | timeline·위치·병원 탐색 재조회 후 SSE 재연결 | 누락된 갱신 복구 |

## 공통 계약

- Base URL: `http://13.124.194.249`
- 인증: `Authorization: Bearer {accessToken}`
- 역할: `PARAMEDIC`
- 시간: ISO-8601 UTC 문자열
- `requestId`: 이송 요청 생성 응답의 `transportRequestId`
- 갱신 허용 상태: `SEARCHING`, `CANDIDATES_EXHAUSTED`, `ACCEPTED_AVAILABLE`, `EN_ROUTE`
- 갱신 차단 상태: `HANDOFF_REQUESTED`, `COMPLETED`, `CANCELLED`
- 임상 POST와 위치 PUT에는 `Idempotency-Key`가 필수입니다.
- 키 제약: 8~100자, `[A-Za-z0-9._:-]`
- 명령 시작 전에 키와 완성된 body를 함께 보관하고 결과 확정 전에는 변경하지 않습니다.

공통 오류 응답:

```json
{
  "code": "ERROR_CODE",
  "message": "사용자에게 표시 가능한 메시지",
  "fieldErrors": [],
  "traceId": "TRACE_ID"
}
```

## API 1~4. 임상 기록 추가

| 종류 | Method·Path | 신규 | 동일 재시도 |
|---|---|---:|---:|
| 활력징후 | `POST /api/v1/transport-requests/{requestId}/clinical-updates/vital-signs` | 201 | 200 |
| 의식 | `POST /api/v1/transport-requests/{requestId}/clinical-updates/consciousness` | 201 | 200 |
| Pre-KTAS | `POST /api/v1/transport-requests/{requestId}/clinical-updates/pre-ktas` | 201 | 200 |
| 처치 | `POST /api/v1/transport-requests/{requestId}/clinical-updates/treatments` | 201 | 200 |

### 활력징후 요청

```json
{
  "measuredAt": "2026-08-04T10:10:00Z",
  "enteredAt": "2026-08-04T10:10:05Z",
  "measurements": [
    {"type":"BLOOD_PRESSURE","state":"VALUE","primaryValue":110,"secondaryValue":70},
    {"type":"PULSE","state":"VALUE","primaryValue":92,"secondaryValue":null},
    {"type":"RESPIRATORY_RATE","state":"VALUE","primaryValue":20,"secondaryValue":null},
    {"type":"TEMPERATURE","state":"VALUE","primaryValue":36.7,"secondaryValue":null},
    {"type":"SPO2","state":"VALUE","primaryValue":94,"secondaryValue":null}
  ]
}
```

- 다섯 종류를 중복 없이 정확히 한 번씩 보냅니다.
- 종류: `BLOOD_PRESSURE`, `PULSE`, `RESPIRATORY_RATE`, `TEMPERATURE`, `SPO2`
- 상태: `VALUE`, `MEASUREMENT_UNAVAILABLE`, `PATIENT_REFUSED`
- `VALUE`: `primaryValue` 필수. 혈압만 `secondaryValue`도 필수입니다.
- `MEASUREMENT_UNAVAILABLE`: 두 값은 `null`, `unavailableReason` 필수입니다.
- 미측정 사유: `PATIENT_CONDITION`, `SCENE_DANGER`, `INJURY_SITE`, `DEVICE_ERROR`, `OTHER`
- 미측정 사유가 `OTHER`이면 `unavailableDetail`이 필요합니다.
- `PATIENT_REFUSED`: 값·사유·상세를 모두 비웁니다.

### 의식 요청

```json
{
  "avpu": "V",
  "unassessableReason": null,
  "unassessableDetail": null,
  "observedAt": "2026-08-04T10:11:00Z",
  "enteredAt": "2026-08-04T10:11:05Z"
}
```

- `avpu`: `A`, `V`, `P`, `U`, `UNASSESSABLE`
- `UNASSESSABLE`일 때만 사유가 필요합니다.
- 사유: `SCENE_DANGER`, `PATIENT_INACCESSIBLE`, `OTHER`
- 사유가 `OTHER`이면 `unassessableDetail`이 필요합니다.
- `UNASSESSABLE`이 아니면 사유와 상세는 비웁니다.

### Pre-KTAS 요청

완료 예시:

```json
{
  "classificationStatus": "COMPLETED",
  "level": 2,
  "exceptionReason": null,
  "exceptionDetail": null,
  "assessedAt": "2026-08-04T10:12:00Z",
  "standardVersion": "DEV_UNCONFIRMED",
  "enteredAt": "2026-08-04T10:12:05Z"
}
```

긴급 미완료 예시:

```json
{
  "classificationStatus": "EMERGENCY_UNFINISHED",
  "level": null,
  "exceptionReason": "CPR_IN_PROGRESS",
  "exceptionDetail": null,
  "assessedAt": null,
  "standardVersion": "DEV_UNCONFIRMED",
  "enteredAt": "2026-08-04T10:12:05Z"
}
```

- `COMPLETED`: `level` 1~5와 `assessedAt` 필수, 예외 사유·상세는 비웁니다.
- `EMERGENCY_UNFINISHED`: `level`·`assessedAt`은 `null`, 예외 사유 필수입니다.
- 예외 사유: `CPR_IN_PROGRESS`, `SCENE_DANGER`, `INSUFFICIENT_ASSESSMENT_TIME`, `OTHER`
- 예외 사유가 `OTHER`이면 `exceptionDetail`이 필요합니다.
- `standardVersion`은 평가 프로토콜 조회 응답의 `preKtasStandardVersion`을 그대로
  사용해야 합니다. 현재 Dev 값은 `DEV_UNCONFIRMED`이며 다른 값은 `PROTOCOL_002`입니다.

### 처치 요청

```json
{
  "type": "OXYGEN",
  "attemptResult": "ONGOING",
  "details": {
    "method": "MASK",
    "flowRateLpm": 5
  },
  "performedAt": "2026-08-04T10:13:00Z",
  "enteredAt": "2026-08-04T10:13:05Z"
}
```

- 갱신 API에서는 `NONE`을 보낼 수 없습니다.
- 종류: `OXYGEN`, `AIRWAY`, `CPR`, `DEFIBRILLATION_AED`, `IV_FLUID`,
  `MEDICATION`, `BLEEDING_WOUND`, `IMMOBILIZATION`, `ECG`,
  `WARMING_COOLING`, `DELIVERY`, `OTHER`
- 결과: `SUCCESS`, `FAILURE`, `ONGOING`, `NOT_APPLICABLE`
- 실제 처치는 `attemptResult`, `performedAt`, `enteredAt`이 필요합니다.

| 유형 | 최소 필수 `details` |
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
| `DELIVERY` | `birthAt` 또는 `currentStatus` |
| `OTHER` | `detail` |

사용 가능한 `details` 필드는 `method`, `device`, `flowRateLpm`, `startedAt`,
`success`, `currentStatus`, `rosc`, `roscAt`, `shockCount`, `fluidName`, `amountMl`,
`medicationName`, `dose`, `route`, `site`, `tourniquetUsed`, `tourniquetAppliedAt`,
`leadType`, `findings`, `transmitted`, `birthAt`, `detail`입니다.

### 공통 임상 성공 응답

```json
{
  "transportRequestId": "REQUEST_UUID",
  "updateType": "VITAL_SIGNS",
  "recordId": "RECORD_UUID",
  "clinicalAt": "2026-08-04T10:10:00Z",
  "serverReceivedAt": "2026-08-04T10:10:06Z",
  "snapshotUpdated": true,
  "lastClinicalUpdateAt": "2026-08-04T10:10:06Z",
  "idempotentReplay": false
}
```

| 필드 | 의미 |
|---|---|
| `updateType` | `VITAL_SIGNS`, `CONSCIOUSNESS`, `PRE_KTAS`, `TREATMENT` |
| `clinicalAt` | 측정·관찰·평가·처치 시각; 처치 시각이 없을 때 입력 시각 |
| `snapshotUpdated` | `false`면 원본은 저장됐지만 더 오래된 기록이라 최신 요약은 유지됨 |
| `lastClinicalUpdateAt` | 서버가 마지막 임상 갱신을 받아들인 시각 |
| `idempotentReplay` | 동일 키·동일 body 재시도 여부 |

## API 5. 자기 임상 timeline 조회

### `GET /api/v1/transport-requests/{requestId}/clinical-timeline?page=0&size=50`

- 성공: `200 OK`
- `page`: 0 이상, `size`: 1~100
- 정렬: `clinicalAt ASC`, 같은 시각은 `serverReceivedAt ASC`, 그다음 record ID
- 최초 생성 시 입력한 임상 원본도 timeline에 포함됩니다.

```json
{
  "transportRequestId": "REQUEST_UUID",
  "latestSnapshot": {
    "preKtas": {"classificationStatus":"COMPLETED","level":2,"assessedAt":"2026-08-04T10:12:00Z","standardVersion":"DEV_UNCONFIRMED"},
    "consciousness": {"avpu":"V","observedAt":"2026-08-04T10:11:00Z"},
    "vitalSigns": {"measuredAt":"2026-08-04T10:10:00Z","measurements":[]},
    "treatments": [],
    "lastClinicalUpdateAt": "2026-08-04T10:13:06Z"
  },
  "items": [
    {
      "recordType": "VITAL_SIGNS",
      "recordId": "RECORD_UUID",
      "clinicalAt": "2026-08-04T10:10:00Z",
      "enteredAt": "2026-08-04T10:10:05Z",
      "serverReceivedAt": "2026-08-04T10:10:06Z",
      "preKtas": null,
      "consciousness": null,
      "vitalSigns": {"measuredAt":"2026-08-04T10:10:00Z","measurements":[]},
      "treatment": null
    }
  ],
  "page": 0,
  "size": 50,
  "totalElements": 8,
  "totalPages": 1,
  "serverNow": "2026-08-04T10:14:00Z"
}
```

- `recordType`에 대응하는 `preKtas`, `consciousness`, `vitalSigns`, `treatment`
  중 하나만 값이 있고 나머지는 `null`입니다.
- 앱이 재실행되면 이 API의 `latestSnapshot`을 현재 표시값으로 사용합니다.

## API 6. 최신 위치 갱신

### `PUT /api/v1/transport-requests/{requestId}/location`

- 성공·동일 재시도: `200 OK`
- 헤더: `Idempotency-Key` 필수

```json
{
  "latitude": 37.5010000,
  "longitude": 127.0010000,
  "capturedAt": "2026-08-04T10:15:00Z"
}
```

| 필드 | 제약 |
|---|---|
| `latitude` | -90~90 |
| `longitude` | -180~180 |
| `capturedAt` | 단말이 실제 좌표를 획득한 ISO-8601 시각 |

- 더 오래된 `capturedAt`이 늦게 도착해도 `200`이지만 `locationReplaced: false`이며
  저장된 최신 좌표를 유지합니다.
- 같은 `capturedAt`이면 서버가 나중에 받은 값을 최신으로 사용합니다.
- 전송 주기는 서버가 강제하지 않지만 제품 기준은 이동 중 약 10초입니다.

## API 7. 자기 최신 위치 조회

### `GET /api/v1/transport-requests/{requestId}/location`

- 성공: `200 OK`

```json
{
  "transportRequestId": "REQUEST_UUID",
  "latitude": 37.5010000,
  "longitude": 127.0010000,
  "capturedAt": "2026-08-04T10:15:00Z",
  "lastReceivedAt": "2026-08-04T10:15:01Z",
  "freshness": "CURRENT",
  "ageSeconds": 8,
  "serverNow": "2026-08-04T10:15:09Z",
  "locationReplaced": null,
  "routeEstimateStatus": "AVAILABLE",
  "routeDistanceMeters": 5400,
  "etaSeconds": 620,
  "etaCalculatedAt": "2026-08-04T10:15:04Z",
  "lastSuccessfulRouteDistanceMeters": 5400,
  "lastSuccessfulEtaSeconds": 620,
  "lastSuccessfulEtaCalculatedAt": "2026-08-04T10:15:04Z",
  "idempotentReplay": false
}
```

| `freshness` | 의미 | 앱 처리 |
|---|---|---|
| `NOT_RECEIVED` | 아직 위치 없음 | 좌표·시각·ETA는 `null`, 위치 대기 표시 |
| `CURRENT` | 마지막 서버 수신 후 30초 미만 | 현재 위치 표시 |
| `STALE` | 마지막 서버 수신 후 30초 이상 | 마지막 좌표 유지, `ageSeconds`로 경과시간 표시 |

- PUT 응답에서만 `locationReplaced`가 `true` 또는 `false`입니다. GET은 `null`입니다.
- 목적지가 없으면 route/ETA 필드는 `null`입니다.
- `CALCULATING`: 최신 위치 기준 계산 중입니다.
- `AVAILABLE`: 현재 계산 성공값을 표시합니다.
- `UNAVAILABLE`: 현재 계산 실패입니다. 마지막 성공 필드가 있으면 시각과 함께 보조
  정보로 표시할 수 있지만 현재값으로 오인하지 않습니다.

## 기존 병원 탐색 응답 추가 필드

`GET /api/v1/transport-requests/{requestId}/hospital-search`의 각 `offers[]`에 다음
필드가 추가됩니다.

| 필드 | Nullable | 의미 |
|---|---:|---|
| `lastSuccessfulRouteDistanceMeters` | YES | 마지막 성공 도로 거리 |
| `lastSuccessfulEtaSeconds` | YES | 마지막 성공 ETA |
| `lastSuccessfulEtaCalculatedAt` | YES | 마지막 성공 계산 시각 |

현재 목적지가 바뀌면 새 목적지 기준 계산이 예약됩니다. 응답·SSE를 받은 뒤 병원
탐색 현황과 위치 조회를 다시 호출해 현재 목적지와 ETA를 함께 맞춥니다.

## 오류

| 코드 | HTTP | 조건 | 앱 처리 |
|---|---:|---|---|
| `COMMON_001` | 400 | body·조건부 필드·좌표·페이지·키 형식 오류 | 입력 수정; 명령 내용 변경 시 새 키 |
| `AUTH_001`, `AUTH_002` | 401 | 인증 없음·토큰 오류 | 토큰 갱신 뒤 같은 키·같은 body 재시도 |
| `AUTH_003`, `COMMON_004` | 403 | 역할·계정·EMS 조직 불일치 | 접근 차단, 운영자 확인 |
| `USER_002` | 403 | 비활성 계정 | 운영자 확인 |
| `TRANSPORT_001` | 404 | 요청 없음·다른 구급대원 소유 | 요청 ID 확인, 타 요청 정보 표시 금지 |
| `TRANSPORT_004` | 409 | 인계 요청·완료·취소 상태에서 갱신 | 위치 전송 중지, 권위 상태 재조회 |
| `PROTOCOL_002` | 409 | 현재 Pre-KTAS 표준 버전과 불일치 | 평가 프로토콜 재조회 후 새 입력 작성 |
| `COMMON_005` | 409 | 같은 키로 다른 명령 body 제출 | 최초 명령 복구 또는 새 키 사용 |

## 실시간 이벤트와 재조회

### `GET /api/v1/realtime/events`

구급대원 계정이 받는 새 위치 신호:

```text
AMBULANCE_LOCATION_UPDATED
```

기존 `ETA_UPDATED`, 목적지·철회 이벤트 계약도 유지됩니다.

```text
id: EVENT_UUID
event: update
data: {"eventId":"EVENT_UUID","type":"AMBULANCE_LOCATION_UPDATED","aggregateType":"TRANSPORT_REQUEST","aggregateId":"REQUEST_UUID","occurredAt":"2026-08-04T10:15:01Z"}
```

- SSE에는 임상값·좌표·연락처가 없습니다.
- 위치 PUT 응답으로 자기 화면을 즉시 갱신하고, SSE는 다른 연결·상태 복구용 신호로
  취급합니다.
- 이벤트 중복·누락을 허용하며 위치 GET·timeline GET·병원 탐색 GET을 최종 기준으로
  사용합니다.
- 연결 종료·앱 복귀·Access Token 갱신 뒤 SSE를 재연결하고 권위 API를 다시 조회합니다.

## 모바일 상태 복구

| 상황 | 서버 계약 | 앱 처리 |
|---|---|---|
| 임상·위치 응답 유실 | 같은 키·같은 body는 최초 결과 반환 | 키와 body를 유지해 재시도 |
| 같은 키 body 변경 | `COMMON_005`, 기존 결과 유지 | 최초 body 복구 또는 새 키 사용 |
| 오래된 임상 도착 | 이력 저장, `snapshotUpdated: false` 가능 | 성공으로 처리하고 최신 snapshot 유지 |
| 오래된 위치 도착 | `locationReplaced: false`, 최신 좌표 유지 | 성공으로 처리하고 응답 좌표 사용 |
| 30초 이상 위치 중단 | 마지막 좌표와 `STALE` 반환 | 경과시간 표시, 경고음·서버 오류로 처리하지 않음 |
| 지도 API 실패 | 위치 저장 성공, ETA `UNAVAILABLE` 가능 | 위치는 유지하고 ETA만 계산 불가 표시 |
| 앱 재실행·SSE 누락 | DB의 snapshot·timeline·최신 위치 유지 | 세 권위 조회 뒤 SSE 재연결 |

## 연동 확인

- [ ] 활력징후·의식·Pre-KTAS·처치 각각 신규 201과 동일 재시도 200
- [ ] 오래된 임상 `snapshotUpdated: false`와 timeline 원본 보존
- [ ] 임상 timeline 페이지·recordType별 본문 표시
- [ ] 최신 위치 한 건, 오래된 위치 `locationReplaced: false`
- [ ] `NOT_RECEIVED`·`CURRENT`·`STALE`와 `ageSeconds` 표시
- [ ] 목적지 유무에 따른 ETA nullable 처리
- [ ] `CALCULATING`·`AVAILABLE`·`UNAVAILABLE`과 마지막 성공값 구분
- [ ] 연결 복구 뒤 timeline·위치·병원 탐색 재조회
- [ ] `HANDOFF_REQUESTED`, `COMPLETED`, `CANCELLED`에서 위치 전송 중지
- [ ] 실제 환자정보·개인 연락처·정확한 실제 위치가 아닌 테스트 데이터로 Dev 연동
