# MVP 병원 탐색 지속 계약 Flutter 구급대원 앱 핸드오프

```text
Feature: mvp-hospital-search-continuation
Backend Feature: docs/features/16-mvp-hospital-search-continuation/
Related PR: NONE
Available After: MAIN_MERGE
Breaking Change: YES
Supersedes: docs/handoffs/04-automatic-hospital-search-response/flutter-paramedic.md
```

## 변경 요약

- 최대 100km에 도달해도 이송 요청은 `SEARCHING`입니다.
- 병원이 응답하지 않으면 해당 제안은 계속 `PENDING`입니다.
- `CANDIDATES_EXHAUSTED`, 소진 사유, 전체 재전송 UI를 새 요청에 사용하지 않습니다.
- `POST .../{requestId}/dispatch-attempts`는 제거되어 호출하면 `404`입니다.
- 첫 병원 수락 뒤에는 기존처럼 `ACCEPTED_AVAILABLE`이 되고 자동 확대가 중단됩니다.

## 사용자 흐름

| 순서 | 앱 동작 | API | 결과 |
|---:|---|---|---|
| 1 | 이송 요청 생성 | `POST /api/v1/transport-requests` | `SEARCHING` |
| 2 | 탐색 화면 진입·복구 | `GET .../{requestId}/hospital-search` | 현재 반경·병원 응답 표시 |
| 3 | SSE 갱신 수신 | `GET /api/v1/realtime/events` | 탐색 현황 재조회 |
| 4 | 최대 반경 도달 | 탐색 현황 재조회 | `SEARCHING`, 다음 확대 없음 |
| 5 | 병원 수락 | 탐색 현황 재조회 | `ACCEPTED_AVAILABLE`, 수락 병원 선택 가능 |

## 인증과 접근

| 항목 | 계약 |
|---|---|
| Base URL | `http://13.124.194.249` |
| 인증 | `Authorization: Bearer {accessToken}` |
| 역할 | `PARAMEDIC` |
| 소유권 | 로그인한 구급대원이 자기 EMS 조직에서 생성한 요청만 조회 |

## 탐색 현황 API

### `GET /api/v1/transport-requests/{requestId}/hospital-search`

- 성공: `200 OK`
- 호출 시점: 화면 진입, 앱 복구, SSE 수신·재연결 뒤

최대 반경 대기 예시:

```json
{
  "transportRequestId": "REQUEST_UUID",
  "status": "SEARCHING",
  "currentDestinationOfferId": null,
  "currentAttempt": {
    "dispatchAttemptId": "ATTEMPT_UUID",
    "number": 1,
    "status": "SEARCHING",
    "currentRadiusKm": 100,
    "candidateShortage": true,
    "nextExpansionAt": null,
    "startedAt": "2026-08-15T01:00:00Z",
    "endedAt": null
  },
  "exhaustionReason": null,
  "offers": [
    {
      "offerId": "OFFER_UUID",
      "dispatchAttemptNumber": 1,
      "hospitalName": "테스트병원",
      "hospitalContact": null,
      "status": "PENDING",
      "currentDestination": false,
      "straightLineDistanceMeters": 5230,
      "routeEstimateStatus": "AVAILABLE",
      "routeDistanceMeters": 6840,
      "etaSeconds": 780,
      "etaCalculatedAt": "2026-08-15T01:00:03Z",
      "lastSuccessfulRouteDistanceMeters": 6840,
      "lastSuccessfulEtaSeconds": 780,
      "lastSuccessfulEtaCalculatedAt": "2026-08-15T01:00:03Z",
      "rejectionReason": null,
      "rejectionDetail": null,
      "withdrawalReason": null,
      "withdrawalDetail": null,
      "offeredAt": "2026-08-15T01:00:00Z",
      "respondedAt": null,
      "withdrawnAt": null,
      "closedAt": null
    }
  ],
  "serverNow": "2026-08-15T01:02:00Z"
}
```

## 화면 상태

| 서버 값 | 앱 처리 |
|---|---|
| `status=SEARCHING`, `nextExpansionAt` 존재 | 병원 응답 대기와 다음 탐색 시각 표시 |
| `status=SEARCHING`, `nextExpansionAt=null` | 최대 반경에서 병원 응답 대기 표시 |
| 제안 `PENDING` | 응답 대기 카드 유지 |
| 제안 `REJECTED` | 거절 사유 표시, 재전송 버튼 없음 |
| 제안 `ACCEPTED` | 선택 가능한 수락 병원 카드 표시, 연락처 사용 가능 |
| `status=ACCEPTED_AVAILABLE` | 수락 병원 중 목적지 선택 허용 |

`CANDIDATES_EXHAUSTED`, `NO_RESPONSE`, `EXHAUSTED` enum은 과거 데이터 호환용입니다.
새 요청의 정상 화면 상태로 사용하지 않습니다. `exhaustionReason`도 새 요청에서는 `null`입니다.

## 제거된 계약

| 항목 | 처리 |
|---|---|
| 전체 재전송 API | 호출하지 않음; 경로는 `404` |
| 후보 소진 화면 | 구현하지 않음 |
| 전화 연결 복구 | MVP 제외 |
| 무응답 종료 타이머 | 구현하지 않음; `PENDING` 유지 |

## SSE와 재조회

새 요청에서 상태 갱신에 사용하는 이벤트:

```text
HOSPITAL_OFFER_ACCEPTED
HOSPITAL_OFFER_REJECTED
ETA_UPDATED
DESTINATION_SELECTED
DESTINATION_CHANGED
HOSPITAL_ACCEPTANCE_WITHDRAWN
```

`HOSPITAL_OFFER_NO_RESPONSE`, `HOSPITAL_SEARCH_EXHAUSTED`,
`HOSPITAL_SEARCH_RETRY_STARTED`는 새 흐름에서 생성되지 않습니다. SSE는 신호일 뿐이며
수신·재연결 뒤 탐색 현황 API를 권위 상태로 사용합니다.

## 오류

| 코드 | HTTP | 앱 처리 |
|---|---:|---|
| `AUTH_001`, `AUTH_002` | 401 | 로그인 또는 토큰 갱신 후 재조회 |
| `AUTH_003`, `USER_002` | 403 | 접근 차단·운영자 확인 |
| `TRANSPORT_001` | 404 | 요청 ID 확인, 다른 요청 정보 표시 금지 |
| 제거된 재전송 경로 | 404 | 호출 제거 |

## 연동 확인

- [ ] 최대 반경에서 대기 화면 유지
- [ ] `PENDING` 병원 카드 자동 종료 없음
- [ ] 수락 병원 카드와 목적지 선택 흐름
- [ ] 후보 소진·재전송·전화 UI 미사용
- [ ] SSE 재연결 뒤 권위 조회 복구
- [ ] dev API 연결
