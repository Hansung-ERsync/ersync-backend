# 병원별 이송 이력 상태 및 처리 시각 구현 계획

> **정책 개정 알림:** 이 문서는 2026-08-13 이전 정책으로 완료된 구현 기록입니다. 새 작업 계획으로 사용하지 말고, 현재 `spec.md`를 기준으로 구현 계획을 다시 작성해야 합니다.

```text
Feature: hospital-specific-history-status
Author: backend AI collaboration
Handoff Targets: REACT_HOSPITAL_ADMIN
```

> `spec.md`의 정책 결정과 현재 병원 제안·목적지·인계 구현을 기준으로 작성한
> 계획입니다. 전역 이송 상태와 병원의 원래 응답은 변경하지 않고, 기존 이력에서
> 병원별 표시 결과와 처리 시각을 계산해 API에 추가합니다.

## 현재 구조와 문제 원인

현재 데이터는 이미 다음 두 단위로 분리돼 있습니다.

- `TransportRequest`: 환자 이송 요청 한 건의 전역 상태와 현재 목적지, 완료·취소 시각
- `HospitalOffer`: 한 병원에 전달된 제안의 고유 `offerId`, 응답 상태와 응답·철회·종료 시각

병원 목록 DTO도 `transportRequestStatus`와 `offerStatus`를 함께 반환하지만 React
병원 웹은 `transportRequestStatus: COMPLETED`를 병원 카드의 `인계 완료`로 우선
표시합니다. 목적지 병원에서 인계가 완료되면 같은 요청을 받은 모든 병원 item에
전역 `COMPLETED`가 반환되므로 비목적지 병원도 자신이 인계를 완료한 것처럼 보입니다.

종료 이력은 개인정보를 제한하기 위해 `toMinimalHistoryItem`을 사용합니다. 이 DTO는
자기 병원이 했던 응답과 요청 종료 시각은 보존하지만, 병원별 종료 의미를 하나의
필드로 제공하지 않습니다. 따라서 프론트가 `offerStatus`, `transportRequestStatus`,
목적지와 여러 시각의 우선순위를 자체적으로 추측하고 있습니다.

새 병원 ID나 이송 요청 복제는 필요하지 않습니다. 기존 `offerId`, 현재 목적지 연결,
불변 목적지 명령 이력과 종료 시각으로 병원별 결과를 결정할 수 있습니다.

## 설계 요약

### 선택한 방식

- 공개 enum `HospitalOutcome`을 추가하고 병원 제안 목록·상세 DTO에
  `hospitalOutcome`, `processedAt`을 additive 필드로 추가합니다.
- `HospitalOfferOutcomeResolver`를 순수 계산기로 두어 병원별 결과 우선순위와 처리
  시각 선택을 한곳에서 관리합니다.
- `transportRequestStatus`는 요청 전체 상태, `offerStatus`는 병원의 실제 응답으로
  그대로 유지합니다.
- 종료 후 `currentDestination`은 기존 활성 목적지 의미대로 `false`를 유지하고,
  최종 인계 병원은 `HANDOFF_COMPLETED_HERE`로 구분합니다.
- 진행 중 비목적지의 `NOT_SELECTED` 처리 시각은 기존
  `transport_destination_commands.occurred_at`에서 현재 목적지 선택·변경 시각을
  가져옵니다.
- 목록 한 페이지의 요청 ID를 모아 목적지 시각을 한 번에 조회해 item마다 별도
  쿼리를 실행하지 않습니다.

### 선택 이유

- 전역 상태를 병원별로 다르게 만들지 않아 이송 상태 전이·SSE·감사 계약을 유지합니다.
- 병원의 실제 응답을 덮어쓰지 않아 거절·무응답·철회 이력을 보존합니다.
- React는 `hospitalOutcome` 하나로 문구를 결정하고 `processedAt` 하나로 처리 시각을
  표시할 수 있습니다.
- 최종 목적지 병원의 식별정보를 다른 병원에 공개하지 않고도 자기 결과를 구분합니다.
- 필요한 원본과 시각이 기존 테이블에 있어 migration 없이 구현할 수 있습니다.

### 검토한 대안과 제외 이유

- 병원별 이송 요청 행을 복제: 하나의 환자 이송이 여러 전역 상태를 갖게 되고 목적지·
  인계·취소 경합을 병원 수만큼 동기화해야 하므로 제외합니다.
- 병원 또는 제안 ID를 새로 추가: 기존 `offerId`가 병원별 요청을 이미 고유하게
  식별하고 조직 범위도 검증하므로 중복입니다.
- `offerStatus`에 `HANDOFF_COMPLETED` 등을 추가: 병원이 실제로 수락·거절한 응답과
  요청 종료 결과가 섞이고 기존 감사 의미가 사라지므로 제외합니다.
- `transportRequestStatus`를 병원마다 변환해 반환: 같은 요청이 조회 병원에 따라 다른
  전역 상태로 보이고 기존 클라이언트 계약과 충돌하므로 제외합니다.
- React가 기존 필드 조합을 계속 추측: 우선순위 로직이 화면마다 복제되고 이번과 같은
  오표시가 반복될 수 있어 제외합니다.
- 목적지 선택 때 비목적지 `HospitalOffer.closedAt`을 갱신: 수락 병원은 나중에 다시
  목적지가 될 수 있고 `closedAt`은 활성 조회와 ETA 종료에도 사용되므로 제외합니다.

## 병원별 결과 계산

### 반환 타입

```java
public enum HospitalOutcome {
    AWAITING_RESPONSE,
    ACCEPTED,
    REJECTED,
    NO_RESPONSE,
    ACCEPTANCE_WITHDRAWN,
    NOT_SELECTED,
    HANDOFF_COMPLETED_HERE,
    COMPLETED_ELSEWHERE,
    TRANSPORT_CANCELLED
}
```

계산 결과는 enum과 nullable 시각을 함께 가진 내부 값으로 반환합니다.

```java
record HospitalOfferOutcomeResult(
        HospitalOutcome outcome,
        Instant processedAt
) {
}
```

### 계산 우선순위

`HospitalOfferOutcomeResolver.resolve(offer, currentDestinationChangedAt)`는 아래 순서만
사용하며 Controller나 React에 같은 조건문을 복제하지 않습니다.

1. 요청이 `COMPLETED`이고 제안이 보존된 최종 목적지이면
   `HANDOFF_COMPLETED_HERE`, 요청 `completedAt`을 반환합니다.
2. 제안 상태가 `REJECTED`면 `REJECTED`, `respondedAt`을 반환합니다.
3. 제안 상태가 `NO_RESPONSE`면 `NO_RESPONSE`, `closedAt`을 반환합니다.
4. 제안 상태가 `ACCEPTANCE_WITHDRAWN`이면 `ACCEPTANCE_WITHDRAWN`, `withdrawnAt`을
   반환합니다.
5. 요청이 `COMPLETED`이고 제안이 `PENDING` 또는 `ACCEPTED`인 비목적지이면
   `COMPLETED_ELSEWHERE`, 요청 `completedAt`을 반환합니다.
6. 요청이 `CANCELLED`이고 제안이 `PENDING` 또는 `ACCEPTED`이면
   `TRANSPORT_CANCELLED`, 요청 `cancelledAt`을 반환합니다.
7. 진행 중 요청에 다른 현재 목적지가 있고 자기 제안이 `PENDING` 또는 `ACCEPTED`면
   `NOT_SELECTED`, 현재 목적지가 확정된 목적지 명령 시각을 반환합니다.
8. 그 밖의 `PENDING`은 `AWAITING_RESPONSE`, `null`을 반환합니다.
9. 그 밖의 `ACCEPTED`는 `ACCEPTED`, `respondedAt`을 반환합니다.

도메인에서 허용하지 않는 조합이 발견되면 새로운 공개 오류를 만들지 않습니다.
resolver 단위 테스트가 모든 enum 조합을 고정하고, 필수 시각이 없는 레거시·비정상
행은 기존 원본 시각 또는 `null`을 반환해 목록 전체를 500으로 실패시키지 않도록
방어적으로 처리합니다. 정상 생성·전이 경로에서는 완료·취소·응답 시각이 항상
존재하는 것을 통합 테스트로 검증합니다.

### 목적지 선택 시각 일괄 조회

`NOT_SELECTED`에는 다른 병원이 현재 목적지로 확정된 시각이 필요합니다. 이 시각은
기존 불변 `TransportDestinationCommand.occurredAt`에 이미 저장됩니다.

- 목록 결과의 `transportRequest.id`를 중복 제거해 수집합니다.
- 현재 목적지가 있고 요청이 종료되지 않은 항목만 조회 대상으로 좁힙니다.
- `TransportDestinationCommandRepository`에 요청별 최신 유효 목적지 명령
  (`SELECTED` 또는 `CHANGED`)의 요청 ID·목적지 제안 ID·발생 시각 projection을
  한 번에 반환하는 조회를 추가합니다.
- `UNCHANGED` 명령은 실제 상태 변경이 아니므로 처리 시각 기준에서 제외합니다.
- 결과를 `Map<transportRequestId, occurredAt>`으로 만들어 목록 mapper에 전달합니다.
- 같은 요청의 여러 병원 제안이 한 페이지에 있더라도 목적지 명령 조회는 한 번만
  실행합니다.
- 상세 API는 `NOT_SELECTED` 제안을 기존 정책상 404로 숨기므로 별도 목적지 시각
  조회 없이 같은 resolver를 사용할 수 있습니다.

목적지 명령은 기존 `(transport_request_id, occurred_at)` 인덱스를 사용합니다.
필요하면 조회 projection의 JPQL과 실제 MySQL 실행 계획을 테스트하되, 이번 기능을
위한 새 인덱스나 migration은 추가하지 않습니다.

## API 변경

### 병원 제안 목록

```text
GET /api/v1/hospitals/me/offers?view=ACTIVE|HISTORY&page=0&size=20
```

각 `items[]`에 다음 필드를 추가합니다.

| 필드 | 타입 | Nullable | 의미 |
|---|---|---:|---|
| `hospitalOutcome` | enum | X | 현재 로그인 병원 기준 표시 결과 |
| `processedAt` | string(datetime) | O | 결과가 현재 값으로 확정된 서버 시각, 응답 대기는 `null` 또는 생략 |

최종 목적지 A병원 이력 예시:

```json
{
  "offerId": "A_OFFER_UUID",
  "transportRequestId": "REQUEST_UUID",
  "transportRequestStatus": "COMPLETED",
  "offerStatus": "ACCEPTED",
  "currentDestination": false,
  "hospitalOutcome": "HANDOFF_COMPLETED_HERE",
  "processedAt": "2026-08-06T07:30:25Z",
  "completedAt": "2026-08-06T07:30:25Z"
}
```

같은 요청의 비목적지 B병원 이력 예시:

```json
{
  "offerId": "B_OFFER_UUID",
  "transportRequestId": "REQUEST_UUID",
  "transportRequestStatus": "COMPLETED",
  "offerStatus": "ACCEPTED",
  "currentDestination": false,
  "hospitalOutcome": "COMPLETED_ELSEWHERE",
  "processedAt": "2026-08-06T07:30:25Z",
  "completedAt": "2026-08-06T07:30:25Z"
}
```

두 응답 모두 전역 완료 시각을 포함할 수 있지만 `hospitalOutcome`이 자기 병원의
처리 결과를 구분합니다. B병원 응답에는 A병원의 이름·조직 ID·제안 ID를 넣지
않습니다.

### 병원 제안 상세

```text
GET /api/v1/hospitals/me/offers/{offerId}
```

- 접근 가능한 활성·응답 상세에 목록과 같은 두 필드를 추가합니다.
- 종료·철회·비목적지 최소 이력은 기존처럼 상세를 404로 차단하고 HISTORY item만
  사용합니다.
- 목록과 상세가 동시에 접근 가능한 상태에서는 같은 `hospitalOutcome`과
  `processedAt`을 반환합니다.

### 기존 계약 유지

- `transportRequestStatus`, `offerStatus`, `currentDestination`과 기존 시각 필드는
  삭제·변경하지 않습니다.
- `currentDestination`은 활성 현재 목적지만 뜻하며 종료 이력에서는 `false`입니다.
- 병원 응답·철회·인계 명령 API의 요청·응답과 오류는 변경하지 않습니다.
- `HANDOFF_COMPLETED` 등 기존 SSE는 payload를 늘리지 않고 REST 재조회 신호로
  유지합니다.
- Flutter 이송 목록·상세, 슈퍼 관리자 API에는 새 필드를 추가하지 않습니다.

## 시간 직렬화와 웹 표시

- `processedAt`은 Java `Instant`를 사용해 기존 Jackson ISO-8601 UTC 형식으로
  직렬화합니다.
- 서버는 분 단위로 자르지 않고 초 이하 정밀도를 보존합니다.
- 예시와 핸드오프는 최소 초가 포함된 `2026-08-06T07:30:25Z` 형식을 사용합니다.
- React는 서버 문자열을 사용자 시간대로 변환하고 `hour`, `minute`, `second`를
  모두 표시합니다.
- 서버가 별도 한국 시각 문자열을 만들지 않아 브라우저·지역 설정 책임과 중복하지
  않습니다.

## 권한·개인정보

- 기존 `requireHospitalContext`와 병원 조직 조건이 적용된 목록·상세 조회만
  변경합니다.
- 응답은 로그인 병원의 자기 `offerId`만 포함하고 다른 병원의 식별자를 추가하지
  않습니다.
- `COMPLETED_ELSEWHERE`는 다른 병원에서 종료됐다는 최소 사실만 제공합니다.
- 종료 이력의 임상정보·회신 연락처·거리·ETA·정확한 위치 제한과 상세 404를
  그대로 유지합니다.
- `PARAMEDIC`과 `SUPER_ADMIN`의 병원 제안 API 접근 차단을 회귀 테스트합니다.
- 새 필드와 계산 과정은 로그, 감사와 SSE payload에 추가하지 않습니다.

## 구현 Step

| Step | 작업 | 구현 후 검증 | 완료 기준 |
|---:|---|---|---|
| 1 | `HospitalOutcome`과 순수 resolver 작성 | 결과 우선순위·처리 시각별 단위 테스트 | 9개 결과와 종료·응답 경계가 표대로 계산됨 |
| 2 | 목적지 명령 repository에 최신 유효 변경 시각 batch projection 추가 | 선택→변경→UNCHANGED 이력 repository 테스트 | 요청별 실제 최신 선택·변경 시각만 한 번에 조회됨 |
| 3 | 병원 목록·상세 DTO에 두 additive 필드 추가 | 직렬화·nullable·기존 필드 회귀 테스트 | 목록·상세 JSON이 새 계약과 일치함 |
| 4 | `HospitalOfferService` 목록 mapper에 batch 결과와 resolver 연결 | ACTIVE·HISTORY·상세 통합 테스트 | 같은 병원 상태가 목록과 상세에서 일치하고 N+1이 추가되지 않음 |
| 5 | 다병원 목적지·인계 완료 시나리오 추가 | A 최종 인계, B 비목적지, C 거절 결과 비교 | 전역 상태는 같고 병원별 결과·시각은 정확히 구분됨 |
| 6 | 취소·무응답·철회·목적지 변경과 권한 회귀 보강 | 상태별 처리 시각, 타 조직·관리자·민감정보 검증 | 기존 이력과 접근 제한을 보존함 |
| 7 | React 핸드오프와 `review.md`를 실제 구현 기준으로 작성 | DTO·enum·예시·초 단위 표시 대조 | 프론트가 새 문서만으로 상태 문구와 시각을 연동 가능 |
| 8 | 전체 검사와 로컬 MySQL 실행·readiness 검증 | `./gradlew clean check`, dev 실행, readiness, `git diff --check` | 전체 회귀·실행 검증 통과와 결과 기록 |

## 변경 범위

| 패키지·파일 | 변경 내용 |
|---|---|
| `hospital/search/api/HospitalOutcome.java` | 병원별 공개 결과 enum 추가 |
| `hospital/search/api/HospitalOfferListResponse.java` | item에 `hospitalOutcome`, `processedAt` 추가 |
| `hospital/search/api/HospitalOfferDetailResponse.java` | 상세에 같은 필드 추가 |
| `hospital/search/application/HospitalOfferOutcomeResolver.java` | 결과 우선순위와 처리 시각의 단일 계산기 추가 |
| `hospital/search/application/HospitalOfferService.java` | 목록 batch 시각 조회, DTO mapper와 resolver 연결 |
| `transport/destination/infrastructure/TransportDestinationCommandRepository.java` | 요청별 최신 유효 목적지 변경 시각 projection 조회 추가 |
| `src/test/java/...` | resolver, repository, API, 다병원·권한·개인정보 회귀 테스트 추가 |
| `docs/handoffs/12-hospital-specific-history-status/react-hospital-admin.md` | React 최신 연동 계약 추가 |
| `docs/features/12-hospital-specific-history-status/review.md` | 실제 구현·검증 결과 기록 |

실제 구현 중 클래스명은 프로젝트 convention에 맞게 소폭 조정할 수 있지만 공개
JSON 필드와 enum 값은 `spec.md`를 따릅니다.

## DB 변경

- 없음.
- `hospital_offers`의 응답·철회·`closed_at`, `transport_requests`의 현재 목적지·
  완료·취소 시각, `transport_destination_commands`의 불변 `occurred_at`을 읽습니다.
- 적용된 Flyway migration은 수정하지 않고 신규 migration도 만들지 않습니다.
- `HospitalOfferStatus`, `TransportRequestStatus`의 DB 값과 CHECK 제약은 변경하지
  않습니다.

## 테스트 계획

### 단위 테스트

- [x] `PENDING` → `AWAITING_RESPONSE`, `processedAt: null`
- [x] 활성 `ACCEPTED` → `ACCEPTED`, `respondedAt`
- [x] `REJECTED`, `NO_RESPONSE`, `ACCEPTANCE_WITHDRAWN` 원래 결과·시각 보존
- [x] 최종 목적지 완료 → `HANDOFF_COMPLETED_HERE`
- [x] 비목적지 `PENDING`·`ACCEPTED` 완료 → `COMPLETED_ELSEWHERE`
- [x] 응답 대기·수락 중 취소 → `TRANSPORT_CANCELLED`
- [x] 진행 중 다른 목적지 선택 → `NOT_SELECTED`와 목적지 변경 시각
- [x] 완료·취소보다 거절·무응답·철회 보존 우선순위
- [x] 비정상 nullable 시각에서 목록 전체가 실패하지 않는 방어 동작

### API·통합 테스트

- [x] A·B 수락 → A 선택 → A 인계 완료 후 A·B HISTORY 결과 비교
- [x] C 거절 뒤 A 완료 시 C가 `REJECTED`로 유지됨
- [x] 목적지 선택 전후 A ACTIVE와 B HISTORY의 `NOT_SELECTED` 전환
- [x] 목적지를 A→B→A로 변경할 때 현재 병원 결과와 `processedAt` 갱신
- [x] PENDING·ACCEPTED 제안이 있는 요청 취소 시 `TRANSPORT_CANCELLED`
- [x] NO_RESPONSE·철회 결과와 처리 시각 보존
- [x] 목록·상세 동일 상태의 새 필드 일치
- [x] HISTORY 최소 item의 임상·연락처·좌표·거리 필드 비노출 유지
- [x] 다른 병원 제안 404, `PARAMEDIC`·`SUPER_ADMIN` 403 유지
- [x] 기존 JSON 필드와 SSE 재조회 계약 회귀 없음

### 조회·DB·전체 검증

- [x] 목록 페이지의 목적지 시각이 batch 조회되고 item별 repository 호출이 없음
- [x] 목적지 최신 명령에서 `UNCHANGED`를 제외하고 마지막 `SELECTED|CHANGED` 사용
- [x] MySQL 8.4에서 projection 쿼리와 기존 인덱스가 정상 동작
- [x] 기존 migration checksum 변경 없음
- [x] `./gradlew clean check`
- [x] `./scripts/dev-start.sh`
- [x] `GET /actuator/health/readiness` → `{"status":"UP"}`
- [x] `git diff --check`

## 프론트 핸드오프

- 대상: `REACT_HOSPITAL_ADMIN`
- Flutter: `NONE`
- React: `docs/handoffs/12-hospital-specific-history-status/react-hospital-admin.md`
- 구현과 로컬 검증 후 실제 코드 기준으로 작성합니다.
- React 문서에는 다음 내용을 독립적으로 포함합니다.
  - 목록·상세의 새 필드와 전체 enum
  - `transportRequestStatus`, `offerStatus`, `hospitalOutcome`의 차이
  - `HANDOFF_COMPLETED_HERE`와 `COMPLETED_ELSEWHERE` 화면 문구
  - `processedAt` nullable 조건과 `HH:mm:ss` 표시
  - `currentDestination`은 종료 후 `false`라는 기존 의미
  - 기존 04·05·08·09 병원 API 중 함께 유지되는 계약
  - SSE 수신 뒤 ACTIVE·HISTORY REST 재조회 조건

## 유지할 계약

- `spec.md`의 병원별 결과 우선순위와 처리 시각 기준
- 전역 `transportRequestStatus`와 병원 실제 `offerStatus`의 기존 의미
- 하나의 현재 목적지와 양측 인계 확인 뒤 한 번만 완료되는 상태 전이
- 기존 `offerId`와 병원 조직·요청 소유권 검증
- 종료 이력 최소화와 슈퍼 관리자 임상·위치 접근 금지
- 공통 오류 응답, `X-Trace-Id`, JWT와 SSE 재조회 계약
- 기존 공개 API 필드와 명령의 멱등성·동시성 계약

## 리스크

| 리스크 | 대응 |
|---|---|
| 결과 우선순위가 분산돼 목록·상세가 다르게 표시됨 | 순수 resolver 하나를 두고 모든 DTO mapper가 재사용하며 enum 전 조합 단위 테스트 |
| `NOT_SELECTED` 시각 조회가 페이지 항목마다 실행돼 성능 저하 | 요청 ID batch projection 한 번과 map 조회, repository 호출 수·페이지 통합 테스트 |
| 전역 완료와 병원 응답을 섞어 거절 병원이 다시 완료로 표시됨 | 거절·무응답·철회 우선순위를 완료 비목적지보다 앞에 두고 다병원 테스트 |
| 최종 목적지 정보를 비목적지 병원에 과도하게 노출 | 자기 결과 enum만 반환하고 다른 병원의 이름·조직·제안 ID는 DTO에 추가하지 않음 |
| 프론트가 계속 `transportRequestStatus`로 배지를 결정함 | 실제 API 기준 React 핸드오프에 전환표와 초 단위 표시 예시를 명시하고 배포 공유 시 최신 문서 안내 |
