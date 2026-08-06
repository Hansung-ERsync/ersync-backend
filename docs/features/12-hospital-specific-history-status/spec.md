# 병원별 이송 이력 상태 및 처리 시각 요구사항

```text
Feature: hospital-specific-history-status
Owner: backend
Related Issue: NONE
Frontend Impact: REACT_HOSPITAL_ADMIN
Policy Decision Status: RESOLVED
```

> 하나의 이송 요청이 종료돼도 병원 웹에는 각 병원이 실제로 처리한 결과를
> 구분해 표시합니다. 이송 요청 전체 상태와 병원별 결과를 별도 필드로 유지하며,
> 다른 병원의 식별정보는 노출하지 않습니다.

## 목적

- 목적지 병원에서 인계가 완료됐을 때 요청을 받았던 모든 병원이 자신도 인계를 완료한 것처럼 보이는 문제를 해결합니다.
- 이송 요청 전체 상태와 각 병원의 응답·선택·종료 결과를 명확히 구분합니다.
- 병원별 카드의 상태와 처리 시각을 백엔드가 일관된 의미로 반환합니다.
- 병원 웹이 별도 추측 없이 병원별 결과를 표시하고 시·분·초 단위의 처리 시각을 제공할 수 있게 합니다.

## 이번 기능 범위

### 포함

- 병원 제안 목록·상세 응답에 `hospitalOutcome`, `processedAt` 추가
- 종료 이력에서 자기 병원이 최종 인계 병원이었는지 `hospitalOutcome`으로 정확히 구분
- 전체 이송 상태, 원래 병원 응답 상태와 병원별 표시 결과의 의미 분리
- 목적지 병원 인계 완료와 비목적지 병원의 요청 종료 구분
- 거절·무응답·수락 철회와 이송 취소 결과의 병원별 처리 시각 정규화
- React 병원 웹 전환 계약과 다병원 종료 시나리오 테스트

### 제외

- 병원, 제안 또는 이송 요청을 새 ID 체계로 변경
- 병원마다 서로 다른 `transportRequestStatus`를 저장하거나 반환
- 기존 병원 응답 상태와 이력 삭제·덮어쓰기
- 완료·취소된 요청의 재개 또는 인계 완료 취소
- 다른 병원의 이름, 조직 ID, 제안 ID와 환자정보 공개
- Flutter 구급대원 앱 API·화면 변경
- React 병원·관리자 웹 저장소의 실제 코드 수정

## 정책 기준

### 적용할 MVP 요구사항

- 여러 병원이 같은 요청을 수락할 수 있지만 현재 목적지는 항상 한 곳입니다.
- 목적지를 선택하면 선택되지 않은 병원의 활성 카드는 사라져도 자기 병원의 응답 이력은 유지합니다.
- 구급대원의 인계 요청과 현재 목적지 병원의 확인이 모두 있어야 요청이 `COMPLETED`가 됩니다.
- 완료 시 모든 활성 제안은 닫히고 병원 이력에는 자기 제안의 응답 상태와 요청 종료 상태·시각을 제공합니다.
- 종료된 요청의 환자 임상정보, 구급대원 연락처와 정확한 위치는 병원에 다시 제공하지 않습니다.

### 기존 정책과 충돌

- 없음. 기존 `COMPLETED`는 이송 요청 전체의 종료 상태로 유지합니다.
- 이번 기능은 병원 웹이 전역 상태를 자기 병원의 행위 결과로 잘못 해석하지 않도록 병원별 결과를 추가합니다.

## 상태 의미

### 이송 요청 전체 상태

- `transportRequestStatus`는 환자 이송 요청 하나의 전역 상태입니다.
- 목적지 병원에서 인계가 확인되면 요청을 받았던 모든 병원 응답에 `COMPLETED`가 반환될 수 있습니다.
- `COMPLETED`는 해당 응답을 조회한 병원이 직접 인계를 완료했다는 뜻이 아닙니다.
- `CANCELLED`도 이송 요청 전체가 취소됐다는 뜻이며 병원이 취소 행위를 했다는 뜻이 아닙니다.

### 병원의 원래 응답 상태

- `offerStatus`는 해당 병원이 실제로 한 응답을 보존합니다.
- 허용값은 기존 `PENDING`, `ACCEPTED`, `REJECTED`, `NO_RESPONSE`, `ACCEPTANCE_WITHDRAWN`입니다.
- 이송 완료나 취소가 발생해도 기존 거절·무응답·철회 상태를 다른 값으로 덮어쓰지 않습니다.

### 병원별 표시 결과

`hospitalOutcome`은 병원 웹이 자기 카드에 표시할 병원별 결과입니다.

| 값 | 의미 | 대표 표시 |
|---|---|---|
| `AWAITING_RESPONSE` | 자기 병원이 아직 응답하지 않은 활성 제안 | 응답 대기 |
| `ACCEPTED` | 자기 병원이 수락했고 아직 선택·종료 결과가 확정되지 않음 | 수락 |
| `REJECTED` | 자기 병원이 거절함 | 수용 거절 |
| `NO_RESPONSE` | 응답 기한까지 자기 병원이 응답하지 않음 | 응답 없음 |
| `ACCEPTANCE_WITHDRAWN` | 자기 병원이 수락을 철회함 | 수락 철회 |
| `NOT_SELECTED` | 다른 병원이 현재 목적지로 선택돼 자기 제안이 활성 목록에서 제외됨 | 다른 병원 선택 |
| `HANDOFF_COMPLETED_HERE` | 자기 병원이 최종 목적지이며 인계를 직접 확인함 | 인계 완료 |
| `COMPLETED_ELSEWHERE` | 다른 병원에서 인계가 완료돼 자기 제안이 종료됨 | 다른 병원으로 이송 완료 |
| `TRANSPORT_CANCELLED` | 자기 제안이 응답 대기·수락 상태일 때 이송 요청 자체가 취소됨 | 이송 취소 |

병원별 결과는 다음 우선순위로 결정합니다.

1. 완료된 요청의 최종 목적지 제안이면 `HANDOFF_COMPLETED_HERE`입니다.
2. 자기 제안이 `REJECTED`, `NO_RESPONSE`, `ACCEPTANCE_WITHDRAWN`이면 해당 결과를 보존합니다.
3. 완료된 요청의 비목적지 `PENDING`·`ACCEPTED` 제안이면 `COMPLETED_ELSEWHERE`입니다.
4. 취소된 요청의 `PENDING`·`ACCEPTED` 제안이면 `TRANSPORT_CANCELLED`입니다.
5. 진행 중 요청에서 다른 목적지가 선택된 자기 `PENDING`·`ACCEPTED` 제안이면 `NOT_SELECTED`입니다.
6. 그 밖의 활성 `PENDING`·`ACCEPTED` 제안은 각각 `AWAITING_RESPONSE`, `ACCEPTED`입니다.

### 병원별 처리 시각

- `processedAt`은 `hospitalOutcome`이 현재 값으로 결정된 서버 시각입니다.
- 시간 형식은 기존 계약과 동일한 ISO-8601 UTC이며 초를 포함합니다.
- 예: `2026-08-06T07:30:25Z`.
- 병원 웹은 사용자 시간대로 변환해 `HH:mm:ss`까지 표시할 수 있습니다.

| `hospitalOutcome` | `processedAt` 기준 |
|---|---|
| `AWAITING_RESPONSE` | `null` |
| `ACCEPTED`, `REJECTED` | 병원 응답 시각 |
| `NO_RESPONSE` | 응답 없음이 확정된 시각 |
| `ACCEPTANCE_WITHDRAWN` | 수락 철회 시각 |
| `NOT_SELECTED` | 다른 목적지가 선택돼 자기 제안이 이력으로 전환된 시각 |
| `HANDOFF_COMPLETED_HERE`, `COMPLETED_ELSEWHERE` | 인계 완료 시각 |
| `TRANSPORT_CANCELLED` | 이송 취소 시각 |

`processedAt`은 화면 표시용으로 정규화한 시각이며 기존 `offeredAt`, `respondedAt`,
`withdrawnAt`, `handoffRequestedAt`, `completedAt`, `cancelledAt`은 호환성을 위해
그대로 유지합니다.

## 시나리오

| # | 상황 | 기대 결과 |
|---:|---|---|
| 1 | A병원과 B병원이 수락하고 A병원이 목적지로 선택된 뒤 A병원이 인계를 확인함 | 양쪽의 `transportRequestStatus`는 `COMPLETED`; 종료 후 `currentDestination`은 양쪽 모두 `false`; A는 `HANDOFF_COMPLETED_HERE`, B는 `COMPLETED_ELSEWHERE` |
| 2 | A병원은 최종 목적지, C병원은 앞서 거절한 뒤 A병원에서 인계가 완료됨 | C병원의 `offerStatus`와 `hospitalOutcome`은 `REJECTED`로 유지되고 전역 요청만 `COMPLETED`로 표시됨 |
| 3 | B병원이 수락했지만 A병원이 현재 목적지로 선택되고 아직 이송 중임 | B병원 이력은 `NOT_SELECTED`, A병원 활성 카드는 `ACCEPTED`와 `currentDestination: true`를 반환함 |
| 4 | 병원들이 응답 대기 또는 수락 상태인 동안 구급대원이 이송을 취소함 | 해당 병원들은 `TRANSPORT_CANCELLED`와 취소 시각을 받고 기존 응답·제안 기록은 유지됨 |
| 5 | 병원 웹이 완료 이력을 다시 조회함 | 같은 `offerId`에 같은 `hospitalOutcome`과 `processedAt`이 반환되고 종료 후 임상·연락처·정확한 위치는 노출되지 않음 |

## 외부 동작

| 행위 | 요청·응답 또는 상태 변화 |
|---|---|
| 병원 활성 제안 목록 조회 | 기존 항목에 현재 병원 기준 `hospitalOutcome`, nullable `processedAt`을 추가함 |
| 병원 이력 목록 조회 | 자기 제안의 원래 `offerStatus`, 병원별 `hospitalOutcome`, 전역 `transportRequestStatus`와 처리 시각을 함께 반환함 |
| 병원 제안 상세 조회 | 접근 가능한 활성 상세에 목록과 동일한 병원별 결과·처리 시각을 반환함 |
| 인계 완료 | 전역 요청은 한 번만 `COMPLETED`로 전이하고 목적지·비목적지 병원은 서로 다른 `hospitalOutcome`을 조회함 |
| SSE 상태 변경 수신 | 기존 최소 이벤트를 받은 뒤 REST 목록을 재조회해 병원별 결과를 확정함 |

## API 호환성

- `GET /api/v1/hospitals/me/offers?view=ACTIVE|HISTORY&page=...&size=...` 응답 item에 필드를 추가합니다.
- `GET /api/v1/hospitals/me/offers/{offerId}` 응답에 같은 필드를 추가합니다.
- 기존 `offerId`, `transportRequestId`, `transportRequestStatus`, `offerStatus`와 시각 필드를 삭제하거나 이름을 바꾸지 않습니다.
- 기존 `currentDestination`은 활성 목적지 여부를 계속 뜻하므로 요청 종료 뒤에는 `false`를 유지합니다.
- 종료 뒤 최종 인계 병원 여부는 `currentDestination`을 재해석하지 않고 `hospitalOutcome`으로 구분합니다.
- 명령 API, SSE 이벤트 형식, Flutter API와 관리자 전용 API는 변경하지 않습니다.
- 추가 필드 방식이므로 기존 클라이언트는 역직렬화 시 새 필드를 무시할 수 있습니다. 병원 웹은 잘못된 완료 표시를 고치기 위해 새 필드로 전환해야 합니다.

## 권한과 정보 노출

| 역할 | 허용 작업 | 접근 범위 |
|---|---|---|
| `HOSPITAL_STAFF` | 자기 병원 제안 목록·허용된 상세에서 병원별 결과 조회 | JWT의 자기 병원 조직에 전달된 제안만 |
| `PARAMEDIC` | 변경 없음 | 기존 자기 이송 요청·응답 범위 유지 |
| `SUPER_ADMIN` | 이 기능의 병원 제안·이력 조회 불가 | 환자 임상·위치·병원별 이송 이력 접근 불가 |

- 비목적지 병원에는 실제 목적지 병원의 이름, 조직 ID와 `offerId`를 반환하지 않습니다.
- `COMPLETED_ELSEWHERE`는 다른 병원에서 종료됐다는 최소 사실만 나타냅니다.
- 종료 이력에는 기존과 동일하게 임상정보, 회신 연락처, 거리·ETA와 정확한 위치를 반환하지 않습니다.

## 오류

| 조건 | 기대 결과 또는 오류 코드 | HTTP |
|---|---|---:|
| 인증 없음·유효하지 않은 토큰 | 기존 `AUTH_001` 또는 `AUTH_002` | 401 |
| 병원 역할이 아니거나 비활성 계정·조직 | 기존 `AUTH_003`, `COMMON_004` 또는 `USER_002` | 403 |
| 다른 병원 제안 또는 종료 후 상세에 접근 | 기존 `TRANSPORT_005` | 404 |
| `view`, `page`, `size` 형식·범위 오류 | 기존 `COMMON_001` | 400 |

새로운 명령 API가 없으므로 병원별 결과 조회를 위한 새 공개 오류 코드는 추가하지 않습니다.

## 완료 조건

- [ ] 하나의 이송 요청은 병원 수와 관계없이 전역 `transportRequestStatus` 하나만 유지함
- [ ] 병원 제안별 `offerId`와 기존 응답 상태가 그대로 보존됨
- [ ] 병원 목록·허용된 상세에 `hospitalOutcome`, `processedAt`이 일관되게 반환됨
- [ ] 최종 목적지 병원만 `HANDOFF_COMPLETED_HERE`를 받고 종료 후 `currentDestination`은 기존 의미대로 `false`를 유지함
- [ ] 비목적지 `PENDING`·`ACCEPTED` 병원은 완료 시 `COMPLETED_ELSEWHERE`를 받음
- [ ] 거절·무응답·철회 병원의 결과가 다른 병원의 인계 완료로 덮어써지지 않음
- [ ] 병원별 처리 시각이 ISO-8601 UTC로 초를 포함하고 상태별 기준 시각과 일치함
- [ ] 다른 병원의 식별정보와 종료된 환자의 민감정보가 추가로 노출되지 않음
- [ ] 기존 API 필드, 병원 응답·목적지·인계·취소·SSE 계약이 회귀하지 않음
- [ ] 다병원 인계 완료·취소·거절·무응답·목적지 변경 시나리오와 전체 검사가 통과함
- [ ] React 병원 웹 핸드오프가 상태 표시와 초 단위 시각 표시 기준을 설명함

## 기능 내 결정 사항

| 쟁점 | 결정 | 이유·영향 |
|---|---|---|
| 전체 상태와 병원별 상태 | `transportRequestStatus`는 전역 상태로 유지하고 `hospitalOutcome`을 별도로 추가 | 병원마다 서로 다른 전역 상태를 만드는 모순 없이 화면 의미를 명확히 함 |
| 병원 식별 | 기존 병원별 `offerId`와 인증 조직 범위를 사용 | 이미 제안별 고유 ID가 있어 새 병원 ID나 요청 복제가 필요하지 않음 |
| 원래 응답 보존 | `offerStatus`를 인계 완료·취소 결과로 덮어쓰지 않음 | 병원이 실제로 수락·거절·무응답·철회한 감사 이력을 유지함 |
| 화면용 결과 | `hospitalOutcome`을 서버가 결정해 반환 | React가 여러 필드 우선순위를 중복 구현하거나 전역 완료를 자기 완료로 오해하지 않게 함 |
| 처리 시각 | 상태별 기준을 정규화한 `processedAt`을 반환하고 기존 시각 필드는 유지 | 화면은 한 필드로 표시하고 상세·감사 의미는 기존 필드로 확인 가능 |
| 초 단위 표시 | 서버는 초를 포함한 ISO-8601 시각을 반환하고 웹은 `HH:mm:ss`로 표시 | 시간 원본과 사용자 표시 책임을 분리함 |
| 최종 인계 병원 구분 | 종료 후 `currentDestination`을 재사용하지 않고 자기 제안의 `hospitalOutcome`만 반환 | 활성 목적지라는 기존 의미를 유지하면서 필요한 상태만 구분하고 조직 간 정보 노출을 막음 |
| 기존 API 전환 | 필드 추가 방식으로 유지하되 병원 웹은 상태 표시에 새 필드를 우선 사용 | 기존 호출은 깨지지 않지만 잘못된 문구를 고치려면 프론트 전환이 필요함 |

## 확인 필요 사항

- 없음

## 진행 기준

- 현재 상태는 `RESOLVED`입니다.
- 팀 리뷰에서 전체 이송 상태와 병원별 결과를 분리하고 DTO에 병원별 결과·처리 시각을 추가하는 방향을 선택했습니다.
- ID를 새로 만들지 않고 기존 `offerId`와 최종 목적지 관계를 사용하며, 정보 노출과 API 전환 기준까지 확정했으므로 상세 `implementation.md` 작성과 구현을 진행할 수 있습니다.
