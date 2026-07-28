# 임상 DTO·검증·Append-only 엔티티 요구사항

```text
Feature: clinical-dto-validation-append-only-entity
Domain: clinical
Owner: backend
Related Issue: NONE
Frontend Impact: NONE
```

> 최초 이송 요청과 이송 중 임상 갱신 API가 사용할 임상 입력 DTO, 교차 필드
> 검증, append-only 영속 모델을 먼저 구축합니다.

## 목적

- 환자·발생 정보, Pre-KTAS, AVPU, 활력징후, 처치 입력 DTO를 정의합니다.
- 빈 값 대신 값 또는 명시 상태를 요구하는 교차 필드 검증을 구현합니다.
- 제출된 임상 기록은 수정하지 않고 새 row로만 추가되는 append-only 엔티티를 구현합니다.

## 시나리오

| # | 상황 | 기대 결과 |
|---:|---|---|
| 1 | 나이 상태가 `EXACT` 또는 `ESTIMATED`인데 나이가 없음 | 임상 검증 실패 |
| 2 | Pre-KTAS가 `COMPLETED`인데 1~5 외 단계가 들어옴 | 임상 검증 실패 |
| 3 | 활력징후 항목이 `MEASUREMENT_UNAVAILABLE`인데 사유가 없음 | 임상 검증 실패 |
| 4 | `NONE` 처치와 다른 처치를 함께 제출함 | 임상 검증 실패 |
| 5 | 같은 이송 요청에 환자 평가를 다시 추가함 | 기존 row는 유지되고 다음 version row가 추가됨 |

## API

| 행위 | Method·Path | 요청·응답 핵심 |
|---|---|---|
| 없음 | 없음 | 이번 기능은 후속 API에서 사용할 내부 DTO·검증·엔티티 기반만 추가 |

프론트엔드 영향이 `NONE`이므로 계약 문서를 만들지 않습니다.

## 권한

| 역할 | 허용 작업 | 접근 범위 |
|---|---|---|
| 없음 | 없음 | 이번 기능은 Controller를 열지 않음 |

## 오류

| 조건 | 오류 코드 | HTTP |
|---|---|---:|
| 임상 DTO 조합 검증 실패 | `COMMON_001` | 400 |

## 완료 조건

- [x] 임상 enum과 DTO를 정의합니다.
- [x] 환자 평가, Pre-KTAS, AVPU, 활력징후, 처치의 교차 필드 검증을 구현합니다.
- [x] 임상 append-only 테이블을 새 Flyway migration으로 추가합니다.
- [x] 임상 기록 저장 서비스가 기존 row를 수정하지 않고 새 row를 추가합니다.
- [x] 단위·통합·MySQL migration 검증 테스트를 추가합니다.

## 확정 정책

| 쟁점 | 최종 결정 | 결정 이유·영향 |
|---|---|---|
| 이번 기능의 API 노출 | Controller를 만들지 않음 | transport request API 전 단계 기반 작업이므로 프론트 계약 없음 |
| transport FK | 이번 migration에서는 `transport_request_id` 문자열과 인덱스만 저장 | transport aggregate가 아직 없으므로 후속 transport migration에서 FK 추가 가능 |
| supplemental/current snapshot | 이번 범위에서 제외 | 조건부 프로토콜과 snapshot은 initial request 기능에서 함께 확정 |

## 결정 필요 사항

- 없음

## 구현 전 확인

- [x] AI 또는 작성자가 기존 요구사항과의 충돌·미확정 정책을 검토함
- [x] 팀에서 목적, 시나리오, API, 권한, 오류와 완료 조건을 검토함
- [x] 최종 결정을 `확정 정책`에 반영했고 `결정 필요 사항`이 없음

세 항목을 모두 확인했으므로 `implementation.md` 계획에 따라 구현합니다.
