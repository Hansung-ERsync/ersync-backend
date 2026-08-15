# 병원별 이송 이력 상태 및 처리 시각 구현 검수

> **정책 개정 알림:** 이 문서는 2026-08-13 이전 구현의 검증 기록입니다. 현재 `spec.md`의 비목적지 `PENDING`·`ACCEPTED` 활성 유지 정책이 구현됐다는 근거로 사용하지 않습니다.

```text
Feature: hospital-specific-history-status
Implemented By: backend AI collaboration
Related PR: NONE
Frontend Impact: REACT_HOSPITAL_ADMIN
Flutter Handoff: NONE
React Handoff: docs/handoffs/12-hospital-specific-history-status/react-hospital-admin.md
```

> 2026-08-06 작업 브랜치의 실제 코드, 전체 자동 테스트와 로컬 MySQL 8.4 실행
> 결과를 기준으로 작성했습니다. 커밋·푸시·PR과 Dev 서버 배포는 수행하지
> 않았습니다.

## 구현 요약

- 병원 제안 목록·상세에 additive 필드 `hospitalOutcome`, `processedAt`을
  추가했습니다.
- 전역 `transportRequestStatus`와 병원 원래 응답 `offerStatus`는 변경하지 않고,
  하나의 `HospitalOfferOutcomeResolver`가 병원별 화면 결과를 계산합니다.
- 목적지 병원 인계 완료는 `HANDOFF_COMPLETED_HERE`, 같은 요청의 다른 수락 병원은
  `COMPLETED_ELSEWHERE`로 구분합니다.
- 거절·무응답·수락 철회는 요청이 나중에 완료·취소돼도 자기 병원의 기존 결과와
  원래 처리 시각을 보존합니다.
- 진행 중 다른 목적지가 선택된 병원은 `NOT_SELECTED`와 마지막 실제
  `SELECTED|CHANGED` 명령 시각을 반환합니다. `UNCHANGED` 재선택은 제외합니다.
- 목록 한 페이지의 목적지 변경 시각은 요청 ID를 모아 repository projection 한
  번으로 조회하고 현재 목적지 ID와 일치할 때만 사용합니다.
- 종료·숨겨진 HISTORY 최소화, 상세 404, 병원 조직 격리와 슈퍼 관리자 차단은
  그대로 유지했습니다.
- React 12번 핸드오프에 9개 결과 enum, 처리 시각, 초 단위 표시와 SSE 재조회
  계약을 기록했습니다. Flutter와 관리자 전용 API에는 영향이 없습니다.

## 요구사항 체크

| 요구사항 | 결과 | 근거 |
|---|---|---|
| 병원별 결과 분리 | PASS | 같은 요청의 목적지·비목적지·거절 병원 API 결과 비교 |
| 최종 인계 병원 구분 | PASS | 목적지 `HANDOFF_COMPLETED_HERE`, 비목적지 `COMPLETED_ELSEWHERE` |
| 원래 병원 응답 보존 | PASS | 완료 뒤 거절 유지, 취소보다 기존 거절 우선 단위 테스트 |
| 병원별 처리 시각 | PASS | 응답·철회·종료·목적지 명령별 `processedAt` 검증 |
| 실제 목적지 변경 시각 | PASS | SELECTED→CHANGED→UNCHANGED 후 CHANGED 시각 projection 검증 |
| 목록·상세 일치 | PASS | 활성 현재 목적지 상세·목록 모두 `ACCEPTED`와 응답 시각 |
| 기존 API 호환 | PASS | 기존 필드 유지, 새 필드만 추가, 전체 189개 테스트 통과 |
| 조직·민감정보 보호 | PASS | 자기 병원 범위 query, HISTORY 최소화·상세 404 회귀 통과 |
| 프론트 최신 계약 | PASS | React 12번 핸드오프에 독립적인 API·enum·전환 규칙 작성 |

## 변경 API·DB

| 구분 | 변경 내용 | 호환성 |
|---|---|---|
| 병원 제안 목록 | item에 `hospitalOutcome`, nullable `processedAt` 추가 | additive, 기존 필드 유지 |
| 병원 제안 상세 | 같은 두 필드 추가 | additive, 기존 접근·404 유지 |
| 응답·철회·인계 명령 | 변경 없음 | 기존 멱등성·상태 오류 유지 |
| SSE | payload·이벤트 변경 없음 | 이벤트 뒤 REST 재조회 유지 |
| DB | migration·Entity 필드 변경 없음, 기존 이력 읽기 | 기존 스키마·체크섬 유지 |

## Spec 이후 변경

- 제품 정책 변경: 없음
- 구현 중 추가된 설계 결정:
  - 목적지 projection은 불변 명령의 증가 ID 중 `SELECTED|CHANGED` 최대값을 사용해
    같은 시각 충돌에서도 마지막 저장 명령을 하나로 결정합니다.
  - projection의 목적지 제안 ID가 현재 요청의 목적지 ID와 일치할 때만 시각을
    사용해 레거시·불일치 데이터에서 잘못된 `NOT_SELECTED` 시각을 표시하지 않습니다.

## 범위 확인

- spec 밖 추가 작업: 없음
- 의도적으로 제외한 작업:
  - React 저장소의 배지·시각 표시 코드 수정
  - 다른 병원의 이름·조직 ID·제안 ID 노출
  - 전역 이송 상태 또는 병원 응답 상태 변경
  - Flutter·슈퍼 관리자 API 변경
  - 신규 DB 컬럼·인덱스·migration

## 테스트 결과

| 테스트 | 결과 | 근거 |
|---|---|---|
| `./gradlew clean check` | PASS | Javadoc·Spotless·전체 자동 테스트 189개 성공, 실패·오류·건너뜀 0 |
| resolver 단위 테스트 | PASS | 9개 결과의 활성·완료·취소·원래 응답 우선순위와 nullable 시각 |
| 목적지 projection 통합 테스트 | PASS | SELECTED→CHANGED→UNCHANGED 뒤 마지막 실제 CHANGED 결과 반환 |
| MySQL 8.4 projection | PASS | 실제 MySQL 8.4에서 JPQL 실행, 최신 목적지·시각과 기존 복합 인덱스 확인 |
| CI 시간 정밀도 회귀 | PASS | H2·MySQL `DATETIME(6)` 재조회 시각을 원본 시각과 방향 무관 1마이크로초 이내로 검증 |
| 다병원 인계 완료 API | PASS | 목적지·다른 수락·거절 병원의 서로 다른 결과와 처리 시각 |
| 무응답·철회·취소 API | PASS | `NO_RESPONSE`, `ACCEPTANCE_WITHDRAWN`, `TRANSPORT_CANCELLED`와 시각 |
| local 실행·readiness | PASS | MySQL 8.4.11, Flyway 9개 검증, JPA 기동, `{"status":"UP"}` |
| `git diff --check` | PASS | 공백·충돌 표식 오류 없음 |

### 주요 충돌 시나리오

| 시나리오 | 검증 결과 |
|---|---|
| A·B 수락 후 목적지를 A→B→B→A로 변경 | 이전 목적지만 `NOT_SELECTED`; B→B `UNCHANGED`는 처리 시각을 바꾸지 않고 마지막 실제 변경 시각 사용 |
| A 목적지, B 수락, C 거절, D 철회, E 응답 대기 | 완료 전 A는 `ACCEPTED`, B·E는 `NOT_SELECTED`, C는 `REJECTED`, D는 `ACCEPTANCE_WITHDRAWN` |
| 위 다섯 병원 요청을 A에서 인계 완료 | A는 `HANDOFF_COMPLETED_HERE`, B·E는 `COMPLETED_ELSEWHERE`, C·D는 기존 결과 유지 |
| 수락·거절·응답 대기 병원이 섞인 상태에서 이송 취소 | 수락·응답 대기는 `TRANSPORT_CANCELLED`, 이미 거절한 병원은 `REJECTED` 유지 |
| 비목적지 수락 병원이 철회 | `NOT_SELECTED`에서 `ACCEPTANCE_WITHDRAWN`으로 바뀌고 철회 시각 사용 |
| 최종 응답 창 만료 | 병원 결과 `NO_RESPONSE`, 제안 종료 시각 사용 |

## 프론트 핸드오프

| 대상 | 문서 | 실제 코드와 일치 |
|---|---|---|
| Flutter 구급대원 앱 | `NONE` | N/A |
| React 병원·관리자 웹 | `docs/handoffs/12-hospital-specific-history-status/react-hospital-admin.md` | YES |

## 남은 리스크

| 리스크 | 영향 | 대응 |
|---|---|---|
| React가 계속 전역 `transportRequestStatus`로 배지를 결정 | 비목적지 병원이 다시 인계 완료로 오표시 | 12번 문서 기준으로 `hospitalOutcome`을 최종 표시 기준으로 전환 |
| 현재 브랜치는 Dev 서버에 미배포 | 공개 Base URL에는 아직 새 두 필드가 없음 | PR 병합·배포 SHA 확인 뒤 테스트 계정으로 병원별 HISTORY 확인 |
| Dev 서버가 HTTP | 환자·자격정보 전송에 부적합 | HTTPS 적용 전 가짜 환자·계정·좌표만 사용 |
