# 기능별 명세

이 폴더에는 팀 리뷰를 거쳐 구현할 기능별 명세를 저장합니다.

## 작성 방법

1. `docs/templates/feature-spec-template.md`를 기준으로 문서를 만든다.
2. 파일명은 `<kebab-case-feature-name>.md`로 작성한다.
3. 팀 리뷰가 끝날 때까지 상태를 `DRAFT` 또는 `REVIEW`로 둔다.
4. 정책과 오류 코드가 확정되면 `APPROVED`로 변경한다.
5. 구현과 테스트가 끝나면 `IMPLEMENTED`로 변경한다.

예시:

```text
hospital-offer-response.md
transport-destination-selection.md
transport-completion.md
```

현재 승인된 기능 명세는 없습니다.
