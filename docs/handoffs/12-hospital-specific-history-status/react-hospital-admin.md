# 병원별 이송 이력 상태 및 처리 시각 React 병원·관리자 웹 핸드오프

> **사용 중지:** 현재 백엔드의 이전 계약 기록입니다. 2026-08-13 개정 정책 구현과 핸드오프 갱신 전에는 진행 중 `NOT_SELECTED`·`NO_RESPONSE`를 새 병원 화면 계약으로 사용하지 않습니다.

```text
Feature: hospital-specific-history-status
Backend Feature: docs/features/12-hospital-specific-history-status/
Related PR: NONE
Available After: MAIN_MERGE
Breaking Change: NO
Hospital Impact: YES
Admin Impact: NONE
```

> 병원 제안 목록·상세 응답의 최신 계약입니다. 기존 필드는 유지하면서 현재 로그인
> 병원 기준 결과인 `hospitalOutcome`과 그 결과가 확정된 `processedAt`을 추가했습니다.
> 병원 카드의 결과 문구는 전역 `transportRequestStatus`가 아니라
> `hospitalOutcome`으로 결정해야 합니다.

## 변경 요약

- 같은 이송 요청을 받은 병원마다 자기 결과를 구분해 표시할 수 있습니다.
- 목적지 병원에서 인계가 완료돼도 다른 병원 카드가 `인계 완료`로 표시되지 않습니다.
- 목적지 병원은 `HANDOFF_COMPLETED_HERE`, 다른 병원은 응답 이력에 따라
  `COMPLETED_ELSEWHERE`, `REJECTED`, `NO_RESPONSE` 또는
  `ACCEPTANCE_WITHDRAWN`으로 표시됩니다.
- 병원별 결과의 처리 시각을 `processedAt` 하나로 표시할 수 있습니다.
- 기존 `transportRequestStatus`, `offerStatus`, `currentDestination`과 모든 명령
  API는 변경되지 않았습니다.
- 슈퍼 관리자 API와 화면은 변경되지 않았습니다.

## 역할별 사용자 흐름

| 역할 | 순서 | 사용자·웹 동작 | API 호출 | 성공 후 상태 |
|---|---:|---|---|---|
| 병원 | 1 | 활성 카드와 이력 조회 | `GET .../offers?view=ACTIVE|HISTORY` | 각 item의 병원별 결과·처리 시각 표시 |
| 병원 | 2 | 허용된 활성·응답 상세 조회 | `GET .../offers/{offerId}` | 목록과 같은 병원별 결과 확인 |
| 병원 | 3 | 목적지·철회·취소·인계 SSE 수신 | ACTIVE·HISTORY 재조회 | 서버의 최신 병원별 결과로 카드 갱신 |
| 관리자 | - | 변경 없음 | 없음 | 환자·이송 정보 접근 없음 |

## 인증과 접근 범위

| 역할 | 인증 | 허용 작업 | 조직·정보 접근 범위 |
|---|---|---|---|
| 병원 관계자 | Bearer Access Token, 활성 `HOSPITAL_STAFF` | 자기 제안 목록과 허용된 상세 조회 | JWT의 자기 병원 조직 제안만 조회, 다른 병원 제안은 404 |
| 슈퍼 관리자 | Bearer Access Token | 이 기능의 병원 API 사용 불가 | 환자 임상·연락처·위치·목적지·병원별 결과 조회 불가 |

## 공통 계약

- Base URL: `http://13.124.194.249`
- Content-Type: `application/json`
- 시간: ISO-8601 UTC, 예: `2026-08-06T07:30:25.123456Z`
- JSON의 nullable 필드는 서버 설정에 따라 `null`이면 생략될 수 있습니다.
- `processedAt`은 서버 정밀도를 보존합니다. 화면에서는 사용자 시간대로 변환해
  시·분·초(`HH:mm:ss`)까지 표시합니다.

## API 1. 병원 제안 목록

### `GET /api/v1/hospitals/me/offers?view={view}&page={page}&size={size}`

- 인증·역할: Bearer, 활성 `HOSPITAL_STAFF`
- 성공 HTTP: `200 OK`
- 기본값: `view=ACTIVE`, `page=0`, `size=20`
- `page`: 0 이상, `size`: 1~100
- 정렬: `offeredAt` 오래된 순, 같은 시각은 서버 내부 안정 순서

각 `items[]`에 다음 두 필드가 추가됐습니다.

| 필드 | 타입 | Nullable | 설명 |
|---|---|---:|---|
| `hospitalOutcome` | enum | X | 현재 로그인 병원 기준 결과, 아래 enum 전체 목록 참고 |
| `processedAt` | string(datetime) | O | 결과 확정 서버 시각, 응답 대기는 `null` 또는 생략 |

최종 목적지 병원의 완료 이력 예시:

```json
{
  "items": [
    {
      "offerId": "DESTINATION_OFFER_UUID",
      "transportRequestId": "REQUEST_UUID",
      "transportRequestStatus": "COMPLETED",
      "offerStatus": "ACCEPTED",
      "hospitalOutcome": "HANDOFF_COMPLETED_HERE",
      "processedAt": "2026-08-06T07:30:25Z",
      "currentDestination": false,
      "completedAt": "2026-08-06T07:30:25Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "serverNow": "2026-08-06T07:31:00Z"
}
```

같은 요청을 수락했지만 목적지가 아니었던 병원의 이력 예시:

```json
{
  "offerId": "MY_OFFER_UUID",
  "transportRequestId": "REQUEST_UUID",
  "transportRequestStatus": "COMPLETED",
  "offerStatus": "ACCEPTED",
  "hospitalOutcome": "COMPLETED_ELSEWHERE",
  "processedAt": "2026-08-06T07:30:25Z",
  "currentDestination": false,
  "completedAt": "2026-08-06T07:30:25Z"
}
```

진행 중 다른 병원이 목적지로 선택된 경우:

```json
{
  "offerId": "MY_OFFER_UUID",
  "transportRequestId": "REQUEST_UUID",
  "transportRequestStatus": "EN_ROUTE",
  "offerStatus": "ACCEPTED",
  "hospitalOutcome": "NOT_SELECTED",
  "processedAt": "2026-08-06T07:20:21Z",
  "currentDestination": false,
  "canWithdraw": true
}
```

- `NOT_SELECTED.processedAt`은 다른 목적지가 실제로 선택·변경된 시각입니다.
- 같은 목적지를 멱등하게 다시 선택한 `UNCHANGED` 명령 시각으로 갱신되지 않습니다.
- `currentDestination`은 현재 진행 중인 활성 목적지 표시입니다. 종료 HISTORY에서는
  최종 목적지 병원도 `false`이므로 완료 병원 판정에 사용하지 않습니다.
- 종료·철회·비목적지 최소 이력의 기존 임상·연락처·좌표·거리·ETA 필드는
  `null` 또는 생략됩니다.

## API 2. 병원 제안 상세

### `GET /api/v1/hospitals/me/offers/{offerId}`

- 인증·역할: Bearer, 활성 `HOSPITAL_STAFF`
- 성공 HTTP: `200 OK`
- 조직 범위: 현재 로그인 병원에 전달된 자기 제안만
- 활성·허용된 응답 상세에 목록과 같은 `hospitalOutcome`, `processedAt`이 추가됐습니다.

```json
{
  "offerId": "OFFER_UUID",
  "transportRequestId": "REQUEST_UUID",
  "transportRequestStatus": "EN_ROUTE",
  "offerStatus": "ACCEPTED",
  "hospitalOutcome": "ACCEPTED",
  "processedAt": "2026-08-06T07:10:11Z",
  "currentDestination": true,
  "canWithdraw": true
}
```

> 예시는 이번 변경 필드만 표시합니다. 실제 응답에는 기존 환자 평가·요청자·경로·
> 시각·철회·인계 필드가 함께 있습니다.

- 목록과 상세를 동시에 조회할 수 있는 상태에서는 두 필드 값이 같습니다.
- 목적지 선택 뒤 숨겨진 비목적지 수락, 철회, 완료·취소 이력의 상세는 기존처럼
  `404 TRANSPORT_005`입니다. 이 상태는 HISTORY 최소 item만 사용합니다.

## 세 상태 필드의 차이

| 필드 | 범위 | 예시 | 화면 사용 |
|---|---|---|---|
| `transportRequestStatus` | 환자 이송 요청 전체 | `EN_ROUTE`, `COMPLETED` | 전체 흐름·명령 가능 조건 참고 |
| `offerStatus` | 자기 병원의 원래 응답 | `PENDING`, `ACCEPTED`, `REJECTED` | 응답·철회 사실 표시와 버튼 조건 참고 |
| `hospitalOutcome` | 자기 병원 카드의 현재 결과 | `NOT_SELECTED`, `HANDOFF_COMPLETED_HERE` | 카드 결과 배지와 제목의 최종 기준 |

`transportRequestStatus: COMPLETED`만 보고 `인계 완료` 배지를 표시하면 같은 오류가
재발합니다. 카드 결과 문구는 반드시 `hospitalOutcome`으로 결정합니다.

## HospitalOutcome 전체 목록

| 값 | 의미 | `processedAt` 기준 | 권장 표시 의미 |
|---|---|---|---|
| `AWAITING_RESPONSE` | 자기 병원이 아직 응답하지 않음 | `null` | 응답 대기 |
| `ACCEPTED` | 자기 병원이 수락했고 아직 다른 결과로 끝나지 않음 | `respondedAt` | 수락 |
| `REJECTED` | 자기 병원이 거절함 | `respondedAt` | 거절 |
| `NO_RESPONSE` | 응답 시간 안에 응답하지 않음 | 제안 `closedAt` | 무응답 |
| `ACCEPTANCE_WITHDRAWN` | 수락 뒤 자기 병원이 철회함 | `withdrawnAt` | 수락 철회 |
| `NOT_SELECTED` | 진행 중 다른 병원이 목적지로 선택됨 | 최신 실제 목적지 선택·변경 시각 | 미선택 |
| `HANDOFF_COMPLETED_HERE` | 자기 병원이 최종 목적지로 환자 인계를 완료함 | `completedAt` | 인계 완료 |
| `COMPLETED_ELSEWHERE` | 요청이 다른 병원에서 인계 완료됨 | `completedAt` | 타 병원 이송 완료 |
| `TRANSPORT_CANCELLED` | 수락·응답 대기 중 이송 자체가 취소됨 | `cancelledAt` | 이송 취소 |

거절·무응답·철회가 먼저 확정된 병원은 요청이 나중에 완료·취소돼도 기존 자기 결과를
유지합니다. 예를 들어 `transportRequestStatus: COMPLETED`, `offerStatus: REJECTED`인
item의 `hospitalOutcome`은 계속 `REJECTED`입니다.

## 화면 상태 조건

| 대상 | 조건 | 웹에서 필요한 처리 |
|---|---|---|
| 카드 결과 배지 | 모든 목록 item | `hospitalOutcome`으로 문구·색상 결정 |
| 처리 시각 | `processedAt` 존재 | 사용자 시간대로 변환해 초 단위까지 표시 |
| 응답 대기 시각 | `AWAITING_RESPONSE`, `processedAt` 없음 | 처리 시각을 표시하지 않음 |
| 최종 인계 완료 | `HANDOFF_COMPLETED_HERE` | 자기 병원 인계 완료로 표시 |
| 다른 병원 완료 | `COMPLETED_ELSEWHERE` | 자기 병원 인계 완료 문구를 사용하지 않음 |
| 목적지 표시 | 진행 중 `currentDestination: true` | 활성 목적지 표시; 종료 결과 판정에는 사용하지 않음 |
| 상세 링크 | 종료·숨겨진 최소 HISTORY item | 비활성화하고 목록의 최소 결과만 표시 |
| 상태 변경 | SSE 수신 또는 명령 성공·409·404 | ACTIVE와 HISTORY를 첫 페이지부터 다시 조회 |

## 오류 처리

이번 기능에서 새 오류 코드는 추가되지 않았습니다.

| 오류 코드 | HTTP | 발생 조건 | 웹에서 필요한 처리 |
|---|---:|---|---|
| `COMMON_001` | 400 | page·size·view 형식 오류 | 파라미터 수정 |
| `AUTH_001`, `AUTH_002` | 401 | 인증 없음·토큰 오류 | 토큰 갱신 뒤 재조회 |
| `AUTH_003`, `COMMON_004`, `USER_002` | 403 | 병원 역할·조직·계정 상태 오류 | 접근 차단·운영자 확인 |
| `HOSPITAL_001` | 404 | 현재 계정의 병원 프로필 없음 | 프로필·가입 상태 확인 |
| `TRANSPORT_005` | 404 | 제안 없음·다른 조직·숨겨진 상세 | 상세 닫고 자기 목록 재조회 |

공통 오류 응답:

```json
{
  "code": "TRANSPORT_005",
  "message": "병원 이송 요청을 찾을 수 없습니다.",
  "fieldErrors": [],
  "traceId": "TRACE_ID"
}
```

- 오류 문의 시 API 경로, HTTP 상태, `code`, `traceId`를 전달합니다.
- 환자정보·구급대원 연락처·정확한 위치·토큰 원문은 공유하지 않습니다.

## 실시간 이벤트와 재조회

- 기존 `GET /api/v1/realtime/events` 계약과 이벤트 종류는 변경되지 않았습니다.
- 목적지 선택·변경, 수락 철회, 이송 취소, 인계 완료 등 병원 카드에 영향을 주는
  이벤트를 받으면 ACTIVE와 HISTORY를 모두 다시 조회합니다.
- SSE payload에는 `hospitalOutcome`이 추가되지 않았습니다. SSE는 변경 신호이며
  REST 응답의 `hospitalOutcome`, `processedAt`이 최종 기준입니다.
- 연결은 약 14분 뒤 종료될 수 있습니다. 토큰 갱신 뒤 재연결하고 두 목록을
  재조회합니다.

## 관리자 접근

- `SUPER_ADMIN`은 병원 제안 목록·상세 API를 사용할 수 없습니다.
- 다른 병원이 최종 목적지라는 사실 외에 그 병원의 이름·조직 ID·제안 ID는
  비목적지 응답에 추가되지 않습니다.
- 이 기능으로 변경되는 관리자 전용 API는 없습니다.

## 연동 확인

- [ ] 카드 결과를 `transportRequestStatus`가 아닌 `hospitalOutcome`으로 표시
- [ ] `processedAt`을 사용자 시간대로 바꾸고 초 단위까지 표시
- [ ] `AWAITING_RESPONSE`의 처리 시각 없음 처리
- [ ] 목적지 병원 완료를 `HANDOFF_COMPLETED_HERE`로 표시
- [ ] 같은 요청의 비목적지 병원을 `COMPLETED_ELSEWHERE` 또는 기존 응답 결과로 표시
- [ ] 거절·무응답·철회가 요청 완료 뒤에도 자기 결과를 유지하는지 확인
- [ ] 진행 중 비목적지 `NOT_SELECTED`와 수락 철회 버튼 조건 확인
- [ ] 종료 HISTORY의 `currentDestination: false`를 최종 목적지 판정에 사용하지 않음
- [ ] 종료·숨겨진 이력에서 상세 링크와 민감정보 재조회 차단
- [ ] SSE 수신·재연결 뒤 ACTIVE와 HISTORY 재조회
- [ ] 다른 조직과 슈퍼 관리자 접근 차단
- [ ] 실제 환자정보·개인 연락처·정확한 실제 위치가 아닌 테스트 데이터로 Dev 연동
