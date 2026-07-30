# ERSync DevOps 가이드

- 범위: Spring Boot dev 환경
- 리전: `ap-northeast-2`
- 최종 점검: 2026-07-30

## 1. 구성

```text
GitHub Actions
  → ECR에 Docker 이미지 저장

EC2 Docker
  → ECR 이미지 pull
  → Secrets Manager에서 DB 설정 조회
  → Spring Boot 컨테이너 교체
  → readiness 실패 시 이전 컨테이너 복구

RDS MySQL
  → TLS로 Spring Boot와 연결
  → Flyway로 스키마 변경

후속 구성
  → CloudWatch에 애플리케이션 로그 전송
```

| 서비스 | 역할 |
|---|---|
| ECR | Docker 이미지 저장 |
| EC2 | Spring Boot 실행 |
| RDS MySQL | 데이터 저장 |
| Secrets Manager | 비밀번호와 API Key 저장 |
| Systems Manager | SSH 없는 배포와 접속 |
| CloudWatch | 애플리케이션 로그와 장애 확인, 아직 미구성 |

S3는 백엔드 배포에 사용하지 않는다.

## 2. 핵심 정책

### 네트워크

- 현재 dev EC2는 고정 IP의 HTTP 80 요청을 Nginx로 받고 Spring Boot `127.0.0.1:8080`으로 프록시한다.
- 도메인과 HTTPS는 후속 작업으로 분리한다.
- 현재 HTTP dev 환경에는 가짜 데이터만 사용한다. 실제 환자정보를 다루기 전에 HTTPS를 적용한다.
- SSH 포트는 열지 않는다.
- RDS는 Public access를 비활성화한다.
- RDS `3306`은 EC2 Security Group만 접근할 수 있다.

```text
인터넷 → EC2:80 허용
EC2 → RDS:3306 허용
인터넷 → RDS 차단
```

### Docker와 ECR

- Private Repository `ersync-api`를 사용한다.
- 이미지 스캔과 태그 불변을 활성화한다.
- 배포 태그는 Git Commit SHA를 사용한다.
- 배포에 `latest` 태그를 사용하지 않는다.

예시:

```text
123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/ersync-api:a1b2c3d
```

### Secrets Manager

dev Secret 이름은 `ersync/dev/backend`으로 한다.

```json
{
  "engine": "mysql",
  "host": "private-rds-endpoint",
  "port": 3306,
  "dbname": "ersync",
  "username": "ersync_app",
  "password": "secret"
}
```

- `.env`를 EC2에 직접 배포하지 않는다.
- Secret을 GitHub와 Docker 이미지에 넣지 않는다.
- EC2 IAM Role로 Secret을 조회한다.
- Secret 값을 로그에 남기지 않는다.
- 배포 스크립트는 Secret 구조를 검증한 뒤 `/run/ersync/` 아래 설정 파일로 변환한다.
- 설정 파일은 UID/GID `10001`만 읽을 수 있으며 컨테이너의 `/app/config/application.yaml`에 읽기 전용으로 마운트한다.
- Secret 값은 Docker 환경변수나 `docker inspect` 결과에 저장하지 않는다.
- `/etc/ersync/current-secret-path`에는 현재 런타임 파일 경로만 저장하며 Secret 값은 저장하지 않는다.
- `ersync-secret-refresh.service`는 EC2 재부팅 시 Docker보다 먼저 런타임 설정 파일을 다시 생성한다.
- Secret 준비가 실패하면 Docker 시작을 차단하고 15초 간격으로 다시 시도한다.
- `ersync-container-recovery.service`는 중단된 배포가 남긴 현재·이전 컨테이너를 부팅 후 정리하거나 복구한다.
- Secret 변경 후 Spring Boot 컨테이너를 재시작한다.

### RDS

- MySQL 8.4를 기준으로 한다.
- Public access를 비활성화한다.
- 저장 암호화와 자동 백업 7일을 설정한다.
- 애플리케이션 계정 `ersync_app`은 `REQUIRE SSL`로 생성한다.
- JDBC는 `sslMode=VERIFY_IDENTITY`를 사용한다.
- 애플리케이션 전용 계정 `ersync_app`을 사용한다.
- 스키마는 Flyway로 변경한다.
- Hibernate `ddl-auto=update`는 사용하지 않는다.

### IAM

GitHub Actions Role:

- ECR 이미지 push
- 지정된 EC2에 Systems Manager 배포 명령
- Secrets Manager와 RDS 접근 금지

EC2 Role:

- ECR 이미지 pull
- `ersync/dev/backend` 조회
- CloudWatch 로그 전송, 구성 시 사용
- Systems Manager 접속

GitHub Actions는 Access Key 대신 OIDC 임시 권한을 사용한다.

## 3. CI/CD 시나리오

### 로컬 개발

로컬 개발자는 RDS에 직접 접속하지 않는다. Docker MySQL과 `local` profile을 사용한다.

```bash
./scripts/dev-start.sh
```

`local` profile은 `127.0.0.1:3306`의 MySQL만 바라본다.
Docker Compose와 Gradle 명령을 따로 실행하는 방법은 로컬 자동화 문제를
조사할 때만 사용한다.

### Pull Request

```text
Pull Request 생성
→ Gradle 테스트
→ Docker 이미지 빌드 확인
```

### dev 배포

```text
main 브랜치 병합
→ GitHub Actions가 OIDC 권한 획득
→ Gradle 검사
→ Git SHA를 주입해 Docker 이미지 빌드
→ Git SHA 태그로 ECR push
→ SSM Run Command로 EC2에 배포 명령 전달
→ EC2가 새 이미지 pull
→ 기존 컨테이너를 rollback 대상으로 보관
→ 새 컨테이너 readiness 확인
→ 성공하면 이전 컨테이너 제거
→ 실패하면 이전 컨테이너 복구
```

자동 배포와 수동 재실행은 모두 `main` 브랜치에서만 허용한다.

배포 실행 계약:

- 컨테이너 이름은 `ersync-api`다.
- EC2의 `127.0.0.1:8080`에서만 애플리케이션을 노출한다.
- 외부 dev API 접근은 Nginx가 고정 IP의 HTTP 80을 받아 컨테이너로 프록시한다.
- readiness 경로는 `/actuator/health/readiness`다.
- readiness에는 Spring Boot 상태와 DB 연결 상태를 포함한다.
- readiness 대기 시간은 최대 90초다.
- 배포 버전 경로는 `/api/system/version`이다.
- 버전 응답의 `commitSha`는 Docker 빌드 시 주입한 `main` Git SHA다.
- Docker 로그는 파일당 10MB, 최대 3개로 순환한다.
- 배포 태그로 `latest`를 허용하지 않는다.
- 배포 스크립트는 EC2의 `/usr/local/bin/ersync-deploy`에 설치된다.
- RDS CA 인증서는 이미지의 Java truststore에 포함한다.
- 애플리케이션 JAR와 truststore는 root 소유 읽기 전용 파일이다.
- 컨테이너 루트 파일시스템은 읽기 전용이며 `/tmp`만 제한된 tmpfs로 제공한다.

## 4. 현재 AWS 구성

### 구성·확인 완료

- [x] Private ECR Repository `ersync-api`
- [x] GitHub OIDC Provider와 dev 배포 Role
- [x] GitHub Repository Variable `EC2_INSTANCE_ID`
- [x] EC2 Docker, Systems Manager와 ECR pull 권한
- [x] `main` push 자동 배포와 readiness 성공
- [x] private RDS MySQL 8.4와 `ersync_app` 계정
- [x] `ersync/dev/backend` Secret과 EC2 조회 권한
- [x] JDBC `VERIFY_IDENTITY` 연결
- [x] Elastic IP와 Nginx reverse proxy
- [x] Git SHA 기반 이미지 태그와 배포 버전 API
- [x] readiness 실패 시 이전 컨테이너를 복구하는 배포 로직

### 남은 운영 검증

- [ ] 의도적인 readiness 실패로 실제 이전 이미지 복구를 훈련한다.
- [ ] CloudWatch 애플리케이션 로그 수집을 구성한다.
- [ ] 실제 환자정보를 다루기 전에 도메인과 HTTPS를 적용한다.

## 5. 관련 저장소 파일

```text
Dockerfile
.dockerignore
.editorconfig
.github/workflows/backend-ci.yml
.github/workflows/backend-deploy-dev.yml
scripts/deploy-ec2.sh
scripts/dev-start.sh
compose.yaml
src/main/resources/application-local.yaml
```

실제 Secret과 AWS Endpoint는 저장소에 커밋하지 않는다.

## 6. 참고 자료

- [Amazon ECR 이미지 태그 불변](https://docs.aws.amazon.com/AmazonECR/latest/userguide/image-tag-mutability.html)
- [GitHub Actions OIDC](https://docs.github.com/en/actions/concepts/security/openid-connect)
- [AWS Systems Manager Run Command](https://docs.aws.amazon.com/systems-manager/latest/userguide/run-command.html)
- [RDS를 VPC에서 운영하기](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_VPC.WorkingWithRDSInstanceinaVPC.html)
- [AWS Secrets Manager](https://docs.aws.amazon.com/secretsmanager/latest/userguide/intro.html)
