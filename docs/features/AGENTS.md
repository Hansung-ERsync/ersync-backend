# 기능 문서 작업 규칙

이 파일은 `docs/features/` 아래의 모든 기능 폴더에 적용됩니다.

## 작업 전

1. 루트 `AGENTS.md`와 `docs/README.md`를 읽는다.
2. 제품 요구사항과 담당 AI 컨텍스트를 읽는다.
3. `docs/development/conventions.md`와 `docs/development/workflow.md`를 읽는다.
4. 담당 `docs/domains/<domain>/README.md`에서 책임과 경계를 확인한다.
5. 해당 기능 폴더의 세 문서를 모두 읽는다.

## 기능 폴더

경로는 `docs/features/<domain>/<kebab-case-feature-name>/`를 사용합니다.

- `README.md`: 기능 목적, 범위, 시나리오, 권한, 정책과 계약
- `plan.md`: 구현할 파일, 순서, 위험과 검증 계획
- `implementation.md`: 실제 변경, 계획 차이, 테스트 결과와 남은 위험

새 폴더는 `docs/templates/feature/`의 세 파일을 복사해 만듭니다.
하나의 기능은 주 담당 도메인 한 곳에만 두고 다른 도메인은 링크로 연결합니다.

## 상태와 구현

- `DRAFT`와 `REVIEW` 상태에서는 정책을 확정하거나 구현하지 않는다.
- `README.md`가 `APPROVED`된 뒤 `plan.md`를 검토하고 구현한다.
- 미확정 정책은 `결정 필요`에 남기고 임의로 코드화하지 않는다.
- 구현 후 `implementation.md`에 확인 가능한 사실과 명령 결과를 기록한다.
- 구현과 검증이 끝난 뒤 `README.md`를 `IMPLEMENTED`로 변경한다.

## 기록하지 않는 내용

- AI 내부 사고 과정, 숨은 추론과 시간순 개발 일기
- 실제 환자정보, 계정정보, 토큰, 비밀번호, 가입 코드와 Secret
- 코드나 테스트 결과로 확인할 수 없는 추측
