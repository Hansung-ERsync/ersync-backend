# 비목적지 병원 제안 활성 상태 구현 검수

```text
Feature: active-hospital-offer-contract
Implemented By: Codex
Related PR: NONE
Review Base: feature/active-hospital-offer-contract @ 2d7c8fb
Frontend Impact: REACT_HOSPITAL_ADMIN
Flutter Handoff: NONE
React Handoff: docs/handoffs/15-active-hospital-offer-contract/react-hospital-admin.md
```

## 구현 요약

- 목적지 선택 뒤에도 다른 병원의 `PENDING`·`ACCEPTED` 제안을 ACTIVE로 유지했습니다.
- 병원별 임상 공개 종료 시각을 저장하고 append-only 원본으로 당시 snapshot과 timeline을 재구성합니다.
- 비목적지의 정확한 위치와 동적 ETA를 차단하고, 목적지 변경 시 공개 범위를 전환합니다.

## 요구사항 체크

| 요구사항 | 결과 | 근거 |
|---|---|---|
| 비목적지 PENDING·ACCEPTED 활성 유지 | PASS | 목록 쿼리·목적지 통합 테스트 |
| 늦은 수락·거절과 수락 철회 | PASS | `TransportDestinationServiceIntegrationTest` |
| 진행 중 `NOT_SELECTED` 제거 | PASS | resolver 단위·lifecycle 통합 테스트 |
| 임상 상세·timeline 동결 | PASS | 목적지 전후 5건/6건 분리 검증 |
| 목적지 변경 공개 범위 전환 | PASS | 이전·새·제3 병원 cutoff 검증 |
| 비목적지 위치·동적 ETA 차단 | PASS | 위치 404, 상세 ETA null 검증 |
| 조직·역할·종료 상태 회귀 방지 | PASS | 전체 `clean check` |

## 변경 API·DB

| 구분 | 변경 내용 | 호환성 |
|---|---|---|
| API | 경로·메서드·DTO 유지, ACTIVE/HISTORY와 진행 상태 의미 변경 | 화면 동작 변경 필요 |
| DB | V11에서 병원 제안에 임상 공개 종료·동결 갱신 시각 추가 | 순방향 호환 |
| SSE | 최초 목적지 선택 신호를 활성 PENDING·ACCEPTED 병원에 전달 | 기존 이벤트 형식 유지 |

## Spec 이후 변경

- 제품 정책 변경: 없음
- 구현 중 추가된 설계 결정: 현재 목적지가 수락을 철회해 목적지가 없어지면 남은 활성 제안의 최신 임상 접근을 복원합니다.

## 범위 확인

- spec 밖 추가 작업: 없음
- 의도적으로 제외한 작업: 긴급 철회 재검색 계약 변경, `NO_RESPONSE`, 후보 소진, 전체 재전송

## 테스트 결과

| 테스트 | 결과 | 근거 |
|---|---|---|
| `./gradlew clean check` | PASS | 209 tests, failures 0, errors 0, skipped 0 |
| MySQL 8.4·Flyway V1~V11·JPA validate | PASS | Testcontainers 통합 테스트, skipped 0 |
| local 실행·readiness | PASS | Docker MySQL, `{"status":"UP"}` |
| 늦은 수락·거절 대상 테스트 | PASS | 대상 통합 테스트 재실행 |
| EC2·RDS 배포 | NOT_RUN | main 병합 전 로컬 작업 트리 |

## 프론트 핸드오프

| 대상 | 문서 | 실제 코드와 일치 |
|---|---|---|
| Flutter 구급대원 앱 | NONE | N/A |
| React 병원·관리자 웹 | `docs/handoffs/15-active-hospital-offer-contract/react-hospital-admin.md` | YES |

## 남은 리스크

| 리스크 | 영향 | 대응 |
|---|---|---|
| 기존 진행 데이터는 실제 최초 목적지 시각을 복원할 수 없음 | 배포 시 기존 비목적지 정보가 제안 시점 기준으로 보수적 동결 | V11 backfill로 이후 임상 노출 방지 |
| 동결 카드마다 시점 기준 Pre-KTAS 조회가 추가됨 | 활성 카드가 많으면 목록 쿼리 증가 | MVP 트래픽에서 관찰 후 batch 조회 검토 |
| 실제 React·EC2·RDS 연동 미실행 | 배포 환경 계약은 아직 미검증 | PR 병합 뒤 dev 연동 체크리스트 수행 |
