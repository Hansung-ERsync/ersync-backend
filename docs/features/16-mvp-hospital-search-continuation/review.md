# MVP 병원 탐색 지속 계약 구현 검수

```text
Feature: mvp-hospital-search-continuation
Implemented By: Codex
Related PR: NONE
Review Base: feature/mvp-hospital-search-continuation @ 1fda2a6
Frontend Impact: BOTH
Flutter Handoff: docs/handoffs/16-mvp-hospital-search-continuation/flutter-paramedic.md
React Handoff: docs/handoffs/16-mvp-hospital-search-continuation/react-hospital-admin.md
```

## 구현 요약

- 최대 100km에서도 요청과 탐색 회차를 `SEARCHING`으로 유지하고 scheduler 예약만 종료했습니다.
- 병원 미응답 제안을 `PENDING`으로 유지하며 자동 무응답·후보 소진 전이를 제거했습니다.
- 전체 재전송 API와 신규 `MANUAL_RETRY` 생성 경로를 제거했습니다.
- 철회 복구 중 기존 대기 병원이 수락해도 활성 자동 탐색이 즉시 중단되도록 정렬했습니다.

## 요구사항 체크

| 요구사항 | 결과 | 근거 |
|---|---|---|
| 10~100km·최소 3곳·60초 확대 유지 | PASS | `HospitalSearchIntegrationTest` |
| 최대 반경에서 검색 지속 | PASS | 무후보·미응답 통합 테스트 |
| 미응답 제안 `PENDING` 유지 | PASS | ACTIVE 목록과 이벤트 미생성 검증 |
| 전원 거절 뒤 소진 전이 없음 | PASS | API 통합 테스트 |
| 첫 수락 시 자동 탐색 중단 | PASS | 일반·철회 복구 동시성 테스트 |
| 전체 재전송 API 제거 | PASS | POST 경로 404와 회차 번호 유지 검증 |
| 수락·목적지·취소·철회 회귀 방지 | PASS | 전체 `clean check` |

## 변경 API·DB

| 구분 | 변경 내용 | 호환성 |
|---|---|---|
| API | `POST .../{requestId}/dispatch-attempts` 제거 | 해당 호출 제거 필요 |
| 조회 | 최대 반경에도 `SEARCHING`, `nextExpansionAt: null`, `exhaustionReason: null` | 화면 상태 변경 필요 |
| 병원 제안 | 자동 `NO_RESPONSE` 없음, `PENDING` 유지 | 병원 카드 유지 필요 |
| DB | migration 없음, 레거시 enum·컬럼 유지 | 기존 데이터 읽기 가능 |
| SSE | 신규 무응답·소진·재전송 이벤트 생성 없음 | 해당 이벤트 의존 제거 필요 |

## Spec 이후 변경

- 제품 정책 변경: 없음
- 구현 중 추가된 설계 결정: 첫 수락은 제안 생성 회차와 무관하게 현재 활성 탐색 회차를 중단합니다.

## 범위 확인

- spec 밖 추가 작업: 없음
- 의도적으로 제외한 작업: 목적지 병원 긴급 철회 후 기존 병원 재알림 상세 정책

## 테스트 결과

| 테스트 | 결과 | 근거 |
|---|---|---|
| `./gradlew clean check` | PASS | 209 tests, failures 0, errors 0, skipped 0 |
| 주요 기능 대상 테스트 | PASS | 검색·API·동시성·목적지·lifecycle 35건 |
| MySQL 8.4·Flyway V1~V11·JPA validate | PASS | Testcontainers 6건, skipped 0 |
| local 실행·readiness | PASS | Docker MySQL, `{"status":"UP"}` |
| EC2·RDS·프론트 E2E | NOT_RUN | main 병합 전 로컬 작업 트리 |

## 프론트 핸드오프

| 대상 | 문서 | 실제 코드와 일치 |
|---|---|---|
| Flutter 구급대원 앱 | `docs/handoffs/16-mvp-hospital-search-continuation/flutter-paramedic.md` | YES |
| React 병원 웹 | `docs/handoffs/16-mvp-hospital-search-continuation/react-hospital-admin.md` | YES |

## 남은 리스크

| 리스크 | 영향 | 대응 |
|---|---|---|
| 전 병원 거절 시 요청이 계속 `SEARCHING` | 종료 화면 없음 | 최소 한 병원 수락이라는 MVP 가정 적용 |
| 레거시 소진 상태·이벤트 enum 유지 | 신규 코드로 오인 가능 | 신규 생성 경로 금지 테스트 유지 |
| 기존 기능 04 핸드오프와 계약 차이 | 이전 문서 오독 가능 | 기능 16 핸드오프를 최신 계약으로 명시 |
| 실제 배포·프론트 연동 미실행 | 운영 전달 상태 미확인 | main 병합 뒤 dev E2E 확인 |
