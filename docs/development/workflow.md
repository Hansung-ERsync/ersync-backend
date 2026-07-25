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

1. 담당 `docs/domains/<domain>/README.md`에서 기능의 소유 도메인을 확인한다.
2. `docs/templates/feature/`를 `docs/features/<domain>/<feature>/`로 복사한다.
3. 기능 `README.md`에 요구사항, 시나리오와 결정 필요 정책을 작성한다.
4. 팀원이 명세를 리뷰하고 결정이 끝나면 `APPROVED`로 바꾼다.
5. `plan.md`에 변경 범위와 구현·검증 계획을 작성하고 검토한다.
6. GitHub Issue와 작업 브랜치를 만든다.
7. 구현과 테스트를 진행한다.
8. `implementation.md`에 실제 변경, 계획 차이와 검증 결과를 기록한다.
9. PR에서 코드와 기능 폴더의 세 문서를 같이 리뷰한다.
10. 완료 후 기능 `README.md` 상태를 `IMPLEMENTED`로 바꾼다.

## 4. AI 작업 입력 순서

AI에게 다음 문서를 순서대로 제공합니다.

1. `docs/requirements/product-requirements.md`
2. `docs/ai/project-context.md`
3. 담당 영역의 AI 컨텍스트
4. `docs/development/conventions.md`
5. 담당 `docs/domains/<domain>/README.md`
6. 해당 기능 폴더의 `README.md`
7. 구현 전에는 해당 기능의 `plan.md`
8. 리뷰 시에는 해당 기능의 `implementation.md`
9. `docs/development/error-codes.md`

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

1. 담당자가 `docs/features/transport/hospital-offer-response/`를 만든다.
2. AI가 `README.md`에 병원 수락·거절 시나리오와 API 초안을 작성한다.
3. 팀은 여러 병원이 동시에 수락할 수 있다는 정책을 확인한다.
4. 팀은 오류 발생 조건을 확정하고 명세를 `APPROVED`로 변경한다.
5. AI가 `plan.md`에 동시 수락과 중복 응답 검증 계획을 작성한다.
6. `feature/hospital-offer-response` 브랜치에서 구현한다.
7. `implementation.md`에 실제 구현과 테스트 결과를 기록해 PR에 포함한다.

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

## 8. 로컬 개발과 dev 배포

로컬 개발자는 RDS에 직접 연결하지 않습니다. 기능 브랜치에서는 Docker MySQL과 `local` profile을 사용합니다.

```bash
docker compose up -d
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

검사는 다음 명령으로 통일합니다.

```bash
./gradlew clean check
```

협업 흐름:

```text
feature 브랜치 개발
→ 로컬 Docker MySQL로 테스트
→ PR 생성
→ Backend CI 통과
→ main merge
→ ECR push
→ EC2 자동 배포
→ RDS readiness 확인
```

`main`에는 직접 push하지 않습니다. GitHub branch protection에서 PR과 Backend CI 통과를 필수로 설정합니다.

현재 GitHub Ruleset 계약:

- `main` 변경은 Pull Request로만 병합한다.
- 필수 상태 체크의 실제 context는 `verify`다.
- GitHub 화면에는 `Backend CI / verify (pull_request)`로 표시될 수 있다.
- 현재 1인 개발 단계의 필수 승인 수는 0명이다.
- 쓰기 권한을 가진 팀 리뷰어가 합류하면 필수 승인 수를 1명으로 변경한다.
- 리뷰 대화가 남아 있으면 병합하지 않는다.
- 병합 방식은 squash만 허용한다.
- 브랜치 삭제와 force push를 차단한다.

Ruleset에 화면 표시 문자열인 `Backend CI / verify`를 직접 context로 등록하면 성공한 `verify` 작업과 다른 체크로 인식될 수 있습니다.
