# 목적지 선택·변경 및 수락 철회 구현 검수

```text
Feature: destination-selection-change-acceptance-withdrawal
Implemented By: backend AI collaboration
Related PR: NONE
Frontend Impact: BOTH
Flutter Handoff: docs/handoffs/05-destination-selection-change-acceptance-withdrawal/flutter-paramedic.md
React Handoff: docs/handoffs/05-destination-selection-change-acceptance-withdrawal/react-hospital-admin.md
```

> `spec.md`의 확정 MVP 정책과 실제 코드·자동 테스트 결과를 기준으로 작성했습니다.
> 커밋·푸시·PR과 Dev 서버 배포는 수행하지 않았습니다.

## 구현 요약

- 구급대원이 자기 이송 요청의 수락 병원 한 곳을 현재 목적지로 선택하고, 다른
  수락 병원으로 변경할 수 있는 멱등 API와 불변 명령 이력을 추가했습니다.
- `transport_requests.current_destination_offer_id`로 현재 목적지를 최대 한 곳으로
  유지하고 최초 선택 때 `EN_ROUTE`로 전환합니다.
- 병원은 자기 조직의 `ACCEPTED` 제안을 필수 사유와 함께 철회할 수 있습니다.
  현재 목적지 철회는 목적지를 해제하고, 비목적지 철회는 기존 목적지와
  `EN_ROUTE`를 유지합니다.
- 목적지가 없어진 철회는 같은 이송 요청에 `ACCEPTANCE_WITHDRAWAL` 탐색 회차를
  만들고, 해당 요청에서 이미 연락한 병원과 철회 병원을 제외합니다.
- 연속된 병원 철회 중 복구 탐색이 이미 진행 중이면 기존 회차를 재사용해 같은
  요청에 활성 재탐색이 중복 생성되지 않게 했습니다.
- 복구 탐색 소진 뒤 수동 재탐색 중 기존 병원이 늦게 수락하면 요청을
  `ACCEPTED_AVAILABLE`로 복구하고 활성 수동 회차를 즉시 중단합니다.
- 목적지 선택 중인 복구 회차는 `STOPPED_ON_DESTINATION`으로 닫아 추가 환자정보
  전송을 중단합니다.
- 병원 ACTIVE/HISTORY를 목적지 관계까지 반영하도록 변경했습니다. 숨겨진
  비목적지 수락과 철회는 최소 이력만 반환하고 임상 상세를 차단합니다.
- 구급대원 탐색 응답에 현재 목적지, 병원별 목적지 여부와 철회 정보를 추가하고,
  철회 병원 연락처는 후보 소진 뒤에도 노출되지 않게 했습니다.
- 목적지 선택·변경·수락 철회의 audit와 최소 outbox 이벤트를 상태 변경과 같은
  트랜잭션에 저장합니다.
- 요청 → 탐색 회차 → 제안 잠금 순서를 통일해 목적지 명령끼리 또는 목적지 선택과
  철회가 동시에 실행돼도 모순된 상태를 만들지 않게 했습니다.

## 요구사항 체크

| 요구사항 | 결과 | 근거 |
|---|---|---|
| 유일한 목적지 선택·변경 | PASS | 최초 A 선택, A→B 변경, B→A 재선택과 명령 이력 통합 테스트 |
| 동일 목적지·멱등성 | PASS | `UNCHANGED`, 동일 키 재시도, 같은 키 다른 payload `COMMON_005` 검증 |
| 병원 수락 철회 | PASS | 비목적지·현재 목적지 철회, 사유·OTHER·재시도·충돌 API 테스트 |
| 철회 후 요청 상태 | PASS | 남은 수락 유무에 따른 `ACCEPTED_AVAILABLE`·`SEARCHING`·`CANDIDATES_EXHAUSTED` 검증 |
| 소진 뒤 늦은 수락 | PASS | 기존 PENDING 병원의 수락으로 `ACCEPTED_AVAILABLE` 복구 후 목적지 선택·`EN_ROUTE` 전환 |
| 수동 재탐색 중 늦은 수락 | PASS | 요청 상태 복구와 활성 수동 회차 `STOPPED_ON_ACCEPTANCE` 전환 검증 |
| 목적지 병원의 수신 OFF | PASS | 신규 후보 자격만 종료하고 기존 목적지·상세·철회 권한 유지 검증 |
| 철회 복구 재탐색 | PASS | 같은 요청의 새 회차, 기연락 병원 제외, 새 병원 제안과 목적지 선택 중단 검증 |
| ACTIVE/HISTORY·상세 접근 | PASS | 비목적지 수락 최소 이력·철회 가능, 철회 최소 이력, 임상 상세 `TRANSPORT_005` 검증 |
| 개인정보·연락처 제한 | PASS | 최소 이력 민감 필드 null, 철회 병원 연락처 후보 소진 뒤에도 null 검증 |
| 역할·조직·소유권 | PASS | 다른 구급대원·병원 조직·역할의 명령과 조회 차단 |
| audit·outbox | PASS | 선택·변경·철회별 행위와 대상별 최소 이벤트 생성 검증 |
| 동시성 | PASS | 서로 다른 목적지 명령, 선택·철회, 복구 소진·늦은 수락 경합 직렬화 테스트 |
| MySQL 8.4 호환 | PASS | V1→V5 migration, CHECK·FK·JPA validate와 readiness 테스트 |
| 기존 기능 회귀 | PASS | 가입·인증·요청 생성·자동 탐색·병원 응답을 포함한 전체 `clean check` |

## 변경 API·DB

| 구분 | 변경 내용 | 호환성 |
|---|---|---|
| API | `POST /api/v1/transport-requests/{requestId}/destination` | 신규, 구급대원 소유 요청 전용 |
| API | `POST /api/v1/hospitals/me/offers/{offerId}/withdraw-acceptance` | 신규, 자기 병원 조직 수락 제안 전용 |
| 기존 API | `GET /api/v1/transport-requests/{requestId}/hospital-search` | 현재 목적지·철회 선택 필드 추가 |
| 기존 API | `GET /api/v1/hospitals/me/offers?view=ACTIVE|HISTORY` | 필드 추가 및 목적지 관계에 따른 목록 소속 변경 |
| 기존 API | `GET /api/v1/hospitals/me/offers/{offerId}` | 목적지·철회 필드 추가, 숨겨진 수락·철회 임상 상세 차단 |
| 기존 API | `GET /api/v1/realtime/events` | 목적지 선택·변경·수락 철회 이벤트 타입 추가 |
| DB | 현재 목적지 FK, 목적지 명령 테이블, 철회 snapshot·이벤트 컬럼 | 신규 V5 migration, V1~V4 미수정 |
| DB | 탐색 시작 원인·검색 좌표와 `STOPPED_ON_DESTINATION` | 기존 탐색 회차 확장 및 기존 행 backfill |

## Spec 이후 변경

- 제품 정책 변경: 없음
- 구현 중 추가된 설계 결정:
  - 잠금 전에 Entity graph를 읽지 않고 ID projection으로 scope만 확인한 뒤 요청을
    먼저 잠그도록 했습니다.
  - 동시 트랜잭션에서 영속성 컨텍스트의 이전 요청 상태가 남지 않도록 요청 잠금
    직후 DB 상태로 refresh하고 최신 목적지·상태를 검증합니다.
  - 후보 소진 연락처 공개 규칙에서도 `ACCEPTANCE_WITHDRAWN` 병원은 예외로 두어
    철회 뒤 연락처 원문을 반환하지 않습니다.
  - 현재 목적지 철회 직후 남은 수락 병원의 선택·철회가 경합하면 기존 활성 철회
    복구 회차를 재사용하고, 목적지 선택이 먼저 끝나 기존 회차가 중단된 경우에만
    새 복구 회차를 생성합니다.
  - 수동 재탐색 중 이전 회차 병원이 늦게 수락하면 활성 수동 회차를 중단합니다.
    다만 목적지 철회 복구 중 이전 병원의 수락만으로는 복구 회차를 중단하지 않아
    대체 목적지 탐색을 계속합니다.

## 범위 확인

- spec 밖 추가 작업: 없음
- 의도적으로 제외한 작업:
  - 구급차 최신 위치 업로드와 이동 중 ETA 재계산
  - 이송 취소, 임상정보 갱신, 인계 요청·완료
  - 전화 통화 결과에 따른 자동 목적지 변경

## 테스트 결과

| 테스트 | 결과 | 근거 |
|---|---|---|
| `./gradlew clean check` | PASS | 컴파일·Javadoc·Spotless·전체 98개 자동 테스트 성공 |
| 목적지·철회 통합 테스트 | PASS | 선택·변경·무변경·멱등성·권한·조회·복구 분기 성공 |
| 동시성 통합 테스트 | PASS | 목적지 명령 2개, 선택/철회, 복구 소진/늦은 수락 경합에서 일관된 최종 상태 확인 |
| 30km 복수 병원 실사용 시나리오 | PASS | 30km 4곳 전달·2곳 수락·1병원 철회 뒤 2병원 선택/철회 경합·활성 재탐색 1개·새 병원 요청 확인 |
| Testcontainers MySQL 8.4 | PASS | V1→V5 적용, JPA validate, DB 제약과 readiness 확인 |
| 격리 로컬 MySQL 실행 | PASS | 별도 검증 DB에 V1→V5 적용 후 `/actuator/health/readiness`가 `UP` |
| 가짜 데이터 API E2E | PASS | 가입·수신 ON·요청 생성·복수 수락→A 선택→B 변경→비목적지 철회→현재 목적지 철회→복구 탐색 |
| `git diff --check` | PASS | 공백 오류 없음 |

## 프론트 핸드오프

| 대상 | 문서 | 실제 코드와 일치 |
|---|---|---|
| Flutter 구급대원 앱 | `docs/handoffs/05-destination-selection-change-acceptance-withdrawal/flutter-paramedic.md` | YES |
| React 병원·관리자 웹 | `docs/handoffs/05-destination-selection-change-acceptance-withdrawal/react-hospital-admin.md` | YES |

## 남은 리스크

| 리스크 | 영향 | 대응 |
|---|---|---|
| 현재 위치 저장 기능이 아직 없음 | 철회 복구 탐색은 요청 생성 좌표를 기준으로 수행 | 위치 업로드 기능 전까지 문서화된 fallback 사용 |
| 실제 Nginx SSE 전달은 로컬에서 미검증 | Dev에서 이벤트 지연·연결 종료 동작이 다를 수 있음 | main 배포 뒤 공개 Base URL에서 REST 재조회와 함께 smoke test |
| Dev DB·배포 환경의 V5 적용은 미검증 | 환경 설정 차이로 배포가 실패할 수 있음 | PR merge 뒤 workflow readiness와 서버 `commitSha` 확인 |
