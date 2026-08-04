# 이송 취소·인계 완료 및 이력 구현 계획

```text
Feature: transport-cancellation-handoff-history
Author: AI with backend engineer
Handoff Targets: BOTH
```

> 정책 확인이 끝난 `spec.md`, 현재 V7 스키마와 기존 목적지·철회·임상·위치
> 구현을 기준으로 작성한 상세 계획입니다. 구현 중 제품 동작 충돌이 발견되면
> 코드를 먼저 바꾸지 않고 `spec.md`를 갱신합니다.

## 설계 요약

- 선택한 방식:
  - `TransportRequest`를 취소·인계 상태 전이의 단일 잠금 기준으로 사용합니다.
  - 취소·인계 요청·병원 확인 결과는 요청의 현재 종료 snapshot과 불변 lifecycle command 이력에 함께 기록합니다.
  - 세 명령은 기존과 같은 `Idempotency-Key`·SHA-256 fingerprint 계약을 사용합니다.
  - 취소 시 제안 상태는 덮어쓰지 않고 `closedAt`만 확정해 기존 병원 응답 이력을 보존합니다.
  - 구급대원 목록은 기존 `TransportRequestController` 아래에 `ACTIVE`, `HISTORY`, `RECENT` 페이지 조회를 추가합니다.
  - 병원은 기존 제안 `ACTIVE`·`HISTORY` API를 유지하고 종료·인계 optional 필드만 추가합니다.
  - 상태 변경과 audit·realtime outbox 저장을 한 DB 트랜잭션으로 처리합니다.
- 선택 이유:
  - 요청 잠금 → 탐색 회차 → 제안 순서를 기존 목적지·철회 구현과 맞추면 취소·인계 경합을 직렬화할 수 있습니다.
  - 현재 snapshot만으로는 성공 응답을 잃은 재시도와 과거 행위자를 복구하기 어려우므로 불변 명령 이력이 필요합니다.
  - 병원 제안의 `ACCEPTED`·`PENDING`을 취소 상태로 덮어쓰면 실제 응답 이력이 사라지므로 종료 시각을 별도로 사용합니다.
  - Flutter 현재 코드는 최근 이송을 한 목록으로 받으므로 `RECENT` 한 번으로 인계 대기·완료·취소를 반환하는 편이 연동이 단순합니다.
- 검토한 대안과 제외 이유:
  - `TransportRequest.updatedAt`만 종료 시각으로 사용: 임상·검색 등 다른 변경과 의미가 섞이고 행위자·사유를 복구할 수 없어 제외합니다.
  - `HospitalOfferStatus`에 `CANCELLED`·`COMPLETED` 추가: 병원이 실제로 한 수락·거절 응답을 덮어쓰게 되어 제외합니다.
  - 취소·인계별 테이블 세 개 분리: 명령 수는 세 개뿐인데 멱등·조회 코드가 반복되므로 공통 lifecycle command로 통합합니다.
  - Flutter가 `ACTIVE`와 `HISTORY`를 각각 호출해 합치기: 현재 단일 최근 목록 화면에 불필요한 호출·정렬 책임을 주므로 `RECENT`를 추가합니다.

## API 계약

### 1. 구급대원 이송 취소

```http
POST /api/v1/transport-requests/{requestId}/cancel
Authorization: Bearer {accessToken}
Idempotency-Key: {8~100자 키}
Content-Type: application/json
```

```json
{
  "reason": "PATIENT_REFUSED_TRANSPORT",
  "detail": null
}
```

- `reason`은 `PATIENT_REFUSED_TRANSPORT`, `GUARDIAN_SELF_TRANSPORT`, `SCENE_RESOLVED`, `OTHER` 중 하나입니다.
- `OTHER`만 trim 후 1~200자의 `detail`이 필수이며 다른 사유의 `detail`은 `null`이어야 합니다.
- 성공은 HTTP 200으로 다음 최소 결과를 반환합니다.

```json
{
  "transportRequestId": "request-public-id",
  "status": "CANCELLED",
  "reason": "PATIENT_REFUSED_TRANSPORT",
  "detail": null,
  "cancelledAt": "2026-08-05T01:00:00Z",
  "idempotentReplay": false
}
```

### 2. 구급대원 인계 완료 요청

```http
POST /api/v1/transport-requests/{requestId}/handoff-request
Authorization: Bearer {accessToken}
Idempotency-Key: {8~100자 키}
```

- 요청 본문은 없습니다.
- `EN_ROUTE`이며 현재 목적지 제안이 `ACCEPTED`인 자기 요청만 허용합니다.
- 성공은 HTTP 200으로 요청 ID, `HANDOFF_REQUESTED`, 목적지 제안 ID·병원명, 요청 시각과 멱등 재생 여부를 반환합니다.

```json
{
  "transportRequestId": "request-public-id",
  "status": "HANDOFF_REQUESTED",
  "destinationOfferId": "offer-public-id",
  "destinationHospitalName": "한양대학교병원",
  "handoffRequestedAt": "2026-08-05T01:20:00Z",
  "idempotentReplay": false
}
```

### 3. 목적지 병원 인계 확인

```http
POST /api/v1/hospitals/me/offers/{offerId}/confirm-handoff
Authorization: Bearer {accessToken}
Idempotency-Key: {8~100자 키}
```

- 요청 본문은 없습니다.
- `HANDOFF_REQUESTED`인 요청의 현재 목적지 병원 조직만 허용합니다.
- 성공은 HTTP 200으로 제안 ID, 요청 ID, `COMPLETED`, 완료 시각과 멱등 재생 여부를 반환합니다.

```json
{
  "offerId": "offer-public-id",
  "transportRequestId": "request-public-id",
  "status": "COMPLETED",
  "completedAt": "2026-08-05T01:22:00Z",
  "idempotentReplay": false
}
```

### 4. 구급대원 활성·최근 이송 목록

```http
GET /api/v1/transport-requests?view=RECENT&page=0&size=20
Authorization: Bearer {accessToken}
```

- `view`는 `ACTIVE`, `HISTORY`, `RECENT`입니다.
- `ACTIVE`: `SEARCHING`, `CANDIDATES_EXHAUSTED`, `ACCEPTED_AVAILABLE`, `EN_ROUTE`, `HANDOFF_REQUESTED`.
- `HISTORY`: `COMPLETED`, `CANCELLED`.
- `RECENT`: Flutter 홈에서 사용하는 `HANDOFF_REQUESTED`, `COMPLETED`, `CANCELLED`.
- 기본 `page=0`, `size=20`, 최대 `size=100`이며 상태 변경 시각 내림차순, 같은 시각이면 요청 ID로 안정 정렬합니다.
- 모든 view는 JWT 본인 소유 요청만 반환합니다.

```json
{
  "items": [
    {
      "transportRequestId": "request-public-id",
      "status": "HANDOFF_REQUESTED",
      "hospitalName": "한양대학교병원",
      "createdAt": "2026-08-05T00:00:00Z",
      "statusUpdatedAt": "2026-08-05T01:20:00Z",
      "cancellationReason": null
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

- `CANCELLED`가 목적지 선택 전에 발생하면 `hospitalName`은 `null`입니다.
- 목록에 임상정보, 회신 연락처, 정확한 좌표와 내부 PK를 포함하지 않습니다.

### 5. 기존 병원 제안 조회 보완

- `GET /api/v1/hospitals/me/offers?view=ACTIVE`는 현재 목적지의 `HANDOFF_REQUESTED` 제안을 확인 전까지 유지합니다.
- `COMPLETED`·`CANCELLED` 요청의 모든 자기 제안은 `ACTIVE`에서 제외하고 `HISTORY`에 포함합니다.
- 기존 목록·상세 응답에 다음 optional 필드를 추가합니다.

```text
handoffRequestedAt
completedAt
cancelledAt
cancellationReason
canConfirmHandoff
```

- 기존 필드는 제거하거나 의미를 바꾸지 않아 React 기존 연동을 호환되게 유지합니다.
- 종료 후 `HISTORY` 상세는 최소 상태 이력만 제공하고 임상 timeline·연락처·정확한 위치 접근은 기존 권한 정책대로 차단합니다.

## 프론트 화면 대응

### Flutter 현재 화면과 일치하는 부분

- 취소 사유 API 값 4개는 현재 `TransportCancellationReason`과 일치합니다.
- 이송 진행 화면의 `인계 요청` 확인창은 `handoff-request` 명령과 직접 연결됩니다.
- 인계 요청 성공 후 홈의 `인계 대기 중`, 병원 확인 후 `인계 완료`는 각각 `HANDOFF_REQUESTED`, `COMPLETED`에 대응합니다.
- `statusUpdatedAt`은 ISO-8601 시각으로 반환하고 Flutter가 현재 구현처럼 `오늘 HH:mm` 등을 표시합니다.

### Flutter에서 연동 시 필요한 변경

- `OTHER` 선택 시 200자 이하 상세 사유 입력과 빈값 검증을 추가합니다.
- 병원 탐색·수락 화면뿐 아니라 `transport_in_progress_page.dart`에도 인계 요청 전 취소 동작을 제공합니다.
- `HandoffStatus` 두 값만 사용하는 최근 이송 모델을 서버 `TransportRequestStatus` 기준으로 바꾸고 `CANCELLED` 배지를 추가합니다.
- `hospitalName`을 nullable로 받아 목적지 전 취소는 `목적지 미정`처럼 표시합니다.
- 목 repository를 실제 REST·SSE 후 REST 재조회 구현으로 교체하고 명령별 멱등성 키를 성공 응답을 받을 때까지 재사용합니다.

### React 병원 웹

- 현재 로컬 Flutter 저장소에는 React 코드가 없어 화면 직접 비교는 하지 못했습니다.
- 실제 구현·검증 후 병원 제안 상세의 `canConfirmHandoff`, 인계 요청 시각과 확인 명령을 기준으로 별도 핸드오프를 작성합니다.

## DB 변경

새 migration `V8__add_transport_cancellation_handoff_history.sql`을 추가하고 V1~V7은 수정하지 않습니다.

### `transport_requests` 종료 snapshot 컬럼

```text
cancellation_reason VARCHAR(40) NULL
cancellation_detail VARCHAR(200) NULL
cancelled_by_account_id BIGINT NULL
cancelled_at DATETIME(6) NULL
handoff_requested_by_account_id BIGINT NULL
handoff_requested_at DATETIME(6) NULL
handoff_confirmed_by_account_id BIGINT NULL
completed_at DATETIME(6) NULL
```

- 각 account 컬럼은 `user_accounts(id)` FK를 사용합니다.
- 기존 Dev 데이터 보존을 위해 컬럼은 nullable로 추가하고 새 애플리케이션 명령은 상태별 필수 조합을 보장합니다.
- 취소 사유 CHECK는 네 enum 값만 허용하고 `OTHER` 상세 조건은 DB와 애플리케이션에서 함께 검증합니다.
- 본인 최근 목록을 위해 `(owner_account_id, status, completed_at, cancelled_at, handoff_requested_at)` 조회 인덱스를 추가합니다.

### `transport_lifecycle_commands` 불변 명령 이력

```text
id BIGINT PK
public_id CHAR(36) UNIQUE
transport_request_id BIGINT FK NOT NULL
command_type VARCHAR(30) NOT NULL
actor_account_id BIGINT FK NOT NULL
actor_organization_id BIGINT FK NOT NULL
destination_offer_id BIGINT FK NULL
cancellation_reason VARCHAR(40) NULL
cancellation_detail VARCHAR(200) NULL
idempotency_key VARCHAR(100) NOT NULL
request_fingerprint BINARY(32) NOT NULL
resulting_request_status VARCHAR(30) NOT NULL
occurred_at DATETIME(6) NOT NULL
```

- `command_type`은 `CANCEL`, `HANDOFF_REQUEST`, `HANDOFF_CONFIRM`입니다.
- `(transport_request_id, idempotency_key)`를 unique로 만들어 같은 요청에서 같은 키를 다른 lifecycle 명령에 재사용하지 못하게 합니다.
- 취소 명령의 `destination_offer_id`는 취소 직전 마지막 목적지를 보존하며 활성 목적지 의미로 사용하지 않습니다.
- 인계 요청·확인 명령은 당시 현재 목적지 제안을 보존합니다.
- 이력 행은 수정·삭제하지 않고 같은 키 재시도 응답 복구에 사용합니다.

### 병원 탐색 상태

- `HospitalDispatchAttemptStatus`와 기존 CHECK에 `STOPPED_ON_CANCELLATION`을 추가합니다.
- 취소 시 활성 `SEARCHING` attempt의 `nextExpansionAt`을 `null`로 만들고 종료 시각을 기록합니다.
- 검색 round는 이미 평가된 사실이므로 수정하지 않습니다.
- `hospital_offers.closed_at` 기존 컬럼을 사용하고 응답 status는 유지합니다.

## 도메인·트랜잭션 설계

### `TransportRequest`

- `cancel(actor, reason, detail, occurredAt)`: 허용 상태 검증, 종료 snapshot 저장, 현재 목적지 해제, `CANCELLED` 전이.
- `requestHandoff(actor, occurredAt)`: `EN_ROUTE`와 유효한 현재 목적지 검증, 요청 snapshot 저장, `HANDOFF_REQUESTED` 전이.
- `confirmHandoff(actor, occurredAt)`: `HANDOFF_REQUESTED` 검증, 확인 snapshot 저장, `COMPLETED` 전이.
- 최종 목적지 연결은 완료 이력 조회를 위해 `COMPLETED`에서 유지합니다.

### 잠금 순서

1. 인증 계정·조직 검증
2. `TransportRequest` 비관적 잠금 및 refresh
3. 기존 lifecycle command 멱등 결과 확인
4. 필요하면 활성 dispatch attempt 잠금
5. 관련 `HospitalOffer` PK 오름차순 잠금
6. 상태 변경·command·audit·outbox 저장

- 병원 확인도 먼저 offer에서 요청 PK만 조회한 뒤 요청을 잠그고 제안을 잠가 목적지·철회 서비스와 같은 순서를 유지합니다.
- unique key 경합은 최초 명령을 재조회해 같은 fingerprint면 재생하고 다르면 `COMMON_005`로 변환합니다.

### 취소 트랜잭션

- 모든 관련 제안의 병원 조직 ID를 먼저 수집합니다.
- 요청을 취소하고 활성 attempt를 `STOPPED_ON_CANCELLATION`으로 종료합니다.
- `PENDING`·`ACCEPTED`를 포함한 닫히지 않은 제안에 동일 취소 시각으로 `closedAt`을 기록합니다.
- command, `TRANSPORT_CANCELLED` audit와 대상별 outbox를 저장합니다.
- 어느 단계든 실패하면 요청·attempt·offer·command·audit·outbox 전체를 롤백합니다.

### 인계 요청·확인 트랜잭션

- 인계 요청은 현재 목적지 제안의 `ACCEPTED`와 조직을 확인하고 `HANDOFF_REQUESTED`로 전이합니다.
- 병원 확인은 JWT 병원 조직과 현재 목적지 제안 조직이 같은지 다시 검증합니다.
- 완료 시 모든 닫히지 않은 제안을 닫고 `HANDOFF_CONFIRM` command를 기록합니다.
- 현재 목적지 병원 수신 상태가 `OFF`여도 이미 진행 중인 인계 확인 권한은 유지합니다.

## 조회 설계

- `TransportRequestView` enum에 `ACTIVE`, `HISTORY`, `RECENT`를 둡니다.
- Repository는 owner account와 상태 집합으로 페이지 조회하고 현재 목적지·병원 snapshot을 EntityGraph 또는 projection으로 함께 가져옵니다.
- 취소 요청의 마지막 목적지는 한 페이지의 request ID들로 cancellation command를 batch 조회해 N+1을 피합니다.
- `RECENT`의 `statusUpdatedAt`은 `handoffRequestedAt`, `completedAt`, `cancelledAt` 중 현재 상태에 해당하는 서버 시각입니다.
- 페이지 정렬과 같은 시각 tie-break를 DB query에서 고정합니다.
- 병원 `ACTIVE` query에는 `closedAt is null`과 종료 상태 제외 조건을 추가합니다.
- 병원 `HISTORY` query에는 닫힌 제안 또는 부모 요청 `COMPLETED`·`CANCELLED`를 포함하고 같은 제안이 중복되지 않게 합니다.

## 감사·실시간 이벤트

### AuditAction 추가

```text
TRANSPORT_CANCELLED
HANDOFF_REQUESTED
HANDOFF_CONFIRMED
```

### RealtimeEventType 추가

```text
TRANSPORT_CANCELLED
HANDOFF_REQUESTED
HANDOFF_COMPLETED
```

- 취소 이벤트: 요청 소유 계정과 해당 요청에서 아직 `PENDING`·`ACCEPTED`였던 모든 병원 조직.
- 인계 요청 이벤트: 요청 소유 계정과 현재 목적지 병원 조직.
- 완료 이벤트: 요청 소유 계정과 관련 병원 조직. 각 클라이언트는 이벤트 후 자기 REST 목록을 재조회합니다.
- 이벤트 aggregate는 lifecycle command 공개 ID를 사용합니다.
- audit·SSE payload에는 취소 상세, 환자정보, 연락처와 정확한 좌표를 포함하지 않습니다.

## 구현 Step

| Step | 작업 | 단계 검증 | 완료 기준 |
|---:|---|---|---|
| 1 | V8 migration, lifecycle enum·command와 요청 종료 snapshot 추가 | MySQL V7→V8 migration, JPA validate, 기존 행 보존 테스트 | 기존 데이터 손실 없이 새 컬럼·FK·CHECK·unique·index 적용 |
| 2 | 요청 상태 전이와 제안·탐색 종료 도메인 구현 | 상태별 단위 테스트, 잘못된 전이 테스트 | 취소·인계 요청·확인과 offer·attempt 종료 규칙이 도메인에서 강제됨 |
| 3 | 구급대원 취소 API와 멱등 결과 구현 | 네 허용 상태, 사유·OTHER, 롤백·재시도·권한 통합 테스트 | 취소가 원자적으로 `CANCELLED`가 되고 관련 활성 작업·제안이 닫힘 |
| 4 | 구급대원 인계 요청과 목적지 병원 확인 API 구현 | 정상 2단계, 한쪽만 요청, 잘못된 병원·상태·OFF 병원 테스트 | 양측 확인에서만 `COMPLETED`, 최종 목적지·행위자·시각 보존 |
| 5 | 구급대원 `ACTIVE`·`HISTORY`·`RECENT` 및 병원 목록 보완 | 페이지·정렬·nullable 병원명·다른 대원/조직·종료 정보 차단 테스트 | Flutter 최근 목록과 React 활성·이력이 최소 정보로 일치 |
| 6 | audit·outbox·SSE와 종료 후 기존 명령 차단 보완 | 이벤트 대상·중복·민감 payload, 임상·위치·목적지·철회 회귀 테스트 | 종료 상태에서 후속 변경이 차단되고 대상별 재조회 신호가 한 번 저장됨 |
| 7 | 멱등성·동시성·MySQL 회귀 검증 | 취소/수락, 취소/목적지, 인계/철회, 확인/재시도 경합 테스트 | 어떤 실행 순서에서도 종료 상태·이력·알림이 중복·모순되지 않음 |
| 8 | 전체 로컬 검증, review와 양쪽 핸드오프 작성 | `./gradlew clean check`, `dev-start`, readiness, 실제 코드 계약 대조 | 모든 검사 통과와 Flutter·React 독립 연동 문서 완성 |

## 변경 범위

| 패키지·파일 | 변경 내용 |
|---|---|
| `db/migration/V8__add_transport_cancellation_handoff_history.sql` | 종료 snapshot, lifecycle command, 탐색 취소 상태와 인덱스 추가 |
| `transport/domain` | 취소 사유, lifecycle command와 요청 상태 전이 추가 |
| `transport/application` | 취소·인계·목록 서비스, fingerprint와 종료 권한 검증 추가 |
| `transport/api` | 취소·인계 요청·목록 DTO와 Controller 추가 |
| `transport/infrastructure` | 요청 잠금·목록·command batch 조회 Repository 추가·보완 |
| `hospital/search/domain` | offer 종료와 탐색 취소 상태 추가 |
| `hospital/search/application` | 인계 확인, 종료 상태 제안 조회·응답·ETA 차단 보완 |
| `hospital/search/api` | 인계 확인 API와 기존 목록·상세 optional 종료 필드 추가 |
| `audit`, `realtime` | 취소·인계 audit와 최소 SSE event type·outbox 추가 |
| `src/test` | 단위·통합·권한·멱등·동시성·MySQL migration 회귀 테스트 |
| `docs/handoffs/08-transport-cancellation-handoff-history` | 구현 후 Flutter·React 실제 연동 계약 작성 |

## 테스트 계획

### 취소

- [x] 네 활성 상태 각각에서 자기 요청 취소 성공
- [x] 네 사유와 `OTHER` 상세 trim·필수·길이, 다른 사유 상세 거절
- [x] `HANDOFF_REQUESTED`, `COMPLETED`, `CANCELLED` 취소 차단
- [x] 목적지 해제, 활성 attempt 종료, 모든 열린 offer 종료와 기존 응답 보존
- [x] 숨겨진 비목적지 수락 병원을 포함한 outbox 대상
- [x] 같은 키 같은 payload 재생, 같은 키 다른 payload `COMMON_005`
- [x] 중간 저장 실패 시 요청·offer·attempt·command·audit·outbox 전체 롤백

### 인계

- [x] `EN_ROUTE` 자기 요청의 인계 요청 후 한쪽만으로 `HANDOFF_REQUESTED` 유지
- [x] 현재 목적지 병원 확인에서만 `COMPLETED`
- [x] 이전 목적지·비목적지·다른 조직·구급대원·관리자 확인 차단
- [x] 수신 `OFF` 목적지 병원의 기존 인계 확인 권한 유지
- [x] 현재 목적지 없음·철회됨·잘못된 상태 차단
- [x] 완료 시 모든 열린 offer 종료와 최종 목적지 보존
- [x] 같은 명령 재시도에서 command·audit·outbox 중복 없음

### 목록·개인정보

- [x] `ACTIVE`, `HISTORY`, `RECENT` 상태 집합과 최신순·페이지 경계
- [x] 인계 대기→완료 시 RECENT 동일 요청 한 항목의 상태·시각 변경
- [x] 목적지 전 취소 `hospitalName=null`, 목적지 후 취소 마지막 병원 snapshot
- [x] 다른 대원·다른 조직 요청 미노출
- [x] 임상정보·연락처·좌표·내부 PK가 목록과 SSE에 없음
- [x] 병원 `HANDOFF_REQUESTED` ACTIVE, 종료 offer HISTORY 전환
- [x] 종료 후 병원 임상 timeline·정확한 위치 접근 차단

### 동시성·회귀

- [x] 취소와 병원 수락 경합
- [x] 취소와 목적지 선택 경합
- [x] 인계 완료 요청과 현재 목적지 철회 경합
- [x] 병원 확인과 중복 인계 요청·후속 임상·위치 명령 경합
- [x] V7 기존 요청·제안·목적지·임상·위치 데이터 V8 보존
- [x] MySQL 8.4 migration·JPA validate
- [x] 기존 139개 이상 전체 회귀 테스트
- [x] `./gradlew clean check`
- [x] 로컬 실행과 `/actuator/health/readiness`

## 프론트 핸드오프

- 대상: `BOTH`
- Flutter: `docs/handoffs/08-transport-cancellation-handoff-history/flutter-paramedic.md`
- React: `docs/handoffs/08-transport-cancellation-handoff-history/react-hospital-admin.md`
- 구현과 로컬 검증 후 실제 path·DTO·오류·SSE 재조회 조건만 기록합니다.
- Flutter 문서에는 `OTHER` 상세, 이동 중 취소, `RECENT` 상태 매핑과 nullable 병원명을 직접 기록합니다.
- React 문서에는 인계 요청 카드, 확인 권한, ACTIVE/HISTORY 전환과 종료 후 정보 차단을 직접 기록합니다.

## 유지할 계약

- `spec.md`의 취소·양측 인계·종료 이력과 개인정보 제한
- 기존 `TransportRequestStatus` 이름과 선행 기능의 허용·차단 상태
- 기존 목적지·철회·임상·위치 명령의 멱등성 및 요청 잠금 순서
- 기존 병원 제안 API 필드의 하위 호환성
- 공통 오류 응답, `X-Trace-Id`와 안전한 공개 메시지
- `SUPER_ADMIN` 임상정보·위치·이송 이력 접근 금지
- 기존 Flyway V1~V7 불변

## 리스크

| 리스크 | 대응 |
|---|---|
| 취소·철회·목적지·인계가 서로 다른 순서로 offer를 잠가 deadlock 발생 | 요청을 먼저 잠그고 attempt·offer PK 오름차순 순서를 모든 lifecycle 명령에 통일, 동시성 테스트로 검증 |
| 종료된 PENDING·ACCEPTED 제안이 기존 ACTIVE query에 남음 | `closedAt`과 부모 요청 상태를 ACTIVE/HISTORY query에 함께 반영하고 전체 상태 조합 테스트 |
| 취소 시 current destination 해제로 최근 병원명을 잃음 | cancellation command에 취소 직전 destination offer를 불변 snapshot 관계로 보존하고 batch 조회 |
| Flutter 목 모델이 `OTHER` 상세·`CANCELLED`·nullable 병원명을 지원하지 않음 | 백엔드 계약을 실제 화면 기준으로 문서화하고 프론트 전달사항과 핸드오프에 명시 |
| V8 이전 수동 terminal 상태 행에 행위자·시각이 없음 | 새 snapshot 컬럼을 nullable로 추가하고 신규 명령부터 완전성 보장, 레거시 행 조회 fallback과 migration 테스트 수행 |
