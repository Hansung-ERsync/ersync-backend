# 거절 병원 이력 개인정보 보호 구현 검수

```text
Feature: 18-rejected-hospital-history-privacy
Implemented By: Backend AI Agent
Related PR: NONE
Frontend Impact: REACT_HOSPITAL_ADMIN
Flutter Handoff: NONE
React Handoff: docs/handoffs/18-rejected-hospital-history-privacy/react-hospital-admin.md
```

## 구현 요약

- HISTORY 목록을 모든 종료 상태에서 최소 DTO로 반환하도록 통일했습니다.
- 거절 사유·상세를 목록 응답에 추가하고 종료 제안 상세를 `404 TRANSPORT_005`로 차단했습니다.
- 거절 뒤 환자 임상 갱신, 임상 timeline과 위치가 노출되지 않도록 회귀 테스트를 정렬했습니다.

## 요구사항 체크

| 요구사항 | 결과 | 근거 |
|---|---|---|
| HISTORY 최소 목록 | PASS | HISTORY view는 상태와 무관하게 최소 mapper 사용 |
| 거절 사유·상세·처리 시각 유지 | PASS | `OTHER`·상세와 `processedAt` API 검증 |
| 종료 상세·임상·위치 차단 | PASS | 세 API 모두 `404 TRANSPORT_005` 검증 |
| 환자·임상·연락처·거리·ETA 비노출 | PASS | HISTORY JSON 필드 부재 검증 |
| 활성 제안·권한 회귀 없음 | PASS | 병원 검색·이송 lifecycle 및 전체 검사 통과 |

## 변경 API·DB

| 구분 | 변경 내용 | 호환성 |
|---|---|---|
| API | HISTORY item에 `rejectionReason`, `rejectionDetail` 추가 | Additive |
| API | 종료 제안 상세 200을 `404 TRANSPORT_005`로 변경 | React 호환성 변경 |
| DB | 없음 | 호환 |

## Spec 이후 변경

- 제품 정책 변경: 없음
- 구현 중 추가된 설계 결정: HISTORY view 자체를 최소 응답 기준으로 사용

## 범위 확인

- spec 밖 추가 작업: 없음
- 의도적으로 제외한 작업: 통합 리뷰의 기존 P2·P3 리스크

## 테스트 결과

| 테스트 | 결과 | 근거 |
|---|---|---|
| 대상 API·lifecycle | PASS | 13 tests, 실패 0 |
| MySQL 8.4 집중 실행 | PASS | 7 tests, 실패·오류·생략 0 |
| `./gradlew clean check` | PASS | 213 tests, 실패·오류·생략 0; Javadoc·Spotless 포함 |
| local 실행·readiness | PASS | MySQL 8.4, Flyway V1~V12, `{"status":"UP"}` |
| `git diff --check` | PASS | 공백·충돌 표식 오류 없음 |

## 프론트 핸드오프

| 대상 | 문서 | 실제 코드와 일치 |
|---|---|---|
| Flutter 구급대원 앱 | `NONE` | N/A |
| React 병원·관리자 웹 | `docs/handoffs/18-rejected-hospital-history-privacy/react-hospital-admin.md` | YES |

## 남은 리스크

| 리스크 | 영향 | 대응 |
|---|---|---|
| React가 거절 카드에서 이전 상세 API를 호출 | 404 표시 가능 | 기능 18 핸드오프 기준으로 상세 링크 제거 |
| 현재 브랜치는 Dev 서버에 미배포 | 공개 API에는 아직 이전 계약 | main 병합과 배포 SHA 확인 후 연동 |
