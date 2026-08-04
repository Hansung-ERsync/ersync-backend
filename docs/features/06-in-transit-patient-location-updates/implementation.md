# 이송 중 환자·위치 갱신 구현 계획

```text
Feature: in-transit-patient-location-updates
Author: backend AI collaboration
Handoff Targets: BOTH
```

> `Policy Decision Status: NONE`인 `spec.md`, 현재 V1~V5 스키마와 목적지·병원
> 탐색·ETA·SSE 구현을 기준으로 작성했습니다. 제품 정책은 `spec.md`를 따르고,
> 이 문서는 그 정책을 안전하게 구현하고 검증하기 위한 기술 계획입니다.

## 설계 요약

### 선택한 방식

- 임상 갱신은 활력징후·의식·Pre-KTAS·처치별 타입이 명확한 API로 한 건씩 받습니다.
- 기존 `vital_sign_sets`, `consciousness_assessments`, `pre_ktas_assessments`,
  `treatment_events`에 append-only로 저장하고 `CurrentPatientSnapshot`의 최신 포인터만
  조건부로 전진시킵니다.
- 네 임상 명령과 위치 명령의 멱등성은 공통 `transport_update_commands` 테이블에서
  요청 단위로 보장합니다. 명령 본문은 저장하지 않고 타입·키·SHA-256 fingerprint·
  결과 식별자와 서버 시각만 저장합니다.
- 위치는 `transport_current_locations`의 요청별 단일 행을 갱신합니다. 이전 좌표를
  별도 행, 감사 payload 또는 outbox payload로 보존하지 않습니다.
- 병원 임상 접근과 정확한 위치 접근은 분리합니다. 임상은 `PENDING` 및 목적지
  선정 전 `ACCEPTED`, 또는 현재 목적지에 허용하지만 정확한 위치는 현재 목적지에만
  허용합니다.
- 위치 freshness는 DB 상태를 바꾸는 scheduler 없이 조회 시 `Clock`과
  `last_received_at` 차이로 `CURRENT`·`STALE`을 계산합니다.
- ETA는 기존 비동기 scheduler를 재사용하되 계산 세대 번호를 추가합니다. 외부
  지도 호출 중 새 위치가 들어오면 이전 세대 결과를 버리고 최신 세대만 반영합니다.
- 임상 timeline은 기존 네 원본 테이블을 수정하지 않고, 타입·임상 시각·서버 수신
  시각을 `UNION ALL`로 정렬·페이징한 뒤 타입별 레코드를 묶어 DTO로 조립합니다.

### 선택 이유

- 임상 원본 테이블은 이미 측정·관찰·평가·처치 시각과 서버 수신 시각, 작성자와
  환자 요청 FK를 갖고 있어 새 임상 저장 모델을 중복 만들 필요가 없습니다.
- 타입별 API는 하나의 자유 형식 갱신 API보다 Bean Validation과 조건부 의료 입력
  검증이 명확하고, Flutter가 잘못된 필드 조합을 보내는 문제를 줄입니다.
- 위치 테이블을 한 행으로 제한하면 “최신 위치만 유지하고 경로는 저장하지 않는다”는
  MVP 개인정보 원칙을 DB 구조로 강제할 수 있습니다.
- 요청 단위 공통 명령 테이블은 서로 다른 endpoint에서 같은 멱등성 키가 재사용되는
  경우도 충돌로 처리하여 중복 임상 기록과 위치 갱신을 막습니다.
- 계산 세대 번호는 네이버 API 호출이 DB 트랜잭션 밖에서 실행되는 동안 더 최신
  위치가 들어오는 경합을 안전하게 해결합니다.
- stale 여부를 조회 시 계산하면 30초마다 DB를 변경하거나 경고 이벤트를 만들 필요가
  없고, `STALE`이 오류가 아니라 표시 상태라는 정책과 일치합니다.

### 검토한 대안과 제외 이유

- 전체 환자 snapshot을 매번 통째로 전송하는 방식: 변경되지 않은 임상정보까지
  반복 전송되고 기존 기록을 실수로 덮어쓸 가능성이 있어 제외합니다.
- 모든 임상 타입을 자유 형식 JSON 한 endpoint로 받는 방식: 타입별 조건부 검증과
  프론트 계약이 불명확해져 제외합니다.
- GPS 위치를 append-only로 모두 저장하는 방식: 전체 이동 경로를 만들기 때문에
  MVP 개인정보 원칙과 직접 충돌하여 제외합니다.
- 위치가 올 때마다 네이버 Directions API를 요청 트랜잭션 안에서 동기 호출하는 방식:
  외부 장애가 위치 저장을 실패시키고 응답 지연이 커져 제외합니다.
- 30초 시점에 scheduler가 위치 행을 `STALE`로 변경하는 방식: 실제 좌표 변화가 없는
  시간 기반 파생 상태를 계속 저장해야 하므로 조회 시 계산으로 대체합니다.

## API 계획

### Flutter 구급대원 임상 명령

모든 명령은 다음 공통 조건을 사용합니다.

```text
Authorization: Bearer {accessToken}
Idempotency-Key: 8~100자의 [A-Za-z0-9._:-]
Content-Type: application/json
```

| API | 입력 핵심 | 신규 성공 | 멱등 재시도 |
|---|---|---:|---:|
| `POST /api/v1/transport-requests/{requestId}/clinical-updates/vital-signs` | `measuredAt`, `enteredAt`, 다섯 `measurements` | 201 | 200 |
| `POST /api/v1/transport-requests/{requestId}/clinical-updates/consciousness` | `avpu`, 평가 불가 사유·상세, `observedAt`, `enteredAt` | 201 | 200 |
| `POST /api/v1/transport-requests/{requestId}/clinical-updates/pre-ktas` | 완료 단계 또는 긴급 미완료 사유, `assessedAt`, `standardVersion`, `enteredAt` | 201 | 200 |
| `POST /api/v1/transport-requests/{requestId}/clinical-updates/treatments` | 처치 종류·결과·조건부 상세, `performedAt`, `enteredAt` | 201 | 200 |

공통 응답은 다음 정보를 반환합니다.

```text
transportRequestId
updateType
recordId
clinicalAt
serverReceivedAt
snapshotUpdated
lastClinicalUpdateAt
idempotentReplay
```

- `recordId`는 새 임상 원본의 UUID입니다.
- `clinicalAt`은 활력징후 `measuredAt`, 의식 `observedAt`, Pre-KTAS의
  `assessedAt` 또는 미완료 시 `enteredAt`, 처치의 `performedAt` 또는 없으면
  `enteredAt`입니다.
- `snapshotUpdated=false`여도 원본 저장은 성공한 것입니다. 더 오래된 임상 기록이
  늦게 도착해 현재 요약을 되돌리지 않았음을 뜻합니다.
- 평가 프로토콜 버전은 request body로 받지 않고 `TransportRequest`의 고정값을
  사용합니다. Pre-KTAS `standardVersion`은 기존과 같이 기록별로 보존합니다.

### Flutter 구급대원 위치 명령·조회

| API | 입력·조회 | 성공 |
|---|---|---:|
| `PUT /api/v1/transport-requests/{requestId}/location` | `latitude`, `longitude`, `capturedAt`과 `Idempotency-Key` | 200 |
| `GET /api/v1/transport-requests/{requestId}/location` | 자기 요청의 최신 위치·freshness·현재 목적지 ETA 조회 | 200 |
| `GET /api/v1/transport-requests/{requestId}/clinical-timeline?page=0&size=50` | 자기 요청의 최신 snapshot과 임상 원본 이력 | 200 |

위치 응답:

```text
transportRequestId
latitude
longitude
capturedAt
lastReceivedAt
freshness: NOT_RECEIVED | CURRENT | STALE
ageSeconds
serverNow
locationReplaced
routeEstimateStatus
routeDistanceMeters
etaSeconds
etaCalculatedAt
lastSuccessfulRouteDistanceMeters
lastSuccessfulEtaSeconds
lastSuccessfulEtaCalculatedAt
idempotentReplay
```

- 위치가 아직 없으면 조회 응답의 좌표·시각·ETA는 `null`, freshness는
  `NOT_RECEIVED`로 반환합니다. `NOT_RECEIVED`는 입력 저장 상태가 아니라 조회용
  파생 상태입니다.
- 더 오래된 `capturedAt`이 도착하면 `locationReplaced=false`로 반환하고 저장된
  최신 좌표를 유지합니다.
- `ageSeconds`는 `serverNow - lastReceivedAt`이며 음수가 되지 않게 0 이상으로
  정규화합니다.

### React 병원 웹 조회

| API | 허용 범위 | 반환 |
|---|---|---|
| 기존 `GET /api/v1/hospitals/me/offers?view=ACTIVE` | 기존 ACTIVE 정책 | 최신 임상 갱신 시각 필드 추가 |
| 기존 `GET /api/v1/hospitals/me/offers/{offerId}` | 임상 공개 권한이 남은 제안 | 갱신된 `CurrentPatientSnapshot` 기준 최신 요약 |
| `GET /api/v1/hospitals/me/offers/{offerId}/clinical-timeline?page=0&size=50` | `PENDING`, 목적지 전 `ACCEPTED`, 현재 목적지 | 타입이 있는 시간순 임상 원본 이력 |
| `GET /api/v1/hospitals/me/offers/{offerId}/location` | 현재 목적지 `ACCEPTED` 제안만 | 정확한 최신 위치·freshness·현재 목적지 ETA |

- 병원 임상 timeline은 최대 `size=100`으로 제한하고
  `clinicalAt ASC, serverReceivedAt ASC, recordId ASC` 순서를 사용합니다.
- 목적지가 정해진 뒤 비목적지 수락 병원의 임상 timeline·위치 조회는
  `TRANSPORT_005`로 숨깁니다.
- 위치 조회는 현재 목적지 여부를 요청 잠금 또는 일관된 단일 조회 안에서 다시
  검증하고, 이전 목적지에는 좌표 일부도 반환하지 않습니다.
- 기존 응답 필드는 삭제하지 않고 새 필드만 추가합니다.

## 상태·권한 규칙

### 구급대원 명령 가능 상태

| 요청 상태 | 임상 추가 | 위치 갱신 | 이유 |
|---|---:|---:|---|
| `SEARCHING` | 허용 | 허용 | 응답 대기·목적지 철회 뒤에도 환자와 구급차는 계속 이동 가능 |
| `CANDIDATES_EXHAUSTED` | 허용 | 허용 | 재전송·전화 대응 중 최신 상태 보존 |
| `ACCEPTED_AVAILABLE` | 허용 | 허용 | 목적지 선택 전 최신 수용 판단 지원 |
| `EN_ROUTE` | 허용 | 허용 | 정상 이송 중 갱신 |
| `HANDOFF_REQUESTED` | 거절 | 거절 | 인계 완료 단계 진입 후 임상·위치 고정 |
| `COMPLETED` | 거절 | 거절 | 종료 이력 불변 |
| `CANCELLED` | 거절 | 거절 | 취소 후 재개 금지 |

### 병원 임상 조회 가능 여부

| 제안·목적지 상태 | 최신 요약 | timeline | 정확한 위치 |
|---|---:|---:|---:|
| `PENDING`, 목적지 없음 | 허용 | 허용 | 금지 |
| `ACCEPTED`, 목적지 없음 | 허용 | 허용 | 금지 |
| `ACCEPTED`, 자기 병원이 현재 목적지 | 허용 | 허용 | 허용 |
| `ACCEPTED`, 다른 병원이 현재 목적지 | 최소 응답 이력만 | 금지 | 금지 |
| `REJECTED`, `NO_RESPONSE`, `ACCEPTANCE_WITHDRAWN` | 금지 | 금지 | 금지 |
| `HANDOFF_REQUESTED`의 현재 목적지 | 이번 기능에서는 조회 유지 | 조회 유지 | 마지막 위치 조회 유지 |
| `COMPLETED`, `CANCELLED` | 활성 API에서 금지 | 이번 기능 신규 API에서 금지 | 금지 |

- `HANDOFF_REQUESTED` 이후의 최종 조회·이력 화면 전환은 다음 인계·종료 기능과
  함께 다시 검증합니다. 이번 기능은 새 갱신만 차단하고 현재 목적지의 마지막
  수신 상태를 잃지 않게 합니다.
- `SUPER_ADMIN`, 다른 구급대원, 다른 병원 조직은 임상·위치에 접근하지 못합니다.

## 도메인·애플리케이션 설계

### 공통 명령과 소유권

- `TransportUpdateCommand`
  - `transportRequest`, `commandType`, `idempotencyKey`, `requestFingerprint`
  - `resultRecordPublicId`, `resultClinicalAt`, `snapshotUpdated`,
    `locationReplaced`, `serverReceivedAt`, `createdAt`
  - 요청별 `idempotencyKey` unique
- `TransportUpdateCommandType`
  - `VITAL_SIGNS`, `CONSCIOUSNESS`, `PRE_KTAS`, `TREATMENT`, `LOCATION`
- `TransportUpdateFingerprint`
  - command type과 정규화된 입력을 고정 순서로 SHA-256
  - 문자열 trim·null·소수 좌표 scale을 결정적으로 정규화
  - 원본 임상정보와 좌표를 별도 저장하거나 로그에 출력하지 않음
- 모든 명령은 `PARAMEDIC` 계정과 조직을 확인한 뒤
  `TransportRequestRepository.findLockedOwnedByPublicId(...)`로 request를 먼저
  잠급니다.
- 같은 request에 대한 임상·위치·목적지·철회 명령이 request lock에서
  직렬화되도록 기존 잠금 순서를 유지합니다.

### 임상 갱신 서비스

- `TransportClinicalUpdateController`
  - 네 typed endpoint의 인증·DTO 검증·HTTP 상태 변환만 담당
- `TransportClinicalUpdateService`
  1. 멱등성 키 형식, 계정·조직·요청 소유권과 상태 확인
  2. 기존 명령 조회 후 fingerprint replay·충돌 판정
  3. 타입별 입력을 기존 Entity로 append-only 저장
  4. 임상 시각 비교 후 `CurrentPatientSnapshot` 포인터 또는 처치 목록 갱신
  5. 공통 명령 결과, audit와 대상 병원 outbox 저장
  6. 한 트랜잭션으로 commit 후 응답 반환
- `ClinicalInputValidator`
  - `TransportRequestService` 내부의 Pre-KTAS·AVPU·활력징후·처치 조건부 검증을
    공통 component로 추출
  - 최초 요청과 갱신 API가 같은 숫자 범위, 공식 상태와 `OTHER` 상세 규칙 사용
- `ClinicalRecordMapper`
  - 생성 API와 갱신 API의 DTO를 기존 임상 Entity로 변환하는 중복 제거
- `CurrentPatientSnapshot`
  - `advanceVitalSigns`, `advanceConsciousness`, `advancePreKtas`,
    `appendTreatment` 도메인 메서드 추가
  - 임상 시각이 현재 포인터보다 늦거나, 같은 시각에 서버 수신 시각이 늦을 때만
    최신 포인터 변경
  - 처치 원본은 항상 이력에 보존합니다. 최초 `NONE` 뒤 실제 처치가 추가되면
    `NONE`은 현재 snapshot 목록에서만 제거하고, 정렬은 `clinicalAt`,
    `serverReceivedAt`, PK로 결정합니다.
  - `lastClinicalUpdateAt`은 서버가 갱신을 받아들인 최신 시각으로 단조 증가
- `ClinicalTimelineQueryService`
  - 네 원본 테이블에서 request ID, 타입, record ID, 임상 시각과 서버 수신 시각만
    `UNION ALL`로 조회해 DB에서 정렬·페이징
  - 한 페이지의 ID를 타입별 batch 조회한 뒤 공통 item DTO로 재조립
  - 환자 직접 식별정보는 DTO에 포함하지 않음

### 임상 알림 대상 계산

- `ClinicalAudienceResolver`가 request의 현재 목적지와 offer 상태를 기준으로 조직
  public ID 집합을 계산합니다.
- 현재 목적지가 없으면 `PENDING`과 `ACCEPTED` 병원 조직에 알립니다.
- 현재 목적지가 있으면 현재 목적지 병원 조직 하나에만 알립니다.
- 이벤트 type은 갱신 종류에 따라 `VITAL_SIGNS_ADDED`,
  `CONSCIOUSNESS_CHANGED`, `PRE_KTAS_CHANGED`, `TREATMENT_ADDED`를 사용합니다.
- 이벤트에는 임상 값이 없고 `aggregateType=TRANSPORT_REQUEST`, request UUID만
  포함합니다. 병원은 이벤트 수신 후 ACTIVE 목록·상세 또는 timeline을 재조회합니다.
- 구급대원에게는 요청 응답으로 권위 상태를 반환하므로 같은 명령의 SSE는 필수가
  아닙니다. 다른 기기 복구가 필요하면 `PATIENT_SNAPSHOT_UPDATED` 계정 이벤트를
  한 건 추가하되 중복 이벤트는 테스트로 고정합니다.

### 최신 위치와 stale 계산

- `TransportCurrentLocation`
  - request와 1:1, UUID, 위도·경도, `capturedAt`, `lastReceivedAt`, version
  - 최초 수신은 행 생성, 이후 수신은 같은 행 update
  - `capturedAt`이 더 오래되면 좌표·capturedAt을 유지하고 `lastReceivedAt`도
    바꾸지 않습니다. 그렇지 않으면 좌표와 두 시각을 갱신합니다.
  - 같은 `capturedAt`은 request lock으로 직렬화된 마지막 수신을 사용합니다.
- `TransportLocationService`
  - 위치 명령 권한·상태·멱등성 확인
  - 위치 한 행 갱신, 현재 목적지 ETA 예약, audit·outbox·command 저장
  - 오래된 위치는 성공 응답하지만 위치·ETA·위치 SSE를 새로 만들지 않음
- `LocationFreshnessPolicy`
  - 설정 `ersync.location.stale-after: PT30S`
  - 위치 없음 `NOT_RECEIVED`, 경과 30초 미만 `CURRENT`, 이상 `STALE`
  - `Clock`을 주입해 경계값 29.999초·30초를 결정적으로 테스트
- `TransportLocationQueryService`
  - 구급대원 owner 조회와 현재 목적지 병원 조회를 분리된 메서드로 제공
  - DTO 조립 직전에도 request의 최신 목적지와 조직을 확인
- `SearchOriginResolver`
  - `transport_current_locations`가 있으면 최신 위치를 반환
  - 없으면 기존 `originLatitude`, `originLongitude` fallback 유지

### 동적 ETA 계산

- V6에서 `hospital_offers.route_estimate_generation`을 추가하고 기존 행은 0으로
  backfill합니다.
- 위치가 최신 행을 교체하거나, 저장 위치가 있는 상태에서 목적지가 바뀌면 새
  목적지 offer의 계산 세대를 1 증가시키고 다음 계산 시각을 현재로 예약합니다.
- 예약 시 `routeEstimateStatus=CALCULATING`, `etaAttemptCount=0`으로 초기화합니다.
- `RouteEstimateWork`에 `generation`을 추가합니다.
- `RouteEstimatePersistence.claim`은 현재 목적지 offer라면 최신 위치를 출발점으로,
  그 외 최초·재탐색 후보라면 `dispatchAttempt.searchOrigin`을 출발점으로 사용합니다.
- 외부 API 호출 뒤 `complete`, `retryOrFinish`, `finishUnavailable`은 expected
  generation이 현재 offer generation과 같은 경우에만 결과를 적용합니다.
- 세대가 다르면 오래된 결과를 버리고 새 세대의 예약 상태를 유지합니다.
- 마지막 성공 거리·ETA·계산 시각은 별도 컬럼에 보존하여 새 계산이 실패해도
  계산 시각과 함께 표시할 수 있게 합니다.
- 목적지 변경 시 `TransportDestinationService`가 저장된 최신 위치 존재 여부를
  확인해 새 목적지 ETA를 즉시 예약합니다. 이전 목적지는 더 이상 동적 ETA·위치
  이벤트 대상이 아닙니다.
- 지도 API 장애는 기존처럼 DB 트랜잭션 밖에서 처리하며 위치 명령을 rollback하지
  않습니다.

## DB 변경

새 migration `V6__create_in_transit_patient_location_update_schema.sql`을 추가합니다.
기존 V1~V5는 수정하지 않습니다.

### `transport_update_commands`

| 컬럼 | 형식·제약 | 용도 |
|---|---|---|
| `id` | `BIGINT PK AUTO_INCREMENT` | 내부 식별자 |
| `public_id` | `CHAR(36) UNIQUE NOT NULL` | 외부·audit 식별자 |
| `transport_request_id` | `BIGINT FK NOT NULL` | 요청 aggregate |
| `command_type` | `VARCHAR(30) NOT NULL CHECK` | 다섯 갱신 타입 |
| `idempotency_key` | `VARCHAR(100) NOT NULL` | 요청 단위 멱등성 키 |
| `request_fingerprint` | `BINARY(32) NOT NULL` | 정규화 payload digest |
| `result_record_public_id` | `CHAR(36) NULL` | 임상 원본 또는 위치 행 UUID |
| `result_clinical_at` | `DATETIME(6) NULL` | 임상 명령 발생 시각 |
| `snapshot_updated` | `BOOLEAN NULL` | 최신 snapshot 전진 여부 |
| `location_replaced` | `BOOLEAN NULL` | 최신 위치 교체 여부 |
| `server_received_at` | `DATETIME(6) NOT NULL` | 최초 처리 서버 시각 |
| `created_at` | `DATETIME(6) NOT NULL` | 행 생성 시각 |

제약·인덱스:

- unique `(transport_request_id, idempotency_key)`
- index `(transport_request_id, server_received_at)`
- command type별 결과 컬럼 null 조합 CHECK
- fingerprint·키·결과만 저장하고 임상 본문과 좌표는 저장하지 않음

### `transport_current_locations`

| 컬럼 | 형식·제약 | 용도 |
|---|---|---|
| `id` | `BIGINT PK AUTO_INCREMENT` | 내부 식별자 |
| `public_id` | `CHAR(36) UNIQUE NOT NULL` | 위치 snapshot UUID |
| `transport_request_id` | `BIGINT FK UNIQUE NOT NULL` | 요청별 정확히 한 행 |
| `latitude` | `DECIMAL(10,7) NOT NULL CHECK -90~90` | 최신 위도 |
| `longitude` | `DECIMAL(10,7) NOT NULL CHECK -180~180` | 최신 경도 |
| `captured_at` | `DATETIME(6) NOT NULL` | 단말 위치 획득 시각 |
| `last_received_at` | `DATETIME(6) NOT NULL` | 서버 최신 수신 시각 |
| `created_at`, `updated_at` | `DATETIME(6) NOT NULL` | 저장 시각 |
| `version` | `BIGINT NOT NULL DEFAULT 0` | 낙관적 잠금 |

- 요청 삭제 cascade는 현재 FK 정책과 동일하게 명시적으로 결정하고 MySQL 테스트로
  검증합니다. MVP에서는 이송 요청을 물리 삭제하지 않습니다.
- 이전 좌표용 테이블·이력 컬럼·좌표 audit 테이블은 만들지 않습니다.

### `hospital_offers` 확장

| 컬럼 | 형식 | backfill·의미 |
|---|---|---|
| `route_estimate_generation` | `BIGINT NOT NULL DEFAULT 0` | 기존 계산 세대 0 |
| `last_success_route_distance_m` | `BIGINT NULL` | 기존 AVAILABLE 거리 backfill |
| `last_success_eta_seconds` | `BIGINT NULL` | 기존 AVAILABLE ETA backfill |
| `last_success_eta_calculated_at` | `DATETIME(6) NULL` | 기존 AVAILABLE 계산 시각 backfill |

- 거리·ETA는 음수가 될 수 없도록 CHECK를 추가합니다.
- 기존 `route_distance_m`, `eta_seconds`, `eta_calculated_at` 계약은 유지하고 새
  last-success 필드는 additive로 노출합니다.
- 스키마 변경 뒤 `ddl-auto=validate`와 V1→V6 clean migration을 MySQL 8.4에서
  확인합니다.

## 트랜잭션·잠금 순서

동일 요청 aggregate의 명령은 다음 순서를 유지합니다.

```text
행위 계정
→ TransportRequest PESSIMISTIC_WRITE
→ TransportUpdateCommand replay 확인
→ CurrentPatientSnapshot 또는 TransportCurrentLocation
→ 현재 목적지 HospitalOffer
→ 임상 원본·command·audit·outbox 저장
```

- 목적지 선택·철회·검색 scheduler도 request를 먼저 잠그는 현재 규칙을 유지합니다.
- ETA scheduler는 먼저 offer가 속한 request ID만 projection으로 읽고 request를 잠근
  뒤 offer를 잠그도록 보완합니다.
- 외부 네이버 API 호출은 어떤 DB lock도 유지하지 않은 상태에서 수행합니다.
- request lock으로 같은 요청의 중복·서로 다른 임상·위치·목적지 경합을
  직렬화하고 DB unique 제약을 최종 방어선으로 둡니다.
- `OptimisticLockException`이나 unique 충돌을 일반 500으로 노출하지 않고, 동일
  명령이면 저장 결과를 재조회하고 다른 payload면 `COMMON_005`로 변환합니다.

## 구현 Step

| Step | 작업 | 구현 후 검증 |
|---:|---|---|
| 1 | 공통 임상 검증기를 추출하고 typed request·response·fingerprint를 정의 | 최초 요청 검증 회귀와 네 임상 DTO의 정상·조건부 오류 단위 테스트 |
| 2 | V6 migration, `TransportUpdateCommand`, `TransportCurrentLocation`, repository와 snapshot 전진 메서드 구현 | MySQL 8.4 V1→V6, CHECK·unique·FK·JPA validate 테스트 |
| 3 | 네 임상 갱신 API와 `TransportClinicalUpdateService` 구현 | append-only, snapshot 전진·미전진, 201/200 replay, `COMMON_005`, 상태·소유권 테스트 |
| 4 | 구급대원·병원 clinical timeline과 기존 병원 최신 요약 갱신 구현 | 시간순 페이징, 접근 대상 전환, 비목적지·타 조직·관리자 차단 테스트 |
| 5 | 최신 위치 PUT/GET, freshness 정책과 `SearchOriginResolver` 연동 구현 | 단일 행 overwrite, 오래된 좌표 무시, 30초 경계·복구, 재탐색 최신 위치 테스트 |
| 6 | ETA 세대·last-success·동적 재계산과 목적지 선택 연동 구현 | 최신 세대만 반영, 지도 실패 격리, 목적지 변경 전후 ETA·좌표 접근 테스트 |
| 7 | 임상·위치 audit, 대상별 outbox·SSE type과 민감정보 비노출 구현 | recipient 행렬, rollback, 중복 이벤트, payload·로그 좌표/임상 원문 부재 테스트 |
| 8 | 전체 회귀·동시성·로컬 실행 검증 후 `review.md`와 양쪽 핸드오프 작성 | `./gradlew clean check`, 로컬 MySQL readiness, 실제 코드 기준 문서 일치 |

각 Step은 구현 → 해당 범위 자동 검증 → 발견 문제 수정 후 다음 Step으로 이동합니다.
커밋·푸시·PR은 전체 Step과 검수 문서가 끝난 뒤 사용자가 요청할 때만 수행합니다.

## 변경 범위

| 패키지·파일 | 변경 내용 |
|---|---|
| `transport/api` | typed 임상 갱신, 소유자 timeline, 위치 PUT/GET DTO·Controller |
| `transport/application` | 임상 갱신·timeline·위치 command/query, fingerprint, freshness·audience 정책 |
| `transport/domain` | update command, current location, snapshot 최신 포인터·처치 추가 규칙 |
| `transport/infrastructure` | command·location repository, 임상 timeline union query, request lock 조회 |
| `hospital/search/api` | 병원 timeline·위치 조회와 기존 목록 additive 필드 |
| `hospital/search/application` | 병원 임상·위치 접근 정책, SearchOriginResolver, ETA 세대 coordinator |
| `hospital/search/domain` | route generation, 재계산·last-success 도메인 메서드 |
| `realtime`, `audit` | 임상·위치 이벤트 타입과 최소 audit action 추가 |
| `application*.yaml` | stale 기준 30초 설정과 테스트 override |
| `db/migration/V6__...sql` | command·최신 위치 테이블과 ETA 세대·last-success 컬럼 |
| `src/test/**` | 단위·통합·권한·멱등·동시성·MySQL·회귀 테스트 |
| `docs/handoffs/06-...` | Flutter·React 실제 연동 계약 |

## 테스트 계획

### 임상 입력·append-only

- [x] 활력징후 다섯 타입 정상값과 측정 불가·환자 거부 조합
- [x] 활력징후 중복·누락 타입, 범위·조건부 상세 오류 `COMMON_001`
- [x] AVPU 정상·평가 불가 사유와 `OTHER` 상세 검증
- [x] Pre-KTAS 완료 1~5와 긴급 미완료 사유, 고정 프로토콜 사용
- [x] 처치별 상세, 실패한 처치 시도 보존, 잘못된 조합 차단
- [x] 각 원본의 임상·입력·서버 시각 구분
- [x] 이전 임상 행 불변과 새 public ID 생성
- [x] 더 오래된 임상 기록 늦은 도착 시 원본 추가·snapshot 미전진
- [x] 같은 임상 시각의 서버 수신 순서 결정성
- [x] snapshot 변경과 원본·command·audit·outbox 원자 rollback

### 멱등성·상태·권한

- [x] 같은 키·같은 payload 200 replay와 원본·이벤트 1개
- [x] 같은 키·다른 payload `COMMON_005`
- [x] 서로 다른 임상 endpoint에서 같은 키 재사용 충돌
- [x] 네 활성 상태에서 임상·위치 허용
- [x] `HANDOFF_REQUESTED`, `COMPLETED`, `CANCELLED`에서 `TRANSPORT_004`
- [x] 다른 구급대원·조직·병원·`SUPER_ADMIN` 명령 차단
- [x] 비활성 계정·조직 차단

### 병원 임상 가시성

- [x] 목적지 전 `PENDING`·`ACCEPTED` 병원 최신 요약·timeline 갱신
- [x] 목적지 선택 뒤 현재 목적지만 이후 갱신·timeline 접근
- [x] 비목적지 수락·거절·무응답·철회 병원 `TRANSPORT_005`
- [x] 목적지 변경 즉시 이전 병원 차단·새 병원 전체 최신 snapshot 조회
- [x] timeline 안정 정렬·페이지 경계·최대 size 검증
- [x] 기존 병원 목록·상세 DTO의 추가 필드와 기존 필드 회귀

### 위치·freshness·개인정보

- [x] 최초 위치 행 생성과 다음 위치 동일 행 update
- [x] DB에 요청별 위치 행 1개만 존재하고 이전 좌표가 남지 않음
- [x] 오래된 capturedAt이 최신 위치·lastReceivedAt·ETA를 되돌리지 않음
- [x] 같은 capturedAt 동시 명령의 결정적 최종 위치
- [x] 29.999초 `CURRENT`, 30초 `STALE`, 경과시간과 마지막 좌표 유지
- [x] 새 위치 수신 뒤 `CURRENT` 자동 복구
- [x] 위치 없음 `NOT_RECEIVED`
- [x] 현재 목적지와 owner만 정확한 좌표 조회
- [x] 목적지 변경·철회 경합 뒤 이전 병원 좌표 접근 차단
- [x] audit·outbox·오류·애플리케이션 로그에 좌표 원문 없음

### ETA·재탐색·동시성

- [x] 최신 위치에서 현재 목적지까지 새 거리·ETA 예약
- [x] 빠른 위치 갱신 중 이전 generation 외부 응답 무시
- [x] 최신 generation 성공만 `AVAILABLE` 반영
- [x] 일시·영구 네이버 실패에도 위치 갱신 성공 유지
- [x] 실패 후 last-success 값과 계산 시각 유지
- [x] 위치 저장 후 목적지 선택 시 다음 위치를 기다리지 않고 ETA 예약
- [x] 목적지 철회 뒤 새 offer 거리·ETA가 최신 search origin 사용
- [x] 위치 갱신·목적지 변경·철회·검색 scheduler 양방향 경합에서 deadlock·권한 누출 없음

### 전체 검증

- [x] 기존 가입·인증·요청 생성·자동 탐색·병원 응답·목적지·철회 회귀
- [x] H2 빠른 검사
- [x] Testcontainers MySQL 8.4 V1→V6 migration·JPA validate
- [x] `./gradlew clean check`
- [x] Docker 로컬 MySQL과 `./scripts/dev-start.sh`
- [x] `/actuator/health/readiness`가 `UP`
- [x] 가짜 환자·연락처·좌표만 사용한 MockMvc API 통합 시나리오

## 프론트 핸드오프

- 대상: `BOTH`
- Flutter: `docs/handoffs/06-in-transit-patient-location-updates/flutter-paramedic.md`
  - 네 임상 갱신 request/response와 멱등성
  - 위치 약 10초 전송, 오래된 위치 응답과 재연결 처리
  - 목적지 변경·철회 뒤 권위 상태 재조회
- React: `docs/handoffs/06-in-transit-patient-location-updates/react-hospital-admin.md`
  - 최신 임상 summary·timeline
  - 현재 목적지 전용 정확한 위치·freshness·동적 ETA
  - SSE 이후 상세·timeline·위치 REST 재조회
- 구현 전 예정 API가 아니라 구현과 테스트가 끝난 실제 path, DTO, enum, HTTP 상태,
  오류와 재조회 조건만 기록합니다.
- Flutter 백그라운드 위치 권한·상태관리·지도 UI와 React 컴포넌트 구조는 지시하지
  않습니다.

## 유지할 계약

- 임상 원본은 append-only이며 이전 기록을 수정·삭제하지 않습니다.
- 요청별 최신 정확한 위치 한 행만 유지하고 전체 GPS 경로를 저장하지 않습니다.
- 정확한 위치는 요청 owner와 현재 목적지 병원에만 제공합니다.
- 목적지 전에는 PENDING·ACCEPTED 병원, 목적지 후에는 현재 목적지만 최신 임상정보를 조회합니다.
- 30초 stale은 마지막 위치를 유지하는 표시 상태이며 오류·긴급 알림이 아닙니다.
- 지도 API 실패는 임상·위치 갱신을 실패시키지 않습니다.
- 목적지 철회 재탐색은 저장된 최신 위치를 우선 사용합니다.
- SSE는 민감정보 없는 변경 신호이고 클라이언트는 REST로 권위 상태를 재조회합니다.
- 공통 오류 응답, `X-Trace-Id`, 민감정보 비로그와 V1~V5 migration 불변을 유지합니다.
- 기존 목적지 선택·변경·수락 철회·수신 OFF 계약을 변경하지 않습니다.

## 리스크

| 리스크 | 대응 |
|---|---|
| 오래된 임상·위치 요청이 늦게 도착해 최신 snapshot을 되돌림 | 임상 시각·capturedAt 우선 비교, 동일 시각 tie-break와 순서 역전 통합 테스트 |
| 목적지 변경과 위치 조회 경합으로 이전 병원에 정확한 좌표 노출 | request 우선 잠금, 응답 직전 현재 목적지·조직 재검증, 양방향 경합 테스트 |
| 10초 위치 갱신마다 ETA 호출이 쌓이거나 오래된 외부 응답이 최신 결과를 덮음 | offer 계산 generation·최신 작업 합치기·lease, 오래된 generation 결과 폐기 테스트 |
| 공통 멱등성 fingerprint·audit·SSE에 임상정보나 정확한 좌표가 노출 | digest와 UUID만 저장, payload 없는 이벤트·감사, 로그·DB 컬럼 부재 보안 테스트 |
| 임상 timeline 조회가 길어져 응답 지연 또는 과다 노출 발생 | size 최대 100, DB 정렬·페이징, 타입별 batch 조회와 권한 선검증 |
