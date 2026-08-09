# ERSync DevOps 가이드

- 범위: Spring Boot dev 서버와 병원·슈퍼 관리자 데모 웹
- AWS 리전: `ap-northeast-2`
- 기준일: 2026-08-09

## 1. 목표 구성

```text
백엔드 main
  → GitHub Actions
  → ECR
  → EC2 Docker + Nginx
  → RDS MySQL

프론트 웹 main
  → GitHub Actions
  → 병원 Cloudflare Worker
  → 슈퍼 관리자 Cloudflare Worker
  → 두 Worker가 EC2 백엔드 API 호출
```

| 서비스 | 역할 |
|---|---|
| ECR | Spring Boot Docker 이미지 저장 |
| EC2 | Spring Boot 컨테이너와 Nginx 실행 |
| RDS MySQL | dev 데이터 저장 |
| Secrets Manager | 백엔드 DB·JWT·초기 관리자 Secret 관리 |
| Systems Manager | SSH 없는 EC2 배포와 접속 |
| Cloudflare Workers | 병원 웹과 슈퍼 관리자 웹 실행 |
| CloudWatch | 애플리케이션 로그 수집, 아직 미구성 |

프론트 웹은 AWS S3에 배포하지 않는다. 현재 웹은 정적 파일만 있는 애플리케이션이
아니며 Next.js API Route, HttpOnly 인증 쿠키, 백엔드 프록시와 SSE 프록시를
사용한다. S3는 이 서버 코드를 실행할 수 없다.

## 2. 네트워크와 데모 보안

```text
브라우저
  → HTTPS workers.dev
  → Cloudflare Worker
  → HTTP 13.124.194.249:80
  → Nginx
  → Spring Boot 127.0.0.1:8080
  → TLS RDS:3306
```

- Cloudflare가 제공하는 `workers.dev` HTTPS 주소를 사용하며 별도 도메인을 구매하지 않는다.
- EC2는 고정 IP의 HTTP 80 요청을 Nginx로 받아 Spring Boot로 프록시한다.
- Worker에서 EC2까지는 현재 공인 인터넷의 HTTP 구간이다.
- SSH 포트는 열지 않고 Systems Manager를 사용한다.
- RDS Public access는 비활성화한다.
- RDS `3306`은 EC2 Security Group만 접근할 수 있다.

현재 구성은 다음 조건을 모두 지키는 제한된 데모에만 허용한다.

- 데모 전용 계정만 사용하고 개인·업무용 비밀번호를 재사용하지 않는다.
- 실제 환자정보, 실제 연락처, 실제 GPS와 실제 병원 운영정보를 입력하지 않는다.
- 팀 외부에 장기간 공개하지 않는다.
- 데모 종료 후 EC2 공개 접근을 차단하거나 인스턴스를 중지한다.

실제 데이터나 실제 기관 계정을 사용하기 전에는 EC2 백엔드에도 HTTPS를
적용해야 한다. 브라우저와 Worker 사이만 HTTPS라고 해서 전체 구간이 암호화되는
것은 아니다.

## 3. 백엔드 AWS 운영

### 로컬과 PR

로컬 개발자는 RDS에 직접 접속하지 않는다. Docker MySQL과 `local` profile을
사용한다.

```bash
./scripts/dev-start.sh
```

PR에서는 Gradle 테스트와 Docker 이미지 빌드를 검증한다. 기능 브랜치와 PR은
EC2에 배포하지 않는다.

### main 자동 배포

```text
main 병합
→ GitHub Actions가 AWS OIDC 임시 권한 획득
→ Gradle 검사
→ Git SHA 태그로 ECR push
→ SSM Run Command로 EC2 배포
→ 새 컨테이너 readiness 확인
→ 성공 시 이전 컨테이너 제거
→ 실패 시 이전 컨테이너 복구
```

배포 계약:

- ECR Repository: `ersync-api`
- 컨테이너 이름: `ersync-api`
- 내부 포트: `127.0.0.1:8080`
- readiness: `/actuator/health/readiness`
- 배포 버전: `/api/system/version`
- 이미지 태그: 전체 Git Commit SHA
- 재시작 정책: `unless-stopped`
- `latest` 태그 사용 금지

### Secrets Manager

백엔드 dev Secret 이름은 `ersync/dev/backend`이다.

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
  "naverMapsClientId": "naver-directions-client-id",
  "naverMapsClientSecret": "naver-directions-client-secret"
}
```

- Secret을 GitHub, Docker 이미지, 로그에 넣지 않는다.
- EC2 IAM Role로 Secret을 조회한다.
- Secret은 `/run/ersync/` 아래 런타임 설정 파일로 변환한다.
- 설정 파일은 컨테이너의 `/app/config/application.yaml`에 읽기 전용으로 마운트한다.
- 초기 관리자 ID와 비밀번호는 `SUPER_ADMIN`이 없을 때 한 계정을 만드는 데만 사용한다.
- `naverMapsClientId`와 `naverMapsClientSecret`은 백엔드의 병원 경로·ETA 계산에 사용한다.
- Secret 변경 후 Spring Boot 컨테이너를 재시작한다.

### RDS와 IAM

- RDS는 MySQL 8.4, 저장 암호화, 자동 백업 7일을 사용한다.
- 애플리케이션 계정은 `ersync_app`이며 `REQUIRE SSL`을 적용한다.
- JDBC는 `sslMode=VERIFY_IDENTITY`를 사용한다.
- DB 변경은 Flyway migration으로 관리한다.
- GitHub Actions Role은 ECR push와 지정 EC2 SSM 배포만 허용한다.
- EC2 Role은 ECR pull, 백엔드 Secret 조회와 SSM 접속만 허용한다.
- GitHub Actions는 AWS Access Key 대신 OIDC 임시 권한을 사용한다.

## 4. 프론트 웹 운영

### 배포 단위

프론트 저장소 `Hansung-ERsync/ersync-front-web`의 현재 구조를 유지한다.

| 웹 | 앱 경로 | Worker 이름 | 기본 접속 주소 |
|---|---|---|---|
| 병원 | `apps/hospital-web` | `ersync-hospital-web-dev` | `https://ersync-hospital-web-dev.<subdomain>.workers.dev` |
| 슈퍼 관리자 | `apps/super-admin-web` | `ersync-super-admin-web-dev` | `https://ersync-super-admin-web-dev.<subdomain>.workers.dev` |

두 앱은 하나의 저장소에 있지만 별도 Worker로 배포한다. Flutter 구급대원 앱은
이 프론트 웹 배포 대상에 포함하지 않는다.

### 요청 흐름

```text
브라우저가 Worker의 /api/* 호출
→ 현재 Next.js API Route 실행
→ ERSYNC_API_BASE_URL의 Spring Boot API 호출
→ Worker가 HttpOnly 인증 쿠키와 응답 처리
```

- 백엔드 주소는 Worker 일반 변수 `ERSYNC_API_BASE_URL`로 관리한다.
- dev 값은 `http://13.124.194.249`이다.
- 네이버 지도 자격정보는 병원 Worker Secret으로 관리한다.
- 인증 토큰은 브라우저 JavaScript에 노출하지 않고 현재 HttpOnly 쿠키 구조를 유지한다.
- 배포 웹은 브라우저에서 Spring Boot를 직접 호출하지 않는다.

### Cloudflare 변수

| Worker | 구분 | 키 | dev 값 또는 관리 방법 |
|---|---|---|---|
| 공통 | 일반 변수 | `ERSYNC_API_BASE_URL` | `http://13.124.194.249` |
| 병원 | Secret | `ERSYNC_NAVER_MAPS_CLIENT_ID` | Cloudflare에 직접 등록 |
| 병원 | Secret | `ERSYNC_NAVER_MAPS_CLIENT_SECRET` | Cloudflare에 직접 등록 |

일반 변수는 `wrangler.jsonc`를 기준으로 관리한다. Secret 값은 파일과 GitHub
로그에 기록하지 않는다. Wrangler 배포는 기존 Worker Secret을 삭제하지 않는다.
병원 Worker의 Naver Secret은 웹 주소 검색용이다. AWS Secrets Manager의 백엔드
경로·ETA 계산용 Naver 키와 관리 위치가 다르며, 같은 값이라고 가정하지 않는다.

### CORS

배포 웹 Origin을 Spring Boot CORS 목록에 추가하지 않는다. 브라우저는 같은
Origin의 Worker API Route를 호출하고, Worker가 서버 간 요청으로 Spring Boot를
호출하기 때문이다.

기존 localhost CORS 설정은 로컬 개발 편의를 위해 유지한다. 브라우저가 향후
Spring Boot를 직접 호출하도록 구조를 바꾸는 경우에만 CORS 정책을 다시 검토한다.

### 프론트 CI/CD

```text
PR
→ 두 앱 npm ci
→ 빌드와 테스트

main 병합
→ 두 앱 빌드와 테스트
→ 병원 Worker 배포
→ 슈퍼 관리자 Worker 배포
→ 두 workers.dev 주소 확인
```

- 기능 브랜치와 PR은 고정 dev Worker에 배포하지 않는다.
- 배포 인증은 `CLOUDFLARE_API_TOKEN`과 `CLOUDFLARE_ACCOUNT_ID`를 사용한다.
- 앱 소스, API Route와 인증 구조를 정적 웹에 맞게 변경하지 않는다.
- 프론트 저장소의 상세 인계 문서는 `docs/devops-front-handoff.md`를 기준으로 한다.

## 5. 최초 설정 순서

1. Cloudflare 계정을 만들고 `workers.dev` 계정 서브도메인을 활성화한다.
2. Worker 편집 권한을 가진 Cloudflare API Token을 발급한다.
3. 프론트 GitHub 저장소에 `CLOUDFLARE_API_TOKEN` Secret을 등록한다.
4. 프론트 GitHub 저장소에 `CLOUDFLARE_ACCOUNT_ID` Variable을 등록한다.
5. 두 앱의 `wrangler.jsonc`와 GitHub Actions workflow를 추가한다.
6. 첫 배포 후 병원 Worker에 네이버 지도 Secret 두 개를 등록한다.
7. 프론트 `main` workflow를 다시 실행한다.
8. 두 HTTPS 주소에서 로그인과 백엔드 API 연동을 확인한다.

## 6. 현재 상태

### 완료

- [x] ECR `ersync-api`
- [x] GitHub OIDC와 백엔드 dev 배포 Role
- [x] EC2 Docker, Nginx, Systems Manager와 ECR pull 권한
- [x] private RDS MySQL 8.4와 `ersync_app`
- [x] `ersync/dev/backend` Secret과 EC2 조회 권한
- [x] `main` 백엔드 자동 배포와 readiness 확인
- [x] readiness 실패 시 이전 컨테이너 복구
- [x] Git SHA 기반 이미지 태그와 버전 API
- [x] 프론트 두 앱의 vinext 빌드 확인
- [x] 두 앱의 Worker 배포 설정 생성 가능 여부 확인

### 남은 작업

- [ ] Cloudflare 계정과 `workers.dev` 서브도메인 활성화
- [ ] Cloudflare API Token과 GitHub 설정 등록
- [ ] 프론트 저장소 Worker 설정과 CI/CD 반영
- [ ] 병원 Worker 네이버 지도 Secret 등록
- [ ] 두 Worker 실제 배포와 로그인 검증
- [ ] 의도적인 백엔드 readiness 실패 복구 훈련
- [ ] CloudWatch 애플리케이션 로그 수집
- [ ] 실제 데이터 사용 전 백엔드 HTTPS 적용

## 7. 확인 주소

백엔드:

- 서버 상태: `http://13.124.194.249/actuator/health/readiness`
- 배포 버전: `http://13.124.194.249/api/system/version`

프론트 배포 후:

- 병원 웹: `https://ersync-hospital-web-dev.<subdomain>.workers.dev`
- 슈퍼 관리자 웹: `https://ersync-super-admin-web-dev.<subdomain>.workers.dev`

`<subdomain>`은 Cloudflare 계정에서 확인한 실제 Workers 서브도메인으로 바꾼다.

## 8. 참고 자료

- [Cloudflare Workers](https://developers.cloudflare.com/workers/)
- [Wrangler 설정](https://developers.cloudflare.com/workers/wrangler/configuration/)
- [Worker 환경변수](https://developers.cloudflare.com/workers/configuration/environment-variables/)
- [Worker Secret](https://developers.cloudflare.com/workers/configuration/secrets/)
- [Amazon ECR 이미지 태그 불변](https://docs.aws.amazon.com/AmazonECR/latest/userguide/image-tag-mutability.html)
- [GitHub Actions OIDC](https://docs.github.com/en/actions/concepts/security/openid-connect)
- [AWS Systems Manager Run Command](https://docs.aws.amazon.com/systems-manager/latest/userguide/run-command.html)
- [AWS Secrets Manager](https://docs.aws.amazon.com/secretsmanager/latest/userguide/intro.html)
