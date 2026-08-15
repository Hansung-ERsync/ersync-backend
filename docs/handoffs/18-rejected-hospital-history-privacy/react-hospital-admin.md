# 거절 병원 이력 개인정보 보호 React 병원 웹 핸드오프

```text
Feature: 18-rejected-hospital-history-privacy
Backend Feature: docs/features/18-rejected-hospital-history-privacy/
Related PR: NONE
Available After: MAIN_MERGE
Breaking Change: YES
Hospital Impact: YES
Admin Impact: NONE
```

## 변경 요약

- 거절한 요청은 HISTORY에 상태, 거절 사유와 처리 시각만 표시합니다.
- 거절 카드의 상세·임상 이력·위치 API는 `404 TRANSPORT_005`입니다.
- 기존 상세 조회 200 계약은 더 이상 사용하지 않습니다.
- 슈퍼 관리자 API와 Flutter 구급대원 앱에는 영향이 없습니다.

## 병원 사용자 흐름

| 순서 | 웹 동작 | API | 결과 |
|---:|---|---|---|
| 1 | 병원이 요청을 거절 | `POST .../offers/{offerId}/reject` | 카드가 HISTORY로 이동 |
| 2 | HISTORY 재조회 | `GET .../offers?view=HISTORY` | 거절 사유·상세와 처리 시각 표시 |
| 3 | 거절 카드 선택 | 상세 API를 호출하지 않음 | 상세 링크·버튼 미표시 |

## 인증과 접근 범위

| 역할 | 인증 | 허용 범위 |
|---|---|---|
| `HOSPITAL_STAFF` | Bearer access token | 자기 병원 조직의 최소 HISTORY만 |
| `SUPER_ADMIN` | 동일 JWT 방식 | 병원 제안·환자 임상·위치 접근 불가 |

- Base URL: `http://13.124.194.249`
- 시간: ISO-8601 UTC
- Dev HTTP에서는 가짜 환자·계정·좌표만 사용합니다.

## HISTORY 목록

### `GET /api/v1/hospitals/me/offers?view=HISTORY&page={page}&size={size}`

- 인증·역할: `HOSPITAL_STAFF`
- 성공: 200
- 페이징: `page >= 0`, `1 <= size <= 100`

거절 항목 예시:

```json
{
  "offerId": "OFFER_UUID",
  "transportRequestId": "REQUEST_UUID",
  "transportRequestStatus": "SEARCHING",
  "offerStatus": "REJECTED",
  "hospitalOutcome": "REJECTED",
  "processedAt": "2026-08-15T08:00:00Z",
  "currentDestination": false,
  "canWithdraw": false,
  "rejectionReason": "OTHER",
  "rejectionDetail": "Local treatment unavailable",
  "respondedAt": "2026-08-15T08:00:00Z"
}
```

| 유지 필드 | 웹 처리 |
|---|---|
| `offerId`, `transportRequestId` | 카드 식별·상태 갱신에만 사용 |
| `offerStatus`, `hospitalOutcome` | `거절` 표시 |
| `rejectionReason`, `rejectionDetail` | 거절 사유 표시 |
| `processedAt`, `respondedAt` | 처리 시각 표시 |

다음 정보는 `null` 또는 생략됩니다.

- 환자 나이·성별
- Pre-KTAS와 임상 갱신 시각
- 구급대 조직·회신 연락처
- 직선거리·경로거리·ETA
- 환자 임상 상세와 현재 위치

거절 사유 enum:

```text
ER_GENERAL_BED_SHORTAGE
ISOLATION_BED_SHORTAGE
OPERATING_ROOM_SHORTAGE
ICU_SHORTAGE
SPECIALIST_UNAVAILABLE
EQUIPMENT_UNAVAILABLE
OTHER
```

`OTHER`이면 `rejectionDetail`을 함께 표시합니다.

## 종료 제안 상세 차단

다음 API는 거절·철회·무응답·완료·취소 제안에 `404 TRANSPORT_005`를 반환합니다.

```http
GET /api/v1/hospitals/me/offers/{offerId}
GET /api/v1/hospitals/me/offers/{offerId}/clinical-timeline
GET /api/v1/hospitals/me/offers/{offerId}/location
```

오류 응답:

```json
{
  "code": "TRANSPORT_005",
  "message": "병원 수신 요청을 찾을 수 없습니다.",
  "fieldErrors": [],
  "traceId": "TRACE_ID"
}
```

웹은 404를 받기 전에 HISTORY 카드의 상세 이동을 비활성화합니다. 재조회 경합으로
404가 발생하면 HISTORY 목록으로 돌아가 최신 결과를 사용합니다.

## 실시간 이벤트와 재조회

- 새 SSE 이벤트는 없습니다.
- 거절 성공 또는 기존 카드 변경 SSE를 받으면 ACTIVE와 HISTORY를 재조회합니다.
- SSE payload에는 환자정보와 좌표가 없으며 REST 목록을 권위 상태로 사용합니다.

## 연동 확인

- [ ] 거절 카드가 ACTIVE에서 HISTORY로 이동
- [ ] 거절 사유·상세와 처리 시각 표시
- [ ] 환자·임상·연락처·거리·ETA 미표시
- [ ] 거절 카드 상세 링크 제거
- [ ] 종료 상세 404에서 HISTORY 목록 복구
- [ ] 활성 `PENDING`·`ACCEPTED` 상세 회귀 없음
- [ ] 다른 조직과 슈퍼 관리자 접근 차단
- [ ] 가짜 데이터로 Dev API 확인
