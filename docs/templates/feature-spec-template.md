# 기존 기능 명세 템플릿 안내

이 경로는 기존 문서 참조가 깨지지 않도록 유지합니다.

새 기능은 단일 Markdown 파일로 만들지 않습니다. 다음 폴더형 템플릿을 복사합니다.

```text
docs/templates/feature/
  README.md
  plan.md
  implementation.md
```

복사 대상:

```text
docs/features/<domain>/<kebab-case-feature-name>/
```

세 파일의 역할과 작성 순서는 [기능 문서 작업 규칙](../features/AGENTS.md)을 따릅니다.
