# 이송 중 환자·위치 갱신 React 병원·관리자 웹 핸드오프

```text
Feature: in-transit-patient-location-updates
Backend Feature: docs/features/06-in-transit-patient-location-updates/
Related PR: NONE
Available After: MAIN_MERGE
Breaking Change: NO
Hospital Impact: YES
Admin Impact: NONE
```

## 변경 요약

- 임상정보 공개 권한이 있는 병원은 최신 환자 snapshot과 이송 중 추가된 임상
  원본 이력을 시간순으로 조회할 수 있습니다.
- 현재 목적지 병원만 구급차의 정확한 최신 위치, 수신 경과시간과 동적 ETA를
  조회할 수 있습니다.
- 목적지 선택 전에는 `PENDING`과 목적지가 없는 `ACCEPTED` 제안이 임상정보를
  볼 수 있지만 정확한 위치는 어떤 병원에도 공개되지 않습니다.
- 목적지 선택·변경 즉시 임상 이력과 정확한 위치 권한은 현재 목적지 병원 한 곳으로
  제한됩니다.
- 병원 목록에 `lastClinicalUpdateAt`과 마지막 성공 ETA 필드가, 상세에 같은 최신
  임상 시각과 마지막 성공 ETA 필드가 추가됩니다.
- `STALE`은 서버 오류가 아니라 마지막 위치 수신 후 30초가 지난 표시 상태입니다.
- `SUPER_ADMIN`은 임상 timeline·정확한 위치를 조회할 수 없습니다.

## 역할별 사용자 흐름

| 역할 | 순서 | 사용자·웹 동작 | API | 성공 후 상태 |
|---|---:|---|---|---|
| 병원 | 1 | ACTIVE 제안 카드 조회 | 제안 목록 GET | `lastClinicalUpdateAt`으로 갱신 여부 확인 |
| 병원 | 2 | 임상 상세 화면 진입 | 제안 상세·임상 timeline GET | 최신 요약과 시간순 원본 표시 |
| 병원 | 3 | 현재 목적지 카드의 위치 화면 진입 | 위치 GET | 마지막 좌표·freshness·ETA 표시 |
| 병원 | 4 | 임상·위치·ETA SSE 수신 | 권한 있는 상세·timeline·위치 재조회 | 서버 권위 상태로 갱신 |
| 병원 | 5 | 목적지 변경으로 404 수신 | ACTIVE·HISTORY 재조회 | 임상·위치 화면 닫고 최소 이력으로 전환 |
| 관리자 | - | 임상·위치 API 호출 | 허용 안 됨 | `AUTH_003` |

## 공통 계약

- Base URL: `http://13.124.194.249`
- 인증: `Authorization: Bearer {accessToken}`
- 병원 역할: `HOSPITAL_STAFF`
- 시간: ISO-8601 UTC 문자열
- 다른 병원 조직의 `offerId`와 권한이 끝난 제안은 `TRANSPORT_005`로 숨깁니다.
- 병원은 임상정보와 위치를 조회만 하며 추가·수정·삭제할 수 없습니다.

공통 오류:

```json
{
  "code": "ERROR_CODE",
  "message": "사용자에게 표시 가능한 메시지",
  "fieldErrors": [],
  "traceId": "TRACE_ID"
}
```

## API 1. 병원 제안 목록 추가 필드

### `GET /api/v1/hospitals/me/offers?view={view}&page={page}&size={size}`

- 성공: `200 OK`
- 기본값: `view=ACTIVE`, `page=0`, `size=20`
- `page`: 0 이상, `size`: 1~100
- 기존 ACTIVE/HISTORY 구분과 최소 이력 계약은 유지됩니다.

목록 item에 추가된 필드:

| 필드 | Nullable | 의미 |
|---|---:|---|
| `lastClinicalUpdateAt` | YES | 서버가 마지막 임상 원본을 받아들인 시각; 최소 이력에서는 `null` |
| `lastSuccessfulRouteDistanceMeters` | YES | 마지막으로 성공한 도로 거리 |
| `lastSuccessfulEtaSeconds` | YES | 마지막으로 성공한 ETA |
| `lastSuccessfulEtaCalculatedAt` | YES | 마지막 성공 계산 시각 |

```json
{
  "offerId": "OFFER_UUID",
  "transportRequestId": "REQUEST_UUID",
  "transportRequestStatus": "EN_ROUTE",
  "offerStatus": "ACCEPTED",
  "currentDestination": true,
  "routeEstimateStatus": "UNAVAILABLE",
  "routeDistanceMeters": null,
  "etaSeconds": null,
  "lastSuccessfulRouteDistanceMeters": 5400,
  "lastSuccessfulEtaSeconds": 620,
  "lastSuccessfulEtaCalculatedAt": "2026-08-04T10:14:00Z",
  "lastClinicalUpdateAt": "2026-08-04T10:13:06Z"
}
```

- `lastClinicalUpdateAt`이 바뀌면 상세 또는 timeline을 다시 조회합니다.
- `routeEstimateStatus: UNAVAILABLE`이면 마지막 성공값을 현재 ETA로 표시하지 않습니다.
  별도의 “마지막 계산” 정보로 시각과 함께 표시할 수 있습니다.
- 목적지 선택 뒤 비목적지 `ACCEPTED` HISTORY처럼 임상 공개가 끝난 최소 item에는
  임상 시각·거리·ETA가 모두 `null`입니다.

## API 2. 병원 제안 상세 추가 필드

### `GET /api/v1/hospitals/me/offers/{offerId}`

- 성공: `200 OK`
- 허용: 목적지 없는 요청의 `PENDING`·`ACCEPTED`, 또는 현재 목적지 `ACCEPTED`
- 목적지가 정해진 뒤 비목적지 수락·철회 제안은 기존처럼 `404 TRANSPORT_005`입니다.

추가 필드:

| 위치 | 필드 | Nullable | 의미 |
|---|---|---:|---|
| `timing` | `lastClinicalUpdateAt` | NO | 최신 임상 수신 시각 |
| `route` | `lastSuccessfulRouteDistanceMeters` | YES | 마지막 성공 도로 거리 |
| `route` | `lastSuccessfulEtaSeconds` | YES | 마지막 성공 ETA |
| `route` | `lastSuccessfulCalculatedAt` | YES | 마지막 성공 계산 시각 |

상세의 기존 `preKtas`, `consciousness`, `vitalSigns`, `treatments`는 새 임상 기록이
최신 snapshot으로 반영된 경우 갱신됩니다. 늦게 도착한 과거 기록은 timeline에는
나오지만 최신 상세값을 되돌리지 않습니다.

## API 3. 임상 timeline 조회

### `GET /api/v1/hospitals/me/offers/{offerId}/clinical-timeline?page=0&size=50`

- 성공: `200 OK`
- `page`: 0 이상, `size`: 1~100
- 정렬: `clinicalAt ASC`, 같은 시각은 `serverReceivedAt ASC`, 그다음 record ID

### 접근 범위

| 제안·목적지 상태 | 결과 |
|---|---|
| `PENDING`, 현재 목적지 없음 | 허용 |
| `ACCEPTED`, 현재 목적지 없음 | 허용 |
| `ACCEPTED`, 자기 병원이 현재 목적지 | 허용 |
| 다른 병원이 현재 목적지 | `404 TRANSPORT_005` |
| `REJECTED`, `NO_RESPONSE`, `ACCEPTANCE_WITHDRAWN` | `404 TRANSPORT_005` |
| 요청 `COMPLETED`, `CANCELLED` | `404 TRANSPORT_005` |

`HANDOFF_REQUESTED`에서는 현재 목적지 병원이 마지막 임상정보를 계속 조회할 수
있습니다. 새 임상·위치 입력은 이미 차단되며 최종 이력 화면 전환은 인계·종료 기능
계약에서 확정합니다.

### 응답

```json
{
  "transportRequestId": "REQUEST_UUID",
  "latestSnapshot": {
    "preKtas": {
      "classificationStatus": "COMPLETED",
      "level": 2,
      "exceptionReason": null,
      "exceptionDetail": null,
      "assessedAt": "2026-08-04T10:12:00Z",
      "standardVersion": "DEV_UNCONFIRMED"
    },
    "consciousness": {
      "avpu": "V",
      "unassessableReason": null,
      "unassessableDetail": null,
      "observedAt": "2026-08-04T10:11:00Z"
    },
    "vitalSigns": {
      "measuredAt": "2026-08-04T10:10:00Z",
      "measurements": [
        {"type":"SPO2","state":"VALUE","primaryValue":94,"secondaryValue":null,"unavailableReason":null,"unavailableDetail":null}
      ]
    },
    "treatments": [],
    "lastClinicalUpdateAt": "2026-08-04T10:13:06Z"
  },
  "items": [
    {
      "recordType": "CONSCIOUSNESS",
      "recordId": "RECORD_UUID",
      "clinicalAt": "2026-08-04T10:11:00Z",
      "enteredAt": "2026-08-04T10:11:05Z",
      "serverReceivedAt": "2026-08-04T10:11:06Z",
      "preKtas": null,
      "consciousness": {"avpu":"V","unassessableReason":null,"unassessableDetail":null,"observedAt":"2026-08-04T10:11:00Z"},
      "vitalSigns": null,
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

### recordType과 Enum

| `recordType` | 값이 있는 본문 |
|---|---|
| `VITAL_SIGNS` | `vitalSigns` |
| `CONSCIOUSNESS` | `consciousness` |
| `PRE_KTAS` | `preKtas` |
| `TREATMENT` | `treatment` |

- AVPU: `A`, `V`, `P`, `U`, `UNASSESSABLE`
- Pre-KTAS 상태: `COMPLETED`, `EMERGENCY_UNFINISHED`
- 활력 종류: `BLOOD_PRESSURE`, `PULSE`, `RESPIRATORY_RATE`, `TEMPERATURE`, `SPO2`
- 활력 상태: `VALUE`, `MEASUREMENT_UNAVAILABLE`, `PATIENT_REFUSED`
- 처치 결과: `SUCCESS`, `FAILURE`, `ONGOING`, `NOT_APPLICABLE`
- timeline item은 recordType에 해당하는 본문 하나만 값이 있고 나머지는 `null`입니다.
- `clinicalAt`은 의료 발생 시각, `serverReceivedAt`은 서버 도착 시각입니다.

## API 4. 현재 목적지 최신 위치 조회

### `GET /api/v1/hospitals/me/offers/{offerId}/location`

- 성공: `200 OK`
- 정확한 좌표이므로 자기 병원의 `ACCEPTED` 제안이 현재 목적지일 때만 허용됩니다.
- 목적지 선택 전, 이전 목적지, 비목적지 수락 병원은 `404 TRANSPORT_005`입니다.

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

### 위치·ETA 상태

| 값 | 의미 | 웹 처리 |
|---|---|---|
| `NOT_RECEIVED` | 아직 위치 없음 | 좌표·시각·ETA가 `null`, 위치 대기 표시 |
| `CURRENT` | 마지막 서버 수신 후 30초 미만 | 현재 위치 표시 |
| `STALE` | 마지막 서버 수신 후 30초 이상 | 마지막 좌표 유지, `ageSeconds`로 경과시간 표시 |
| ETA `CALCULATING` | 최신 위치 기준 재계산 중 | 로딩 표시, 기존값을 현재값으로 표시하지 않음 |
| ETA `AVAILABLE` | 현재 계산 성공 | 도로 거리와 ETA 표시 |
| ETA `UNAVAILABLE` | 현재 계산 실패 | 계산 불가 표시; 마지막 성공값은 시각과 함께 보조 표시 가능 |

- 새 위치가 없다고 경고음이나 서버 장애로 처리하지 않습니다.
- `serverNow`와 `ageSeconds`를 기준으로 표시하고 브라우저 시계만으로 freshness를
  다시 판정하지 않습니다.
- 목적지 변경 뒤 이전 병원이 이 API를 다시 호출하면 즉시 `TRANSPORT_005`입니다.

## 오류

| 코드 | HTTP | 조건 | 웹 처리 |
|---|---:|---|---|
| `COMMON_001` | 400 | page·size 범위 오류 | 0 이상 page, 1~100 size로 수정 |
| `AUTH_001`, `AUTH_002` | 401 | 인증 없음·토큰 오류 | 토큰 갱신 후 재조회 |
| `AUTH_003` | 403 | 병원 역할이 아니거나 슈퍼 관리자 | 접근 차단 |
| `USER_002` | 403 | 비활성 계정 | 운영자 확인 |
| `TRANSPORT_005` | 404 | 다른 병원 조직·권한 종료·비목적지 위치 조회 | 상세 닫기, ACTIVE·HISTORY 재조회 |

## 화면 상태 조건

| 대상 | 조건 | 웹 처리 |
|---|---|---|
| 임상 갱신 표시 | 목록 `lastClinicalUpdateAt` 변경 | 허용된 상세·timeline 재조회 |
| timeline 표시 | 목적지 없음의 PENDING/ACCEPTED 또는 현재 목적지 | 페이지와 latestSnapshot 표시 |
| 임상 화면 종료 | timeline `TRANSPORT_005` | 환자정보 제거, 목록 최소 이력만 유지 |
| 위치 화면 표시 | `currentDestination: true`, `offerStatus: ACCEPTED` | 위치 GET 시작 |
| 위치 화면 종료 | 목적지 이벤트 또는 위치 GET `TRANSPORT_005` | 좌표·ETA 즉시 제거 |
| 오래된 위치 | `freshness: STALE` | 마지막 수신 경과시간과 마지막 위치 표시 |
| 현재 ETA 실패 | `routeEstimateStatus: UNAVAILABLE` | 마지막 성공값과 현재 실패 상태를 분리 표시 |

## 실시간 이벤트와 재조회

### `GET /api/v1/realtime/events`

병원 조직이 받을 수 있는 새 `type`:

```text
VITAL_SIGNS_ADDED
CONSCIOUSNESS_CHANGED
PRE_KTAS_CHANGED
TREATMENT_ADDED
AMBULANCE_LOCATION_UPDATED
```

| 이벤트 | 수신 대상 | 재조회 |
|---|---|---|
| 임상 4종 | 목적지 전 PENDING·ACCEPTED 병원, 목적지 후 현재 목적지 | 목록·상세·timeline |
| `AMBULANCE_LOCATION_UPDATED` | 현재 목적지 병원만 | 위치 GET |
| 기존 `ETA_UPDATED` | 기존 계약의 대상 | 위치 GET 또는 제안 상세 |

```text
id: EVENT_UUID
event: update
data: {"eventId":"EVENT_UUID","type":"VITAL_SIGNS_ADDED","aggregateType":"TRANSPORT_REQUEST","aggregateId":"REQUEST_UUID","occurredAt":"2026-08-04T10:10:06Z"}
```

- 이벤트에는 임상값·정확한 좌표·연락처가 없습니다.
- SSE는 변경 신호이며 REST 응답이 최종 권위 상태입니다.
- 목적지 선택·변경 이벤트와 임상·위치 이벤트가 가까이 도착하면 먼저 ACTIVE·
  HISTORY를 재조회하고, 현재 허용된 카드에 대해서만 상세 API를 호출합니다.
- 연결 종료·브라우저 복귀·Access Token 갱신 뒤 SSE를 재연결하고 목록부터 다시 조회합니다.

## 관리자 접근

- `SUPER_ADMIN`은 제안 목록·상세·timeline·위치·병원 SSE를 사용할 수 없습니다.
- 관리자에게 임상정보·정확한 위치·동적 ETA를 제공하는 새 API는 없습니다.
- 관리자 화면 변경은 없습니다.

## 웹 상태 복구

| 상황 | 서버 계약 | 웹 처리 |
|---|---|---|
| SSE 누락·중복 | SSE는 권위 상태가 아님 | 화면 진입·복귀 때 목록부터 재조회 |
| 목적지 변경과 임상 갱신 경합 | 요청 잠금 후 최종 목적지만 권한 보유 | `TRANSPORT_005`면 상세 제거, 새 목록 사용 |
| 위치 전송 중단 | 마지막 좌표와 `STALE` 유지 | 경과시간 표시, 좌표를 임의로 삭제하지 않음 |
| 지도 API 실패 | 위치 조회 성공, ETA만 `UNAVAILABLE` | 위치 유지, ETA 계산 불가 표시 |
| Access Token 만료 | REST·SSE 인증 실패 | 토큰 갱신, SSE 재연결, 목록·상세 재조회 |

## 연동 확인

- [ ] ACTIVE 카드의 `lastClinicalUpdateAt` 갱신
- [ ] 임상 timeline 시간순 페이지와 recordType별 본문 표시
- [ ] 목적지 전 PENDING·ACCEPTED 임상 조회와 위치 조회 차단
- [ ] 목적지 후 현재 목적지만 임상·정확한 위치 조회
- [ ] 목적지 변경 직후 이전 병원 `TRANSPORT_005`와 민감정보 제거
- [ ] `NOT_RECEIVED`·`CURRENT`·`STALE`와 경과시간 표시
- [ ] ETA 현재 상태와 마지막 성공값 분리 표시
- [ ] 임상·위치 SSE 수신 뒤 권위 REST 재조회
- [ ] 슈퍼 관리자 접근 차단
- [ ] 실제 환자정보·연락처·정확한 실제 위치가 아닌 테스트 데이터로 Dev 연동
