# ERSync 프론트 웹 배포 인계서

- 대상 저장소: `Hansung-ERsync/ersync-front-web`
- 대상: 프론트 개발자와 AI 에이전트
- 범위: 병원 웹과 슈퍼 관리자 웹의 Cloudflare 자동 배포
- 제외: 프론트 기능 변경, Flutter 앱, Spring Boot·RDS 운영

## 1. 목표

현재 프론트 구조를 변경하지 않고 두 웹을 Cloudflare Workers에 독립적으로
배포한다.

```text
프론트 PR
→ 두 앱 설치·빌드·테스트

프론트 main 병합
→ 두 앱 설치·빌드·테스트
→ 병원 Worker 배포
→ 슈퍼 관리자 Worker 배포
→ 각 Worker가 AWS EC2 백엔드 API 호출
```

| 앱 | 현재 경로 | Worker 이름 | 배포 주소 형식 |
|---|---|---|---|
| 병원 웹 | `apps/hospital-web` | `ersync-hospital-web-dev` | `https://ersync-hospital-web-dev.<subdomain>.workers.dev` |
| 슈퍼 관리자 웹 | `apps/super-admin-web` | `ersync-super-admin-web-dev` | `https://ersync-super-admin-web-dev.<subdomain>.workers.dev` |

`<subdomain>`은 Cloudflare 계정의 Workers 서브도메인이다. 별도 도메인을 구매하지
않는다.

## 2. 현재 구조와 유지 계약

현재 두 앱은 `vinext`, Cloudflare Vite Plugin과 `worker/index.ts`를 사용한다.
브라우저는 백엔드를 직접 호출하지 않고 같은 Origin의 Next.js API Route를
호출한다.

```text
브라우저
→ Worker의 /api/*
→ Next.js API Route
→ ERSYNC_API_BASE_URL의 Spring Boot API
```

다음 계약을 변경하지 않는다.

- `apps/hospital-web`, `apps/super-admin-web` 분리 구조
- Next.js API Route와 `worker/index.ts`
- 병원 쿠키 `ersync_hospital_access`, `ersync_hospital_refresh`
- 관리자 쿠키 `ersync_admin_access`, `ersync_admin_refresh`
- HttpOnly·Secure 인증 쿠키 처리
- 병원 웹의 `/api/realtime/events` SSE 프록시
- 병원 웹의 `/api/geocode` 서버 프록시

다음 방식으로 전환하지 않는다.

- S3 정적 웹 배포
- 브라우저에서 Spring Boot API 직접 호출
- Access·Refresh Token을 localStorage나 일반 JavaScript 상태에 저장
- 병원 웹과 관리자 웹을 하나의 Worker로 통합
- Flutter 앱을 웹 배포 workflow에 포함

## 3. 프론트 저장소 작업

배포 담당자는 애플리케이션 기능 코드가 아니라 다음 배포 파일을 추가한다.

```text
.github/workflows/frontend-ci.yml
.github/workflows/frontend-deploy-dev.yml
apps/hospital-web/wrangler.jsonc
apps/super-admin-web/wrangler.jsonc
```

### 병원 Worker

`apps/hospital-web`에서 기본 설정을 생성한다.

```bash
npm exec -- vinext deploy --dry-run --name ersync-hospital-web-dev
```

생성된 `wrangler.jsonc`에 다음 계약을 적용한다.

```jsonc
{
  "$schema": "node_modules/wrangler/config-schema.json",
  "name": "ersync-hospital-web-dev",
  "compatibility_date": "2026-08-09",
  "compatibility_flags": ["nodejs_compat"],
  "main": "./worker/index.ts",
  "assets": {
    "directory": "dist/client",
    "not_found_handling": "none",
    "binding": "ASSETS"
  },
  "images": {
    "binding": "IMAGES"
  },
  "vars": {
    "ERSYNC_API_BASE_URL": "http://13.124.194.249"
  }
}
```

### 슈퍼 관리자 Worker

`apps/super-admin-web`에서 기본 설정을 생성한다.

```bash
npm exec -- vinext deploy --dry-run --name ersync-super-admin-web-dev
```

생성된 파일은 병원 설정과 동일한 구조를 유지하고 다음 값만 바꾼다.

```jsonc
{
  "name": "ersync-super-admin-web-dev",
  "vars": {
    "ERSYNC_API_BASE_URL": "http://13.124.194.249"
  }
}
```

실제 관리자 파일에도 `main`, `assets`, `images`, `compatibility_date`와
`compatibility_flags`를 포함한다.

## 4. Cloudflare 최초 설정

Cloudflare 계정 소유자가 한 번 수행한다.

1. Cloudflare Workers & Pages에서 `workers.dev` 서브도메인을 활성화한다.
2. Worker 편집 권한이 있는 API Token을 발급한다.
3. Cloudflare Account ID를 확인한다.
4. GitHub 설정을 등록하고 첫 `main` 배포로 두 Worker를 생성한다.
5. 병원 Worker의 Settings > Variables and Secrets에 지도 Secret을 등록한다.
6. 프론트 `main` 배포 workflow를 다시 실행한다.

병원 Worker에만 다음 값을 `Secret` 유형으로 등록한다.

```text
ERSYNC_NAVER_MAPS_CLIENT_ID
ERSYNC_NAVER_MAPS_CLIENT_SECRET
```

- 두 값은 병원 웹 `/api/geocode`에서 사용한다.
- `.env`, `wrangler.jsonc`, PR과 GitHub 로그에 실제 값을 기록하지 않는다.
- AWS Secrets Manager의 백엔드 Naver 키와 같은 값이라고 가정하지 않는다.
- Wrangler 코드 배포는 기존 Worker Secret을 삭제하지 않는다.

## 5. GitHub 설정

프론트 GitHub 저장소의 Settings > Secrets and variables > Actions에 등록한다.

| 구분 | 이름 | 값 |
|---|---|---|
| Repository secret | `CLOUDFLARE_API_TOKEN` | Worker 배포용 API Token |
| Repository variable | `CLOUDFLARE_ACCOUNT_ID` | Cloudflare Account ID |

AWS Access Key, RDS 정보와 백엔드 JWT Secret은 프론트 저장소에 등록하지 않는다.

## 6. GitHub Actions 계약

### PR 검사

`frontend-ci.yml`은 `main` 대상 PR에서만 실행한다.

```text
checkout
→ Node.js 22.13 이상 설정
→ npm run install:all
→ npm test
```

- 두 앱 중 하나라도 실패하면 check를 실패 처리한다.
- ECR, EC2와 Cloudflare에 배포하지 않는다.

### dev 배포

`frontend-deploy-dev.yml`은 `main` push와 수동 실행에서만 동작한다.

```text
두 앱 설치·테스트
→ 병원 앱 디렉터리에서 vinext deploy
→ 관리자 앱 디렉터리에서 vinext deploy
```

각 앱 디렉터리에서 실행할 명령은 다음과 같다.

```bash
npm exec -- vinext deploy --name ersync-hospital-web-dev
npm exec -- vinext deploy --name ersync-super-admin-web-dev
```

배포 job에는 다음 값을 전달한다.

```text
CLOUDFLARE_API_TOKEN=${{ secrets.CLOUDFLARE_API_TOKEN }}
CLOUDFLARE_ACCOUNT_ID=${{ vars.CLOUDFLARE_ACCOUNT_ID }}
```

기능 브랜치와 PR은 고정 dev Worker에 배포하지 않는다.

## 7. API, CORS와 보안

배포 웹의 실제 호출 흐름은 다음과 같다.

```text
브라우저
→ https://<worker>/api/*
→ Worker API Route
→ http://13.124.194.249/api/*
```

- 브라우저가 EC2를 직접 호출하지 않으므로 workers.dev 주소를 Spring Boot CORS 허용 목록에 추가하지 않는다.
- 현재 localhost CORS 설정은 로컬 개발을 위해 유지한다.
- 브라우저와 Worker 사이는 HTTPS지만 Worker와 EC2 사이는 현재 HTTP다.
- 실제 환자정보와 실제 기관 계정을 사용하지 않는다.
- 실제 사용 전 Spring Boot 백엔드에 HTTPS를 적용한다.

## 8. 검증 시나리오

### PR

- [ ] 병원 웹 빌드와 테스트 통과
- [ ] 슈퍼 관리자 웹 빌드와 테스트 통과
- [ ] Cloudflare 배포 job 미실행

### main 병합 후

- [ ] GitHub Actions 배포 workflow 성공
- [ ] 병원 workers.dev 주소에서 화면 표시
- [ ] 슈퍼 관리자 workers.dev 주소에서 화면 표시
- [ ] 병원 로그인·토큰 갱신·로그아웃 성공
- [ ] 슈퍼 관리자 로그인·토큰 갱신·로그아웃 성공
- [ ] 병원 `/api/realtime/events` 연결 성공
- [ ] 병원 주소 검색 성공
- [ ] 브라우저 JavaScript와 저장소에 인증 토큰이 노출되지 않음
- [ ] 두 웹이 `http://13.124.194.249` 백엔드와 정상 연동

한 Worker만 배포에 실패하면 성공한 다른 Worker를 임의로 되돌리지 않는다.
실패한 Worker의 GitHub Actions와 Cloudflare 배포 로그를 확인한 뒤 해당 앱만
재배포한다.

## 9. 완료 조건

- 두 Worker가 서로 다른 HTTPS 주소로 접근된다.
- PR에서는 검사만 하고 `main`에서만 자동 배포된다.
- 현재 API Route, SSE 프록시와 HttpOnly 쿠키 인증이 유지된다.
- 백엔드 주소는 일반 변수, 지도 자격정보는 병원 Worker Secret으로 관리된다.
- S3, 별도 도메인과 추가 프론트 EC2를 사용하지 않는다.
