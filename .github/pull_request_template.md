<!--
PR 제목과 커밋 메시지는 `[유형] 변경 목적` 형식으로 작성합니다.
유형은 변경 파일이 아니라 작업의 주된 목적을 기준으로 선택합니다.

예시:
- [feature] 초대 코드 발급 기능 추가
- [fix] 만료된 초대 코드 검증 오류 수정
- [refactor] 초대 코드 검증 책임 분리
- [chore] PR 템플릿 개선
- [docs] 병원 수락 정책 문서 보완
-->

# Pull Request

## 유형

<!-- 주된 유형 하나만 체크합니다. -->

- [ ] `feature` 기능 개발
- [ ] `fix` 버그 수정
- [ ] `refactor` 동작을 바꾸지 않는 구조 개선
- [ ] `chore` 설정·빌드·운영 작업
- [ ] `docs` 문서 변경

## 변경

- 변경 목적:
- 도메인:
- 기능 문서:
- 프론트 계약:

<!--
작성 예시:
- 도메인: system
- 기능 문서: docs/features/01-deployment-version-check/
- 프론트 계약: NONE

기능 문서 폴더 안의 spec.md, implementation.md, review.md 경로는 반복하지 않습니다.
기능 문서가 필요 없는 fix, chore, docs 작업은 기능 문서에 NONE을 적습니다.
프론트 영향이 없으면 프론트 계약에 NONE을 적습니다.
-->

## Spec 이후 변경

<!-- 아래 세 항목 중 하나만 체크합니다. -->

- [ ] 정책·API·DB·권한·프론트 계약 변경 없음
- [ ] 변경 있음
- [ ] Spec 적용 대상 아님

변경이 있는 경우:

- 변경 내용:
- [ ] `spec.md` 반영과 팀 재검토 완료
- [ ] 프론트 계약 반영 완료 또는 프론트 영향 없음 확인

## 범위

- [ ] 승인된 Spec 하나 또는 명확한 변경 하나만 다룸
- Spec 밖 추가 작업: `NONE`
- 의도적으로 제외한 후속 작업: `NONE`

## 로컬 검증

- [ ] `./gradlew clean check` 통과
- [ ] Docker MySQL과 `local` 프로필 실행 확인
- [ ] readiness `UP`과 주요 시나리오 확인
- [ ] 실행 검증이 필요 없는 변경임

<!--
clean check는 항상 확인합니다.
코드 변경은 local 실행과 주요 시나리오도 확인합니다.
docs 등 실행 검증이 불필요한 변경만 마지막 항목을 추가로 사용합니다.
-->

- 검증 결과 문서:

<!-- 작성 예시: docs/features/01-deployment-version-check/review.md 또는 NONE -->
