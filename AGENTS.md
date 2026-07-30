# ERSync AI 작업 규칙

## 1. 읽기 순서

역할별 기본 문서:

1. `docs/agents/context.md`
2. 담당 역할 문서
   - 프론트엔드: `docs/agents/frontend.md`
   - 백엔드: `docs/agents/backend.md`

프론트엔드 기능 연동:

1. `docs/agents/context.md`
2. `docs/agents/frontend.md`
3. `docs/contracts/{번호}-{기능명}.md`
4. 현재 프론트엔드 코드

제품 정책을 새로 정하거나 문서 충돌을 검토할 때는
`docs/project/mvp-requirements.md`를 기준으로 확인합니다.

백엔드 기능 작업:

1. `docs/features/{번호}-{기능명}/spec.md`
2. 같은 폴더의 `implementation.md`
3. `docs/conventions.md`
4. 구현 완료 후 같은 폴더의 `review.md`

백엔드 기능 작업의 전체 읽기 순서:

```text
공통 컨텍스트
→ 백엔드 컨텍스트
→ 팀 검토가 끝난 기능 spec·implementation
→ 컨벤션
→ 현재 코드
```

제품 정책은 `mvp-requirements.md`가 기준입니다. 기능 범위와 완료 조건은 팀
검토가 끝난 `spec.md`가 기준입니다. 충돌하거나 미확정인 정책은 임의로 구현하지
않습니다.

## 2. 브랜치 생명주기

모든 작업은 최신 `main`에서 새 브랜치를 만들어 시작합니다.

```bash
git status --short
git switch main
git pull --ff-only origin main
git switch -c {유형}/{짧은-kebab-case-목적}
```

- 작업 시작 전 working tree가 clean인지 확인합니다.
- 기존 변경이 있으면 임의로 삭제하거나 stash하지 않습니다. 변경 출처와 작업
  영향을 먼저 확인합니다.
- 이전 기능 브랜치에서 다음 기능 브랜치를 만들지 않습니다.
- 병합된 브랜치를 새 작업에 재사용하지 않습니다.
- 브랜치 하나는 PR 하나만 담당합니다.
- 기능 문서, 구현, 테스트와 연동 계약은 같은 작업 브랜치에서 완료합니다.
- 필수 로컬 검증과 `review.md` 갱신 전에는 PR을 생성하지 않습니다.

PR이 `main`에 squash merge된 뒤 다음 작업 전에 다시 동기화합니다.

```bash
git switch main
git pull --ff-only origin main

# GitHub에서 PR 병합을 확인한 뒤에만 이전 로컬 브랜치를 삭제합니다.
git branch -D {이전-작업-브랜치}

git switch -c {다음-유형}/{다음-작업}
```

- 다음 브랜치 생성 전 `main`과 `origin/main`이 일치해야 합니다.
- PR이 아직 병합되지 않았다면 해당 브랜치를 삭제하거나 다음 작업에 재사용하지
  않습니다.
- 여러 작업을 병렬로 진행해야 하더라도 각 브랜치는 서로가 아닌 최신 `main`에서
  독립적으로 만듭니다.

## 3. 기능과 PR 단위

- 하나의 PR은 하나의 기능을 구현, 테스트, 문서화까지 완료합니다.
- 기능은 사용자가 하나의 목적을 달성하는 검증 가능한 흐름입니다.
- Controller, Service, Repository, migration을 각각 별도 기능이나 PR로 만들지 않습니다.
- 같은 기능에 필요한 API, DB, 권한, 오류, 테스트는 한 기능 폴더와 한 PR에 포함합니다.
- 너무 큰 기능은 독립적으로 배포하고 검수할 수 있는 사용자 흐름을 기준으로 나눕니다.
- 코드 정리나 기반 변경은 기능과 섞지 말고 목적이 명확한 별도 PR로 만듭니다.

예시:

| 적절한 기능 | 너무 작은 분리 |
|---|---|
| 관리자의 가입 코드 발급·조회·폐기 | 가입 코드 Controller만 추가 |
| 병원의 이송 요청 수락·거절 | 수락 Entity만 추가 |
| 구급대원의 목적지 선택·변경 | API, Service, DB를 각각 다른 PR로 분리 |

기능 작업의 고정 순서:

```text
최신 main 갱신·작업 브랜치 생성
→ 기능 spec 작성
→ AI가 기존 요구사항 충돌·미확정 정책 식별
→ 팀 검토·정책 결정
→ 확정 정책 반영·결정 필요 사항 해소
→ 구현 전 확인
→ implementation 작성
→ 기능 구현
→ 로컬 테스트
→ review.md 갱신
→ 프론트 영향이 있으면 실제 코드 기준 계약 작성
→ PR 생성
→ CI·리뷰
→ main squash merge
→ main 다시 갱신
→ 다음 작업 브랜치 생성
```

## 4. 기능 문서

기능 시작 시 `docs/templates/feature/`의 세 파일을 복사합니다.

```text
docs/features/{2자리 번호}-{기능명}/
  spec.md
  implementation.md
  review.md
```

- 기능 폴더는 도메인 폴더 없이 flat하게 관리합니다.
- 번호는 생성 순서대로 부여하고 재사용하지 않습니다.
- AI는 기존 요구사항과 충돌하거나 선택이 필요한 정책을 선택지·추천과 함께
  `결정 필요 사항`에 기록합니다.
- AI 추천은 팀 결정으로 취급하지 않습니다.
- 팀에서 결정한 내용은 `확정 정책`에 기록하고 `결정 필요 사항`에서 제거합니다.
- `spec.md`의 구현 전 확인 세 항목을 모두 체크한 뒤 구현합니다.
- `implementation.md`는 spec 검토가 끝난 뒤 작성합니다.
- `implementation.md`는 최대 8단계로 작성합니다.
- 구현 완료 후 실제 변경과 검증 결과를 `review.md`에 기록합니다.
- 검수자는 `review.md`만으로 1차 판단할 수 있어야 합니다.
- `spec.md`에 프론트엔드 영향이 있는지 `YES` 또는 `NONE`으로 기록합니다.
- AI 사고 과정과 개발 일기는 남기지 않습니다.

구현 중 새로운 정책 쟁점을 발견하면 임의로 결정하지 않습니다.

```text
구현 중단
→ spec의 결정 필요 사항 갱신
→ 팀 결정
→ 확정 정책 반영·팀 재확인
→ 구현 재개
```

## 5. 프론트엔드 연동 계약

다음 중 하나가 변경되면 `docs/templates/frontend-contract.md`로
`docs/contracts/{번호}-{기능명}.md`를 만듭니다.

- API Method, Path, 요청 또는 응답
- 인증, 역할 또는 조직 접근 범위
- 상태, Enum 또는 화면 분기
- 오류 코드와 프론트 처리
- 실시간 이벤트와 재조회 조건

규칙:

- 계약은 기능별 한 파일로 관리하고 도메인 폴더를 만들지 않습니다.
- 기능 구현과 로컬 검증 후 실제 코드와 테스트를 기준으로 계약을 작성합니다.
- 계약에는 미결정 사항을 남기지 않습니다.
- 구현 후 `review.md`와 PR에 계약 링크를 기록합니다.
- 프론트 영향이 없으면 계약을 만들지 않고 `Frontend Impact: NONE`을 기록합니다.
- 별도의 수동 API 계약 문서를 만들지 않습니다.
- API 필드·권한·오류·이벤트는 프론트엔드 계약 하나에 기록합니다.
- OpenAPI 도입 후 정확한 스키마는 OpenAPI, 연동 절차는 계약 문서가 기준입니다.

## 6. 구현 규칙

- 기존 API, 상태, 역할, 조직 소유권과 개인정보 계약을 유지합니다.
- 오류는 `CustomException`과 `ErrorCode`로 명시적으로 발생시킵니다.
- 새로운 오류는 `ErrorCode`와 `docs/agents/backend.md`의 오류 코드 표에 등록합니다.
- Java, Spring, JPA, Flyway와 JavaDoc 규칙은 `docs/conventions.md`를 따릅니다.
- 실제 환자정보, 토큰, 비밀번호, 가입 코드, Secret과 정확한 GPS를 로그에 남기지 않습니다.
- 모든 DB 변경은 새 Flyway migration으로 추가합니다. 적용된 migration은 수정하지 않습니다.
- 변경 위험에 맞는 단위, 통합, 권한, 동시성 테스트를 추가합니다.

## 7. 로컬 검증과 PR

백엔드 작업자는 다음 검사를 로컬에서 통과시킨 뒤 PR을 생성합니다.

```bash
./gradlew clean check
```

API, DB 또는 실행 설정을 변경했다면 로컬 실행도 확인합니다.

```bash
./scripts/dev-start.sh
```

다른 터미널에서 readiness를 확인합니다.

```bash
curl http://127.0.0.1:8080/actuator/health/readiness
```

- `scripts/dev-start.sh`는 Docker 확인, MySQL healthcheck 대기와 `local`
  프로필 실행을 담당합니다.
- 자동화 문제를 조사할 때만 스크립트 내부의 Docker Compose와 Gradle 명령을
  개별 실행합니다.
- 로컬 개발은 Docker MySQL을 사용하며 RDS에 직접 연결하지 않습니다.
- 실패한 검사를 숨기거나 PR 이후 작업으로 넘기지 않습니다.
- 실행 명령과 결과를 기능 `review.md`에 기록합니다.
- 프론트 영향이 있으면 실제 코드 기준의 계약과 링크를 확인합니다.
- PR은 `main`을 대상으로 생성합니다.
- 필수 CI와 리뷰 승인 1명을 통과한 뒤 squash merge합니다.
- 모든 커밋 메시지와 PR 제목은 `[유형] 변경 목적` 형식으로 작성합니다.
- 허용 유형은 `feature`, `fix`, `refactor`, `chore`, `docs`입니다.
- 브랜치 이름은 `{유형}/{짧은-kebab-case-목적}` 형식으로 작성합니다.
- 브랜치 유형은 PR의 주된 유형과 일치시킵니다.
- 유형 선택과 제목 예시는 `docs/conventions.md`의 Git 협업 규칙을 따릅니다.

## 8. main 병합과 배포

```text
PR merge
→ main push workflow
→ Docker 이미지 빌드
→ ECR에 Git SHA 태그 push
→ EC2가 이미지 pull
→ Secrets Manager 설정으로 컨테이너 실행
→ readiness 확인
→ 실패 시 이전 이미지 복구
```

- 기능 브랜치 push와 PR은 EC2에 배포하지 않습니다.
- `main` 병합만 dev 서버 배포를 시작합니다.
- 자동 복구가 있어도 실패 원인 수정은 새 PR로 진행합니다.

## 9. 완료 보고

- 사용자가 요청하지 않으면 커밋하거나 푸시하지 않습니다.
- 사용자가 직접 수정할 항목을 먼저 개조식으로 나열합니다.
- 각 항목에 대상, 이유, 처리 순서와 확인 방법을 적습니다.
- 직접 작업이 없으면 `사용자 직접 작업 필요 없음`이라고 명시합니다.
