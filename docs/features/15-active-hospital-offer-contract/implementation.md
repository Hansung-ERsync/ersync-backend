# 비목적지 병원 제안 활성 상태 구현 계획

```text
Feature: active-hospital-offer-contract
Author: Codex
Handoff Targets: REACT_HOSPITAL_ADMIN
```

## 설계 요약

- 선택한 방식: 제안마다 임상정보 공개 종료 시각과 그때의 마지막 임상 갱신 시각을 저장합니다.
- 선택 이유: 임상 원본이 append-only이므로 공개 종료 시각 이전 기록만 조회해 당시 화면을 재구성할 수 있습니다.
- 검토한 대안과 제외 이유: 현재 snapshot 전체 복제는 컬럼과 동기화 코드가 과도하고, 현재값 마스킹만으로는 과거 허용 정보를 제공할 수 없어 제외합니다.

## 구현 Step

| Step | 작업 | 완료 기준 |
|---:|---|---|
| 1 | 제안에 임상 공개 종료 시각과 동결된 마지막 갱신 시각 추가 | Flyway V11과 JPA 검증 통과 |
| 2 | 목적지 선택·변경 시 병원별 공개 범위 전환 | 새 목적지만 실시간, 나머지는 최초 동결 시각 유지 |
| 3 | 공개 종료 시각 기준 임상 snapshot·timeline 재구성 | 종료 시각 이후 원본이 응답에 포함되지 않음 |
| 4 | 활성·이력 조회와 병원별 결과 수정 | 진행 중 `PENDING`·`ACCEPTED`가 ACTIVE에만 포함 |
| 5 | 비목적지 위치·ETA·실시간 신호 계약 정리 | 위치 404 유지, 카드·상세의 동적 ETA 비공개 |
| 6 | 단위·통합·MySQL 테스트 보강 | 목적지 선택·변경·늦은 응답·권한 시나리오 통과 |
| 7 | 실제 응답 기준 React 핸드오프와 review 작성 | 문서와 코드·테스트 결과 일치 |

## 변경 범위

| 패키지·파일 | 변경 내용 |
|---|---|
| `hospital/search/domain` | 병원 제안의 임상 공개 종료 상태와 불변식 |
| `hospital/search/application` | 활성 카드, 결과, 상세, ETA와 공개 범위 처리 |
| `hospital/search/infrastructure` | 활성·이력 쿼리와 요청별 제안 조회 |
| `transport/destination/application` | 목적지 선택·변경 시 공개 범위 동결·해제 |
| `transport/application`, `transport/infrastructure` | 종료 시각 기준 snapshot·timeline 조회 |
| `db/migration/V11__...sql` | 병원 제안 공개 범위 컬럼 추가 |
| 관련 단위·통합 테스트 | 정책 회귀와 MySQL 스키마 검증 |

## DB 변경

- `hospital_offers.clinical_visibility_cutoff_at DATETIME(6) NULL`
- `hospital_offers.frozen_last_clinical_update_at DATETIME(6) NULL`
- 두 값은 함께 `NULL`이거나 함께 존재하도록 제약합니다.
- 기존 진행 중 비목적지 제안은 보수적으로 제안 시각을 공개 종료 시각으로 보정합니다.

## 테스트 계획

- [x] 목적지 선택 후 비목적지 `PENDING`·`ACCEPTED` 활성 유지
- [x] 늦은 수락·거절, 비목적지 수락 철회와 목적지 변경
- [x] 목적지 선택 전후 임상 상세·timeline 동결
- [x] 비목적지 위치 404와 ETA 필드 비공개
- [x] 완료·취소·거절·철회 이력 회귀 방지
- [x] 다른 조직·역할 접근 차단
- [x] Flyway V11과 MySQL 8.4 JPA validate
- [x] `./gradlew clean check`

## 프론트 핸드오프

- 대상: `REACT_HOSPITAL_ADMIN`
- Flutter: `NONE`
- React: `docs/handoffs/15-active-hospital-offer-contract/react-hospital-admin.md`
- 구현과 로컬 검증 후 실제 코드 기준으로 작성

## 유지할 계약

- 기존 API 경로, HTTP 메서드와 요청 본문
- `hospitalOutcome`, `offerStatus`, `currentDestination`의 역할 분리
- 비목적지 정확한 위치 404와 조직 격리
- 공통 오류 응답, `X-Trace-Id`, 명령 멱등성
- 적용된 Flyway migration 불변

## 리스크

| 리스크 | 대응 |
|---|---|
| 목적지 변경 시 제3 병원의 동결 시각이 갱신됨 | 이미 동결된 제안은 변경하지 않는 도메인 메서드 사용 |
| 현재 snapshot이 과거 정보에 섞임 | 종료 시각 기준 원본 조회로 별도 snapshot 구성 |
| timeline 페이지 수가 현재 기록 기준으로 계산됨 | 목록과 count 모두 같은 종료 시각 조건 적용 |
| 기존 RDS 진행 데이터에 공개 시각이 없음 | V11에서 보수적인 초기값으로 보정 |
| 비목적지에 동적 ETA가 노출됨 | 목록·상세 매핑에서 현재 목적지 여부로 전체 ETA 묶음 제거 |
