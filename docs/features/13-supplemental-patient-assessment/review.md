# 조건부 추가 환자 평가 저장·조회 구현 검수

```text
Feature: supplemental-patient-assessment
Implemented By: backend AI with engineer decisions
Related PR: NONE
Frontend Impact: BOTH
Flutter Handoff: docs/handoffs/13-supplemental-patient-assessment/flutter-paramedic.md
React Handoff: docs/handoffs/13-supplemental-patient-assessment/react-hospital-admin.md
Status: IMPLEMENTED_AND_LOCALLY_VERIFIED
```

> `spec.md`의 확정 범위만 구현했으며 아직 커밋·푸시·PR·Dev 배포는 하지 않았습니다.

## 구현 요약

- 최초 이송 요청에 nullable `supplementalAssessment`를 추가했습니다.
- 혈당·좌우 동공·과거력·알레르기·복용약·감염·격리 우려를 구조화된
  append-only 원본과 GENERAL 상세로 저장합니다.
- 임상 시각·앱 입력 시각·서버 수신 시각을 분리하고 현재 snapshot이 nullable
  최신 원본을 가리킵니다.
- 구급대원 진행 상세와 병원 제안 상세에 같은 응답 구조를 추가했습니다.
- 병원 clinical timeline의 공개 정책을 공통 component로 추출해, 응답 전 후보와
  현재 목적지만 원문을 보고 거절 등 접근 종료 상태에는 `null`을 반환합니다.
- 기존 생성 응답·병원 목록·최소 이력·timeline·SSE·감사 payload는 변경하지 않았습니다.

## 요구사항 체크

| 요구사항 | 결과 | 근거 |
|---|---|---|
| `spec.md` 정책 결정 | PASS | `Policy Decision Status: RESOLVED` |
| 선택 입력과 기존 요청 호환 | PASS | 객체 생략 요청 정상 생성, 새 record 0건 |
| 입력 검증 | PASS | 빈 객체·공백·한쪽 동공·시각·혈당 범위 검증 |
| 원본·snapshot 저장 | PASS | 공통 record와 GENERAL 상세 1건, snapshot FK 연결 |
| 멱등성·동시성 | PASS | 동일 재시도 1건 유지, 변경 payload 409, 동시 생성 1건 |
| 구급대원 상세 복구 | PASS | 여섯 값·세 시각 반환, 내부 ID 비노출 |
| 병원 공개 범위 | PASS | 후보·현재 목적지 공개, 거절 상태 null, 타 조직 차단 |
| 제외 범위 유지 | PASS | 목록·timeline·SSE·감사와 미확정 5.8 타입 변경 없음 |

## 변경 API·DB

| 구분 | 변경 내용 | 호환성 |
|---|---|---|
| 생성 요청 | `POST /api/v1/transport-requests`에 nullable `supplementalAssessment` 추가 | 기존 본문 호환 |
| 구급대원 상세 | `GET /api/v1/transport-requests/{requestId}`에 nullable 응답 추가 | additive |
| 병원 상세 | `GET /api/v1/hospitals/me/offers/{offerId}`에 권한별 nullable 응답 추가 | additive |
| DB | V10: 공통·GENERAL 두 테이블과 snapshot nullable FK | 기존 행 backfill 없음 |

## Spec 이후 변경

- 제품 정책 변경: 없음
- 구현 중 추가한 기술 검증: `assessedAt`은 `enteredAt`보다 늦을 수 없도록
  application과 DB 양쪽에서 검사했습니다. 이는 spec의 기존 임상 시각 규칙을
  구체화한 것이며 사용자 흐름을 바꾸지 않습니다.

## 범위 확인

- spec 밖 제품 기능: 없음
- 의도적으로 제외: 미확정 심정지·외상·심혈관·뇌졸중·임신·선호 병원 타입,
  이송 중 추가 평가 갱신, Flutter·React 코드 수정

## 테스트 결과

| 테스트 | 결과 | 근거 |
|---|---|---|
| `./gradlew clean check` | PASS | 202 tests, failures 0, errors 0, skipped 0 |
| MySQL 8.4 | PASS | V10·JPA validate·유효 저장·CHECK 위반·readiness 테스트 5개 통과 |
| 동시 생성 | PASS | 같은 계정·키·본문 동시 요청에서 요청·추가 평가·snapshot·감사 각 1건 |
| 구급대원·병원 상세 | PASS | 소유권·조직·후보·거절·현재 목적지 시나리오 통과 |
| local 실행·readiness | PASS | 로컬 DB V10 적용, `GET /actuator/health/readiness` → `UP` |
| 정적 확인 | PASS | Spotless·Javadoc·`git diff --check` 통과 |

## 프론트 핸드오프

| 대상 | 문서 | 실제 코드와 일치 |
|---|---|---|
| Flutter 구급대원 앱 | `docs/handoffs/13-supplemental-patient-assessment/flutter-paramedic.md` | YES |
| React 병원·관리자 웹 | `docs/handoffs/13-supplemental-patient-assessment/react-hospital-admin.md` | YES |

## 남은 리스크

| 리스크 | 영향 | 대응 |
|---|---|---|
| 의료 발동·필수 규칙 미확정 | 현재 여섯 값은 개발용 선택 입력 | 누락을 정상으로 해석하지 않고 확정 뒤 새 계약·migration 추가 |
| Flutter 실제 요청 mapper 미반영 | 화면 입력이 API에 계속 누락될 수 있음 | 핸드오프대로 선택 객체와 동공 enum·시각 연결 |
| 아직 Dev 미배포 | 프론트가 Dev API에서 바로 확인할 수 없음 | PR merge와 배포 SHA 일치 뒤 테스트 데이터로 연동 |
