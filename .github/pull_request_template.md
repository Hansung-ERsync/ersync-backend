<!--
PR 제목: [유형] 변경 목적
브랜치: {유형}/{짧은-kebab-case-목적}
주된 유형 하나만 선택합니다.
-->

## 유형

- [ ] `feature` 사용자 기능 추가
- [ ] `fix` 잘못된 동작 수정
- [ ] `refactor` 동작 변경 없는 구조 개선
- [ ] `chore` 설정·빌드·운영 작업
- [ ] `docs` 문서만 변경

## 변경

- 목적:
- 주요 변경:
- 기능 문서: `docs/features/{번호}-{기능명}/` / `NONE`

## 범위와 정책

- Policy Decision Status: `NONE / RESOLVED`
- Spec 이후 제품 정책 변경: `NONE`
- Spec 밖 추가 작업: `NONE`
- 의도적으로 제외한 작업: `NONE`

<!--
정책이 바뀌었다면 결정 내용과 spec 반영 여부를 적습니다.
내부 구현 선택은 제품 정책 변경으로 기록하지 않습니다.
-->

## 프론트 영향

- [ ] Flutter 구급대원 앱
- [ ] React 병원·관리자 웹
- [ ] 프론트 영향 없음

- Flutter 핸드오프: `docs/handoffs/{번호}-{기능명}/flutter-paramedic.md` / `NONE`
- React 핸드오프: `docs/handoffs/{번호}-{기능명}/react-hospital-admin.md` / `NONE`

## 검증

- [ ] 최신 `main`에서 작업을 시작함
- [ ] 기능 하나 또는 명확한 변경 하나만 포함함
- [ ] `./gradlew clean check` 통과
- [ ] 필요한 local 실행·readiness와 주요 시나리오 확인
- [ ] 실행 검증이 필요 없는 변경임

- 검증 결과: `docs/features/{번호}-{기능명}/review.md` / 간단한 결과

<!--
코드 변경은 필요한 local 실행 확인을 선택합니다.
문서처럼 실행과 무관한 변경만 '실행 검증이 필요 없는 변경'을 선택합니다.
-->
