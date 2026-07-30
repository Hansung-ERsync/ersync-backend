# ERSync Backend

구급대원이 응급환자 정보를 주변 응급실에 전달하고, 병원 응답을 바탕으로
이송 목적지를 정할 수 있게 하는 Spring Boot 백엔드입니다.

## 로컬 실행

Docker를 실행한 뒤 다음 명령을 사용합니다. 로컬 개발에서는 RDS를 사용하지
않습니다.

```bash
./scripts/dev-start.sh
```

다른 터미널에서 서버 상태와 전체 검사를 확인합니다.

```bash
curl http://127.0.0.1:8080/actuator/health/readiness
./gradlew clean check
```

정상 readiness 응답은 `{"status":"UP"}`입니다.

로컬 DB:

| 항목 | 값 |
|---|---|
| 주소 | `127.0.0.1:3306` |
| Database | `ersync` |
| Username | `ersync_local` |
| Password | `ersync_local_password` |

```bash
docker compose down       # DB 데이터 유지
docker compose down -v    # DB 데이터 삭제
```

## 기능 개발

백엔드 엔지니어가 개발할 기능을 선택하고 AI와 함께 기능 문서와 코드를
완성합니다.

```text
기능 선택
→ 엔지니어가 AI와 spec 작성
→ 정책 결정이 없거나 해결됐으면 AI가 계속 진행
→ AI가 implementation·구현·로컬 검증 수행
→ AI가 review와 필요한 프론트 핸드오프 작성
→ PR
```

`docs/features/`는 전체 백로그나 개발 순서가 아닙니다. 선택해서 작업을 시작한
기능의 기록만 보관합니다. 전체 제품 범위는 MVP 요구사항을 확인합니다.

작업 브랜치는 항상 최신 `main`에서 만듭니다.

```bash
git switch main
git pull --ff-only origin main
git switch -c feature/{짧은-kebab-case-목적}
```

- 브랜치 하나는 기능 하나 또는 명확한 변경 하나만 담당합니다.
- 커밋과 PR 제목은 `[유형] 변경 목적` 형식을 사용합니다.
- PR 전에 `./gradlew clean check`와 필요한 로컬 실행 검증을 완료합니다.
- 상세 절차는 [AGENTS.md](AGENTS.md)를 확인합니다.

## 배포 확인

기능 브랜치와 PR은 배포되지 않습니다. `main` 병합만 ECR 이미지 생성과 EC2
자동 배포를 시작합니다.

- [Dev 서버 상태](http://13.124.194.249/api/system/health)
- [Dev 서버 readiness](http://13.124.194.249/actuator/health/readiness)
- [Dev 서버 배포 버전](http://13.124.194.249/api/system/version)
- [GitHub main 커밋](https://github.com/Hansung-ERsync/ersync-backend/commits/main)

배포 버전의 `commitSha`와 GitHub `main` 최신 커밋이 같아야 합니다.

현재 dev API는 HTTP로 제공됩니다. 실제 환자정보를 다루기 전 도메인과 HTTPS를
적용해야 합니다.

## 문서

### 백엔드 개발 필수

1. **[MVP 요구사항](docs/project/mvp-requirements.md)**: 확정된 제품 정책과 전체 범위
2. **[공통 컨텍스트](docs/agents/context.md)**: AI가 이해할 역할·상태·정보 노출 규칙
3. **[AI 작업 규칙](AGENTS.md)**: 기능 선택부터 PR까지의 작업 절차
4. **[개발 컨벤션](docs/conventions.md)**: 필수 가드레일과 기본 권장 방식

### 프론트엔드 개발 필수

1. **[공통 컨텍스트](docs/agents/context.md)**
2. **[프론트엔드 요구사항](docs/agents/frontend.md)**
3. 구현할 기능의 Flutter 또는 React [핸드오프](docs/handoffs/README.md)

### 기능 작업

- [기능 기록 안내](docs/features/README.md)
- [기능 문서 템플릿](docs/templates/feature/)
- [프론트 핸드오프 안내](docs/handoffs/README.md)
- [프론트 핸드오프 템플릿](docs/templates/handoff/)

### 운영

- [DevOps 가이드](docs/devops.md)
