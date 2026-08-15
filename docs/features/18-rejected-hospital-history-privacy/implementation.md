# 거절 병원 이력 개인정보 보호 구현 계획

```text
Feature: 18-rejected-hospital-history-privacy
Author: Backend AI Agent
Handoff Targets: REACT_HOSPITAL_ADMIN
```

## 설계 요약

- 선택한 방식: HISTORY 목록은 조회 상태를 기준으로 항상 최소 DTO를 사용하고, 종료 제안 상세는 공통 종료 판정으로 차단합니다.
- 선택 이유: 목록 query와 DTO 공개 범위를 일치시키고 레거시 종료 상태에서도 정보가 다시 노출되지 않게 합니다.
- 검토한 대안과 제외 이유: `REJECTED`만 조건에 추가하면 다른 `closedAt`·레거시 상태에서 같은 누출이 재발할 수 있어 제외합니다.

## 구현 Step

| Step | 작업 | 완료 기준 |
|---:|---|---|
| 1 | HISTORY 목록 mapper를 최소 DTO로 고정 | 모든 HISTORY 항목의 임상·연락처·거리·ETA가 null |
| 2 | 목록 DTO에 거절 사유·상세 추가 | 거절 결과와 `OTHER` 상세가 목록에서 복구됨 |
| 3 | 종료 제안 상세 차단 판정 통합 | 거절·철회·무응답·완료·취소 상세가 404 |
| 4 | API·권한·회귀 테스트 수정 | 거절 최소 이력과 활성 제안 계약 동시 검증 |
| 5 | MVP·컨텍스트·핸드오프 갱신 | 정책과 실제 API 계약 일치 |
| 6 | 전체 검사·MySQL·readiness 검증 | 실패·오류·생략 0, readiness `UP` |
| 7 | 실제 결과를 review에 기록 | 근거 없는 PASS 없음 |

## 변경 범위

| 패키지·파일 | 변경 내용 |
|---|---|
| 병원 제안 API·서비스 | HISTORY 최소화, 거절 사유 필드, 종료 상세 차단 |
| 병원 검색·이송 lifecycle 테스트 | 비노출·404·활성 회귀 검증 |
| MVP·에이전트·기능·핸드오프 문서 | 확정 정책과 프론트 처리 계약 반영 |

## DB 변경

- 없음

## 테스트 계획

- [ ] 거절 HISTORY 결과·사유·시각 유지
- [ ] 환자·임상·연락처·거리·ETA 비노출
- [ ] 종료 상태 상세·임상 timeline·위치 404
- [ ] 활성 제안과 다른 조직·역할 회귀
- [ ] MySQL 8.4 호환성
- [ ] `./gradlew clean check`

## 프론트 핸드오프

- 대상: `REACT_HOSPITAL_ADMIN`
- Flutter: `NONE`
- React: `docs/handoffs/18-rejected-hospital-history-privacy/react-hospital-admin.md`
- 구현과 로컬 검증 후 실제 코드 기준으로 작성

## 유지할 계약

- ACTIVE의 `PENDING`·`ACCEPTED` 공개 범위와 응답 동작
- HISTORY의 상태·결과·처리 시각과 페이징 구조
- `TRANSPORT_005`와 공통 오류 응답·`X-Trace-Id`
- 역할·조직·소유권과 슈퍼 관리자 접근 차단

## 리스크

| 리스크 | 대응 |
|---|---|
| React가 거절 HISTORY에서 상세 API를 계속 호출 | 핸드오프에 상세 링크 제거와 목록 필드 사용 명시 |
| 최소 DTO에서 거절 사유까지 사라짐 | additive 필드와 API 테스트로 고정 |
| 레거시 종료 상태가 상세를 다시 노출 | `closedAt`·종료 상태를 함께 판정 |
