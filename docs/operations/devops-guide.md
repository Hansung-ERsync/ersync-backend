# ERSync 백엔드 MVP DevOps 가이드

- 범위: Spring Boot dev 환경
- 리전: `ap-northeast-2`

## 1. 구성

```text
GitHub Actions
  → ECR에 Docker 이미지 저장

EC2 Docker
  → ECR 이미지 pull
  → Spring Boot 컨테이너 교체
  → readiness 실패 시 이전 컨테이너 복구

후속 구성
  → Secrets Manager에서 설정 조회
  → RDS MySQL 연결
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
- RDS는 Public access를 비활성화한다.
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
→ SSM Run Command로 EC2에 배포 명령 전달
→ EC2가 새 이미지 pull
→ 기존 컨테이너를 rollback 대상으로 보관
→ 새 컨테이너 readiness 확인
→ 성공하면 이전 컨테이너 제거
→ 실패하면 이전 컨테이너 복구
```

배포 실행 계약:

- 컨테이너 이름은 `ersync-api`다.
- EC2의 `127.0.0.1:8080`에서만 애플리케이션을 노출한다.
- readiness 경로는 `/actuator/health/readiness`다.
- readiness 대기 시간은 최대 90초다.
- Docker 로그는 파일당 10MB, 최대 3개로 순환한다.
- 배포 태그로 `latest`를 허용하지 않는다.
- 배포 스크립트는 EC2의 `/usr/local/bin/ersync-deploy`에 설치된다.

## 4. 현재 AWS 구성

- Private ECR Repository `ersync-api`가 생성되어 있다.
- GitHub OIDC Provider와 dev 배포 Role이 생성되어 있다.
- EC2에 Docker와 Systems Manager가 구성되어 있다.
- EC2 IAM Role은 ECR pull과 Systems Manager 연결 권한을 가진다.
- GitHub Actions Role은 ECR push와 지정 EC2의 SSM 명령 권한을 가진다.
- GitHub Repository Variable `EC2_INSTANCE_ID`가 등록되어 있다.
- RDS와 Secrets Manager는 아직 구성 전이다.

## 5. 첫 dev 환경 생성 순서

1. GitHub Actions에서 자동 EC2 배포를 확인한다.
2. readiness 성공과 이전 컨테이너 정리를 확인한다.
3. 의도적인 readiness 실패로 이전 이미지 복구를 확인한다.
4. RDS MySQL을 만들고 EC2와 연결한다.
5. `ersync_app` DB 계정을 만든다.
6. `ersync/dev/backend` Secret을 만든다.
7. EC2 IAM Role에 해당 Secret 조회 권한을 추가한다.
8. JPA와 Flyway 기반을 추가하고 DB 연결을 확인한다.
9. CloudWatch 로그 수집을 구성한다.

## 6. 현재 저장소에 추가된 파일

```text
Dockerfile
.dockerignore
.editorconfig
.github/workflows/backend-ci.yml
.github/workflows/backend-deploy-dev.yml
scripts/deploy-ec2.sh
```

실제 Secret과 AWS Endpoint는 저장소에 커밋하지 않는다.

## 7. 완료 기준

- [x] 로컬 Gradle 검사와 Docker 빌드가 성공함
- [x] GitHub Actions에 AWS Access Key를 사용하지 않음
- [x] ECR 이미지 태그로 Git SHA를 사용함
- [x] 첫 `main` push에서 ECR 이미지 업로드가 성공함
- [x] EC2가 IAM Role로 ECR에 접근함
- [ ] `main` push에서 EC2 자동 배포가 성공함
- [ ] EC2가 IAM Role로 Secret에 접근함
- [ ] RDS의 Public access가 비활성화됨
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
