# ERSync DevOps 가이드

- 범위: Spring Boot dev 환경과 React 데모 웹 배포
- 리전: `ap-northeast-2`
- 최종 점검: 2026-08-08

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

관리자 React / 병원 React
  → 각각 빌드
  → 각각의 S3 정적 웹사이트에 배포
  → 브라우저가 HTTP dev API 호출

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
| S3 | 관리자·병원 React 데모 정적 파일 배포 |
| CloudWatch | 애플리케이션 로그와 장애 확인, 아직 미구성 |

S3는 백엔드 배포에 사용하지 않는다. React 빌드 결과만 저장하고 제공한다.

## 2. 핵심 정책

### 네트워크

- 현재 dev EC2는 고정 IP의 HTTP 80 요청을 Nginx로 받고 Spring Boot `127.0.0.1:8080`으로 프록시한다.
- 도메인과 HTTPS는 후속 작업으로 분리한다.
- 현재 환경은 실제 병원에 등록하거나 현장에서 사용하는 시스템이 아닌 제한된 데모다.
- 현재 HTTP dev 환경에는 가짜 조직·계정·환자·위치 데이터만 사용한다.
- 실제 환자정보를 다루거나 외부 기관이 사용하기 전에 HTTPS를 적용한다.
- SSH 포트는 열지 않는다.
- RDS는 Public access를 비활성화한다.
- RDS `3306`은 EC2 Security Group만 접근할 수 있다.

```text
인터넷 → EC2:80 허용
EC2 → RDS:3306 허용
인터넷 → RDS 차단
```

### HTTP 데모 허용 기준

현재 단계에서는 다음 조건을 모두 지키는 동안 HTTP 사용을 허용한다.

- 데모 전용 계정만 사용하고 개인·업무용 비밀번호를 재사용하지 않는다.
- 실제 환자정보, 실제 연락처, 실제 GPS와 실제 병원 운영정보를 입력하지 않는다.
- 로그인 비밀번호와 Access·Refresh Token이 암호화되지 않은 네트워크로 전달될 수 있음을 팀원이 인지한다.
- 가능하면 EC2의 HTTP 80 접근을 팀 또는 데모 장소의 공인 IP로 제한한다.
- 데모가 끝나면 EC2의 공개 접근을 차단하거나 인스턴스를 중지한다.

다음 중 하나라도 해당하면 HTTP 데모 기준을 종료하고 HTTPS를 적용한다.

- 실제 병원·구급대 계정을 등록함
- 실제 또는 재식별 가능한 환자정보를 입력함
- 팀 외부 사용자에게 지속적으로 공개함
- HTTPS로 배포된 웹이나 모바일 앱에서 API를 호출함

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
  "password": "secret",
  "jwtSecretBase64": "base64-encoded-secret",
  "superAdminLoginId": "bootstrap-login-id",
  "superAdminPassword": "bootstrap-password",
  "naverMapsClientId": "naver-maps-client-id",
  "naverMapsClientSecret": "naver-maps-client-secret"
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
- 초기 관리자 ID와 비밀번호는 `SUPER_ADMIN`이 없을 때 한 계정을 생성하는 데만 사용한다.
- JWT Secret과 지도 API 자격정보는 서버 실행 중 계속 사용하는 값이다.

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

## 4. React 데모 웹 배포

### 배포 구조

관리자 웹과 병원 웹은 서로 독립적으로 배포한다.

```text
관리자 React main
  → 검사·빌드
  → 관리자 S3 Bucket
  → 관리자 S3 Website URL

병원 React main
  → 검사·빌드
  → 병원 S3 Bucket
  → 병원 S3 Website URL

두 웹의 API Base URL
  → http://13.124.194.249
```

권장 Bucket 이름은 다음과 같다. S3 Bucket 이름은 전 세계에서 고유해야 하므로
실제 생성 시 팀 식별자나 AWS Account ID를 뒤에 붙인다.

```text
ersync-admin-web-dev-<unique>
ersync-hospital-web-dev-<unique>
```

- 관리자와 병원 웹은 각각 `index.html`, JavaScript와 CSS를 S3에서 내려받는다.
- Spring Boot JAR나 백엔드 Docker 이미지에 React 빌드 결과를 포함하지 않는다.
- 두 웹을 분리하면 한쪽 배포 실패가 다른 웹과 백엔드 배포에 영향을 주지 않는다.
- 정적 파일은 공개되므로 Secret, API Key와 실제 자격정보를 빌드 결과에 포함하지 않는다.
- 화면 접근 제한만 신뢰하지 않고 모든 권한은 기존 백엔드 API에서 검증한다.

### S3 설정

데모에서는 도메인과 CloudFront 없이 S3 Website Endpoint를 사용한다.

1. 관리자·병원용 S3 Bucket을 각각 생성한다.
2. 각 Bucket에서 Static website hosting을 활성화한다.
3. Index document와 Error document를 `index.html`로 설정한다.
4. 해당 Bucket의 객체에만 공개 `s3:GetObject`를 허용한다.
5. Bucket 목록, 쓰기와 삭제 권한은 공개하지 않는다.
6. 빌드 결과 디렉터리의 내용을 Bucket 루트에 업로드한다.

접속 주소는 다음 형태다.

```text
http://<bucket-name>.s3-website.ap-northeast-2.amazonaws.com
```

S3 Website Endpoint는 HTTPS를 지원하지 않는다. 현재 HTTP API와 가짜 데이터만
사용하는 제한된 데모이므로 이를 수용한다. HTTPS가 필요해지면 S3를 비공개로
전환하고 CloudFront OAC를 사용한다.

### 프론트엔드 CI/CD

각 프론트엔드 저장소는 `main` 병합 시 자신의 Bucket만 배포한다.

```text
Pull Request
  → 프론트 검사·빌드 검증

main 병합
  → npm ci
  → 테스트
  → npm run build
  → aws s3 sync <build-directory> s3://<target-bucket> --delete
```

- 정적 AWS Access Key를 저장하지 않고 GitHub Actions OIDC Role을 사용한다.
- Role은 대상 프론트 Bucket에 대한 목록·업로드·삭제 권한만 가진다.
- 기능 브랜치와 PR은 S3에 배포하지 않는다.
- 관리자와 병원이 같은 저장소에 있어도 빌드 결과와 배포 대상은 분리한다.

### 백엔드 CORS

현재 백엔드는 localhost Origin만 기본 허용한다. S3 생성 후 두 Website URL을
`ersync.cors.allowed-origins`에 정확히 추가해야 한다.

```text
http://<admin-bucket>.s3-website.ap-northeast-2.amazonaws.com
http://<hospital-bucket>.s3-website.ap-northeast-2.amazonaws.com
```

- Origin에는 경로와 마지막 `/`를 넣지 않는다.
- `*`로 전체 Origin을 허용하지 않는다.
- 두 주소를 추가하기 전에는 S3 웹에서 API 호출이 CORS로 차단된다.
- 프론트 API Base URL은 현재 `http://13.124.194.249`를 사용한다.

## 5. 현재 AWS 구성

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
- [ ] 관리자 React용 S3 정적 웹사이트와 배포 권한을 구성한다.
- [ ] 병원 React용 S3 정적 웹사이트와 배포 권한을 구성한다.
- [ ] 두 S3 Website Origin을 백엔드 CORS에 추가한다.
- [ ] 실제 환자정보를 다루기 전에 도메인과 HTTPS를 적용한다.

## 6. 관련 저장소 파일

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

프론트엔드 빌드 명령, 빌드 결과 디렉터리와 GitHub Actions workflow는 각
프론트엔드 저장소에서 관리한다.

## 7. 참고 자료

- [Amazon ECR 이미지 태그 불변](https://docs.aws.amazon.com/AmazonECR/latest/userguide/image-tag-mutability.html)
- [GitHub Actions OIDC](https://docs.github.com/en/actions/concepts/security/openid-connect)
- [AWS Systems Manager Run Command](https://docs.aws.amazon.com/systems-manager/latest/userguide/run-command.html)
- [RDS를 VPC에서 운영하기](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_VPC.WorkingWithRDSInstanceinaVPC.html)
- [AWS Secrets Manager](https://docs.aws.amazon.com/secretsmanager/latest/userguide/intro.html)
- [S3 Website Endpoint](https://docs.aws.amazon.com/AmazonS3/latest/userguide/WebsiteEndpoints.html)
- [S3 정적 웹사이트 설정](https://docs.aws.amazon.com/AmazonS3/latest/userguide/HostingWebsiteOnS3Setup.html)
- [CloudFront OAC](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/private-content-restricting-access-to-s3.html)
