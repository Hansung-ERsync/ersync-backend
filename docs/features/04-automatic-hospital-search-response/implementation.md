# 자동 병원 탐색 및 병원 응답 구현 계획

```text
Feature: automatic-hospital-search-response
Author: backend AI collaboration
Handoff Targets: BOTH
```

> `Policy Decision Status: RESOLVED`인 `spec.md`와 현재 인증·병원·이송 요청
> 코드를 기준으로 작성한 계획입니다. 후보 반경은 직선거리로 판정하고, 실제
> 도로 거리와 ETA는 네이버 Directions 5 API로 계산합니다.

## 현재 코드에서 이어지는 지점

- `TransportRequestService`는 환자 평가·출발 좌표·구급대원 회신 연락처를 한 트랜잭션에 저장하고 요청을 `SEARCHING`으로 생성합니다.
- `HospitalProfile`에는 병원 좌표, 응급실 연락처와 신규 요청 수신 `ON/OFF` 상태가 있습니다.
- `UserAccount`에는 계정 `ACTIVE/INACTIVE`가 있지만 `Organization`에는 아직 활성 상태가 없습니다.
- `CurrentPatientSnapshot`에는 병원 카드에 필요한 현재 환자 요약이 구조화되어 있습니다.
- 아직 검색 회차, 반경 확대 기록, 병원별 제안, 병원 응답, ETA 결과와 실시간 전달 기록은 없습니다.
- 애플리케이션에는 아직 `@EnableScheduling`이 없으므로 DB 기반 검색·ETA·outbox 작업과 함께 scheduling 설정을 추가합니다.
- Dev 배포는 `ersync/dev/backend` Secret의 JSON을 `scripts/deploy-ec2.sh`가 런타임 `application.yaml`로 변환합니다.

## 설계 요약

### 선택한 방식

- 이송 요청 생성 트랜잭션 안에서 최초 `HospitalDispatchAttempt`를 함께 만들어 요청만 저장되고 탐색이 유실되는 상태를 막습니다.
- 활성·수신 `ON` 병원은 DB에서 먼저 줄이고, 서버가 Haversine 공식으로 요청 좌표와 병원 좌표의 직선거리를 계산합니다.
- 최초 10km부터 최소 3곳을 찾을 때까지 즉시 평가하고, 선택된 첫 반경의 모든 후보에 `HospitalOffer`를 한 번씩 생성합니다.
- 60초 확대 시각과 현재 반경은 DB에 저장하고 스케줄러가 만료된 검색 회차를 다시 처리합니다.
- 병원별 제안 상태는 현재값으로 빠르게 조회하고, 수락·거절·무응답 사실은 별도의 append-only 이벤트로 보존합니다.
- 네이버 Directions 호출은 DB 트랜잭션 밖의 작업자가 수행하며 결과를 제안의 거리·ETA 스냅샷으로 저장합니다.
- 지도 계산 전에는 `CALCULATING`, 성공하면 `AVAILABLE`, 실패가 확정되면 `UNAVAILABLE`을 반환합니다.
- 병원 응답과 재전송은 `Idempotency-Key`, 요청 지문, 행 잠금과 DB 고유 제약을 함께 사용합니다.
- 실시간 알림은 DB outbox와 인증된 SSE 스트림을 사용하며 이벤트에는 민감정보 대신 변경 종류와 공개 ID만 넣습니다.
- 실시간 이벤트를 놓쳐도 활성 목록과 검색 현황 조회 API가 항상 최종 상태를 반환합니다.

### 선택 이유

- 검색 시각과 제안을 DB에 먼저 저장하면 프로세스 재시작 후에도 60초 작업을 이어갈 수 있습니다.
- 후보 선정이 네이버 장애와 분리되어 ETA 계산이 실패해도 병원 요청은 전달됩니다.
- 현재 MySQL 데이터 규모에서는 수신 가능한 병원을 DB로 줄인 뒤 Java에서 Haversine을 계산하는 방식이 단순하고 충분히 검증 가능합니다.
- 제안 현재값과 불변 응답 이력을 분리하면 대시보드는 빠르게 조회하고 감사 기록은 삭제 없이 유지할 수 있습니다.
- outbox는 상태 변경과 알림 의도를 같은 트랜잭션에 저장하므로 DB 반영만 되고 알림이 사라지는 문제를 줄입니다.
- SSE는 서버에서 병원·구급대원으로 보내는 단방향 알림에 맞고, 클라이언트는 이벤트를 받은 뒤 기존 REST API를 재조회할 수 있습니다.

### 검토한 대안과 제외 이유

- 후보 판정까지 네이버 도로 거리 사용: 외부 장애·호출량·응답속도가 병원 선정 자체를 막으므로 제외합니다.
- MySQL 공간 타입과 공간 인덱스 즉시 도입: 현재 `DECIMAL` 좌표와 MVP 병원 수에 비해 migration·쿼리 복잡도가 커서 제외합니다.
- 60초 타이머를 메모리에만 등록: 서버 재시작 때 예정 작업을 잃을 수 있어 제외합니다.
- 외부 호출을 요청 생성 트랜잭션 안에서 실행: 네이버 지연이 DB 잠금과 환자 요청 저장 실패로 이어질 수 있어 제외합니다.
- Redis·Kafka 기반 알림: 현재 단일 Dev 인스턴스와 MVP 규모에 별도 운영 요소가 과하므로 DB outbox와 SSE를 선택합니다.

## 검색 흐름

### 최초 요청

1. 기존 `TransportRequestService`가 인증 구급대원, 구조화된 환자 평가와 현재 위치를 검증합니다.
2. 이송 요청·임상 원본·현재 환자 snapshot을 저장합니다.
3. 같은 트랜잭션에서 검색 회차 1을 `SEARCHING` 상태와 즉시 실행 시각으로 생성합니다.
4. 요청 트랜잭션이 커밋되면 scheduler가 회차를 집어 이송 요청 → 검색 회차 순으로 잠급니다.
5. 활성 병원 조직, 활성 공용 계정, 좌표와 수신 `ON` 조건으로 후보 풀을 조회합니다.
6. 각 병원의 직선거리를 한 번 계산해 10km부터 10km씩 후보 수를 평가합니다.
7. 처음 3곳 이상을 찾은 반경 또는 최대 100km에서 초기 검색 반경을 확정합니다.
8. 확정 반경 안의 제안·이력·outbox·60초 응답 마감 시각을 한 트랜잭션으로 저장합니다.

- 10km 1곳, 20km 2곳, 30km 4곳이면 10·20·30km 평가 기록을 남기고 30km의 4곳에만 제안을 만듭니다.
- 100km까지 후보가 2곳이면 두 곳에 전송하고 검색 회차의 `candidateShortage`를 `true`로 저장합니다.
- 100km까지 후보가 없으면 제안 없이 요청과 검색 회차를 즉시 `CANDIDATES_EXHAUSTED`로 바꿉니다.

### 60초 확대

- `HospitalSearchScheduler`는 짧은 주기로 `nextExpansionAt <= now`인 검색 회차 ID를 제한된 개수만 조회합니다.
- 각 회차는 별도 트랜잭션에서 이송 요청 → 검색 회차 순으로 잠가 동시에 두 작업자가 확대하지 못하게 합니다.
- 요청이 더 이상 `SEARCHING`이거나 회차가 중단 상태라면 타이머를 종료합니다.
- 현재 반경이 100km 미만이면 10km 확대하고 현재 회차에서 아직 제안을 받지 않은 새 병원에만 제안을 만듭니다.
- 새 병원이 없어도 반경 평가 이력을 남기고 다음 60초 시각을 설정합니다.
- 100km의 마지막 마감 시각까지 수락이 없으면 남은 `PENDING`을 `NO_RESPONSE`로 확정하고 후보 소진 처리합니다.
- 100km에서 모든 제안이 일찍 `REJECTED`가 되면 마지막 마감 시각을 기다리지 않고 즉시 후보 소진 처리합니다.
- 서버가 중단됐다가 마감 시각 이후 재시작해도 DB의 예정 시각을 읽어 한 번만 후속 처리를 수행합니다.

### 첫 수락과 복수 수락

- 병원 수락 트랜잭션은 이송 요청 → 검색 회차 → 병원 제안 순서로 잠급니다.
- 첫 수락이면 제안을 `ACCEPTED`, 요청을 `ACCEPTED_AVAILABLE`, 검색 회차를 `STOPPED_ON_ACCEPTANCE`로 변경하고 다음 확대 시각을 제거합니다.
- 이미 만들어진 다른 병원의 `PENDING` 제안은 유지하므로 다른 병원도 수락할 수 있습니다.
- 첫 수락 이후 확대 작업자가 경합해도 잠금 뒤 요청 상태를 재확인하고 새 제안을 만들지 않습니다.
- 같은 병원의 수락·거절이 동시에 오면 먼저 커밋된 응답만 성공하고 다른 명령은 `TRANSPORT_006`을 반환합니다.

### 후보 소진 후 재전송

- 요청 소유자인 `PARAMEDIC`만 `CANDIDATES_EXHAUSTED` 상태에서 재전송할 수 있습니다.
- 같은 요청에 회차 번호를 1 증가시킨 새 검색 회차를 만들고 요청을 `SEARCHING`으로 바꿉니다.
- 이전 회차의 제안과 응답은 변경하지 않으며 이전 거절·무응답 병원도 새 회차 후보에 다시 포함합니다.
- `(transport_request_id, retry_idempotency_key)`와 요청 지문으로 같은 재전송을 한 번만 생성합니다.
- 같은 키와 같은 명령은 기존 회차를 반환하고, 같은 키의 다른 내용은 `COMMON_005`를 반환합니다.

## 외부 API 계획

| 대상 | API | 계획 |
|---|---|---|
| 병원 활성 목록 | `GET /api/v1/hospitals/me/offers?view=ACTIVE&page=0&size=20` | 자기 병원의 `PENDING`·`ACCEPTED` 카드와 페이지 정보를 반환 |
| 병원 응답 이력 | `GET /api/v1/hospitals/me/offers?view=HISTORY&page=0&size=20` | 자기 병원의 종료된 제안도 조회하되 회신 연락처를 마스킹 |
| 병원 요청 상세 | `GET /api/v1/hospitals/me/offers/{offerId}` | 자기 병원에 실제 전달된 최소 임상정보·응답·거리/ETA 조회 |
| 병원 수락 | `POST /api/v1/hospitals/me/offers/{offerId}/accept` | `Idempotency-Key` 필수, `PENDING`을 `ACCEPTED`로 변경 |
| 병원 거절 | `POST /api/v1/hospitals/me/offers/{offerId}/reject` | `Idempotency-Key`와 사유 필수, 조건부 `OTHER` 상세 검증 |
| 구급대원 검색 현황 | `GET /api/v1/transport-requests/{requestId}/hospital-search` | 소유 요청의 회차·반경·다음 확대·후보 부족·병원별 결과 반환 |
| 구급대원 재전송 | `POST /api/v1/transport-requests/{requestId}/dispatch-attempts` | `Idempotency-Key` 필수, 후보 소진 요청에 새 회차 생성 |
| 실시간 이벤트 | `GET /api/v1/realtime/events` | Bearer 인증이 가능한 HTTP 스트림으로 역할·조직·소유자 대상 SSE 전송 |

### 공통 API 규칙

- 모든 응답은 내부 DB ID 대신 UUID `publicId`를 사용합니다.
- 수락·거절·재전송의 `Idempotency-Key`는 기존 8~100자 정책을 재사용합니다.
- 재전송 회차 최초 생성은 `201 Created`, 동일한 재전송은 `200 OK`이며 수락·거절은 모두 `200 OK`입니다.
- 병원 목록은 카드 정렬에 필요한 Pre-KTAS 상태·단계, 요청 도착 시각과 경과 계산 기준 시각을 제공합니다.
- 서버는 임상적 우선순위를 새로 판정하지 않고 확정된 Pre-KTAS와 서버 시각을 그대로 제공합니다.
- 기본 목록 정렬은 활성 요청의 오래된 `offeredAt` 순으로 안정적으로 반환하며 React가 Pre-KTAS와 경과시간을 함께 표시합니다.
- 다른 조직의 제안은 존재 여부를 숨기기 위해 `TRANSPORT_005`로 응답합니다.
- `SUPER_ADMIN`은 이 기능의 목록·상세·검색 현황·실시간 이송 이벤트를 사용할 수 없습니다.

## 병원 카드 읽기 모델

병원 API는 기존 `CurrentPatientSnapshot`과 제안 스냅샷을 조합해 다음 값을 반환합니다.

| 구분 | 반환 내용 |
|---|---|
| 식별·상태 | 요청 ID, 제안 ID, 검색 회차, 요청·제안 상태, 전달 시각 |
| 환자 기본 | 나이 상태·조건부 나이, 성별 |
| 발생·증상 | 발생 유형, 손상 기전·부위, 주증상·부증상, 발생 시각 상태·조건부 시각 |
| 중증도 | Pre-KTAS 완료 단계 또는 긴급 미완료 사유, 분류 시각 |
| 의식 | 최신 AVPU와 관찰 시각 |
| 활력징후 | 다섯 항목의 값·단위 또는 측정 불가·환자 거부 상태, 측정 시각 |
| 처치 | 현재 처치 유형과 병원 판단에 필요한 짧은 요약 |
| 요청자 | 구급대 조직명, 상태에 따라 원문 또는 마스킹한 회신 연락처 |
| 지도 | 직선거리, 도로 거리, ETA 상태·초·계산 시각 |
| 시간 | 요청 도착 시각, 전달 시각, 마지막 임상 갱신 시각, 서버 현재 시각 |

- 환자 이름·주민등록번호·정확한 생년월일·환자 연락처·상세 주소는 현재 데이터 모델에도 없고 응답 DTO에도 추가하지 않습니다.
- 출발 좌표는 후보 계산에만 사용하고 병원 DTO와 SSE 이벤트에 포함하지 않습니다.
- `PENDING`·`ACCEPTED`만 구급대원 회신 연락처 원문을 반환합니다.
- `REJECTED`·`NO_RESPONSE`와 종료 이력은 공통 마스킹 함수를 거쳐 반환합니다.
- 목록과 상세 DTO를 분리해 목록 카드가 불필요한 임상 이력을 한꺼번에 읽지 않게 합니다.

## DB 변경

- 새 Flyway migration `V4__create_hospital_search_response_schema.sql`을 추가합니다.
- 적용된 `V1`·`V2`·`V3`는 수정하지 않습니다.
- 기존 조직은 migration 시 `ACTIVE`로 설정하고 새 조직도 도메인 생성 시 `ACTIVE`가 됩니다.

| 테이블·변경 | 주요 내용과 제약 |
|---|---|
| `organizations.status` | `ACTIVE`, `INACTIVE`; 후보 선정 시 병원 조직 활성 상태 검증 |
| `hospital_dispatch_attempts` | 요청, 회차 번호, 상태, 현재 반경, 후보 부족, 다음 확대 시각, 재전송 멱등성 키·지문, 시작·종료 시각, 낙관적 잠금 버전 |
| `hospital_search_rounds` | 검색 회차, 평가 반경, 전체 후보 수, 새 제안 수, 평가 시각, 응답 마감 시각; 회차·반경 고유 제약 |
| `hospital_offers` | 요청·회차·검색 반경·병원, 병원명·연락처·좌표 스냅샷, 직선거리, 제안 상태, ETA 상태·결과, 응답 멱등성 키·지문, 거절 사유·상세, 응답 계정·시각, 버전 |
| `hospital_offer_events` | `OFFERED`, `ACCEPTED`, `REJECTED`, `NO_RESPONSE`; 제안, 행위 계정·조직, 사유·상세, 서버 시각을 append-only로 보존 |
| `realtime_outbox_events` | 이벤트 ID·종류, 대상 계정 또는 조직, 공개 aggregate ID, 발생 시각, 발행 시도·다음 시도·완료 시각; 임상정보와 연락처 없음 |

### 주요 DB 제약과 인덱스

- `hospital_dispatch_attempts`: `(transport_request_id, attempt_number)` 고유
- `hospital_dispatch_attempts`: `(transport_request_id, retry_idempotency_key)` 고유; 최초 회차는 재전송 키가 없음
- `hospital_search_rounds`: `(dispatch_attempt_id, radius_km)` 고유
- `hospital_offers`: `(dispatch_attempt_id, hospital_profile_id)` 고유
- `hospital_offers`: 상태·거절 사유·응답 시각의 유효 조합을 CHECK로 방어
- 검색 작업용 `(status, next_expansion_at)`, ETA 작업용 `(eta_status, eta_next_attempt_at)` 인덱스
- 병원 목록용 `(hospital_profile_id, status, offered_at)`, 요청 현황용 `(transport_request_id, offered_at)` 인덱스
- outbox 발행용 `(published_at, next_attempt_at, occurred_at)` 인덱스

## 상태와 트랜잭션

### 검색 회차 상태

```text
SEARCHING
  ├─ 첫 수락 ─> STOPPED_ON_ACCEPTANCE
  └─ 최대 반경 소진 ─> EXHAUSTED
```

### 병원 제안 상태

```text
PENDING
  ├─ 병원 수락 ─> ACCEPTED
  ├─ 병원 거절 ─> REJECTED
  └─ 마지막 응답 마감 ─> NO_RESPONSE
```

### 잠금 순서

동시 작업의 교착과 상태 역전을 줄이기 위해 변경 명령은 아래 순서를 지킵니다.

1. `TransportRequest`
2. `HospitalDispatchAttempt`
3. `HospitalOffer`

- 검색 확대, 수락·거절, 후보 소진과 재전송 모두 같은 순서를 사용합니다.
- 상태 확인과 변경은 한 트랜잭션에서 수행하고 outbox 이벤트도 같은 트랜잭션에 저장합니다.
- 네이버 HTTP 호출과 SSE 전송은 이 트랜잭션 안에서 실행하지 않습니다.
- DB 고유 제약은 애플리케이션 잠금이 놓친 중복을 막는 최종 방어선입니다.
- 시간 비교와 응답 순서는 주입된 UTC `Clock`과 서버 저장 시각을 기준으로 합니다.

## 네이버 Directions 5 연동

### 호출 계약

- 서버 전용 `NaverDirectionsClient`가 설정 가능한 base URL의 `/map-direction/v1/driving`을 호출합니다.
- `start`와 `goal`은 네이버 계약에 맞게 `경도,위도` 순서로 전송합니다.
- MVP 기본 경로 옵션은 실시간 최적 경로 `traoptimal`을 사용합니다.
- 응답의 summary에서 도로 거리(m)와 소요시간(ms)을 읽어 내부 초 단위 ETA로 변환합니다.
- 네이버 DTO는 infrastructure 패키지 안에 두고 도메인에는 `RouteEstimate`만 전달합니다.
- 요청 URL에는 정확한 좌표가 포함되므로 전체 URL, 헤더와 응답 원문을 로그에 남기지 않습니다.

### 실패와 재시도

- 연결·응답 시간 제한을 짧게 설정하고 API 지연이 병원 요청 전달을 막지 않게 합니다.
- `429`, 일시적 `5xx`, 연결 실패는 제한된 횟수와 backoff로 다시 시도합니다.
- 인증 실패, 잘못된 응답 또는 최대 재시도 초과는 해당 제안을 `UNAVAILABLE`로 확정합니다.
- ETA가 `CALCULATING`이어도 병원은 요청을 조회하고 수락·거절할 수 있습니다.
- 성공 시 `AVAILABLE`, 도로 거리, ETA와 계산 시각을 저장하고 `ETA_UPDATED` 이벤트를 발행합니다.
- 실패 원인은 민감정보가 없는 분류와 trace ID로만 관측하고 공개 API에는 네이버 내부 오류를 노출하지 않습니다.

### 설정과 Secret

`application.yaml`에는 다음 논리 설정을 추가합니다.

```text
ersync.maps.naver.enabled
ersync.maps.naver.base-url
ersync.maps.naver.client-id
ersync.maps.naver.client-secret
ersync.maps.naver.connect-timeout
ersync.maps.naver.read-timeout
```

- 로컬 기본값은 `enabled=false`이며 ETA가 `UNAVAILABLE`이어도 전체 검색·응답을 검증할 수 있습니다.
- 실제 네이버 호출 로컬 검증은 환경변수로 자격정보를 일시 주입하며 파일에 원문을 작성하지 않습니다.
- Dev 배포 전 AWS Secrets Manager의 기존 `ersync/dev/backend` JSON에 `naverMapsClientId`, `naverMapsClientSecret`을 추가합니다.
- `scripts/deploy-ec2.sh`는 두 값을 런타임 YAML로 변환하되 콘솔 출력에 원문을 남기지 않습니다.
- 프론트 지도 SDK용 키·도메인·앱 등록은 서버 Directions 비밀키와 분리하며 이번 백엔드 PR에 포함하지 않습니다.

## 실시간 알림

### outbox

- 제안 생성·수락·거절·무응답·후보 소진·재전송·ETA 갱신과 같은 상태 변경 트랜잭션에서 outbox 행을 같이 저장합니다.
- 이벤트에는 `eventId`, `type`, `aggregateType`, 공개 `aggregateId`, `occurredAt`과 대상 계정·조직만 저장합니다.
- 환자 임상값, 회신 연락처, 병원 연락처와 정확한 좌표는 이벤트에 넣지 않습니다.
- 발행 작업자는 미발행 행을 작은 묶음으로 가져와 SSE broker에 전달한 후 완료 시각을 기록합니다.
- 전송 중 서버가 중단돼 이벤트가 중복 전달될 수 있으므로 클라이언트는 `eventId`를 중복 제거하고 상태 API를 재조회합니다.

### SSE

- `Authorization: Bearer`를 보낼 수 있는 스트리밍 HTTP 클라이언트로 연결하며 토큰을 URL query에 넣지 않습니다.
- 서버는 JWT의 계정·역할·조직을 확인하고 대상이 맞는 최소 이벤트만 전송합니다.
- 병원은 자기 조직의 새 제안과 ETA 변경을, 구급대원은 자기 요청의 병원 응답·후보 소진·재전송을 받습니다.
- 연결 유지용 heartbeat를 보내고 Access Token 만료 전에 연결을 종료해 새 토큰으로 재연결하게 합니다.
- 응답에 `Cache-Control: no-cache`와 `X-Accel-Buffering: no`를 적용해 Nginx 지연을 줄입니다.
- 연결 종료·재연결 시 클라이언트는 병원 활성 목록 또는 구급대원 검색 현황을 다시 조회합니다.
- 현재 단일 EC2 인스턴스를 기준으로 구현하며 여러 인스턴스 확장 시 공유 broker 도입이 필요합니다.

## 감사와 개인정보

- `AuditAction`에 검색 시작·확대·제안·수락·거절·무응답·후보 소진·재전송을 추가합니다.
- 상세 거절 사유와 응답 행위자는 `hospital_offer_events`에 보존하고 일반 감사 이벤트에는 공개 ID와 행위 종류만 둡니다.
- 병원 제안 생성 시 병원명·응급실 연락처·좌표를 스냅샷으로 저장해 이후 프로필 변경과 과거 이력을 구분합니다.
- 병원 좌표 스냅샷은 거리 계산에만 사용하고 병원 API·SSE·애플리케이션 로그에 반환하지 않습니다.
- 구급대원 회신 연락처는 기존 요청 스냅샷을 사용하고 상태별 원문·마스킹 정책을 한 서비스에서 적용합니다.
- `SUPER_ADMIN` 조회, 다른 병원 조직 접근과 다른 구급대원의 요청 접근을 차단합니다.
- 네이버 키, Authorization 헤더, Idempotency-Key, 임상정보·연락처·좌표를 로그에 남기지 않습니다.

## 구현 Step

| Step | 작업 | 완료 기준 |
|---:|---|---|
| 1 | V4 migration과 검색 회차·반경·병원 제안·응답 이벤트·outbox Entity/Repository 작성 | MySQL 8.4 migration, FK·CHECK·고유 제약·인덱스와 JPA `validate`가 일치함 |
| 2 | Haversine 후보 계산기와 이송 요청 생성 시 최초 탐색 연결 | 10~100km 최소 3곳 규칙, 후보 부족, 활성·수신 상태와 회차 내 중복 방지가 검증됨 |
| 3 | 60초 확대·후보 소진·멱등 재전송 작업 작성 | 서버 재시작 복구, 새 후보만 전달, 전원 거절·무응답과 새 검색 회차가 정확히 처리됨 |
| 4 | 병원 목록·상세·수락·거절과 구급대원 검색 현황 API 작성 | 역할·조직·소유권·연락처 마스킹·복수 수락·응답 경합 계약이 통과함 |
| 5 | 네이버 Directions 5 어댑터·ETA 작업·설정과 Secret 변환 작성 | 성공·타임아웃·429·5xx·잘못된 응답에서 요청 전달을 막지 않고 ETA 상태가 갱신됨 |
| 6 | outbox 발행 작업자와 인증 SSE 스트림 작성 | 상태와 이벤트 의도가 원자적으로 저장되고 양쪽 클라이언트가 3초 목표로 갱신 신호를 받음 |
| 7 | 단위·통합·권한·동시성·시간·MySQL·비로그 테스트 작성 | 반경 경계, 타이머, 경합, 멱등성, Naver fake와 개인정보 가드레일을 자동 검증함 |
| 8 | 전체 검사·로컬 실행 후 review와 양쪽 핸드오프 작성 | `./gradlew clean check`, readiness와 로컬 E2E가 통과하고 실제 API·DB·설정이 문서와 일치함 |

각 Step은 `구현 → 해당 범위 테스트 → 발견된 오류 수정` 순서로 끝낸 뒤 다음
Step으로 넘어갑니다. 전체 Step이 끝나면 전체 검사와 코드 리뷰를 별도로 수행합니다.

## 변경 범위

| 패키지·파일 | 변경 내용 |
|---|---|
| `organization/domain/**` | 조직 활성 상태 추가와 후보 검증 지원 |
| `hospital/domain/**`, `hospital/infrastructure/**` | 검색 회차·반경·제안·응답 이력과 조회 Repository 추가 |
| `hospital/search/**` | Haversine 후보 선정, 최초 탐색, 확대·소진·재전송 서비스와 scheduler 추가 |
| `hospital/api/**`, `hospital/application/**` | 병원 목록·상세·수락·거절과 카드 DTO 조립 추가 |
| `transport/api/**`, `transport/application/**` | 구급대원 검색 현황·재전송 API와 요청 생성 후 최초 탐색 연결 |
| `hospital/search/**` | 제공자 중립 포트, 네이버 Directions client, ETA 작업과 실패 변환 추가 |
| `realtime/**` | outbox Entity·발행기, 대상별 broker와 인증 SSE endpoint 추가 |
| `privacy/**` | 요청 상태에 따른 구급대원 회신 연락처 마스킹 추가 |
| `audit/domain/AuditAction.java` | 검색·제안·응답·후보 소진·재전송 감사 행위 추가 |
| `global/exception/ErrorCode.java` | 기존 `TRANSPORT_004`~`006` 활용과 필요한 검색 공개 오류 최소 추가 |
| `application*.yaml` | 검색 반경·시간, scheduler, SSE, 네이버 API와 timeout 설정 추가 |
| `scripts/deploy-ec2.sh` | 기존 Secret의 네이버 서버 자격정보를 런타임 YAML에 안전하게 변환 |
| `db/migration/V4__*.sql` | 조직 상태와 검색·제안·이력·outbox 스키마 추가 |
| `src/test/**` | 검색·응답·지도·SSE·권한·멱등·동시성·MySQL 테스트 추가 |

## 테스트 계획

### 후보 탐색

- [ ] 10km에 3곳 이상이면 해당 반경의 모든 후보에 한 번씩 제안 생성
- [ ] 10km 1곳, 20km 2곳, 30km 4곳이면 30km의 4곳에만 최초 제안 생성
- [ ] 정확히 반경 경계에 있는 병원 포함과 경계 밖 병원 제외
- [ ] 수신 `OFF`, 비활성 계정·조직, 좌표 없는 병원 제외
- [ ] 100km에 3곳 미만이면 발견 병원에 전달하고 후보 부족 표시
- [ ] 100km에 후보가 없으면 즉시 후보 소진
- [ ] 한 검색 회차에서 같은 병원 제안 중복 불가

### 시간·후보 소진·재전송

- [ ] 59초에는 확대하지 않고 60초가 되면 정확히 10km 확대
- [ ] 확대 시 새로 포함된 병원에만 제안 생성
- [ ] 최대 반경 전원 거절은 즉시 후보 소진
- [ ] 최대 반경에 `PENDING`이 있으면 마지막 60초 뒤 `NO_RESPONSE`와 후보 소진
- [ ] 재시작처럼 예정 시각이 지난 작업을 다시 실행해도 한 번만 처리
- [ ] 후보 소진 재전송이 같은 요청의 새 회차를 생성하고 이전 병원을 다시 후보로 허용
- [ ] 같은 재전송 키의 동시 호출은 회차 하나, 다른 명령은 `COMMON_005`

### 병원 응답과 권한

- [ ] 자기 병원에 전달된 제안만 목록·상세·수락·거절 가능
- [ ] 다른 병원·구급대원·슈퍼 관리자·미인증 접근 차단
- [ ] `OTHER` 상세 필수와 허용된 거절 사유 검증
- [ ] 수락·거절 동시 요청에서 한 응답만 성공
- [ ] 서로 다른 병원의 동시 수락이 모두 보존되고 첫 수락 이후 확대 중단
- [ ] 동일 응답 멱등 재시도는 응답·이벤트·감사 기록을 중복 생성하지 않음
- [ ] `PENDING`·`ACCEPTED` 연락처 원문과 종료 상태 마스킹
- [ ] 정확한 좌표와 직접 환자 식별정보가 병원·구급대원 DTO에 없음

### 지도와 실시간

- [x] Haversine 기준값과 위도·경도 순서 검증
- [x] 네이버 성공 응답의 거리·밀리초가 내부 m·초로 정확히 변환됨
- [x] timeout·429·5xx·인증 오류·잘못된 JSON에서 병원 제안은 유지되고 ETA만 재시도 또는 `UNAVAILABLE`
- [x] 네이버 요청 URL·키·좌표와 응답 원문이 로그에 없음
- [x] outbox 저장이 상태 변경과 같은 트랜잭션에서 rollback·commit됨
- [x] SSE가 대상 계정·조직에 최소 이벤트만 보내고 다른 대상에는 보내지 않음
- [ ] SSE 연결 종료 후 REST 재조회로 최종 상태 복구

### 회귀·전체 검증

- [x] 기존 가입·로그인·수신 ON/OFF·환자 평가·이송 요청 생성 테스트 유지
- [x] H2는 빠른 회귀에 사용하고 Testcontainers MySQL 8.4에서 migration·잠금·고유 제약 검증
- [x] `./gradlew clean check`
- [x] 로컬 MySQL 실행과 `/actuator/health/readiness`
- [ ] 테스트 병원·가짜 환자정보로 최초 탐색→병원 조회→복수 수락→소진→재전송 E2E
- [ ] 실제 네이버 키를 사용할 수 있을 때 테스트 좌표 한 쌍의 도로 거리·ETA smoke test

## 프론트 핸드오프

- 대상: `BOTH`
- Flutter: `docs/handoffs/04-automatic-hospital-search-response/flutter-paramedic.md`
  - 검색 현황·반경·후보 부족·병원별 상태·거절 사유·수락 병원·전화 연락처
  - 후보 소진 후 멱등 재전송
  - 구급대원 대상 SSE와 연결 복구 재조회
- React: `docs/handoffs/04-automatic-hospital-search-response/react-hospital-admin.md`
  - 병원 활성 목록·상세 카드·거리/ETA 상태
  - 수락·거절과 멱등성·오류 계약
  - 병원 대상 SSE와 연결 복구 재조회
- 구현과 로컬 검증 후 실제 필드·enum·HTTP 상태·오류·이벤트만 기록합니다.
- 프론트의 상태관리·폴더·컴포넌트 구조는 지시하지 않습니다.

## 배포 전 준비

- [x] 네이버 클라우드 플랫폼에서 Maps 애플리케이션과 Directions 5 사용 설정
- [x] 서버용 Client ID와 Client Secret 발급
- [x] AWS `ersync/dev/backend`에 `naverMapsClientId`, `naverMapsClientSecret` 추가
- [x] 저장소·노션·카카오톡·PR·로그에 키 원문을 남기지 않음
- [ ] Dev 서버에서 Nginx를 통과한 SSE 지연·heartbeat 확인
- [ ] Dev 배포 후 GitHub main SHA와 `/api/system/version`의 `commitSha` 일치 확인
- [ ] 테스트 좌표와 가짜 환자정보로 실제 ETA 및 양쪽 상태 API 확인

## 유지할 계약

- `spec.md`의 10~100km, 최소 3곳, 60초, 첫 수락 확대 중단과 복수 수락 정책
- 후보는 직선거리, 실제 도로 거리·ETA는 네이버 Directions 5를 사용하는 결정
- 이송 요청 생성의 멱등성, 구조화된 임상 원본과 현재 snapshot
- 병원 공용 계정, 조직 격리와 응급실 수신 `ON/OFF`
- `SUPER_ADMIN`의 임상정보·위치·회신 연락처 접근 금지
- 정확한 위치는 현재 목적지 병원만 볼 수 있으므로 이번 병원 요청에는 미노출
- 공통 오류 응답, `X-Trace-Id`와 민감정보 비로그
- 적용된 Flyway migration 불변과 MySQL 8.4 호환성
- 목적지 선택·수락 철회·이송 중 위치 갱신·인계 완료는 이번 기능에서 제외

## 리스크

| 리스크 | 대응 |
|---|---|
| 60초 작업과 병원 응답이 동시에 실행되어 수락 뒤 새 제안이 만들어짐 | 일관된 잠금 순서, 상태 재확인, 회차·병원 고유 제약과 경합 테스트 |
| 네이버 지연·장애·쿼터가 요청 전달을 늦춤 | 후보 탐색과 외부 호출 분리, 짧은 timeout·제한 재시도, `UNAVAILABLE` 대체와 사용량 관측 |
| DB 저장 후 실시간 알림이 유실되거나 중복됨 | 트랜잭션 outbox, event ID 중복 제거와 재연결 시 권위 REST 상태 재조회 |
| SSE가 Nginx buffering·연결 timeout으로 3초 목표를 넘김 | 비버퍼링 헤더·heartbeat·연결 갱신을 적용하고 Dev 공개 주소를 통한 지연 검증 |
| 병원 카드 조립·로그·이벤트에서 임상정보·연락처·좌표가 과다 노출됨 | 대상별 DTO, 상태별 마스킹, 최소 이벤트와 캡처 로그·권한 테스트 |
