# 목적지 철회 복구 재알림 구현 계획

```text
Feature: destination-withdrawal-recovery-notification
Author: Codex
Handoff Targets: BOTH
```

## 설계 요약

- 선택한 방식: 기존 제안에 최근 요청 시각과 재알림 횟수를 저장하고, 현재 목적지의
  철회 트랜잭션에서 기존 `PENDING` 제안만 재요청 처리합니다.
- 선택 이유: 기존 `offerId`와 최초 요청 시각을 보존하면서 재연결 뒤에도 재요청 카드를
  복구하고, 요청 잠금으로 철회·수락 경합을 직렬화할 수 있습니다.
- 거리·ETA: 재요청 제안에 복구 회차를 연결해 원점을 고정하고 직선거리를 갱신합니다.
  현재·최근 성공 ETA를 모두 초기화하며 재시도도 같은 복구 회차 원점을 사용합니다.
- 임상정보: 재요청 시 현재 snapshot의 `lastClinicalUpdateAt`과 재요청 시각으로 새 공개
  고정점을 저장합니다. 이후 기록은 숨기고 목적지로 선택될 때만 live 접근을 복원합니다.
- 실시간 처리: 새 공개 이벤트 타입 없이 기존 `TRANSPORT_REQUEST_RECEIVED`를 재요청
  병원 자신의 `offerId`로 보냅니다. 기존 수락 병원에는 `HOSPITAL_ACCEPTANCE_WITHDRAWN`을
  요청 단위 aggregate로 보내 ACTIVE 목록 재조회를 유도합니다.
- 검토한 대안: `offeredAt` 덮어쓰기는 최초 전달 시각을 잃으므로 제외하고, 요청·병원
  전역 유일 제약은 기존 데이터와 과거 회차 호환 위험 때문에 추가하지 않습니다.

## 구현 Step

| Step | 작업 | 완료 기준 |
|---:|---|---|
| 1 | V12에 최근 요청 시각·횟수·회차 FK와 재알림 이력 제약 추가 | 기존 행 backfill, MySQL·JPA 검증 통과 |
| 2 | `HospitalOffer` 재요청 도메인 동작 추가 | PENDING만 시각·횟수·거리·임상 고정점·ETA 세대 갱신 |
| 3 | 현재 목적지 철회 복구에 기존 PENDING 재요청 결합 | 같은 카드 유지, 일반 철회·중복 복구는 재알림 안 함 |
| 4 | 병원별 outbox와 제안 이력 저장 | PENDING 재조회, ACCEPTED 상태 재조회, 종료 병원 무신호 |
| 5 | ETA 작업의 재요청 원점 처리 | 고정된 복구 원점으로 재계산, 다른 목적지 확정 시 중단 |
| 6 | 병원·구급대원 응답 확장 | 재요청 필드와 `currentAttempt.triggerType`의 비파괴적 추가 |
| 7 | 통합·동시성·MySQL 테스트 보강 | spec 핵심 시나리오와 멱등성·권한 회귀 통과 |
| 8 | review와 Flutter·React 핸드오프 작성 | 실제 코드·테스트·외부 계약 일치 |

## 변경 범위

| 패키지·파일 | 변경 내용 |
|---|---|
| `hospital/search/domain` | 재요청 상태·이력, 거리·ETA 재계산 불변식 |
| `hospital/search/application` | 긴급 철회 재알림, 대상별 outbox, 최신 위치 ETA 원점 |
| `hospital/search/api` | 병원 재요청 필드와 구급대원 탐색 회차 유형 추가 |
| `hospital/search/infrastructure` | PENDING·ACCEPTED 제안 조회와 잠금 순서 유지 |
| `db/migration/V12` | 최근 요청 시각·횟수·회차 FK와 `RENOTIFIED` 제약 |
| 목적지·검색·API·MySQL 테스트 | 정상 흐름, 경합, 멱등성, migration 검증 |

## DB 변경

- `hospital_offers.last_requested_at DATETIME(6) NOT NULL`
  - nullable 추가, `offered_at` backfill, NOT NULL 전환 순서로 적용합니다.
- `hospital_offers.renotification_count INT NOT NULL DEFAULT 0`
  - 음수를 허용하지 않는 CHECK를 추가합니다.
- `hospital_offers.last_requested_attempt_id BIGINT NOT NULL`
  - 최초에는 기존 `dispatch_attempt_id`, 재요청에는 새 복구 회차를 저장합니다.
  - nullable 컬럼 추가, `dispatch_attempt_id` backfill, null·잘못된 참조 검증, NOT NULL 전환,
    index·FK 추가 순서로 적용합니다. MySQL은 FK 적용 뒤 nullability 변경을 허용하지 않습니다.
  - 도메인은 최초 회차가 기존 제안 회차와 같고, 복구 회차가 같은 이송 요청 소속이며
    trigger가 `ACCEPTANCE_WITHDRAWAL`인지 검증합니다.
- 요청 시각·횟수 CHECK로 최초 요청은 시각 동일·횟수 0, 재요청은 시각 증가·횟수 양수를 보장합니다.
- V12에서 `hospital_offer_events`의 type·payload CHECK를 모두 재정의합니다.
  - 내부 이력 `RENOTIFIED`는 actor·거절·철회 payload가 모두 null입니다.
- 기존 `offered_at`, 제안·검색 회차 FK와 적용된 migration은 수정하지 않습니다.
- 요청·병원 전역 유일 제약은 추가하지 않습니다.

## 동시성·멱등성

- 철회와 병원 수락은 모두 `TransportRequest`를 먼저 잠근 뒤 제안을 잠급니다.
- 복구 시작 결과는 회차와 `created`를 함께 반환합니다. 새 회차가 생성되고 현재 목적지가
  철회된 경우에만 재알림합니다.
- 동일 멱등 키 재실행은 저장된 철회 결과를 반환하며 재알림을 반복하지 않습니다.
- 철회가 먼저면 당시 `PENDING`만 한 번 재알림하고, 수락이 먼저면 `ACCEPTED`를 건드리지 않습니다.
- 스케줄러 반경 확대는 재알림을 반복하지 않고 신규 병원 제안만 생성합니다.
- 재요청 ETA는 요청·제안이 열려 있고 generation이 일치하며 현재 목적지가 없을 때
  `PENDING`과 `ACCEPTED` 모두 완료할 수 있습니다. 다른 병원이 목적지가 되면 폐기하고,
  해당 제안이 목적지가 되면 목적지용 새 generation으로 다시 계산합니다.
- SSE는 중복 전달될 수 있으므로 프론트는 REST 목록·상세를 권위 상태로 사용합니다.

## 테스트 추적표

| 완료 조건 | 테스트 위치 | 필수 검증 |
|---|---|---|
| A/B/C/D/E 통합 흐름 | `TransportDestinationServiceIntegrationTest` | 상태, C 카드 수·ID 불변, C 이력·outbox 1회, E만 신규 제안 |
| 상태별 API·정보 노출 | `HospitalSearchApiIntegrationTest` | C `PENDING`만 재요청 시점 임상·ETA, B `ACCEPTED` 동결, D `REJECTED`·A `ACCEPTANCE_WITHDRAWN`·취소·완료는 추가 정보·outbox 없음, 모든 비목적지 위치 404 |
| 동일 키 재실행·다른 본문 | `TransportDestinationServiceIntegrationTest` | 시각·횟수·이력·outbox·회차 불변, `COMMON_005` |
| 철회 선행·수락 선행 | `TransportDestinationConcurrencyIntegrationTest` | C 카드 수·ID 불변, `RENOTIFIED`·C outbox 각각 1/0회, C 수락, 활성 회차 최대 1개 |
| scheduler·목적지·ETA 경합 | 기존 목적지 동시성 테스트 확장 | 재요청→수락→ETA 완료 허용, 다른 목적지 뒤 신규 제안·ETA 중단, 해당 제안 목적지 선택 시 새 generation |
| 최신 위치·최초 위치 fallback | `MvpCollisionJourneyIntegrationTest`, 목적지 통합 테스트 | `lastRequestedAttempt` 원점과 직선거리 |
| 최대 100km 대기 | `HospitalSearchIntegrationTest`·기능 16 회귀 | C 재알림 1회, 신규만 제안, `SEARCHING`, next null, 후보 소진 상태·`HOSPITAL_SEARCH_EXHAUSTED` 0건, 수동 재시도 경로 404 |
| 인증·권한·상태 실패 | API·목적지 통합 테스트 | `AUTH_001/002/003`, 존재 비노출 404, `TRANSPORT_006/004`, `COMMON_005` 뒤 회차·C 필드·이력·outbox·카드 수 불변 |
| 역할·조직·SSE 최소 신호 | API·realtime 통합 테스트 | 타 조직·관리자 차단, PENDING 자기 offer aggregate, ACCEPTED 요청 aggregate, 민감 payload 없음 |
| V12 schema | `MySqlDatabaseIntegrationTest` | backfill, NOT NULL·CHECK·FK, `RENOTIFIED`, skipped 0 |
| MySQL 8.4 실제 철회 | `MySqlDatabaseIntegrationTest` | A 철회 후 flush·clear한 새 영속 상태에서 C 동일 ID, 시각·횟수·회차·`RENOTIFIED` 재조회 |
| 전체·실행 | Gradle·로컬 | `clean check`, readiness `UP` |

Flutter 실제 문구 렌더링은 백엔드에서 실행하지 않습니다. 백엔드는
`currentDestinationOfferId == null`과 `triggerType=ACCEPTANCE_WITHDRAWAL`을 검증하고,
화면 문구 연동은 핸드오프에서 `PENDING`으로 기록합니다.

## 프론트 핸드오프

- Flutter: `docs/handoffs/17-destination-withdrawal-recovery-notification/flutter-paramedic.md`
- React: `docs/handoffs/17-destination-withdrawal-recovery-notification/react-hospital-admin.md`
- 구현과 로컬 검증 후 실제 응답·SSE·상태 기준으로 작성합니다.

## 유지할 계약

- 기존 철회·목적지 선택 API, 멱등 키와 오류 응답
- 같은 이송 요청과 병원에 활성 카드 하나
- 기능 16의 최대 반경 대기와 PENDING 지속 계약
- 현재 목적지만 정확한 위치를 조회하는 정보 노출 제한
- 역할, 조직과 요청 소유권 및 `X-Trace-Id`
- 적용된 Flyway migration과 과거 enum 데이터 호환

## 리스크

| 리스크 | 대응 |
|---|---|
| 기존 ETA가 재요청 뒤 최신값처럼 노출됨 | 재요청 전용 초기화로 현재·최근 성공 ETA 제거 후 재계산 |
| 일반 철회도 PENDING을 다시 알림 | `currentDestinationWithdrawn`과 새 회차 생성 여부를 함께 확인 |
| 철회·수락 경합에서 중복 신호 생성 | 요청 선잠금과 최신 PENDING 상태 확인 |
| 기존 ACCEPTED 정보가 실수로 최신화됨 | 재요청 도메인 동작을 PENDING에만 적용하고 회귀 테스트 |
| migration과 JPA 기본값 불일치 | backfill 후 NOT NULL·CHECK, MySQL Testcontainers 검증 |
