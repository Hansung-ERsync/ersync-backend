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
EC2 자동 배포를 시작합니다. readiness 실패 시 이전 이미지로 복구합니다.

- [Dev 서버 readiness](http://13.124.194.249/actuator/health/readiness)
- [Dev 서버 배포 버전](http://13.124.194.249/api/system/version)
- [GitHub main 커밋](https://github.com/Hansung-ERsync/ersync-backend/commits/main)

배포 버전의 `commitSha`와 GitHub `main` 최신 커밋이 같아야 합니다.

## 문서

| 대상 | 문서 |
|---|---|
| 백엔드 개발자 필수 | [프로젝트 안내](docs/project/guide.md) |
| 제품 정책 검토 | [MVP 요구사항](docs/project/mvp-requirements.md) |
| 모든 에이전트 | [공통 컨텍스트](docs/agents/context.md) |
| 프론트엔드 에이전트 | [프론트엔드 컨텍스트](docs/agents/frontend.md) |
| 백엔드 에이전트 | [백엔드 컨텍스트](docs/agents/backend.md) |
| 백엔드 개발자 필수 | [개발 컨벤션](docs/conventions.md) |
| 기능 작업 | [기능 템플릿](docs/templates/feature/) |
| 프론트엔드 연동 | [기능별 계약](docs/contracts/README.md) |
| 운영 | [DevOps 가이드](docs/devops.md) |

AI 작업자는 루트 [AGENTS.md](AGENTS.md)를 먼저 따릅니다.
