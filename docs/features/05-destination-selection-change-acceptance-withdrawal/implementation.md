# 목적지 선택·변경 및 수락 철회 구현 계획

> **정책 개정 알림:** 이 문서는 2026-08-13 이전 정책으로 완료된 구현 기록입니다. 새 작업 계획으로 사용하지 말고, 현재 `spec.md`를 기준으로 구현 계획을 다시 작성해야 합니다.

```text
Feature: destination-selection-change-acceptance-withdrawal
Author: backend AI collaboration
Handoff Targets: BOTH
```

> `Policy Decision Status: RESOLVED`인 `spec.md`와 현재 V1~V4 스키마,
> 병원 탐색·응답 API, DB scheduler와 outbox 구현을 기준으로 작성합니다.
> 구현은 아래 Step마다 `구현 → 해당 범위 검증 → 발견된 오류 수정` 순서로
> 진행하고, 전체 완료 뒤 통합 검사와 리뷰를 별도로 수행합니다.

## 현재 코드 기준

- `TransportRequest`는 `SEARCHING`, `CANDIDATES_EXHAUSTED`,
  `ACCEPTED_AVAILABLE`, `EN_ROUTE` 상태를 이미 가지지만 현재 목적지 참조와
  목적지 변경 이력은 아직 없습니다.
- `HospitalOffer`는 `PENDING`, `ACCEPTED`, `REJECTED`, `NO_RESPONSE`만
  지원하며 수락 철회 사유·행위자·멱등성 정보가 없습니다.
- 병원 `ACTIVE` 목록은 현재 `PENDING`, `ACCEPTED`를 모두 반환하고 상세 API는
  자기 조직 제안이면 상태와 관계없이 최신 임상 snapshot을 반환합니다.
- 구급대원 병원 탐색 현황 API는 모든 제안 이력을 반환하지만 현재 목적지 여부와
  철회 정보를 반환하지 않습니다.
- 탐색 scheduler는 요청이 `SEARCHING`일 때만 동작하고 요청 생성 좌표를 검색
  기준점으로 사용합니다.
- 병원 수락·거절, 후보 소진과 재전송은 DB 상태와 outbox·감사 기록을 같은
  트랜잭션에 저장합니다. 이 패턴을 목적지와 철회에도 유지합니다.

## 설계 요약

- 선택한 방식:
  - `transport_requests.current_destination_offer_id`로 현재 목적지를 한 곳만 참조합니다.
  - 목적지 명령은 불변 `transport_destination_commands`에
    `SELECTED`, `CHANGED`, `UNCHANGED` 결과로 저장해 변경 이력과 멱등성 키를
    함께 보존합니다.
  - `hospital_offers`에 `ACCEPTANCE_WITHDRAWN` 상태와 철회 snapshot·멱등성
    컬럼을 추가하고 `hospital_offer_events`에는 철회 이벤트를 추가합니다.
  - 모든 목적지·철회 명령은 요청을 먼저 잠그고 관련 제안을 잠근 뒤 상태를
    다시 검증합니다.
  - 병원 `ACTIVE`와 `HISTORY`를 상태만으로 나누지 않고 현재 목적지 관계까지
    포함한 조회로 변경합니다.
  - 현재 목적지 철회나 목적지가 없는 상태의 수락 철회는 새 탐색 회차를 만들고,
    현재 목적지가 유지되는 비목적지 철회는 재탐색하지 않습니다.
  - 철회 재탐색 회차는 검색 기준 좌표와 시작 원인을 snapshot으로 저장하고,
    해당 요청에서 이미 연락한 모든 병원을 제외합니다.
- 선택 이유:
  - 현재 목적지 FK 하나로 동시에 두 병원이 목적지가 되는 상태를 구조적으로 막을 수 있습니다.
  - 목적지 명령 기록을 별도 보존하면 같은 목적지를 다시 선택한 무변경 명령도
    멱등성 키를 소비해 이후 다른 payload 재사용을 정확히 차단할 수 있습니다.
  - 철회 snapshot은 목록·응답 조립 시 이벤트 테이블의 최신 행을 반복 조회하지
    않게 하고, 이벤트 테이블은 감사 가능한 불변 원본 역할을 유지합니다.
  - 기존 DB scheduler와 outbox 패턴을 확장하면 서버 재시작과 SSE 단절에도
    권위 REST 상태로 복구할 수 있습니다.
- 검토한 대안과 제외 이유:
  - 목적지 여부를 `HospitalOffer`의 boolean으로 저장하는 방식은 여러 행이 동시에
    true가 되는 경합을 막기 어렵고 목적지 단일성을 요청 aggregate 밖에 분산하므로 제외합니다.
  - 현재 목적지 ID만 저장하고 이력 테이블을 만들지 않는 방식은 변경 전 병원,
    행위자, 멱등성 재시도와 감사 이력을 복구할 수 없어 제외합니다.
  - 숨겨진 수락 병원에 기존 임상 상세 API를 계속 허용하는 방식은 확정된 최소
    응답 이력 정책과 맞지 않아 제외합니다.
  - 비목적지 철회마다 재탐색하는 방식은 이미 목적지가 있는데도 추가 병원에
    환자정보를 전송하므로 제외합니다.

## 상태 전이

### 목적지 명령

| 현재 요청 상태 | 현재 목적지 | 대상 제안 | 결과 |
|---|---|---|---|
| `ACCEPTED_AVAILABLE` | 없음 | `ACCEPTED` | 대상이 현재 목적지가 되고 요청은 `EN_ROUTE`, 명령 결과 `SELECTED` |
| `EN_ROUTE` | A | `ACCEPTED` B | B로 원자적 변경, 요청은 `EN_ROUTE`, 명령 결과 `CHANGED` |
| `EN_ROUTE` | A | `ACCEPTED` A | 상태 변화·목적지 이벤트 없이 명령 결과 `UNCHANGED` 저장 |
| 그 외 | 무관 | 무관 | `TRANSPORT_004` 또는 `TRANSPORT_002` |

### 수락 철회

| 상황 | 목적지·요청 상태 | 재탐색 |
|---|---|---|
| 목적지 선택 전 수락 병원 철회, 다른 수락 존재 | 목적지 없음, `ACCEPTED_AVAILABLE` | 새 철회 복구 회차 시작 |
| 목적지 선택 전 마지막 수락 병원 철회 | 목적지 없음, `SEARCHING` | 새 철회 복구 회차 시작 |
| 현재 목적지 철회, 다른 수락 존재 | 목적지 해제, `ACCEPTED_AVAILABLE` | 새 철회 복구 회차 시작 |
| 현재 목적지 철회, 다른 수락 없음 | 목적지 해제, `SEARCHING` | 새 철회 복구 회차 시작 |
| 현재 목적지가 유지되는 비목적지 수락 철회 | 목적지와 `EN_ROUTE` 유지 | 시작하지 않음 |
| `HANDOFF_REQUESTED` 이후 | 변경 없음 | `TRANSPORT_004` |

철회된 제안은 `ACCEPTANCE_WITHDRAWN`이 되며 다시 `ACCEPTED`로 되돌리지
않습니다. 다른 수락 병원과 과거 탐색·응답·목적지 이력은 삭제하지 않습니다.

## DB 설계

새 migration은 `V5__create_destination_and_acceptance_withdrawal_schema.sql`로
추가하고 V1~V4는 수정하지 않습니다.

### `transport_requests`

- `current_destination_offer_id BIGINT NULL`
- `hospital_offers(id)` FK와 조회 인덱스 추가
- FK는 제안 존재를 보장하고, 같은 `TransportRequest` 소속인지 여부와
  `ACCEPTED` 상태는 애플리케이션 트랜잭션에서 검증합니다.

### `transport_destination_commands`

| 컬럼 | 용도 |
|---|---|
| `public_id` | 외부·감사 식별자 |
| `transport_request_id` | 명령 대상 요청 |
| `previous_destination_offer_id` | 명령 직전 목적지, 최초 선택이면 `NULL` |
| `destination_offer_id` | 선택을 요청한 수락 제안 |
| `result_type` | `SELECTED`, `CHANGED`, `UNCHANGED` |
| `actor_account_id`, `actor_organization_id` | 구급대원과 소속 조직 |
| `idempotency_key`, `request_fingerprint` | 요청별 멱등성·payload 충돌 판정 |
| `resulting_request_status` | 최초 처리 결과를 재시도에 반환하기 위한 상태 snapshot |
| `occurred_at` | 신뢰하는 서버 처리 시각 |

- `(transport_request_id, idempotency_key)` 고유 제약으로 같은 요청 안의 키 재사용을 막습니다.
- `request_fingerprint`는 `transportRequestId + destinationOfferId`를 SHA-256으로 저장합니다.
- 대상과 이전 목적지는 같은 요청 소속인지 서비스에서 확인하고 통합 테스트로 고정합니다.

### `hospital_offers`, `hospital_offer_events`

- `HospitalOfferStatus`와 DB CHECK에 `ACCEPTANCE_WITHDRAWN`을 추가합니다.
- `hospital_offers`에 철회 사유·상세·행위 계정·철회 시각·멱등성 키·fingerprint를
  snapshot으로 추가합니다.
- `ACCEPTANCE_WITHDRAWN`은 기존 수락 행위자·수락 시각을 유지하고
  `closed_at = withdrawn_at`으로 활성 제안을 닫습니다.
- 철회 사유 enum은 거절 사유와 분리한 `HospitalAcceptanceWithdrawalReason`을 사용합니다.
- `hospital_offer_events`에 `ACCEPTANCE_WITHDRAWN`과 철회 사유·상세 컬럼을
  추가해 최초 수락과 이후 철회를 별도 불변 이벤트로 보존합니다.
- 철회 `OTHER` 상세, 상태별 nullable 조합과 철회 멱등성 key/fingerprint 쌍을
  DB CHECK와 애플리케이션 검증 양쪽에서 확인합니다.

### `hospital_dispatch_attempts`, outbox

- 탐색 시작 원인 `INITIAL`, `MANUAL_RETRY`, `ACCEPTANCE_WITHDRAWAL`을 저장합니다.
- 회차마다 `search_origin_latitude`, `search_origin_longitude`를 snapshot으로
  저장해 이후 요청 위치가 바뀌어도 해당 탐색의 거리 판정을 재현할 수 있게 합니다.
- 기존 회차는 요청 생성 좌표로 backfill하고 `attempt_number`에 따라 최초·수동
  재전송 원인을 설정합니다.
- 기존 재전송 멱등성 CHECK를 시작 원인 기준으로 바꿔 `MANUAL_RETRY`만
  retry key/fingerprint를 요구하고 철회 복구 회차는 철회 명령의 멱등성으로
  중복 생성을 막습니다.
- `STOPPED_ON_DESTINATION` 상태를 추가해 목적지 선택으로 철회 재탐색이 중단된
  경우를 첫 수락 중단과 구분합니다.
- outbox CHECK에 `DESTINATION_SELECTED`, `DESTINATION_CHANGED`,
  `HOSPITAL_ACCEPTANCE_WITHDRAWN`을 추가합니다.

## API 설계

### 목적지 선택·변경

```http
POST /api/v1/transport-requests/{transportRequestId}/destination
Authorization: Bearer {accessToken}
Idempotency-Key: {8~100자 키}
Content-Type: application/json

{
  "offerId": "OFFER_UUID"
}
```

- Controller: `TransportDestinationController`
- Request: `SelectTransportDestinationRequest`
- Response: `TransportDestinationResponse`

```text
transportRequestId
transportRequestStatus
selectedDestinationOfferId
previousDestinationOfferId
resultType: SELECTED | CHANGED | UNCHANGED
changedAt
idempotentReplay
```

- 정상·무변경·멱등 재시도 모두 `200 OK`를 사용합니다.
- 재시도 응답은 저장된 명령 결과를 반환하고, 화면의 최신 상태는 병원 탐색
  현황 REST API를 다시 조회해 확정합니다.
- 대상 제안이 요청에 속하지 않거나 `ACCEPTED`가 아니면 `TRANSPORT_002`,
  요청 상태가 허용되지 않으면 `TRANSPORT_004`를 반환합니다.

### 병원 수락 철회

```http
POST /api/v1/hospitals/me/offers/{offerId}/withdraw-acceptance
Authorization: Bearer {accessToken}
Idempotency-Key: {8~100자 키}
Content-Type: application/json

{
  "reason": "SPECIALIST_UNAVAILABLE",
  "detail": null
}
```

- Request: `WithdrawHospitalAcceptanceRequest`
- Response: `HospitalAcceptanceWithdrawalResponse`

```text
offerId
offerStatus: ACCEPTANCE_WITHDRAWN
transportRequestId
transportRequestStatus
currentDestinationOfferId
reason
detail
withdrawnAt
searchRestarted
idempotentReplay
```

- 같은 병원 조직의 `ACCEPTED` 제안만 철회할 수 있습니다.
- `OTHER`는 trim 이후 1~200자의 detail이 필요하고 다른 사유의 detail은
  `NULL`로 정규화합니다.
- 같은 키·같은 사유 재시도는 최초 철회 결과를 재사용합니다. 다른 사유 또는
  다른 명령 의미면 `COMMON_005`입니다.

### 기존 조회 API 확장

`GET /api/v1/transport-requests/{requestId}/hospital-search`

- 최상위에 `currentDestinationOfferId`를 추가합니다.
- 각 offer에 `currentDestination`, 철회 사유·상세·시각을 추가합니다.
- 철회된 병원은 목적지 후보가 아니며 병원 연락처 원문을 반환하지 않습니다.

`GET /api/v1/hospitals/me/offers?view=ACTIVE|HISTORY`

- `ACTIVE`: `PENDING`, 목적지 선택 전의 `ACCEPTED`, 현재 목적지 `ACCEPTED`
- `HISTORY`: 기존 `REJECTED`, `NO_RESPONSE`, `ACCEPTANCE_WITHDRAWN`, 그리고
  현재 목적지가 아닌 숨겨진 `ACCEPTED`
- 목록 item에 `currentDestination`, `canWithdraw`, `respondedAt`, 철회
  사유·시각을 선택 필드로 추가합니다.
- 숨겨진 `ACCEPTED`와 `ACCEPTANCE_WITHDRAWN` item은 offer/request ID,
  응답 상태·수락 시각·철회 가능 여부·철회 정보만 반환합니다. 기존 임상 요약과
  거리·ETA 필드는 `NULL`로 반환합니다.
- 기존 `REJECTED`, `NO_RESPONSE` 목록 계약은 유지합니다.

`GET /api/v1/hospitals/me/offers/{offerId}`

- `PENDING`, 목적지 선택 전 `ACCEPTED`, 현재 목적지 `ACCEPTED`, 기존
  `REJECTED`·`NO_RESPONSE` 상세 동작은 유지합니다.
- 숨겨진 비목적지 `ACCEPTED`와 `ACCEPTANCE_WITHDRAWN`은 임상 상세를
  반환하지 않고 `TRANSPORT_005`로 처리합니다.
- 목록에서 제공한 최소 이력만으로 철회 명령을 수행할 수 있게 합니다.

## 애플리케이션·트랜잭션 설계

### 목적지 서비스

- `TransportDestinationService`가 인증 계정·소유권, 멱등성 키와 fingerprint를 검증합니다.
- 요청 잠금 → 진행 중인 철회 탐색 회차 잠금(있는 경우) → 대상 제안 잠금 →
  현재 목적지·대상 상태 재검증 순서를 사용합니다.
- 기존 명령 키가 있으면 fingerprint를 비교해 재시도 또는 `COMMON_005`를 결정합니다.
- 최초 선택·변경·무변경 결과를 명령 이력에 저장합니다.
- 실제 목적지가 바뀐 경우에만 `TransportRequest.currentDestinationOffer`와 상태를
  변경하고 audit·outbox를 저장합니다.
- 선택 시 진행 중인 `ACCEPTANCE_WITHDRAWAL` 탐색 회차가 있으면
  `STOPPED_ON_DESTINATION`으로 닫아 이후 scheduler 확대를 막습니다.

### 철회 서비스

- 기존 `HospitalOfferService`에 철회 유스케이스를 추가하되, 조회용 조직 scope를
  먼저 확인한 뒤 요청 잠금 → 제안 잠금 순서로 다시 조회합니다.
- 철회 제안이 현재 목적지인지 요청 잠금 안에서 판정합니다.
- 비목적지 철회이면서 다른 현재 목적지가 있으면 제안·이력·알림만 저장하고
  요청 상태와 탐색 회차는 변경하지 않습니다.
- 현재 목적지 철회 또는 목적지가 없는 상태의 수락 철회라면 목적지를 해제하고
  남은 `ACCEPTED` 수를 조회해 `ACCEPTED_AVAILABLE` 또는 `SEARCHING`을 정합니다.
- 진행 중인 `ACCEPTANCE_WITHDRAWAL` 탐색 회차가 있으면 재사용하고, 없을 때만
  동일 트랜잭션에서 새 철회 복구 회차를 하나 생성합니다.
- 상태 변경, 철회 이벤트, destination 해제, 탐색 회차, outbox와 audit 중 하나라도
  실패하면 전체를 rollback합니다.

### 철회 재탐색

- `HospitalDispatchAttempt`에 저장된 검색 기준 좌표를 `HospitalSearchService`가 사용하도록 변경합니다.
- 현재 위치 저장 기능이 아직 없으므로 이 기능 시점의 최신 서버 좌표는 요청 생성
  좌표입니다. `SearchOriginResolver`가 최신 저장 좌표를 우선하고 없으면 요청 생성
  좌표를 반환하는 경계로 두어 계약을 고정합니다.
- `ACCEPTANCE_WITHDRAWAL` 회차는 `hospital_offers` 전체 이력에서 이미 연락한
  병원 profile ID를 조회해 후보에서 제외합니다.
- 모든 후속 회차는 시작 원인과 관계없이 `ACCEPTANCE_WITHDRAWN` 병원을
  후보에서 제외합니다. 수동 재전송은 기존 거절·무응답 병원을 다시 포함할 수
  있지만 철회 병원에는 다시 요청하지 않습니다.
- 요청 상태가 `SEARCHING` 또는 `ACCEPTED_AVAILABLE`이어도 철회 회차 scheduler가
  실행될 수 있게 하되, 현재 목적지가 새로 생기거나 회차가 종료되면 즉시 중단합니다.
- 새 병원이 수락하면 기존 첫 수락 정책처럼 회차 확대를 중단합니다. 남아 있던
  기존 수락 병원은 삭제하지 않습니다.
- 회차가 소진될 때 수락 병원이 남아 있으면 요청은 `ACCEPTED_AVAILABLE`을 유지하고,
  수락 병원이 없을 때만 `CANDIDATES_EXHAUSTED`로 변경합니다.

## 권한·정보 노출

- Controller `@PreAuthorize`와 서비스 역할 검증을 모두 유지합니다.
- 구급대원은 owner account와 EMS organization이 모두 일치해야 목적지를 변경할 수 있습니다.
- 병원은 account·organization·hospital profile이 모두 일치해야 철회할 수 있습니다.
- 다른 병원과 다른 구급대원에게는 리소스 존재를 숨기는 `TRANSPORT_005` 또는
  `TRANSPORT_001`을 반환합니다.
- `SUPER_ADMIN`은 목적지·철회·병원 목록·임상 상세를 사용할 수 없습니다.
- 숨겨진 수락 병원 이력에는 환자 임상정보, 구급대원 연락처, 정확한 위치,
  병원 좌표와 요청 본문을 포함하지 않습니다.
- SSE와 audit에는 공개 ID, 행위 종류, 대상만 저장하고 철회 detail·토큰·멱등성
  키·좌표·임상정보를 넣지 않습니다.

## 실시간·감사 설계

- `RealtimeEventType`:
  - `DESTINATION_SELECTED`
  - `DESTINATION_CHANGED`
  - `HOSPITAL_ACCEPTANCE_WITHDRAWN`
- `AuditAction`도 같은 세 행위를 추가합니다.
- 최초 선택은 구급대원 계정, 새 목적지 병원과 모든 비목적지 수락 병원 조직에
  갱신 신호를 보내 각 클라이언트가 권위 상태를 재조회하도록 합니다.
- 목적지 변경은 새 목적지와 이전 목적지 조직, 구급대원 계정에 신호를 보냅니다.
- 수락 철회는 구급대원 계정과 철회 병원 조직에 신호를 보내 구급대원은 탐색
  현황을, 병원 브라우저들은 `ACTIVE`·`HISTORY`를 재조회하게 합니다.
- 이벤트 payload는 기존과 같이 event ID, event type, aggregate type/public ID와
  발생 시각만 포함합니다.
- SSE 연결이 끊기거나 이벤트가 중복돼도 클라이언트는 REST 권위 상태를 다시 조회합니다.

## 잠금·멱등성 순서

```text
인증 계정·조직 확인
→ 공개 ID로 scope 확인(잠금 없음)
→ TransportRequest PESSIMISTIC_WRITE
→ 관련 HospitalDispatchAttempt PESSIMISTIC_WRITE
→ HospitalOffer PESSIMISTIC_WRITE
→ 멱등성 이력 확인
→ 최신 상태 재검증
→ 상태·이력·outbox·audit 저장
```

- 대상에 따라 없는 단계는 건너뛰되 잠금 순서를 뒤집지 않습니다.
- 목적지 명령 두 개는 요청 잠금으로 직렬화합니다.
- 목적지 선택과 같은 제안 철회도 요청 → 제안 순서로 직렬화합니다.
- scheduler도 기존 요청 → 탐색 회차 순서를 유지하고, 제안 생성·마감 전에
  요청 상태와 현재 목적지를 다시 확인합니다.
- DB 고유 제약 충돌은 알려진 멱등성 경합이면 저장된 결과를 재조회하고, payload가
  다르면 `COMMON_005`로 변환합니다.

## 구현 Step

| Step | 작업 | 완료 기준 |
|---:|---|---|
| 1 | V5 migration과 목적지 명령·철회·탐색 원인/좌표 Entity 및 Repository 작성 | V1→V5 MySQL 적용, FK·CHECK·고유 제약·인덱스와 JPA `validate` 일치 |
| 2 | `TransportRequest` 목적지 전이, 목적지 command fingerprint·이력과 서비스 작성 | 최초 선택·변경·재선택·무변경·멱등 충돌이 단일 목적지와 불변 이력을 유지 |
| 3 | 목적지 API와 구급대원 탐색 현황 응답·outbox·audit 확장 | 소유권·상태·응답 DTO와 `DESTINATION_SELECTED/CHANGED` 재조회 계약 통과 |
| 4 | 철회 사유·상태·멱등성, 병원 철회 API와 현재/비목적지 분기 작성 | OTHER 검증, 현재 목적지 해제, 비목적지 목적지 유지와 철회 이력 검증 |
| 5 | 철회 복구 탐색 회차, 검색 좌표 snapshot·기연락 병원 제외·scheduler 상태 전이 작성 | 같은 요청 유지, 이전 병원 중복 전송 없음, 목적지 선택 시 확대 중단과 소진 상태 정확 |
| 6 | 병원 ACTIVE/HISTORY 조회·상세 접근과 양쪽 SSE 대상 정리 | 숨겨진 수락 최소 이력·철회 가능, 임상 상세 차단, 관련 조직 목록 재조회 신호 검증 |
| 7 | 단위·통합·권한·멱등·동시성·outbox·MySQL 테스트 작성 | 목적지/철회 경합, 조직 격리, rollback, V1~V5와 기존 기능 회귀 통과 |
| 8 | 전체 검사·로컬 실행 후 review와 Flutter·React 핸드오프 작성 | `./gradlew clean check`, readiness와 가짜 데이터 E2E 통과, 실제 계약 문서 일치 |

## 변경 범위

| 패키지·파일 | 변경 내용 |
|---|---|
| `transport/domain/TransportRequest.java` | 현재 목적지 관계와 선택·변경·철회 후 상태 전이 추가 |
| `transport/destination/api/**` | 목적지 선택·변경 요청·응답과 Controller 추가 |
| `transport/destination/application/**` | 소유권·멱등성·잠금·outbox·감사 유스케이스 추가 |
| `transport/destination/domain/**` | 목적지 명령 결과·불변 이력 Entity 추가 |
| `transport/destination/infrastructure/**` | 목적지 명령 조회·잠금·멱등성 Repository 추가 |
| `hospital/search/domain/HospitalOffer*` | 철회 상태·사유·snapshot·이벤트와 전이 추가 |
| `hospital/search/api/**` | 철회 API, 목록 최소 이력, 현재 목적지·철회 응답 필드 추가 |
| `hospital/search/application/HospitalOfferService.java` | 철회 유스케이스와 ACTIVE/HISTORY/상세 가시성 적용 |
| `hospital/search/application/HospitalSearchService.java` | 회차 좌표 사용, 철회 복구 후보 제외·상태·중단 처리 |
| `hospital/search/infrastructure/**` | 목적지 관계·숨김 이력·기연락 병원·활성 회차 조회 추가 |
| `realtime/domain/RealtimeEventType.java` | 목적지 선택·변경·철회 이벤트 추가 |
| `audit/domain/AuditAction.java` | 목적지 선택·변경·수락 철회 감사 행위 추가 |
| `global/exception/ErrorCode.java` | 기존 `TRANSPORT_002`, `004`~`006`, `COMMON_005` 재사용; 새 오류가 꼭 필요할 때만 추가 |
| `db/migration/V5__*.sql` | 목적지 FK·명령 이력·철회·탐색 원인/좌표·CHECK 확장 |
| `src/test/**` | 목적지·철회 API, 개인정보 가시성, 검색 복구와 동시성·MySQL 테스트 추가 |

## 테스트 계획

### 목적지 선택·변경

- [x] 복수 수락 후 최초 선택이 `EN_ROUTE`와 현재 목적지 한 곳을 생성
- [x] A→B 변경 뒤 A 수락 이력 유지·A 활성 숨김·B 활성 복구
- [x] A→B→A 재선택과 목적지 명령 이력 순서 보존
- [x] 같은 목적지 새 키는 `UNCHANGED`, 같은 키 재시도는 저장된 결과 반환
- [x] 같은 키로 다른 offer 선택 시 `COMMON_005`
- [x] `PENDING`, `REJECTED`, `NO_RESPONSE`, `ACCEPTANCE_WITHDRAWN` 선택 차단
- [x] 다른 구급대원·병원·관리자·미인증 접근 차단

### 수락 철회

- [x] 모든 철회 사유와 `OTHER` detail 누락·공백·길이 검증
- [x] 목적지 선택 전 철회와 남은 수락 수에 따른 요청 상태
- [x] 현재 목적지 철회 시 목적지 해제·철회 이벤트·재탐색 회차 원자 저장
- [x] 비목적지 철회 시 현재 목적지·`EN_ROUTE` 유지, 재탐색 미생성
- [x] 철회 명령 같은 키 재시도와 같은 키 다른 사유 충돌
- [x] `HANDOFF_REQUESTED`, 종료 상태와 이미 철회된 제안 차단
- [x] 다른 병원 조직 철회 `TRANSPORT_005`

### 조회·개인정보·실시간

- [x] 목적지 선택 뒤 비목적지 수락이 ACTIVE에서 사라지고 HISTORY 최소 항목에 표시
- [x] 숨겨진 수락·철회 이력에 임상정보·연락처·좌표·ETA가 없음
- [x] 숨겨진 수락·철회 상세 접근 차단, 현재 목적지 상세 유지
- [x] 구급대원 응답에 목적지 표시와 철회 사유·시각 반영
- [x] 최초 선택 시 숨겨지는 모든 수락 병원 조직이 SSE 신호 수신
- [x] 철회 시 구급대원 계정과 철회 병원 조직이 SSE 신호 수신
- [x] 목적지 변경·철회 outbox가 상태 트랜잭션 rollback 시 함께 rollback
- [x] SSE payload와 로그에 임상정보·연락처·좌표·멱등성 키가 없음

### 철회 재탐색·동시성

- [x] 요청 생성 좌표 fallback과 회차 검색 좌표 snapshot 검증
- [x] 철회 병원과 이전 모든 회차의 기연락 병원 후보 제외
- [x] 철회 복구 소진 뒤 수동 재전송에서도 철회 병원은 제외하고 거절·무응답 병원만 재검토
- [x] 다른 수락이 남아도 철회 복구 회차 실행, 목적지 선택 시 `STOPPED_ON_DESTINATION`
- [x] 복구 회차 소진 시 수락이 있으면 `ACCEPTED_AVAILABLE`, 없으면 `CANDIDATES_EXHAUSTED`
- [x] 동시 A/B 목적지 변경에서 직렬화된 단일 최종 목적지
- [x] 대상 제안 목적지 선택과 철회 경합에서 허용된 두 순서 모두 모순 없는 최종 상태
- [x] 철회 API 중복 호출에서 철회·탐색 회차·outbox·audit 하나만 생성
- [x] 현재 목적지 철회 뒤 남은 병원 선택·철회 경합에서 활성 복구 회차가 하나만 유지
- [x] 철회 복구 소진 뒤 기존 `PENDING` 병원의 늦은 수락이 `ACCEPTED_AVAILABLE`을 복구
- [x] 철회 복구 소진과 기존 `PENDING` 병원의 늦은 수락 경합에서도 일관된 최종 상태 유지
- [x] 수동 재탐색 중 기존 `PENDING` 병원이 늦게 수락하면 활성 수동 회차를 `STOPPED_ON_ACCEPTANCE`로 종료
- [x] 병원이 수신 `OFF`로 변경해도 기존 현재 목적지·상세·철회 권한은 유지

### 회귀·전체 검증

- [x] 기존 가입·로그인·수신 ON/OFF·환자 평가·요청 생성·자동 탐색·복수 수락 유지
- [x] H2 빠른 검사와 Testcontainers MySQL 8.4 V1→V5 migration·JPA validate
- [x] `./gradlew clean check`
- [x] Docker MySQL 로컬 실행과 `/actuator/health/readiness`
- [x] 가짜 계정·가짜 환자·테스트 좌표로 복수 수락→A 선택→B 변경→철회 E2E

## 프론트 핸드오프

- 대상: `BOTH`
- Flutter: `docs/handoffs/05-destination-selection-change-acceptance-withdrawal/flutter-paramedic.md`
  - 현재 목적지·수락 병원 표시, 최초 선택·변경 명령
  - 수락 철회 표시, 현재 목적지 철회 후 재탐색 상태
  - 멱등성·오류·SSE 이후 탐색 현황 재조회
- React: `docs/handoffs/05-destination-selection-change-acceptance-withdrawal/react-hospital-admin.md`
  - 목적지 병원 활성 카드와 비목적지 최소 응답 이력
  - 수락 철회 사유·멱등성·상태별 가능 여부
  - 임상 상세 접근 종료와 SSE 이후 ACTIVE/HISTORY 재조회
- 구현과 로컬 검증 뒤 실제 request/response, enum, HTTP 상태와 오류만 기록합니다.
- 프론트 상태관리, 폴더 구조와 화면 컴포넌트 설계는 지시하지 않습니다.

## 유지할 계약

- 현재 목적지는 수락 병원 중 항상 0곳 또는 1곳입니다.
- 비목적지 수락은 구급대원 목록과 병원 최소 이력에 남지만 병원 임상 상세에서는 숨깁니다.
- 비목적지 철회는 현재 목적지를 유지하며 재탐색하지 않습니다.
- 현재 목적지 철회는 목적지를 해제하고 같은 요청·최신 가용 위치로 재탐색합니다.
- 목적지·철회 명령은 멱등하고 상태·이력·outbox·audit가 원자적으로 저장됩니다.
- `SUPER_ADMIN`, 다른 조직과 다른 요청 소유자의 환자정보·목적지 접근을 차단합니다.
- 기존 수락·거절·후보 소진·재전송과 네이버 ETA 실패 격리 계약을 유지합니다.
- 공통 오류 응답, `X-Trace-Id`, 민감정보 비로그와 적용된 migration 불변을 유지합니다.
- 위치 업로드·임상 갱신·이송 취소·인계 완료는 이번 구현에 포함하지 않습니다.

## 리스크

| 리스크 | 대응 |
|---|---|
| 목적지 변경·철회·scheduler의 잠금 순서가 달라 deadlock 또는 혼합 상태 발생 | 요청 → 탐색 회차 → 제안 순서 통일, 짧은 트랜잭션과 양방향 경합 통합 테스트 |
| 현재 목적지 FK가 다른 요청의 제안을 가리키거나 철회 제안을 유지 | 서비스 소속·상태 재검증, FK와 단일 요청 잠금, MySQL 무결성 테스트 |
| 숨겨진 수락 병원이 기존 목록·상세 조립을 통해 최신 임상정보를 계속 조회 | ACTIVE/HISTORY 전용 쿼리·최소 DTO, 상세 가시성 중앙화와 JSON 경로 부재 테스트 |
| 철회 복구 회차가 기존 병원에 중복 전송되거나 남은 수락이 있는데 요청을 소진 처리 | 시작 원인·좌표 snapshot, 요청 전체 기연락 병원 제외, 수락 수 기반 상태 전이 테스트 |
| 멱등 재시도에서 목적지·철회·outbox·감사·탐색 회차가 중복 생성 | 명령 fingerprint와 DB 고유 제약, 충돌 재조회, 동시 중복 호출 및 rollback 테스트 |
