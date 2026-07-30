# 기능 작업 기록

`docs/features/`는 전체 MVP 기능 목록, 백로그 또는 구현 순서가 아닙니다.
담당 백엔드 엔지니어가 선택해 작업을 시작한 기능의 문서만 보관합니다.

전체 제품 범위와 정책은 `docs/project/mvp-requirements.md`를 기준으로 합니다.

## 구조

```text
docs/features/{2자리 번호}-{기능명}/
  spec.md
  implementation.md
  review.md
```

- 번호는 우선순위가 아니라 문서 생성 순서입니다.
- 기능 폴더는 도메인 폴더 없이 flat하게 관리합니다.
- 하나의 기능은 하나의 PR에서 구현, 검증과 문서화를 완료합니다.
- 담당 엔지니어가 AI와 대화해 기능 `spec.md`를 작성합니다.
- 정책 결정 상태가 `NONE` 또는 `RESOLVED`이면 AI가 나머지 작업을 계속 진행합니다.
- `implementation.md`는 AI 작업 계획이며 별도 사람 승인을 요구하지 않습니다.
- `review.md`는 구현 직후 AI가 작성하는 결과 기록이며 승인 문서가 아닙니다.
- 사람은 애자일 주기 종료 시 완료된 `review.md`를 모아 전체 결과를 검수합니다.
- 완료된 기록은 다음 기능의 필수 설계안이나 개발 순서로 사용하지 않습니다.

## 작성 순서

```text
기능 선택
→ 엔지니어가 AI와 spec 작성
→ 정책 결정 상태 확인
→ AI가 implementation·구현·로컬 검증 수행
→ AI가 review·프론트 핸드오프 작성
→ PR
```

템플릿은 `docs/templates/feature/`를 사용합니다.
