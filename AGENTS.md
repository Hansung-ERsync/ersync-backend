# ERSync AI 작업 규칙

## 작업 전

1. `docs/README.md`를 먼저 읽는다.
2. 작업 영역에 맞는 `docs/ai/` 컨텍스트를 읽는다.
3. `docs/development/conventions.md`를 따른다.
4. 담당 `docs/domains/<domain>/README.md`에서 책임과 경계를 확인한다.
5. 기능 작업은 `docs/features/AGENTS.md`와 해당 기능 폴더를 읽는다.
6. 기능 구현 전 해당 기능의 `README.md`가 `APPROVED`인지 확인한다.

## 구현

- 미확정 정책을 임의로 구현하지 않는다.
- 기존 API, 오류 코드, 로그와 역할 계약을 유지한다.
- 새로운 오류는 `ErrorCode`에 등록하고 오류 코드 문서를 갱신한다.
- public 타입과 패키지 간 공용 메서드에는 JavaDoc을 작성한다.
- 실제 환자정보, 토큰, 비밀번호, 가입 코드, Secret과 정확한 GPS를 로그에 남기지 않는다.
- 기능 변경에는 해당 위험도에 맞는 테스트를 추가한다.

## 문서

- 기능 단위 작업은 `docs/templates/feature/`의 세 파일을 복사해 시작한다.
- 기능 문서는 `docs/features/<domain>/<feature>/`에 둔다.
- `README.md`에는 명세, `plan.md`에는 구현·검증 계획, `implementation.md`에는 실제 변경·검증 결과를 기록한다.
- 팀 리뷰 전 상태는 `DRAFT` 또는 `REVIEW`로 둔다.
- 정책이 확정된 기능만 `APPROVED`로 변경한다.
- AI 내부 사고 과정과 개발 일기는 문서에 기록하지 않는다.
- 루트 `README.md`는 간결하게 유지한다.

## 완료

- `./gradlew clean check`를 실행한다.
- 사용자가 요청하지 않으면 커밋하거나 푸시하지 않는다.
- 사용자가 직접 수정하거나 설정해야 하는 항목을 개조식으로 먼저 나열한다.
- 각 항목 아래에 대상 위치, 수정 이유, 처리 순서와 확인 방법을 자세히 작성한다.
- 사용자가 처리할 항목이 없으면 `사용자 직접 작업 필요 없음`이라고 명시한다.

완료 보고 형식:

```text
사용자 직접 작업

- AWS IAM 신뢰 정책 수정
- GitHub Repository Variable 등록

처리 방법

1. AWS IAM 신뢰 정책 수정
   대상: AWS IAM > 역할 > 신뢰 관계
   이유: GitHub Actions가 배포 역할을 사용할 수 있어야 함
   처리: 지정된 subject와 audience를 입력하고 저장
   확인: GitHub Actions의 AWS 인증 단계 성공 여부 확인
```
