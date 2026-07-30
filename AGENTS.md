# ERSync AI 작업 규칙

## 1. 기준 문서

새 기능을 시작할 때는 다음 순서로 확인합니다.

1. `docs/project/mvp-requirements.md`
2. `docs/agents/context.md`
3. `docs/conventions.md`
4. 현재 코드와 테스트

`spec.md`를 작성한 뒤에는 다음 순서로 구현합니다.

1. 정책 확인이 끝난 `docs/features/{번호}-{기능명}/spec.md`
2. 같은 폴더의 `implementation.md`
3. 현재 코드와 테스트

제품 동작은 MVP 요구사항이 기준입니다. 기능 범위는 엔지니어가 AI와 작성한
`spec.md`가 기준입니다. 공통 컨텍스트는 역할·상태·정보 노출 규칙을 AI가
이해하기 위한 문서이며 MVP 요구사항을 덮어쓰지 않습니다. 현재 코드는 이미
구현된 기술 계약의 기준입니다.

`docs/features/`는 전체 백로그나 구현 순서가 아닙니다. 담당 엔지니어가 선택해
작업을 시작한 기능의 기록만 보관합니다.

## 2. 자율 기능 작업

백엔드 엔지니어가 다음 기능을 직접 선택하고 AI와 함께 문서를 작성합니다.

```text
기능 선택
→ 최신 main에서 작업 브랜치 생성
→ AI가 MVP 요구사항과 현재 코드 확인
→ 엔지니어가 AI와 spec 작성
→ 정책 결정 상태 확인
→ AI가 implementation·구현·로컬 검증 수행
→ AI가 review와 필요한 프론트 핸드오프 작성
→ PR 생성
```

- 전체 기능의 구현 순서를 미리 정하지 않습니다.
- AI는 미구현 Entity, API, 패키지와 기술을 기존 제안서에서 복사하지 않습니다.
- 설계는 선택한 기능, 현재 코드와 검증 가능한 요구사항을 기준으로 제안합니다.
- 기존 MVP 정책은 기능마다 다시 논의하지 않습니다.
- `Policy Decision Status`가 `NONE` 또는 `RESOLVED`이면 별도 승인 없이 계속 진행합니다.
- `Policy Decision Status`가 `REQUIRED`이면 결정을 기록할 때까지 구현을 중단합니다.

다음 경우에만 사용자에게 정책 확인을 요청합니다.

- MVP 요구사항끼리 충돌하거나 필요한 동작이 비어 있음
- 보안, 개인정보 또는 의료정보 처리 기준에 영향이 있음
- 기존 외부 API 계약을 호환되지 않게 변경해야 함
- 구현 선택에 따라 사용자 동작이나 제품 정책이 달라짐

내부 클래스 구조, Entity 구성, 패키지명, 라이브러리와 구현 알고리즘은 담당
엔지니어가 AI와 자율적으로 결정합니다. 기본 권장 방식과 다르게 설계하면
`implementation.md`에 이유와 영향을 기록합니다.

## 3. 기능 문서

기능을 시작할 때 `docs/templates/feature/`를 복사합니다.

```text
docs/features/{2자리 번호}-{기능명}/
  spec.md
  implementation.md
  review.md
```

- 번호는 우선순위가 아니라 문서 생성 순서입니다.
- 기능 하나는 사용자가 확인할 수 있는 동작 하나를 완료합니다.
- Controller, Service, Repository, migration을 각각 별도 기능으로 나누지 않습니다.
- 엔지니어가 AI와 대화해 `spec.md`를 작성합니다.
- 정책 결정이 없거나 해결됐으면 AI가 `implementation.md`부터 PR 준비까지 계속 진행합니다.
- 구현 중 제품 정책 충돌을 발견하면 구현을 멈추고 `spec.md`를 먼저 갱신합니다.
- 구현 완료 직후 AI가 실제 변경과 검증 결과를 `review.md`에 기록합니다.
- `review.md`는 승인 단계가 아니며, 사람은 애자일 주기 종료 시 완료된 문서를 모아 검수할 수 있습니다.
- `review.md`에는 다음 기능이나 전체 로드맵을 추천하지 않습니다.

## 4. 프론트 핸드오프

프론트에 영향을 주면 구현과 로컬 검증 후 실제 코드 기준으로 핸드오프를
작성합니다.

```text
docs/handoffs/{번호}-{기능명}/
  flutter-paramedic.md
  react-hospital-admin.md
```

영향이 있는 파일만 생성합니다.

| 영향 대상 | 생성 파일 |
|---|---|
| Flutter 구급대원 앱 | `flutter-paramedic.md` |
| React 병원·관리자 웹 | `react-hospital-admin.md` |
| 양쪽 모두 | 두 파일 모두 |
| 프론트 영향 없음 | 생성하지 않고 `NONE` 기록 |

- 템플릿은 `docs/templates/handoff/`를 사용합니다.
- 각 문서는 다른 백엔드 문서를 읽지 않아도 연동할 수 있게 작성합니다.
- 예정된 API나 추천 설계는 적지 않고 구현된 계약만 적습니다.
- 요청·응답, 권한, 상태, 오류와 재조회 조건을 대상별 문서에 직접 기록합니다.
- Flutter나 React의 상태관리, 폴더 구조와 컴포넌트 설계를 지시하지 않습니다.
- 두 문서가 같은 API를 사용하면 같은 PR의 코드와 테스트를 기준으로 일치시킵니다.

## 5. 필수 구현 가드레일

- 역할, 조직과 요청 소유권은 서버에서 검증합니다.
- 슈퍼 관리자는 환자 임상정보와 위치정보를 조회할 수 없습니다.
- 실제 환자정보, 토큰, 비밀번호, 가입 코드, Secret과 정확한 GPS를 로그에 남기지 않습니다.
- 공개 오류는 `CustomException`과 `ErrorCode`를 사용합니다.
- 공통 오류 응답과 `X-Trace-Id` 계약을 유지합니다.
- DB 변경은 새 Flyway migration으로 추가하고 적용된 migration은 수정하지 않습니다.
- 기존 API를 호환되지 않게 변경하면 영향과 전환 방법을 핸드오프에 기록합니다.
- 변경 위험에 맞는 단위, 통합, 권한과 동시성 테스트를 추가합니다.

세부 규칙은 `docs/conventions.md`를 따릅니다.

## 6. 브랜치와 PR

모든 작업은 최신 `main`에서 시작합니다.

```bash
git status --short
git switch main
git pull --ff-only origin main
git switch -c {유형}/{짧은-kebab-case-목적}
```

- 기존 변경을 임의로 삭제하거나 stash하지 않습니다.
- 이전 작업 브랜치에서 다음 브랜치를 만들지 않습니다.
- 브랜치 하나는 PR 하나만 담당합니다.
- 커밋 메시지와 PR 제목은 `[유형] 변경 목적` 형식을 사용합니다.
- 허용 유형은 `feature`, `fix`, `refactor`, `chore`, `docs`입니다.
- 기능 문서, 구현, 테스트, review와 필요한 핸드오프를 같은 PR에 포함합니다.

PR 전에 다음 검사를 통과합니다.

```bash
./gradlew clean check
```

API, DB 또는 실행 설정을 변경했다면 로컬 실행도 확인합니다.

```bash
./scripts/dev-start.sh
curl http://127.0.0.1:8080/actuator/health/readiness
```

- 로컬 개발은 Docker MySQL을 사용하며 RDS에 직접 연결하지 않습니다.
- 실행 결과를 `review.md`에 기록합니다.
- PR은 `main`을 대상으로 생성합니다.
- CI와 리뷰를 통과한 뒤 squash merge합니다.
- 사용자가 요청하지 않으면 커밋하거나 푸시하지 않습니다.

## 7. main 배포

```text
PR merge
→ main workflow
→ ECR 이미지 push
→ EC2 배포
→ readiness 확인
→ 실패 시 이전 이미지 복구
```

기능 브랜치와 PR은 EC2에 배포하지 않습니다. `main` 병합만 dev 서버 배포를
시작합니다.
