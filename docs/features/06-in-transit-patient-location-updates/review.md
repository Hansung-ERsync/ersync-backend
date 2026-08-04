# 이송 중 환자·위치 갱신 구현 검수

```text
Feature: in-transit-patient-location-updates
Implemented By: backend AI collaboration
Related PR: NONE
Frontend Impact: BOTH
Flutter Handoff: docs/handoffs/06-in-transit-patient-location-updates/flutter-paramedic.md
React Handoff: docs/handoffs/06-in-transit-patient-location-updates/react-hospital-admin.md
```

> 2026-08-04 현재 작업 브랜치의 실제 코드와 로컬 검증 결과를 기준으로 작성했습니다.

## 구현 요약

- 구급대원이 활성 이송 요청에 활력징후·의식·Pre-KTAS·처치 원본을 추가하는
  타입별 API 네 개를 구현했습니다.
- 늦게 도착한 임상 기록도 이력에는 보존하되, 임상 발생 시각과 서버 수신 시각으로
  최신 snapshot을 결정해 과거 값으로 되돌아가지 않게 했습니다.
- 구급대원과 현재 임상 공개 권한이 있는 병원이 최신 snapshot과 시간순 임상
  timeline을 페이지 단위로 조회할 수 있습니다.
- 요청별 최신 위치 한 행만 유지하며, 30초 기준 `CURRENT`·`STALE`과 위치 미수신
  `NOT_RECEIVED`를 조회 시 계산합니다.
- 현재 목적지 병원만 정확한 위치를 조회할 수 있고, 목적지 철회 뒤 재탐색은 최초
  요청 좌표보다 최신 위치를 우선 사용합니다.
- 위치가 바뀌면 현재 목적지 ETA를 새 계산 세대로 예약합니다. 느리게 끝난 과거
  계산 결과는 버리고, 지도 API 실패 시 마지막 성공 거리·ETA는 별도 필드로 유지합니다.
- 임상·위치 명령은 요청별 `Idempotency-Key`로 중복을 막고, 요청 잠금으로 임상·
  위치·목적지 경합을 직렬화합니다.
- 요청 잠금을 기다리는 동안 이미 영속성 컨텍스트에 읽힌 병원 제안이 오래된
  버전으로 남지 않도록, 요청 다음에 병원 제안을 잠그고 현재 DB 상태로 다시
  읽은 뒤 ETA·목적지·철회 변경을 적용합니다.
- 감사와 SSE에는 안전한 공개 식별자만 기록하고 임상 원문과 정확한 좌표를 넣지 않습니다.

## 요구사항 체크

| 요구사항 | 결과 | 근거 |
|---|---|---|
| 네 임상 타입 append-only 저장 | 통과 | 타입별 API·원본 repository·`TransportClinicalUpdateIntegrationTest` |
| 늦은 기록·동시 갱신의 최신 snapshot 판정 | 통과 | snapshot 조건부 전진과 실제 두 스레드 경합 테스트 |
| 목적지 전후 병원 임상 접근 제어 | 통과 | 후보 2곳 조회 후 목적지 1곳만 조회되는 통합 테스트 |
| 요청별 최신 위치 한 행만 유지 | 통과 | DB unique 제약과 새 위치·과거 위치·동시 위치 테스트 |
| 현재 목적지 병원만 정확한 위치 조회 | 통과 | 비목적지 `TRANSPORT_005`, 목적지 `200` 통합 테스트 |
| 30초 freshness와 마지막 위치 유지 | 통과 | `LocationFreshnessPolicyTest`, 위치 API 테스트 |
| 재탐색에서 최신 위치 우선 | 통과 | `SearchOriginResolver`와 위치 통합 테스트 |
| 동적 ETA·과거 계산 결과 폐기·마지막 성공값 유지 | 통과 | 계산 세대 경합·성공 후 실패 통합 테스트 |
| 멱등 재시도와 payload 충돌 | 통과 | 동시 동일 요청은 원본 1건·replay 1건, 다른 명령의 동일 키 경합은 한 명령만 저장하고 `COMMON_005` 반환 |
| 위치 갱신·목적지 철회 경합 | 통과 | 두 스레드 동시 실행 후 최신 위치 1건, 목적지 없음, 재탐색 1건, 철회 병원 위치 접근 차단 검증 |
| 감사·SSE 민감정보 비노출과 중복 방지 | 통과 | 감사/outbox 대상·개수·로그 좌표 미포함 검증 |
| MySQL 8.4 migration·JPA mapping | 통과 | Testcontainers MySQL 및 로컬 MySQL V6 기동 |

## 변경 API·DB

| 구분 | 변경 내용 | 호환성 |
|---|---|---|
| API | 임상 갱신 POST 4개, 구급대원 임상 timeline GET, 위치 PUT·GET 추가 | 신규 API, 기존 계약 유지 |
| API | 병원 임상 timeline GET, 현재 목적지 위치 GET 추가 | 신규 API, 기존 계약 유지 |
| API | 병원 목록·상세·구급대원 탐색 응답에 최신 임상 시각과 마지막 성공 ETA 필드 추가 | 필드만 추가, 삭제·의미 변경 없음 |
| SSE | 임상 4종과 `AMBULANCE_LOCATION_UPDATED` 이벤트 추가 | 기존 SSE 응답 구조 유지 |
| DB | V6에서 `transport_update_commands`, `transport_current_locations` 생성 | 신규 테이블, V1~V5 미수정 |
| DB | `hospital_offers`에 ETA 계산 세대와 마지막 성공값 4개 컬럼 추가 | nullable·기본값 기반 additive 변경 |
| DB | realtime outbox event type CHECK에 새 이벤트 추가 | 기존 이벤트 유지 |
| 설정 | `ersync.location.stale-after: PT30S` 추가 | 기본값이 있어 기존 실행 설정과 호환 |

## Spec 이후 변경

- 제품 정책 변경: 없음
- 구현 중 추가된 설계 결정:
  - 임상 timeline은 새 중복 테이블 없이 기존 네 원본 테이블을 `UNION ALL`하여
    DB에서 정렬·페이징합니다.
  - 위치가 바뀔 때마다 ETA 계산 세대를 증가시켜 이전 외부 API 응답이 최신 ETA를
    덮어쓰지 못하게 합니다.
  - 위치 갱신 감사는 과거 패킷을 포함한 새 명령 수신마다 남기되, replay에는
    중복으로 남기지 않습니다. 좌표는 감사 대상에 포함하지 않습니다.
  - 위치 갱신과 목적지 철회 경합 검증에서 오래된 `HospitalOffer` 버전으로 인한
    낙관적 잠금 충돌을 발견했습니다. 요청→병원 제안 순서로 잠근 뒤 제안을
    `PESSIMISTIC_WRITE`로 다시 읽도록 보완해 ETA·목적지·철회 경합을 직렬화했습니다.
  - 목적지 철회·변경 전에 시작된 동적 ETA 계산은 외부 API 응답이 늦게 도착해도
    비목적지 제안에 적용하지 않습니다. 철회된 동적 계산 작업은 다음 claim에서
    `UNAVAILABLE`로 종료해 반복 외부 호출도 막습니다.

## 범위 확인

- spec 밖 추가 작업: 없음
- 의도적으로 제외한 작업:
  - Flutter 백그라운드 위치 권한·10초 전송 주기·지도 UI
  - GPS 전체 이동 경로와 ETA 이력 저장
  - 이송 취소·인계 요청·인계 완료 상태 전환
  - Dev HTTPS와 실제 모바일·웹 E2E

## 테스트 결과

| 테스트 | 결과 | 근거 |
|---|---|---|
| `./gradlew clean check` | 통과 | 컴파일·Javadoc·Spotless·전체 125개 테스트 성공, 실패·건너뜀 0 |
| 동시성 테스트 | 통과 | 임상·위치 최신값 경합, 동시 동일 재시도, 다른 명령의 동일 키, 위치 갱신·목적지 철회를 실제 두 스레드로 실행 |
| MySQL 8.4 | 통과 | Testcontainers 3개 테스트와 로컬 V6 migration 적용 성공 |
| local 실행·readiness | 통과 | `./scripts/dev-start.sh`, `GET /actuator/health/readiness` → `UP` |
| 임상 갱신·timeline | 통과 | 타입 4종, 오래된 기록, 멱등성, 소유권, 종료 상태, 페이징 |
| 위치·ETA | 통과 | 최신 한 행, 과거 패킷, freshness, 목적지 권한, 세대 경합, 실패 보존 |
| 개인정보 보호 | 통과 | 로그·감사·outbox에 테스트 좌표와 임상 payload 미포함 |

## 프론트 핸드오프

| 대상 | 문서 | 실제 코드와 일치 |
|---|---|---|
| Flutter 구급대원 앱 | `docs/handoffs/06-in-transit-patient-location-updates/flutter-paramedic.md` | 예 |
| React 병원·관리자 웹 | `docs/handoffs/06-in-transit-patient-location-updates/react-hospital-admin.md` | 예 |

## 남은 리스크

| 리스크 | 영향 | 대응 |
|---|---|---|
| Dev 서버가 HTTP | 실제 환자·연락처·정확한 위치 전송에 부적합 | HTTPS 전까지 테스트 전용 가짜 데이터만 사용 |
| 모바일 백그라운드 위치 정책 미검증 | 앱이 잠들면 위치가 `STALE`로 보일 수 있음 | Flutter에서 권한·절전·재전송 E2E 수행 |
| 네이버 Directions 호출량 | 10초 위치마다 현재 목적지 ETA 재계산 가능 | 현재는 세대 병합·비동기 처리, 운영 전 호출량과 비용 확인 |
| 인계·종료 기능 미구현 | `HANDOFF_REQUESTED` 이후 최종 이력 전환은 아직 없음 | 이번 기능은 새 갱신 차단과 현재 목적지 마지막 조회만 유지 |
