# 병원별 이송 이력 상태 및 처리 시각 요구사항

```text
Feature: hospital-specific-history-status
Owner: backend
Related Issue: NONE
Frontend Impact: REACT_HOSPITAL_ADMIN
Policy Decision Status: RESOLVED
Policy Revised At: 2026-08-13
Implementation Alignment: REQUIRED
```

> 2026-08-13 확정 정책을 반영한 목표 계약입니다. 현재 `implementation.md`,
> `review.md`, 프론트 핸드오프와 코드는 목적지 선택 뒤 비목적지 제안을
> `NOT_SELECTED` 이력으로 숨기는 이전 구현 기록이므로 후속 구현 전까지 이
> 문서와 다를 수 있습니다.

## 목적

- 이송 요청의 전체 상태와 각 병원의 실제 응답 결과를 구분합니다.
- 목적지 선택 뒤에도 비목적지 수락·응답 대기 병원의 활성 상태를 정확히 표시합니다.
- 완료·취소·거절·철회의 결과와 처리 시각을 병원별로 일관되게 반환합니다.
- 다른 병원의 식별정보와 허용되지 않은 임상·위치정보를 노출하지 않습니다.

## 정책 기준

- 여러 병원이 수락할 수 있지만 현재 목적지는 최대 한 곳입니다.
- 목적지 선택은 비목적지 `PENDING`·`ACCEPTED` 제안을 종료하지 않습니다.
- 비목적지 `PENDING`은 `AWAITING_RESPONSE`와 `다른 병원으로 이동 중 · 응답 가능`입니다.
- 비목적지 `ACCEPTED`는 `ACCEPTED`와 `수락 완료 · 다른 병원으로 이동 중`입니다.
- 진행 중 요청에서는 다른 목적지가 있다는 이유만으로 `NOT_SELECTED`를 만들지 않습니다.
- 새 요청에서는 별도 `NO_RESPONSE` 상태를 만들지 않고 응답하지 않은 제안을 `PENDING`으로 유지합니다.
- 기존 데이터나 구버전 응답의 `NO_RESPONSE`·`NOT_SELECTED`는 호환 조회할 수 있지만 새 정책 전이로 생성하지 않습니다.
- 완료·취소는 원래 병원 응답을 덮어쓰지 않고 병원별 화면 결과만 계산합니다.

## 병원별 표시 결과

| `hospitalOutcome` | 의미 | 활성 여부·표시 |
|---|---|---|
| `AWAITING_RESPONSE` | 아직 수락·거절하지 않음 | 활성, 응답 대기 또는 다른 병원 이동 중·응답 가능 |
| `ACCEPTED` | 병원이 수락함 | 활성, 현재 목적지 또는 수락 완료·다른 병원 이동 중 |
| `REJECTED` | 병원이 거절함 | 이력, 수용 거절 |
| `ACCEPTANCE_WITHDRAWN` | 수락 후 철회함 | 이력, 수락 철회 |
| `HANDOFF_COMPLETED_HERE` | 자기 병원이 최종 인계를 확인함 | 이력, 인계 완료 |
| `COMPLETED_ELSEWHERE` | 다른 병원에서 인계가 완료됨 | 이력, 다른 병원으로 이송 완료 |
| `TRANSPORT_CANCELLED` | 이송 요청 자체가 취소됨 | 이력, 이송 취소 |

새 요청에서 `NO_RESPONSE`와 진행 중 `NOT_SELECTED`는 목표 결과에 포함하지 않습니다.

병원별 결과 우선순위는 다음과 같습니다.

1. 완료된 요청의 최종 목적지이면 `HANDOFF_COMPLETED_HERE`입니다.
2. 원래 제안이 `REJECTED` 또는 `ACCEPTANCE_WITHDRAWN`이면 기존 결과를 보존합니다.
3. 완료된 요청의 비목적지 `PENDING`·`ACCEPTED`이면 `COMPLETED_ELSEWHERE`입니다.
4. 취소된 요청의 `PENDING`·`ACCEPTED`이면 `TRANSPORT_CANCELLED`입니다.
5. 진행 중 `PENDING`은 `AWAITING_RESPONSE`입니다.
6. 진행 중 `ACCEPTED`는 현재 목적지 여부와 관계없이 `ACCEPTED`입니다.

## 처리 시각

`processedAt`은 현재 `hospitalOutcome`이 확정된 서버 시각이며 ISO-8601 UTC로 반환합니다.

| `hospitalOutcome` | `processedAt` 기준 |
|---|---|
| `AWAITING_RESPONSE` | `null` |
| `ACCEPTED`, `REJECTED` | 병원 응답 시각 |
| `ACCEPTANCE_WITHDRAWN` | 수락 철회 시각 |
| `HANDOFF_COMPLETED_HERE`, `COMPLETED_ELSEWHERE` | 인계 완료 시각 |
| `TRANSPORT_CANCELLED` | 이송 취소 시각 |

기존 `offeredAt`, `respondedAt`, `withdrawnAt`, `completedAt`, `cancelledAt`은 삭제하지 않습니다.

## 시나리오

| # | 상황 | 기대 결과 |
|---:|---|---|
| 1 | A·B 수락 후 A가 목적지 | A·B 모두 `ACCEPTED`; A만 `currentDestination: true`, B는 다른 병원 이동 중 |
| 2 | A 목적지, C 응답 대기 | C는 활성 `AWAITING_RESPONSE`이며 수락·거절 가능 |
| 3 | A에서 인계 완료, B 수락, C 거절 | A는 `HANDOFF_COMPLETED_HERE`, B는 `COMPLETED_ELSEWHERE`, C는 `REJECTED` |
| 4 | 현재 목적지 A가 긴급 철회 | A는 `ACCEPTANCE_WITHDRAWN`; 다른 수락·응답 대기 병원은 활성 정책 유지 |
| 5 | 진행 중 요청을 구급대원이 취소 | `PENDING`·`ACCEPTED`는 `TRANSPORT_CANCELLED`, 거절·철회는 기존 결과 유지 |

## 외부 동작

| 행위 | 요청·응답 또는 상태 변화 |
|---|---|
| 병원 활성 목록 | 비목적지 `PENDING`·`ACCEPTED`를 포함하고 `hospitalOutcome`, `processedAt`, `currentDestination` 반환 |
| 병원 이력 목록 | 거절·철회·완료·취소 결과와 기존 응답 상태 반환 |
| 병원 활성 상세 | 현재 권한에 맞는 최소 임상정보 제공, 목적지 이후 갱신·정확한 위치는 현재 목적지만 제공 |
| SSE 상태 변경 | 최소 이벤트 수신 뒤 REST 목록을 재조회해 권위 상태 확정 |

## 권한

| 역할 | 허용 작업 | 접근 범위 |
|---|---|---|
| `HOSPITAL_STAFF` | 자기 병원 제안 목록·허용된 상세 조회 | JWT의 병원 조직에 전달된 제안만 |
| `PARAMEDIC` | 변경 없음 | 기존 자기 이송 요청·응답 범위 |
| `SUPER_ADMIN` | 없음 | 환자 임상·위치·병원별 이송 이력 접근 금지 |

- 비목적지 병원에는 실제 목적지 병원의 이름, 조직 ID와 제안 ID를 반환하지 않습니다.
- 목적지 선택 뒤 비목적지 병원에는 이후 임상 갱신과 정확한 위치를 제공하지 않습니다.
- 종료 이력에는 임상정보, 회신 연락처, 거리·ETA와 정확한 위치를 반환하지 않습니다.

## 오류

| 조건 | 기대 결과 또는 오류 코드 | HTTP |
|---|---|---:|
| 인증 없음·유효하지 않은 토큰 | `AUTH_001` 또는 `AUTH_002` | 401 |
| 병원 역할·활성 조직 검증 실패 | `AUTH_003`, `COMMON_004` 또는 `USER_002` | 403 |
| 다른 병원 제안 또는 허용되지 않은 상세 접근 | `TRANSPORT_005` | 404 |
| 목록 파라미터 형식·범위 오류 | `COMMON_001` | 400 |

## 완료 조건

- [x] 전체 이송 상태와 `hospitalOutcome`이 분리돼 있음
- [x] 완료·취소·거절·철회의 병원별 결과와 처리 시각이 반환됨
- [ ] 진행 중 비목적지 `PENDING`을 활성 `AWAITING_RESPONSE`로 유지함
- [ ] 진행 중 비목적지 `ACCEPTED`를 활성 `ACCEPTED`로 유지함
- [ ] 새 요청에서 `NO_RESPONSE`와 진행 중 `NOT_SELECTED`를 생성하지 않음
- [ ] 정보 노출 제한을 유지한 목록·상세·권한 테스트가 통과함
- [ ] 정책 변경에 맞춘 리뷰와 React 핸드오프가 갱신됨

## 기능 내 결정 사항

| 쟁점 | 결정 | 이유·영향 |
|---|---|---|
| 목적지 선택 뒤 `PENDING` | 활성 응답 대기로 유지 | 늦은 수락·거절을 허용 |
| 목적지 선택 뒤 `ACCEPTED` | 활성 수락 상태로 유지 | 구급대원이 목적지를 다시 변경할 수 있게 함 |
| `NO_RESPONSE` | 새 요청에서 사용하지 않음 | 응답 대기와 의미가 중복됨 |
| `NOT_SELECTED` | 진행 중 새 전이로 사용하지 않음 | 다른 병원 이동 중은 종료 결과가 아니라 현재 표시 상태임 |
| 원래 응답 보존 | 완료·취소 결과로 덮어쓰지 않음 | 병원의 실제 행위와 감사 이력을 유지 |

## 확인 필요 사항

- 없음

## 진행 기준

- 정책은 `RESOLVED`입니다.
- 현재 구현과 공개 계약을 변경해야 하므로 구현 전에 `implementation.md`를 새 정책 기준으로 갱신해야 합니다.
