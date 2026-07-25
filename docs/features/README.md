# 기능별 명세

이 폴더에는 팀 리뷰를 거쳐 구현할 기능의 명세, 계획과 구현 결과를 저장합니다.

## 디렉터리 구조

```text
docs/features/<domain>/<kebab-case-feature-name>/
  README.md
  plan.md
  implementation.md
```

- `README.md`: 사람이 리뷰하는 기능 명세와 확정 정책
- `plan.md`: 구현 전 변경 범위와 검증 계획
- `implementation.md`: 실제 구현 내용, 계획 차이와 검증 결과

## 작성 방법

1. [기능 문서 작업 규칙](AGENTS.md)을 읽는다.
2. 담당 [도메인 문서](../domains/README.md)에서 소유 도메인을 확인한다.
3. `docs/templates/feature/`의 세 파일을 기능 폴더로 복사한다.
4. 기능 명세는 리뷰 전까지 `DRAFT` 또는 `REVIEW`로 둔다.
5. 정책과 계약이 확정되면 `APPROVED`로 변경한다.
6. 승인 후 `plan.md`를 검토하고 구현한다.
7. 구현과 테스트가 끝나면 결과를 기록하고 `IMPLEMENTED`로 변경한다.

예시:

```text
transport/hospital-offer-response/
transport/destination-selection/
handoff/transport-completion/
```

하나의 기능은 주 담당 도메인 한 곳에만 둡니다. 관련 도메인은 링크로 연결합니다.
AI 내부 사고 과정과 시간순 개발 일기는 기록하지 않습니다.

현재 승인된 기능 명세는 없습니다.
