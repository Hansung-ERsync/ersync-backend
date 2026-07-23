# ERSync 백엔드 MVP DevOps 가이드

- 범위: Spring Boot dev 환경
- 리전: `ap-northeast-2`

## 1. 구성

```text
GitHub Actions
  → ECR에 Docker 이미지 저장

EC2 Docker Compose
  → Secrets Manager에서 설정 조회
  → Private RDS MySQL 연결
  → CloudWatch에 로그 전송
```

| 서비스 | 역할 |
|---|---|
| ECR | Docker 이미지 저장 |
| EC2 | Spring Boot 실행 |
| RDS MySQL | 데이터 저장 |
| Secrets Manager | 비밀번호와 API Key 저장 |
| Systems Manager | SSH 없는 배포와 접속 |
| CloudWatch | 로그와 장애 확인 |

S3는 백엔드 배포에 사용하지 않는다.

## 2. 핵심 정책

### 네트워크

- EC2는 HTTPS 요청만 받는다.
- SSH 포트는 열지 않는다.
- RDS는 Private Subnet에 둔다.
- RDS `3306`은 EC2 Security Group만 접근할 수 있다.

```text
인터넷 → EC2:443 허용
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
  "DB_HOST": "private-rds-endpoint",
  "DB_PORT": "3306",
  "DB_NAME": "ersync",
  "DB_USERNAME": "ersync_app",
  "DB_PASSWORD": "secret",
  "JWT_SECRET": "secret",
  "MAP_API_KEY": "secret"
}
```

- `.env`를 EC2에 직접 배포하지 않는다.
- Secret을 GitHub와 Docker 이미지에 넣지 않는다.
- EC2 IAM Role로 Secret을 조회한다.
- Secret 값을 로그에 남기지 않는다.
- Secret 변경 후 Spring Boot 컨테이너를 재시작한다.

### RDS

- MySQL 8.4를 기준으로 한다.
- Public access를 비활성화한다.
- 저장 암호화와 자동 백업 7일을 설정한다.
- `require_secure_transport=ON`을 사용한다.
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
- CloudWatch 로그 전송
- Systems Manager 접속

GitHub Actions는 Access Key 대신 OIDC 임시 권한을 사용한다.

## 3. CI/CD 시나리오

### Pull Request

```text
Pull Request 생성
→ Gradle 테스트
→ bootJar 생성
→ Docker 이미지 빌드 확인
```

### dev 배포

```text
main 브랜치 병합
→ GitHub Actions가 OIDC 권한 획득
→ Gradle 검사
→ Docker 이미지 빌드
→ Git SHA 태그로 ECR push
```

## 4. 지금 AWS에서 할 작업

1. 루트 계정 MFA를 설정한다.
2. AWS Budget과 이메일 경보를 만든다.
3. Private ECR Repository `ersync-api`를 만든다.
4. ECR 이미지 스캔과 태그 불변을 활성화한다.
5. GitHub OIDC Provider를 등록한다.
6. GitHub Actions dev 배포 Role을 만든다.
7. EC2 dev IAM Role을 만든다.
8. 리소스 공통 태그를 정한다.

```text
Project=ersync
Environment=dev
Owner=<team-name>
```

로컬에는 AWS CLI v2를 설치하고 역할 기반 로그인을 설정한다.

## 5. 첫 dev 환경 생성 순서

1. VPC와 Subnet을 만든다.
2. EC2와 RDS Security Group을 만든다.
3. Private RDS MySQL을 만든다.
4. `ersync_app` DB 계정을 만든다.
5. `ersync/dev/backend` Secret을 만든다.
6. EC2를 만들고 EC2 IAM Role을 연결한다.
7. EC2에 Docker와 Compose를 설치한다.
8. CloudWatch Log Group을 만든다.
9. GitHub Actions에서 ECR push를 확인한다.
10. Systems Manager 배포를 확인한다.
11. readiness와 이전 이미지 복구를 확인한다.

## 6. 현재 저장소에 추가된 파일

```text
Dockerfile
.dockerignore
.editorconfig
.github/workflows/backend-ci.yml
.github/workflows/backend-deploy-dev.yml
```

실제 Secret과 AWS Endpoint는 저장소에 커밋하지 않는다.

## 7. 완료 기준

- [x] 로컬 Gradle 검사와 Docker 빌드가 성공함
- [x] GitHub Actions에 AWS Access Key를 사용하지 않음
- [x] ECR 이미지 태그로 Git SHA를 사용함
- [ ] 첫 `main` push에서 ECR 이미지 업로드가 성공함
- [ ] EC2가 IAM Role로 ECR과 Secret에 접근함
- [ ] RDS가 Private으로 구성됨
- [ ] DB 연결에 TLS를 사용함
- [ ] readiness 실패 시 배포가 실패함
- [ ] 이전 이미지로 복구할 수 있음
- [ ] 로그에 환자정보, GPS 좌표, Secret이 없음

## 참고 자료

- [Amazon ECR 이미지 태그 불변](https://docs.aws.amazon.com/AmazonECR/latest/userguide/image-tag-mutability.html)
- [GitHub Actions OIDC](https://docs.github.com/en/actions/concepts/security/openid-connect)
- [AWS Systems Manager Run Command](https://docs.aws.amazon.com/systems-manager/latest/userguide/run-command.html)
- [RDS를 VPC에서 운영하기](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_VPC.WorkingWithRDSInstanceinaVPC.html)
- [AWS Secrets Manager](https://docs.aws.amazon.com/secretsmanager/latest/userguide/intro.html)
