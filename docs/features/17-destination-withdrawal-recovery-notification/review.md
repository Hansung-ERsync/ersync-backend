# 목적지 철회 복구 재알림 구현 검수

```text
Feature: destination-withdrawal-recovery-notification
Implemented By: Codex
Related PR: NONE
Review Base: feature/destination-withdrawal-recovery-notification @ f5b55bc + working tree diff
Frontend Impact: BOTH
Flutter Handoff: docs/handoffs/17-destination-withdrawal-recovery-notification/flutter-paramedic.md
React Handoff: docs/handoffs/17-destination-withdrawal-recovery-notification/react-hospital-admin.md
```

## 구현 요약

- 현재 목적지 철회 시 같은 이송 요청에서 목적지를 해제하고 복구 검색 회차를 시작합니다.
- 기존 `PENDING` 제안은 카드와 최초 요청 시각을 유지한 채 재요청 시각·횟수·회차를 갱신합니다.
- 기존 `ACCEPTED`는 유지하고, `REJECTED`·철회·종료 제안은 재알림 대상에서 제외합니다.
- 재요청 병원의 임상정보와 ETA는 복구 시점·원점으로 다시 고정하며 목적지 선택 전까지 제한합니다.
- 거절 병원의 임상 공개 범위도 거절 시점으로 고정해 이후 환자 업데이트 노출을 차단했습니다.

## 요구사항 체크

| 요구사항 | 결과 | 근거 |
|---|---|---|
| A 목적지 철회·해제·후보 제외 | PASS | 목적지 서비스 통합 테스트 |
| B 기존 수락 유지·직접 선택 | PASS | 상태·목적지 재선정 검증 |
| C 동일 `offerId` 재요청 | PASS | 시각·횟수·회차·이력 검증 |
| C 최신 고정 임상정보·복구 원점 ETA | PASS | 병원 API·ETA generation 테스트 |
| D 거절 상태·정보 제한·무신호 | PASS | 거절 cutoff와 outbox 회귀 테스트 |
| E 신규 적격 병원에만 새 제안 | PASS | 복구 회차 제안 대상 검증 |
| 자동 목적지 선택 없음 | PASS | 철회·수락 경합 및 상태 검증 |
| 동일 철회 재실행 멱등성 | PASS | 회차·카드·이력·outbox 1회 검증 |
| 일반 비목적지 철회 재알림 없음 | PASS | 목적지 서비스 통합 테스트 |
| 후보 소진·전체 재전송 미도입 | PASS | 기능 16 회귀와 전체 검사 |
| 역할·조직·위치 접근 제한 | PASS | API·목적지 권한 테스트 |

## 변경 API·DB

| 구분 | 변경 내용 | 호환성 |
|---|---|---|
| 병원 목록 | 항목에 `reRequested`, `lastRequestedAt` 추가 | 비파괴적 필드 추가 |
| 병원 상세 | `timing`에 `reRequested`, `lastRequestedAt` 추가 | 비파괴적 필드 추가 |
| 구급대원 탐색 | `currentAttempt.triggerType` 추가 | 비파괴적 필드 추가 |
| 명령 API | 기존 철회·목적지 선택 API 유지 | 변경 없음 |
| SSE | 기존 이벤트 타입 재사용, REST 재조회 신호만 전달 | 변경 없음 |
| DB | V12에 최근 요청 시각·횟수·회차 FK와 `RENOTIFIED` 이력 추가 | 기존 행 backfill |

## Spec 이후 변경

- 제품 정책 변경: 없음
- 구현 중 추가된 설계 결정: 거절 시점에도 임상 공개 범위를 고정합니다. 이는 spec의
  `REJECTED` 병원 추가 정보 비노출 계약을 보강한 것입니다.

## 범위 확인

- spec 밖 추가 작업: 없음
- 의도적으로 제외한 작업: 후보 소진, 전체 재전송, 전화 연결, 자동 목적지 선택
- `docs/features/total-review.md` 갱신: 기능 17 병합 후 통합 검수 범위로 제외

## 테스트 결과

| 테스트 | 결과 | 근거 |
|---|---|---|
| `./gradlew clean check` | PASS | 213 tests, failures 0, skipped 0 |
| 핵심 통합 테스트 묶음 | PASS | 목적지 10, 동시성 9, 병원 API 6, MVP 여정 5건 |
| MySQL 8.4·Flyway V1~V12·JPA validate | PASS | Testcontainers 7건, skipped 0 |
| V12 NOT NULL·CHECK·FK | PASS | MySQL이 위반 UPDATE를 거부함 |
| local 실행·readiness | PASS | 2026-08-15 직접 실행, Flyway V12 적용 후 `{"status":"UP"}` |
| `git diff --check` | PASS | 공백 오류 없음 |
| EC2·RDS·프론트 E2E | NOT_RUN | main 병합 전 로컬 작업 트리 |

## 프론트 핸드오프

| 대상 | 문서 | 실제 코드와 일치 |
|---|---|---|
| Flutter 구급대원 앱 | `docs/handoffs/17-destination-withdrawal-recovery-notification/flutter-paramedic.md` | YES |
| React 병원 웹 | `docs/handoffs/17-destination-withdrawal-recovery-notification/react-hospital-admin.md` | YES |

## 남은 리스크

| 리스크 | 영향 | 대응 |
|---|---|---|
| 회차가 같은 요청 소속·철회 복구 유형인지는 애플리케이션 불변식 | 직접 SQL 오염 시 잘못된 연결 가능 | 요청 잠금·도메인 검증·MySQL 회귀 테스트 유지 |
| SSE 실제 브라우저 재연결 미실행 | 화면 갱신 전달 상태 미확인 | 프론트 E2E에서 이벤트 뒤 REST 재조회 검증 |
| Naver ETA 외부 호출 미실행 | 실제 경로 계산 환경 미확인 | dev 배포 뒤 지도 키·ETA 연동 검증 |
| EC2·RDS 배포 미실행 | migration 운영 적용 상태 미확인 | main 병합 workflow와 readiness 확인 |
