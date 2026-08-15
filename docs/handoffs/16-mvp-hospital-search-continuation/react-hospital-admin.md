# MVP 병원 탐색 지속 계약 React 병원 웹 핸드오프

```text
Feature: mvp-hospital-search-continuation
Backend Feature: docs/features/16-mvp-hospital-search-continuation/
Related PR: NONE
Available After: MAIN_MERGE
Breaking Change: YES
Hospital Impact: YES
Admin Impact: NONE
Supersedes: docs/handoffs/04-automatic-hospital-search-response/react-hospital-admin.md
```

## 변경 요약

- 병원이 응답하지 않은 제안은 시간 경과나 최대 반경 도달로 종료되지 않습니다.
- `PENDING` 제안은 병원 ACTIVE 목록에 남고 계속 수락·거절할 수 있습니다.
- 새 요청에서는 `NO_RESPONSE` 카드와 후보 소진 알림이 발생하지 않습니다.
- 한 병원이 수락해도 다른 병원의 `PENDING`·`ACCEPTED` 카드는 활성 상태를 유지합니다.
- 슈퍼 관리자 API와 화면에는 영향이 없습니다.

## 사용자 흐름

| 순서 | 병원 웹 동작 | API | 결과 |
|---:|---|---|---|
| 1 | 새 요청 신호 수신 | `GET /api/v1/realtime/events` | ACTIVE 목록 재조회 |
| 2 | 활성 카드 조회 | `GET /api/v1/hospitals/me/offers?view=ACTIVE` | `PENDING` 카드 유지 |
| 3 | 환자 정보 확인 | `GET .../offers/{offerId}` | 자기 병원 제안 상세 |
| 4 | 수락 또는 거절 | 기존 응답 API | `ACCEPTED` 또는 `REJECTED` |
| 5 | 다른 병원 목적지 선택 | SSE 뒤 목록 재조회 | 비목적지 활성 카드 유지 |

## 인증과 접근

| 역할 | 인증 | 허용 범위 |
|---|---|---|
| `HOSPITAL_STAFF` | `Authorization: Bearer {accessToken}` | 자기 병원 조직에 전달된 제안만 |
| `SUPER_ADMIN` | 동일 JWT 방식 | 이 기능 접근 불가, 환자 임상·위치 조회 금지 |

- Base URL: `http://13.124.194.249`
- 시간: ISO-8601 UTC
- 다른 병원 조직의 `offerId`는 `TRANSPORT_005`로 숨깁니다.

## 목록 계약

### `GET /api/v1/hospitals/me/offers?view={ACTIVE|HISTORY}&page={page}&size={size}`

| view | 새 요청에서 포함되는 주요 상태 |
|---|---|
| `ACTIVE` | `PENDING`, `ACCEPTED` |
| `HISTORY` | `REJECTED`, 수락 철회·취소·완료 등 종료 결과 |

ACTIVE 카드 판단 예시:

```json
{
  "transportRequestStatus": "SEARCHING",
  "offerStatus": "PENDING",
  "hospitalOutcome": "AWAITING_RESPONSE",
  "processedAt": null,
  "currentDestination": false,
  "canWithdraw": false
}
```

- `PENDING` 카드는 자동으로 HISTORY로 이동하지 않습니다.
- 최대 반경 도달 여부는 병원 카드 종료 조건이 아닙니다.
- 목적지 선택 뒤 비목적지 카드 처리와 임상 공개 범위는 기능 15 계약을 유지합니다.

## 응답 API

| 동작 | API | 성공 | 필수 입력 |
|---|---|---:|---|
| 수락 | `POST /api/v1/hospitals/me/offers/{offerId}/accept` | 200 | `Idempotency-Key` |
| 거절 | `POST /api/v1/hospitals/me/offers/{offerId}/reject` | 200 | 키, 거절 사유 본문 |
| 수락 철회 | `POST /api/v1/hospitals/me/offers/{offerId}/withdraw-acceptance` | 200 | 키, 철회 사유 본문 |

`PENDING`은 시간 제한 없이 수락·거절할 수 있습니다. 동일 명령의 응답 유실 시 같은
`offerId`와 `Idempotency-Key`로 재시도합니다.

## 화면 상태

| 조건 | 웹 처리 |
|---|---|
| `offerStatus=PENDING` | ACTIVE 카드와 수락·거절 버튼 유지 |
| `offerStatus=ACCEPTED` | ACTIVE 카드와 수락 상태 유지 |
| `offerStatus=REJECTED` | HISTORY로 이동하고 사유 표시 |
| 다른 병원이 목적지 | 기존 ACTIVE 카드 유지, 정확한 위치·동적 ETA는 표시 금지 |
| 현재 병원이 목적지 | 최신 임상·현재 위치·인계 흐름 사용 |

`NO_RESPONSE`는 과거 데이터 표시 호환용 enum입니다. 새 카드의 상태 분기나 자동 종료
조건으로 사용하지 않습니다.

## SSE와 재조회

새 병원 흐름에서 사용하는 주요 이벤트:

```text
TRANSPORT_REQUEST_RECEIVED
ETA_UPDATED
DESTINATION_SELECTED
DESTINATION_CHANGED
HOSPITAL_ACCEPTANCE_WITHDRAWN
TRANSPORT_CANCELLED
HANDOFF_REQUESTED
HANDOFF_COMPLETED
```

`HOSPITAL_OFFER_NO_RESPONSE`, `HOSPITAL_SEARCH_EXHAUSTED`,
`HOSPITAL_SEARCH_RETRY_STARTED`는 새 흐름에서 생성되지 않습니다. 이벤트에는 환자정보와
좌표가 없으므로 수신·재연결 뒤 목록 또는 상세 API를 다시 조회합니다.

## 오류

| 코드 | HTTP | 웹 처리 |
|---|---:|---|
| `AUTH_001`, `AUTH_002` | 401 | 로그인 또는 토큰 갱신 |
| `AUTH_003`, `USER_002` | 403 | 접근 차단·운영자 확인 |
| `HOSPITAL_001`, `TRANSPORT_005` | 404 | 목록 복귀, 다른 조직 정보 추정 금지 |
| `TRANSPORT_006` | 409 | 이미 결정된 카드 재조회 |
| `COMMON_001`, `COMMON_005` | 400/409 | 입력·멱등 키 확인 후 재조회 |

## 연동 확인

- [ ] 최대 반경·시간 경과 뒤에도 `PENDING` 카드 유지
- [ ] 늦은 수락·거절 정상 처리
- [ ] 다른 병원 수락·목적지 선택 뒤 ACTIVE 계약 유지
- [ ] `NO_RESPONSE` 신규 화면 분기 미사용
- [ ] SSE 재연결 뒤 목록·상세 복구
- [ ] 슈퍼 관리자 영향 없음
- [ ] dev API 연결
