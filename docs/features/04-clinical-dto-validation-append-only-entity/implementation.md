# 임상 DTO·검증·Append-only 엔티티 구현 계획

```text
Feature: clinical-dto-validation-append-only-entity
Author: Codex
Frontend Contract: NONE
```

> 후속 이송 요청 API가 사용할 임상 입력 기반을 한 PR에서 완성합니다.

## 구현 Step

| Step | 작업 | 완료 기준 |
|---:|---|---|
| 1 | 임상 Flyway migration 추가 | append-only 임상 테이블 생성 |
| 2 | 임상 enum 정의 | 문서의 임상 상태·분류 값을 타입화 |
| 3 | 임상 DTO 정의 | 후속 API request record 작성 |
| 4 | 교차 필드 validator 작성 | 명시 상태·사유·시간 조합 검증 |
| 5 | append-only JPA 엔티티·Repository 작성 | update setter 없이 insert 중심 모델 |
| 6 | 임상 기록 append service 작성 | 환자 평가 version 증가와 정정 사유 검증 |
| 7 | 단위·통합 테스트 작성 | 검증 실패와 append-only 저장 검증 |
| 8 | review 문서와 노션 공유 내용 작성 | 실제 결과 기준 갱신 |

## 변경 패키지

| 패키지·파일 | 변경 내용 |
|---|---|
| `clinical/api` | 임상 입력 DTO |
| `clinical/application` | DTO 검증과 append service |
| `clinical/domain` | 임상 enum |
| `clinical/infrastructure` | append-only JPA 엔티티와 Repository |
| `db/migration` | 임상 테이블 migration |

## DB 변경

- `patient_assessment_versions`
- `patient_assessment_injury_sites`
- `patient_assessment_secondary_symptoms`
- `pre_ktas_assessments`
- `consciousness_assessments`
- `vital_sign_sets`
- `treatment_events`

## 테스트 목록

- [x] 단위 테스트
- [x] 통합 테스트
- [ ] 권한·조직 테스트
- [ ] 동시성·멱등성 테스트
- [x] `./gradlew clean check`

권한·조직, 동시성·멱등성은 이번 기능에 Controller와 command idempotency가 없어 후속 transport API에서 검증합니다.

## 프론트엔드 전달

- 영향: `NONE`
- 계약: `NONE`
- 완료 조건: 후속 API 기능에서 실제 endpoint 기준 계약 작성

## 건드리면 안 되는 계약

- 환자 직접 식별정보를 수집하지 않는 정책
- 임상 기록 append-only 정책
- 임상 시간, 입력 시간, 서버 수신 시간 분리
- transport, offer, destination 상태 전이

## 리스크

| 리스크 | 대응 |
|---|---|
| transport FK 부재 | 후속 transport aggregate migration에서 FK 추가 |
| treatment 상세 스키마 확대 | MVP 초기 공통 JSON 저장, 후속 API에서 타입별 DTO 확장 |
| supplemental 미구현 | 프로토콜/initial request 기능에서 별도 구현 |
