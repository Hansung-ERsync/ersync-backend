# 이송 취소·인계 완료 및 이력 구현 검수

```text
Feature: transport-cancellation-handoff-history
Implemented By: backend AI collaboration
Related PR: NONE
Frontend Impact: BOTH
Flutter Handoff: docs/handoffs/08-transport-cancellation-handoff-history/flutter-paramedic.md
React Handoff: docs/handoffs/08-transport-cancellation-handoff-history/react-hospital-admin.md
```

## 구현 요약

- 요청 소유 구급대원이 네 활성 상태에서 필수 사유로 이송을 취소하는 API를 구현했습니다.
- `EN_ROUTE` 구급대원의 인계 요청과 현재 목적지 병원의 확인을 분리해 양측 동작이 모두 있어야 완료됩니다.
- `ACTIVE`, `HISTORY`, `RECENT` 구급대원 목록과 병원 기존 ACTIVE·HISTORY 전환을 구현했습니다.
- 취소·인계 명령을 불변 이력으로 저장하고 동일 요청의 멱등 키를 공유해 다른 lifecycle 명령 재사용을 차단했습니다.
- 요청·제안 잠금 순서를 통일해 취소·수락, 인계 요청·철회, 완료·위치 갱신 경합을 직렬화했습니다.
- 종료 후 병원 임상·연락처·위치 노출을 차단하고 감사·SSE에는 최소 식별자만 저장했습니다.
- V8 migration, 구현·동시성·MySQL migration 테스트와 양쪽 프론트 핸드오프를 추가했습니다.

## 요구사항 체크

| 요구사항 | 결과 | 근거 |
|---|---|---|
| 허용 상태의 자기 요청 취소와 필수 사유 | PASS | 취소 통합 테스트에서 상태·소유권·사유·OTHER 검증 |
| 양측 인계 확인과 종료 상태 | PASS | 구급대원 요청 후 목적지 병원 확인 전후 상태 통합 테스트 |
| 종료 시 탐색·제안 비활성화와 이력 보존 | PASS | attempt·offer 종료, 응답 status 보존, 병원 목록 전환 검증 |
| 멱등성·동시성·원자성 | PASS | 불변 command unique 계약과 3개 동시성 시나리오 검증 |
| 본인 활성·최근 이력과 개인정보 최소화 | PASS | RECENT·HISTORY 소유권, nullable 병원명, 최소 item 검증 |
| 종료 후 변경·민감 조회 차단 | PASS | 완료 후 취소·위치, 인계 대기 후 철회, 종료 상세 차단과 기존 회귀 테스트 |
| 감사와 실시간 갱신 | PASS | 행위별 audit 1회, 대상별 outbox와 최소 이벤트 DTO 검증 |
| 기존 DB 데이터 보존 | PASS | MySQL 8.4 V7→V8 Testcontainers 및 로컬 Flyway 적용 성공 |

## 변경 API·DB

| 구분 | 변경 내용 | 호환성 |
|---|---|---|
| API | `POST /api/v1/transport-requests/{requestId}/cancel` 추가 | 신규 API, 기존 계약 유지 |
| API | `POST /api/v1/transport-requests/{requestId}/handoff-request` 추가 | 신규 API, 기존 계약 유지 |
| API | `POST /api/v1/hospitals/me/offers/{offerId}/confirm-handoff` 추가 | 신규 API, 기존 계약 유지 |
| API | `GET /api/v1/transport-requests?view=...` 추가 | 신규 API |
| API | 병원 제안 목록·상세에 인계·종료 optional 필드 추가 | additive, 기존 필드 유지 |
| DB | `transport_requests`에 취소·인계 snapshot 컬럼 추가 | nullable 추가로 V7 데이터 보존 |
| DB | `transport_lifecycle_commands` 불변 명령 이력 추가 | 신규 테이블 |
| DB | 탐색 취소 상태와 realtime event CHECK 확장 | 기존 값 유지, 새 enum 값 추가 |

## Spec 이후 변경

- 제품 정책 변경: 없음
- 구현 중 추가된 설계 결정:
  - 취소 뒤 현재 목적지는 해제하되 취소 command의 당시 목적지 snapshot으로 최근 이력 병원명을 복구합니다.
  - 병원이 실제로 한 응답은 덮어쓰지 않고 기존 `offerStatus`를 유지하며 `closedAt`으로 종료 여부를 구분합니다.
  - 동일 요청에서는 취소·인계 요청·인계 확인이 하나의 멱등 키 공간을 사용합니다.
  - nullable JSON 필드는 기존 Jackson 설정대로 응답에서 생략될 수 있습니다.

## 범위 확인

- spec 밖 추가 작업: 종료 요청에서 비동기 ETA 결과가 뒤늦게 반영되지 않도록 route persistence 차단을 보완했습니다. 종료 상태 불변성을 지키는 필수 가드입니다.
- 의도적으로 제외한 작업: 취소 요청 재개, 인계 요청 철회, 완료 후 수정, 법정 보존·자동 삭제, 관리자 이력 화면은 spec 제외 범위를 유지했습니다.

## 테스트 결과

| 테스트 | 결과 | 근거 |
|---|---|---|
| `./gradlew clean check` | PASS | 2026-08-04, 54초, build·Javadoc·Spotless·150개 테스트 성공 |
| 기능 통합 테스트 | PASS | 취소·인계·목록·권한·멱등·최소 이력 6개 시나리오 |
| 동시성 테스트 | PASS | 취소/수락, 인계 요청/철회, 완료/위치 갱신 3개 경합 시나리오 |
| MySQL migration 테스트 | PASS | MySQL 8.4에서 V7 데이터 보존, V8 제약·이력 확인 1개 시나리오 |
| local 실행·readiness | PASS | MySQL 8.4 V7→V8 적용, `GET /actuator/health/readiness` → `{"status":"UP"}` |
| 문서·patch 검사 | PASS | `git diff --check`, 실제 DTO·상태·오류와 핸드오프 대조 |

## MVP 종합 시나리오 검증

| 번호 | 구체적인 상황 | 확인한 결과 | 결과 |
|---:|---|---|---|
| 1 | 가입 코드 확인 → 구급대원 가입 → 로그인 → 프로필·평가 프로토콜 조회 → 필수 환자정보 입력 → 동일 키 재전송 → 자동 병원 탐색 | 요청은 하나만 생성되고 10km 내 수신 ON 병원 3곳에 제안이 전달됨 | PASS |
| 2 | 30km에서 병원 4곳 발견, 2곳 수락, 첫 목적지 철회 뒤 두 번째 목적지 선택과 해당 병원 철회가 동시에 발생 | 혼합 목적지가 남지 않고 요청은 `SEARCHING`, 수락 철회 복구 회차는 하나만 생성되며 새 병원만 탐색 | PASS |
| 3 | 이동 중 새 임상 평가와 서로 다른 시각의 위치 패킷이 동시에 도착 | 임상 원본은 모두 추가되고 최신 snapshot이 유지되며 위치는 최신 한 건만 저장; 목적지 병원만 최신 임상·정확한 위치 조회 | PASS |
| 4 | 구급대원 인계 요청과 목적지 병원 철회가 동시에 발생하고, 별도 완료 흐름에서 병원 확인과 늦은 위치 갱신이 충돌 | 앞선 전이 하나만 확정되고, 완료가 확정된 뒤 위치·철회·취소가 요청을 다시 변경하지 못함 | PASS |
| 5 | 병원 수락과 구급대원 취소가 동시에 발생하고 같은 취소 명령을 재시도 | 최종 요청은 일관된 `CANCELLED`, 모든 활성 탐색·제안 종료, 감사·SSE·명령 이력 한 번, 재개·후속 변경 차단 | PASS |

- 시나리오 1은 `MvpJourneyIntegrationTest`로 새로 추가했습니다.
- 시나리오 2~5는 실제 동시 실행용 latch와 별도 트랜잭션을 사용하는 기존 통합·동시성 테스트를 조합해 재검증했습니다.
- 세부 입력 검증·권한·멱등성·DB migration을 포함한 전체 회귀 테스트 150개도 함께 통과했습니다.

## 프론트 핸드오프

| 대상 | 문서 | 실제 코드와 일치 |
|---|---|---|
| Flutter 구급대원 앱 | `docs/handoffs/08-transport-cancellation-handoff-history/flutter-paramedic.md` | YES |
| React 병원·관리자 웹 | `docs/handoffs/08-transport-cancellation-handoff-history/react-hospital-admin.md` | YES |

## 남은 리스크

| 리스크 | 영향 | 대응 |
|---|---|---|
| 현재 브랜치는 Dev 서버에 아직 배포되지 않음 | 프론트가 Dev API를 바로 호출할 수 없음 | main 병합·배포 SHA 확인 뒤 Dev 연동 |
| Flutter 화면의 OTHER 상세·이동 중 취소·CANCELLED 배지는 별도 프론트 변경 필요 | 백엔드 성공 후에도 UI에서 기능을 사용할 수 없을 수 있음 | Flutter 핸드오프의 화면 대응·연동 체크 적용 |
| 인계 요청 뒤 되돌리기 정책은 MVP 범위 밖 | 현장 예외를 앱에서 복구할 수 없음 | 현재는 상태를 되돌리지 않고 운영 절차로 처리 |
