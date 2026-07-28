# 임상 DTO·검증·Append-only 엔티티 구현 검수

```text
Feature: clinical-dto-validation-append-only-entity
Implemented By: Codex
Related PR:
Frontend Impact: NONE
Frontend Contract: NONE
```

## 구현 요약

- 임상 입력 enum과 DTO를 `clinical` 패키지에 추가했습니다.
- 환자 평가, Pre-KTAS, AVPU, 활력징후, 처치 입력의 교차 필드 검증을 추가했습니다.
- 임상 기록을 update 없이 새 row로 추가하는 append-only 엔티티, Repository, appender service를 추가했습니다.
- 새 Flyway migration `V3__clinical_append_only_records.sql`로 임상 기록 테이블을 추가했습니다.

## 요구사항 체크

| 요구사항 | 결과 | 근거 |
|---|---|---|
| 임상 DTO와 enum | PASS | `clinical/api`, `clinical/domain` 추가 |
| 교차 필드 검증 | PASS | `ClinicalRecordValidator`, `ClinicalRecordValidatorTest` |
| append-only 엔티티 | PASS | `ClinicalRecordAppender`, `ClinicalRecordAppenderIntegrationTest` |
| Flyway migration | PASS | `V3__clinical_append_only_records.sql`, local bootRun에서 v3 적용 확인 |

## 변경 API·DB

| 구분 | 변경 내용 | 호환성 |
|---|---|---|
| API | 없음 | 프론트 영향 없음 |
| DB | 임상 append-only 테이블 7개 추가 | 새 Flyway migration이라 기존 migration 수정 없음 |

## 프론트엔드 전달

| 영향 | 계약 |
|---|---|
| `NONE` | `NONE` |

## Spec 이후 정책 변경

- 없음

## 범위 확인

- spec 범위를 넘어 추가한 작업: 없음
- 의도적으로 제외한 후속 작업: supplemental assessment, current snapshot, transport request API

## 테스트 결과

| 테스트 | 결과 | 근거 |
|---|---|---|
| `./gradlew clean check` | PASS | 2026-07-29 실행 성공 |
| local 실행·readiness | PASS | `SPRING_PROFILES_ACTIVE=local ./gradlew bootRun`, V3 적용 후 `{"status":"UP"}` |

## 남은 리스크

| 리스크 | 영향 | 대응 |
|---|---|---|
| 없음 |  |  |

## 다음 작업 추천

1. 초기 이송 요청 transaction과 current patient snapshot을 구현합니다.
