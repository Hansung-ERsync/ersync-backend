# MVP 병원 탐색 지속 계약 구현 계획

```text
Feature: mvp-hospital-search-continuation
Author: Codex
Handoff Targets: BOTH
```

## 설계 요약

- 선택한 방식: 최대 반경에 도달하면 탐색 회차의 다음 실행 시각만 제거하고
  `SEARCHING` 상태와 기존 제안을 유지합니다.
- 선택 이유: 별도 실패 상태 없이 늦은 병원 응답을 계속 받을 수 있고 scheduler의
  반복 실행도 막을 수 있습니다.
- 호환 전략: 기존 enum·컬럼·CHECK 제약은 유지하되 신규 전이와 공개 재전송 API만 제거합니다.

## 구현 Step

| Step | 작업 | 완료 기준 |
|---:|---|---|
| 1 | 탐색 회차에 최대 반경 대기 전이 추가 | 상태·종료 시각 유지, `nextExpansionAt`만 null |
| 2 | 무후보·전원 거절·최종 응답 창 종료 전이 제거 | 신규 소진·무응답 상태와 이벤트가 생성되지 않음 |
| 3 | 수동 전체 재전송 API·서비스 경로 제거 | POST 경로 404, MANUAL_RETRY 신규 생성 불가 |
| 4 | 검색 현황의 신규 계약 정리 | 새 요청은 exhaustionReason null, accepted만 연락처 공개 |
| 5 | 기존 연계 서비스·테스트 정렬 | 수락·거절·취소·철회 흐름 컴파일·회귀 통과 |
| 6 | H2·MySQL·로컬 실행 검증 | `clean check`, skipped 0, readiness UP |
| 7 | review와 Flutter·React 핸드오프 작성 | 실제 코드·응답과 문서 일치 |

## 변경 범위

| 패키지·파일 | 변경 내용 |
|---|---|
| `hospital/search/application/HospitalSearchService` | 최대 반경 대기, 소진·무응답 전이 제거 |
| `hospital/search/domain/HospitalDispatchAttempt` | 확대 예약만 종료하는 도메인 메서드 추가 |
| `hospital/search/api`, `TransportHospitalSearchService` | 전체 재전송 공개 경로와 코드 제거 |
| `HospitalOfferService` | 최대 반경 전원 거절 소진 호출 제거 |
| 검색·API·동시성·lifecycle 테스트 | 새 지속 상태와 기존 흐름 회귀 검증 |

## DB 변경

- 새 Flyway migration 없음
- 기존 `NO_RESPONSE`, `CANDIDATES_EXHAUSTED`, `EXHAUSTED`, `MANUAL_RETRY` 저장값 유지
- 기존 retry 컬럼과 CHECK 제약은 적용된 migration 호환을 위해 유지

## 테스트 목록

- [x] 최초 최소 후보 반경 선택과 60초 확대
- [x] 최대 반경 무후보에서도 `SEARCHING` 유지
- [x] 최대 반경 `PENDING` 제안이 자동 종료되지 않음
- [x] 최대 반경 전원 거절에서도 소진 전이가 발생하지 않음
- [x] 최대 반경 대기와 병원 수락 경합 시 수락 성공
- [x] 제거된 전체 재전송 경로 404
- [x] 중복 병원 카드 방지
- [x] 수락·거절·목적지·취소·철회 회귀
- [x] MySQL 8.4·Flyway V1~V11·JPA validate
- [x] `./gradlew clean check`

## 프론트 핸드오프

- Flutter: `docs/handoffs/16-mvp-hospital-search-continuation/flutter-paramedic.md`
- React: `docs/handoffs/16-mvp-hospital-search-continuation/react-hospital-admin.md`
- 후보 소진·무응답·전체 재전송 UI 제거와 최대 반경 대기 표시를 실제 응답 기준으로 기록

## 유지할 계약

- 최초 10km, 최소 3곳, 10km 단위·60초 확대, 최대 100km
- 첫 수락 시 자동 확대 중단과 복수 수락
- 병원 수락·거절 멱등성과 조직 격리
- 목적지 선택·변경, 취소·인계와 기능 15의 활성 제안 계약
- 기존 DB enum·CHECK와 과거 데이터 읽기

## 리스크

| 리스크 | 대응 |
|---|---|
| 최대 반경 회차가 scheduler에서 반복 실행됨 | `nextExpansionAt`을 null로 종료 |
| 기존 소진 데이터 역직렬화 실패 | enum·DB 제약 유지 |
| 제거된 API 코드가 다른 기능에서 참조됨 | 전체 참조 검색 후 컴파일·통합 테스트 |
| 전원 거절 시 영구 SEARCHING | 최소 한 병원 수락이라는 MVP 가정을 spec에 명시 |
| 긴급 철회 재검색까지 과도하게 변경 | 이번 기능은 공통 종료 전이만 제거하고 재알림 정책은 제외 |
