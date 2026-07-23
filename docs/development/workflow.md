# ERSync 개발 절차

## 1. 기본 원칙

- 기능을 구현하기 전에 기능 명세를 작성한다.
- AI는 명세와 코드 초안을 만들 수 있지만 정책을 확정하지 않는다.
- 팀원이 명세를 리뷰하고 `APPROVED`로 변경한 뒤 구현한다.
- 구현 중 API·상태·오류 정책이 바뀌면 코드를 먼저 고치지 않는다. 명세를 먼저 갱신한다.
- 실제 환자정보, 계정정보, 토큰, Secret을 AI 입력에 사용하지 않는다.

## 2. 기능 문서 상태

```text
DRAFT       AI 또는 담당자가 작성 중
REVIEW      팀 리뷰 중
APPROVED    구현 가능한 상태
IMPLEMENTED 구현과 테스트 완료
```

기능 문서 상단에 다음 정보를 적습니다.

```text
Status: DRAFT
Owner: 담당자
Reviewers: 리뷰어
Related Issue: GitHub Issue
```

## 3. 기능 개발 순서

1. `docs/templates/feature-spec-template.md`를 복사한다.
2. `docs/features/<기능명>.md`를 만든다.
3. AI가 요구사항과 기존 컨텍스트를 읽고 초안을 작성한다.
4. 담당자가 모호한 정책을 `결정 필요` 항목으로 분리한다.
5. 팀원이 시나리오, 권한, API, 오류 코드를 리뷰한다.
6. 결정이 끝나면 상태를 `APPROVED`로 바꾼다.
7. GitHub Issue와 작업 브랜치를 만든다.
8. 구현과 테스트를 진행한다.
9. PR에서 코드와 기능 문서를 같이 리뷰한다.
10. 완료 후 상태를 `IMPLEMENTED`로 바꾼다.

## 4. AI 작업 입력 순서

AI에게 다음 문서를 순서대로 제공합니다.

1. `docs/requirements/product-requirements.md`
2. `docs/ai/project-context.md`
3. 담당 영역의 AI 컨텍스트
4. 해당 기능 명세
5. `docs/development/conventions.md`
6. `docs/development/error-codes.md`

AI에게 구현을 요청할 때는 다음을 명시합니다.

```text
- 구현할 기능 명세 경로
- 변경 가능한 패키지
- 변경하면 안 되는 API 계약
- 필요한 테스트
- 미결정 정책은 임의로 구현하지 말고 보고할 것
```

## 5. 리뷰 체크리스트

- 기능의 사용자와 목적이 명확한가
- 정상·실패·재시도 시나리오가 있는가
- 역할과 조직 단위 권한이 정의됐는가
- 요청·응답과 상태 전이가 정의됐는가
- 오류 코드가 기존 코드와 중복되지 않는가
- 로그와 감사 기록에 환자정보가 노출되지 않는가
- 동시 요청과 중복 요청을 고려했는가
- 테스트 가능한 완료 조건이 있는가

## 6. 작업 시나리오 예시

### 병원 수락 기능

1. 담당자가 `docs/features/hospital-offer-response.md`를 만든다.
2. AI가 병원 수락·거절 시나리오와 API 초안을 작성한다.
3. 팀은 여러 병원이 동시에 수락할 수 있다는 정책을 확인한다.
4. 팀은 `HOSPITAL_OFFER_ALREADY_DECIDED` 오류 발생 조건을 확정한다.
5. 문서 상태를 `APPROVED`로 변경한다.
6. `feature/hospital-offer-response` 브랜치에서 구현한다.
7. 동시 수락과 중복 응답 테스트를 포함해 PR을 만든다.

## 7. 브랜치와 커밋

브랜치:

```text
feature/<설명>
fix/<설명>
refactor/<설명>
chore/<설명>
docs/<설명>
```

예시:

```text
feature/hospital-offer-response
docs/transport-request-spec
```

커밋:

```text
[feature] 병원 수락 처리 구현
[fix] 중복 응답 상태 검사 수정
[docs] 병원 수락 기능 명세 추가
```

`main`은 항상 테스트와 Docker 빌드가 가능한 상태를 유지합니다.
