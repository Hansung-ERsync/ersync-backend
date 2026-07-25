# ERSync Backend

구급대원이 응급환자 정보를 주변 응급실에 전달하고, 병원의 응답을 바탕으로
이송 목적지를 정할 수 있게 하는 Spring Boot 백엔드입니다.

## 로컬 개발 시작

사전 조건:

- Docker Desktop 또는 Docker Engine이 실행 중이어야 합니다.
- 로컬 개발에서는 AWS RDS를 사용하지 않습니다.

### 1. MySQL 실행

```bash
docker compose up -d
docker compose ps
```

`docker compose up -d`가 MySQL 8.4 컨테이너와 로컬 DB를 준비합니다.
첫 실행에서는 이미지를 다운로드하므로 시간이 조금 걸릴 수 있습니다.

> `./gradlew bootRun`은 MySQL을 자동으로 실행하지 않습니다.
> 반드시 Docker MySQL을 먼저 실행해야 합니다.

로컬 DB 기본값:

| 항목 | 값 |
|---|---|
| 주소 | `127.0.0.1:3306` |
| Database | `ersync` |
| Username | `ersync_local` |
| Password | `ersync_local_password` |

### 2. Spring Boot 실행

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

`local` profile은 위 Docker MySQL에 연결합니다. 로컬 실행을 위해 별도의 `.env`
파일이나 AWS Secret을 만들 필요가 없습니다.

### 3. 실행 확인

```bash
curl http://127.0.0.1:8080/actuator/health/readiness
```

정상 응답:

```json
{"status":"UP"}
```

### 4. 검사

PR을 만들기 전에 실행합니다.

```bash
./gradlew clean check
```

### 5. 종료와 초기화

```bash
# MySQL을 중지하고 데이터는 유지
docker compose stop

# 컨테이너를 제거하고 데이터는 유지
docker compose down

# 컨테이너와 로컬 DB 데이터를 모두 삭제
docker compose down -v
```

`docker compose down -v`는 로컬 DB를 완전히 초기화할 때만 사용합니다.
EC2 dev 서버만 AWS RDS와 Secrets Manager를 사용합니다.

## 기능 개발과 배포

하나의 PR은 사용자가 확인할 수 있는 기능 하나를 끝까지 완성합니다. Controller,
Service, DB migration처럼 구현 계층만 기준으로 PR을 잘게 나누지 않습니다.

```text
기능 spec 작성
→ AI가 정책 충돌·미확정 사항 식별
→ 팀 검토·정책 확정
→ implementation 작성
→ 기능 브랜치 구현
→ 로컬 테스트
→ review.md 갱신
→ 프론트 영향이 있으면 계약 작성
→ main 대상 PR
→ Backend CI / verify
→ 리뷰 승인 1명
→ squash merge
→ ECR 이미지 생성
→ EC2 자동 배포
→ readiness 확인
```

기능 브랜치 push와 PR 생성만으로는 EC2가 변경되지 않습니다. `main` 병합 후
배포가 시작되며, readiness가 실패하면 배포 workflow가 이전 이미지로 복구합니다.

## 문서

| 대상 | 문서 |
|---|---|
| 사람 | [프로젝트 안내](docs/project/guide.md) |
| 제품 정책 검토 | [MVP 요구사항](docs/project/mvp-requirements.md) |
| 모든 에이전트 | [공통 컨텍스트](docs/agents/context.md) |
| 프론트엔드 에이전트 | [프론트엔드 컨텍스트](docs/agents/frontend.md) |
| 백엔드 에이전트 | [백엔드 컨텍스트](docs/agents/backend.md) |
| 백엔드 구현 규칙 | [개발 컨벤션](docs/conventions.md) |
| 기능 작업 | [기능 템플릿](docs/templates/feature/) |
| 프론트엔드 연동 | [기능별 계약](docs/contracts/README.md) |
| 운영 | [DevOps 가이드](docs/devops.md) |

AI 작업자는 루트 [AGENTS.md](AGENTS.md)를 먼저 따릅니다.

## 작업 순서 요약

1. `spec.md`를 작성하고 AI가 정책 충돌과 미확정 사항을 찾습니다.
2. 팀에서 정책을 결정하고 spec의 구현 전 확인을 마칩니다.
3. `implementation.md`를 작성한 뒤 사용자 기능 하나를 완성합니다.
4. Docker MySQL로 실행하고 로컬 검사를 통과합니다.
5. `review.md`와 필요한 프론트 계약을 작성하고 PR을 생성합니다.
6. CI와 리뷰 승인 후 `main`에 squash merge하고 자동 배포를 확인합니다.

자세한 과정은 [프로젝트 안내](docs/project/guide.md)를 확인합니다.
