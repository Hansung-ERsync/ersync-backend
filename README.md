# ERSync Backend

구급대원이 응급환자 정보를 주변 응급실에 전달하고, 병원 응답을 바탕으로
이송 목적지를 정할 수 있게 하는 Spring Boot 백엔드입니다.

## 로컬 실행

Docker를 실행한 뒤 다음 스크립트를 사용합니다. 로컬 개발에서는 AWS RDS를
사용하지 않습니다.

```bash
./scripts/dev-start.sh
```

스크립트는 Docker 상태 확인, MySQL 실행·healthcheck 대기, Spring Boot의
`local` 프로필 실행을 순서대로 처리합니다. 이미 준비된 MySQL은 재사용합니다.

다른 터미널에서 실행 상태와 전체 검사를 확인합니다.

```bash
curl http://127.0.0.1:8080/actuator/health/readiness
./gradlew clean check
```

정상 readiness 응답은 `{"status":"UP"}`입니다. `Ctrl+C`는 Spring Boot만
종료하며 MySQL은 유지합니다.

로컬 DB 기본값:

| 항목 | 값 |
|---|---|
| 주소 | `127.0.0.1:3306` |
| Database | `ersync` |
| Username | `ersync_local` |
| Password | `ersync_local_password` |

```bash
docker compose down       # DB 데이터 유지
docker compose down -v    # DB 데이터까지 삭제
```

## 브랜치와 PR

**작업 브랜치는 항상 최신 `main`에서 만듭니다. 브랜치 하나는 PR 하나만
완료합니다.**

| 브랜치 | 사용 기준 | 예시 |
|---|---|---|
| `main` | CI를 통과하고 dev 서버에 배포되는 통합 브랜치 | 직접 작업하지 않음 |
| `feature/*` | 사용자 기능 추가 | `feature/admin-invitation-code` |
| `fix/*` | 잘못된 동작 수정 | `fix/expired-invitation-validation` |
| `refactor/*` | 동작을 바꾸지 않는 구조 개선 | `refactor/invitation-validation` |
| `chore/*` | 설정, 빌드, CI/CD와 운영 작업 | `chore/update-pr-template` |
| `docs/*` | 코드 변경 없는 문서 작업 | `docs/hospital-acceptance-policy` |

브랜치 유형은 커밋 메시지와 PR 제목의 유형과 일치시킵니다.

작업 시작:

```bash
git switch main
git pull --ff-only origin main
git switch -c feature/admin-invitation-code
```

기능 작업:

```text
spec 작성·팀 검토
→ implementation 작성
→ 기능 구현
→ 로컬 검증
→ review.md와 필요한 프론트 계약 갱신
→ main 대상 PR
→ CI·리뷰
→ squash merge
```

병합 후 다음 작업:

```bash
git switch main
git pull --ff-only origin main

# PR 병합을 확인한 뒤 이전 로컬 브랜치 삭제
git branch -D feature/admin-invitation-code

git switch -c feature/next-feature
```

- 이전 기능 브랜치에서 다음 기능 브랜치를 만들지 않습니다.
- 병합된 브랜치를 다음 기능에 재사용하지 않습니다.
- 구현, 로컬 검사, `review.md`와 필요한 프론트 계약을 완료한 뒤 PR을 만듭니다.
- 커밋과 PR 제목은 `[유형] 변경 목적` 형식을 사용합니다.
- 상세 규칙은 [AGENTS.md](AGENTS.md)와 [개발 컨벤션](docs/conventions.md)을
  따릅니다.

## 배포 확인

기능 브랜치와 PR은 EC2에 배포되지 않습니다. `main` 병합만 ECR 이미지 생성과
EC2 자동 배포를 시작합니다. 배포 스크립트는 readiness 실패 시 이전 이미지로
복구하도록 구성되어 있습니다.

- [Dev 서버 readiness](http://13.124.194.249/actuator/health/readiness)
- [Dev 서버 배포 버전](http://13.124.194.249/api/system/version)
- [GitHub main 커밋](https://github.com/Hansung-ERsync/ersync-backend/commits/main)

배포 버전의 `commitSha`와 GitHub `main` 최신 커밋이 같아야 합니다.

현재 dev API는 고정 IP의 HTTP로만 제공됩니다. 개발·연동에는 가짜 데이터만
사용하며, 실제 환자정보를 다루기 전 도메인과 HTTPS를 적용해야 합니다.

## 문서

### 백엔드 개발자 필수

다음 순서로 읽습니다.

1. **[프로젝트 안내](docs/project/guide.md)**: 프로젝트 구조, 환경과 전체 작업 흐름
2. **[개발 컨벤션](docs/conventions.md)**: 코드, DB, 테스트, Git 규칙

### 백엔드 AI 에이전트 필수

1. **[AGENTS.md](AGENTS.md)**: 에이전트 작업 순서와 금지 사항
2. **[공통 컨텍스트](docs/agents/context.md)**: 공통 제품 정책과 상태 계약
3. **[백엔드 컨텍스트](docs/agents/backend.md)**: 백엔드 도메인, API, 오류와 테스트 계약

### 프론트엔드 AI 에이전트 필수

1. **[공통 컨텍스트](docs/agents/context.md)**
2. **[프론트엔드 컨텍스트](docs/agents/frontend.md)**
3. 전달받은 `docs/contracts/{번호}-{기능명}.md`

### 기능 작업 시

- **[기능 템플릿](docs/templates/feature/)**: `spec.md`, `implementation.md`, `review.md`
- [기능별 프론트 계약](docs/contracts/README.md): 프론트 영향이 있을 때 작성

### 참고 문서

- [MVP 요구사항](docs/project/mvp-requirements.md): 제품 정책을 검토하거나 결정할 때
- [DevOps 가이드](docs/devops.md): AWS와 배포 환경을 변경하거나 점검할 때
