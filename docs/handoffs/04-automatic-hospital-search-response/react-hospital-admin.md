# 자동 병원 탐색 및 병원 응답 React 병원·관리자 웹 핸드오프

> **사용 중지:** 현재 백엔드의 이전 계약 기록입니다. 2026-08-13 개정 정책 구현과 핸드오프 갱신 전에는 `NO_RESPONSE`를 새 병원 화면 계약으로 사용하지 않습니다.

```text
Feature: automatic-hospital-search-response
Backend Feature: docs/features/04-automatic-hospital-search-response/
Related PR: NONE
Available After: MAIN_MERGE
Breaking Change: NO
Hospital Impact: YES
Admin Impact: NONE
```

## 변경 요약

- 수신 상태가 `ON`인 활성 병원에는 반경 조건을 만족한 새 이송 요청이 자동 전달됩니다.
- 병원 공용 계정은 자기 병원의 활성·종료 목록과 최소 임상정보 상세를 조회하고
  수락 또는 필수 사유가 있는 거절을 할 수 있습니다.
- 수락은 목적지 확정이 아니라 현재 수용 가능 응답입니다. 이미 제안을 받은 다른
  병원도 추가로 수락할 수 있습니다.
- 네이버 ETA 실패는 병원 응답을 막지 않습니다.
- SSE는 새 요청·ETA 변경의 갱신 신호이고, 수신 뒤 목록 또는 상세를 다시 조회합니다.
- `SUPER_ADMIN`은 이 API와 환자 임상정보·위치·연락처에 접근할 수 없습니다.
- 병원 가입·로그인·수신 ON/OFF는 기능 2 핸드오프를 함께 사용합니다.

## 역할별 사용자 흐름

| 역할 | 순서 | 사용자·웹 동작 | API | 성공 후 상태 |
|---|---:|---|---|---|
| 병원 | 1 | 로그인 후 신규 요청 수신 ON | 기존 `PUT /api/v1/hospitals/me/receiving-status` | 이후 새 후보 탐색에 포함 |
| 병원 | 2 | 대시보드 활성 목록 조회 | `GET .../offers?view=ACTIVE` | `PENDING`·`ACCEPTED` 카드 표시 |
| 병원 | 3 | 요청 상세 확인 | `GET .../offers/{offerId}` | 최소 임상정보·회신 연락처·거리 확인 |
| 병원 | 4 | 수락 | `POST .../{offerId}/accept` | 제안 `ACCEPTED` |
| 병원 | 5 | 사유 선택 후 거절 | `POST .../{offerId}/reject` | 제안 `REJECTED` |
| 병원 | 6 | 종료 이력 조회 | `GET .../offers?view=HISTORY` | `REJECTED`·`NO_RESPONSE` 표시 |
| 병원 | 상시 | SSE 갱신 신호 수신 | `GET /api/v1/realtime/events` | 목록·상세 재조회 |
| 관리자 | - | 이 기능 API 호출 | 허용 안 됨 | `AUTH_003` |

## 공통 계약

- Base URL: `http://13.124.194.249`
- 인증: `Authorization: Bearer {accessToken}`
- 병원 역할: `HOSPITAL_STAFF`
- 시간: ISO-8601 UTC
- 다른 병원 조직의 `offerId`는 `TRANSPORT_005`로 숨깁니다.
- 응답에는 환자의 이름·주민등록번호·생년월일·연락처·상세 주소와 구급차의
  정확한 출발 좌표가 없습니다.

공통 오류:

```json
{
  "code": "ERROR_CODE",
  "message": "사용자에게 표시 가능한 메시지",
  "fieldErrors": [],
  "traceId": "TRACE_ID"
}
```

## API 1. 병원 제안 목록

### `GET /api/v1/hospitals/me/offers?view={view}&page={page}&size={size}`

- 성공: `200 OK`
- 기본값: `view=ACTIVE`, `page=0`, `size=20`
- `page`: 0 이상
- `size`: 1~100
- 정렬: `offeredAt` 오래된 순, 같은 시각은 서버 내부 안정 순서

| `view` | 포함 상태 |
|---|---|
| `ACTIVE` | `PENDING`, `ACCEPTED` |
| `HISTORY` | `REJECTED`, `NO_RESPONSE` |

응답:

```json
{
  "items": [
    {
      "offerId": "OFFER_UUID",
      "transportRequestId": "REQUEST_UUID",
      "dispatchAttemptNumber": 1,
      "transportRequestStatus": "SEARCHING",
      "offerStatus": "PENDING",
      "ageStatus": "ESTIMATED",
      "ageYears": 45,
      "sex": "UNKNOWN",
      "preKtasClassificationStatus": "COMPLETED",
      "preKtasLevel": 2,
      "preKtasExceptionReason": null,
      "straightLineDistanceMeters": 5230,
      "routeEstimateStatus": "AVAILABLE",
      "routeDistanceMeters": 6840,
      "etaSeconds": 780,
      "offeredAt": "2026-08-04T03:09:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "serverNow": "2026-08-04T03:09:10Z"
}
```

목록에는 회신 연락처와 상세 임상정보가 없습니다. 카드 선택 뒤 상세 API를 호출합니다.

### 목록 오류

| 코드 | HTTP | 조건 | 웹 처리 |
|---|---:|---|---|
| `COMMON_001` | 400 | page·size 범위 오류 | 기본 페이지 값으로 다시 조회 |
| `AUTH_001`, `AUTH_002` | 401 | 인증 오류 | 로그인 또는 토큰 갱신 |
| `AUTH_003` | 403 | 병원 역할 아님·조직 불일치 | 접근 차단 |
| `USER_002` | 403 | 비활성 계정 | 운영자 확인 |
| `HOSPITAL_001` | 404 | 현재 계정의 병원 프로필 없음 | 운영자 확인 |

## API 2. 병원 제안 상세

### `GET /api/v1/hospitals/me/offers/{offerId}`

- 성공: `200 OK`
- 자기 조직에 실제 전달된 제안만 허용합니다.

응답 구조:

```json
{
  "offerId": "OFFER_UUID",
  "transportRequestId": "REQUEST_UUID",
  "dispatchAttemptNumber": 1,
  "transportRequestStatus": "SEARCHING",
  "offerStatus": "PENDING",
  "patient": {
    "ageStatus": "ESTIMATED",
    "ageYears": 45,
    "sex": "UNKNOWN"
  },
  "incident": {
    "occurrenceType": "DISEASE",
    "injuryMechanism": null,
    "injurySites": [],
    "primarySymptom": "CHEST_PAIN",
    "primarySymptomDetail": null,
    "secondarySymptoms": ["DYSPNEA"],
    "onsetTimeStatus": "ESTIMATED",
    "onsetAt": "2026-08-04T03:00:00Z"
  },
  "preKtas": {
    "classificationStatus": "COMPLETED",
    "level": 2,
    "exceptionReason": null,
    "exceptionDetail": null,
    "assessedAt": "2026-08-04T03:00:00Z",
    "standardVersion": "DEV_UNCONFIRMED"
  },
  "consciousness": {
    "avpu": "A",
    "unassessableReason": null,
    "unassessableDetail": null,
    "observedAt": "2026-08-04T03:00:00Z"
  },
  "vitalSigns": {
    "measuredAt": "2026-08-04T03:00:00Z",
    "measurements": [
      {
        "type": "BLOOD_PRESSURE",
        "state": "VALUE",
        "primaryValue": 120,
        "secondaryValue": 80,
        "unavailableReason": null,
        "unavailableDetail": null
      }
    ]
  },
  "treatments": [
    {
      "type": "NONE",
      "attemptResult": null,
      "performedAt": null,
      "method": null,
      "device": null,
      "flowRateLpm": null,
      "currentStatus": null,
      "medicationName": null,
      "dose": null,
      "route": null,
      "site": null,
      "detail": null
    }
  ],
  "requester": {
    "organizationName": "테스트구급대",
    "callbackContact": "010-0000-0001"
  },
  "route": {
    "straightLineDistanceMeters": 5230,
    "status": "AVAILABLE",
    "routeDistanceMeters": 6840,
    "etaSeconds": 780,
    "calculatedAt": "2026-08-04T03:09:03Z"
  },
  "timing": {
    "requestReceivedAt": "2026-08-04T03:09:00Z",
    "offeredAt": "2026-08-04T03:09:00Z",
    "lastClinicalUpdateAt": "2026-08-04T03:09:00Z"
  },
  "rejectionReason": null,
  "rejectionDetail": null,
  "respondedAt": null,
  "serverNow": "2026-08-04T03:09:10Z"
}
```

### 회신 연락처 노출

| 제안 상태 | `requester.callbackContact` |
|---|---|
| `PENDING`, `ACCEPTED` | 요청 생성 당시 구급대원 회신 연락처 원문 |
| `REJECTED`, `NO_RESPONSE` | `****-0001`처럼 마지막 네 자리만 보임 |

연락처 원문을 브라우저 콘솔·분석도구·오류 보고 본문에 남기지 않습니다.

### 활력징후 상태

| `state` | 값 계약 |
|---|---|
| `VALUE` | `primaryValue`, 혈압만 `secondaryValue` 사용 |
| `MEASUREMENT_UNAVAILABLE` | 수치 없음, `unavailableReason`과 조건부 상세 사용 |
| `PATIENT_REFUSED` | 수치·사유 없음 |

측정 종류는 `BLOOD_PRESSURE`, `PULSE`, `RESPIRATORY_RATE`, `TEMPERATURE`, `SPO2`입니다.

### ETA 상태

| 상태 | 처리 |
|---|---|
| `CALCULATING` | 직선거리 표시, 도로 거리·ETA는 계산 중 |
| `AVAILABLE` | 도로 거리와 `etaSeconds` 표시 |
| `UNAVAILABLE` | 지도 계산 실패 표시, 수락·거절 버튼은 계속 사용 가능 |

### 상세 오류

| 코드 | HTTP | 조건 | 웹 처리 |
|---|---:|---|---|
| `TRANSPORT_005` | 404 | 제안 없음 또는 다른 병원 조직 | 목록으로 이동, 존재 여부 추정 금지 |
| `AUTH_001`, `AUTH_002` | 401 | 인증 오류 | 로그인 또는 토큰 갱신 후 재조회 |
| `AUTH_003` | 403 | 병원 역할 아님 | 접근 차단 |

## API 3. 수락

### `POST /api/v1/hospitals/me/offers/{offerId}/accept`

- 성공·동일 재시도: `200 OK`
- 헤더: `Idempotency-Key` 필수
- 요청 본문: 없음
- `PENDING` 제안만 새로 수락할 수 있습니다.

```http
Idempotency-Key: accept-offer-20260804-01
```

```json
{
  "offerId": "OFFER_UUID",
  "offerStatus": "ACCEPTED",
  "transportRequestId": "REQUEST_UUID",
  "transportRequestStatus": "ACCEPTED_AVAILABLE",
  "respondedAt": "2026-08-04T03:12:00Z",
  "idempotentReplay": false
}
```

- 응답 유실 시 같은 `offerId`와 같은 키로 재시도하면
  `idempotentReplay: true`와 최초 결과를 반환합니다.
- 다른 병원이 먼저 수락했어도 이미 받은 자기 `PENDING` 제안은 수락할 수 있습니다.

## API 4. 거절

### `POST /api/v1/hospitals/me/offers/{offerId}/reject`

- 성공·동일 재시도: `200 OK`
- 헤더: `Idempotency-Key` 필수
- `PENDING` 제안만 새로 거절할 수 있습니다.

```json
{
  "reason": "SPECIALIST_UNAVAILABLE",
  "detail": null
}
```

거절 사유:

```text
ER_GENERAL_BED_SHORTAGE
ISOLATION_BED_SHORTAGE
OPERATING_ROOM_SHORTAGE
ICU_SHORTAGE
SPECIALIST_UNAVAILABLE
EQUIPMENT_UNAVAILABLE
OTHER
```

- `reason`은 필수입니다.
- `OTHER`는 공백이 아닌 `detail`이 필수이며 최대 200자입니다.
- 응답 형식은 수락과 같고 `offerStatus: REJECTED`입니다.

## 수락·거절 공통 오류

| 코드 | HTTP | 조건 | 웹 처리 |
|---|---:|---|---|
| `COMMON_001` | 400 | 키 형식·사유·OTHER 상세 오류 | 입력 수정, 새 유효 키 사용 |
| `AUTH_001`, `AUTH_002` | 401 | 인증 오류 | 토큰 갱신 후 같은 키로 재시도 |
| `TRANSPORT_005` | 404 | 제안 없음·다른 조직 | 목록 재조회 |
| `TRANSPORT_006` | 409 | 이미 다른 응답·무응답으로 확정 | 상세 재조회 후 확정 상태 표시 |
| `COMMON_005` | 409 | 같은 키로 다른 수락·거절 내용 | 최초 명령을 복구하거나 새 키 사용 |

`Idempotency-Key`는 8~100자, `[A-Za-z0-9._:-]`입니다. 버튼 클릭부터 응답 확정까지
키와 명령 내용을 보관하고 네트워크 오류에는 그대로 재사용합니다.

## API 5. 실시간 갱신 신호

### `GET /api/v1/realtime/events`

- 요청 헤더: `Authorization: Bearer {accessToken}`
- 응답: `200 OK`, `Content-Type: text/event-stream`
- 토큰 query parameter는 지원하지 않습니다.
- 서버 연결은 약 14분 뒤 종료될 수 있으므로 새 토큰으로 재연결합니다.
- 응답 헤더: `Cache-Control: no-cache`, `X-Accel-Buffering: no`

```text
id: EVENT_UUID
event: update
data: {"eventId":"EVENT_UUID","type":"TRANSPORT_REQUEST_RECEIVED","aggregateType":"HOSPITAL_OFFER","aggregateId":"OFFER_UUID","occurredAt":"2026-08-04T03:09:00Z"}
```

병원이 받을 수 있는 타입:

```text
TRANSPORT_REQUEST_RECEIVED
ETA_UPDATED
```

| 이벤트 | 재조회 |
|---|---|
| `TRANSPORT_REQUEST_RECEIVED` | `view=ACTIVE` 목록 재조회 |
| `ETA_UPDATED` | 해당 `aggregateId` 상세 또는 활성 목록 재조회 |
| 연결 재개·브라우저 복귀 | 활성 목록 전체 재조회 |

- 이벤트에는 환자정보·연락처·좌표가 없습니다.
- heartbeat와 `connected` 이벤트에는 상태 데이터가 없습니다.
- 이벤트 중복·누락을 허용하고 REST 조회 결과를 최종 기준으로 사용합니다.

## 관리자 접근

- `SUPER_ADMIN`이 목록·상세·수락·거절·SSE를 호출하면 `AUTH_003`입니다.
- 조직 관리 응답에는 조직 `status`가 추가되지만 기존 필드는 유지됩니다.
- 관리자는 이 기능으로 병원 응답, 환자 임상정보, 구급대원 연락처와 위치를
  조회하지 않습니다.

## 웹 상태 복구

| 상황 | 서버 계약 | 웹 처리 |
|---|---|---|
| 새 요청 SSE 누락 | 활성 목록은 항상 권위 상태 | 화면 진입·재연결 때 전체 재조회 |
| 수락·거절 응답 유실 | 같은 키·명령은 최초 결과 반환 | 같은 키로 재시도 |
| `TRANSPORT_006` | 다른 응답이 먼저 확정됨 | 상세·목록 재조회 |
| ETA 지연·실패 | 제안 응답과 독립 | 수락·거절은 계속 허용 |
| Access Token 만료 | REST·SSE 인증 실패 | 토큰 갱신, SSE 재연결, 활성 목록 재조회 |
| 수신 상태 OFF 전환 | 이미 받은 제안 응답 권한 유지 | 기존 활성 카드 유지, 새 요청만 중단 |

## 연동 확인

- [ ] 수신 ON 뒤 새 요청 SSE와 활성 목록 표시
- [ ] 다른 병원 제안 `TRANSPORT_005`
- [ ] 상세 임상정보와 정확한 출발 좌표 미노출
- [ ] PENDING 연락처 원문, 종료 이력 연락처 마스킹
- [ ] 수락 성공·같은 키 재시도·다른 응답 충돌
- [ ] 모든 거절 사유와 OTHER 상세 검증
- [ ] `CALCULATING`·`AVAILABLE`·`UNAVAILABLE` 표시
- [ ] SSE Authorization·14분 재연결·목록 재조회
- [ ] 슈퍼 관리자 접근 차단
- [ ] 실제 환자정보·연락처가 아닌 테스트 데이터로 Dev 연동
