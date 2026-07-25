# 배포 버전 확인 구현 계획

```text
Feature: deployment-version-check
Author: Codex
Frontend Contract: NONE
```

> 공개 버전 API와 Docker 빌드 SHA 주입을 한 PR에서 완성합니다.

## 구현 Step

| Step | 작업 | 완료 기준 |
|---:|---|---|
| 1 | 공통 설정에 커밋 SHA 기본값 추가 | 미주입 시 `local` 사용 |
| 2 | 시스템 버전 API 추가 | `commitSha` JSON 응답 |
| 3 | 버전 API 공개 권한 추가 | 미인증 요청 HTTP 200 |
| 4 | Docker build arg를 런타임 환경변수로 전달 | 이미지에 Git SHA 저장 |
| 5 | CI·배포 workflow에서 Git SHA 주입 | `github.sha`가 빌드에 전달됨 |
| 6 | 통합·Docker 테스트와 문서 검증 | 계획한 검사가 모두 통과함 |

## 변경 패키지

| 패키지·파일 | 변경 내용 |
|---|---|
| `system/ServerStatusController` | 버전 조회 API |
| `global/security/SecurityConfig` | 공개 경로 |
| `application.yaml` | 커밋 SHA 설정 |
| `Dockerfile` | `GIT_SHA` build arg |
| GitHub Actions | Docker 빌드 인자 전달 |
| 기반 통합 테스트 | 공개 응답과 주입값 검증 |

## DB 변경

- 없음

## 테스트 목록

- [x] 버전 API 공개 접근과 응답 통합 테스트
- [x] 환경변수 SHA를 사용한 local 실행 확인
- [x] Git SHA를 주입한 Docker 이미지 확인
- [x] `./gradlew clean check`

## 프론트엔드 전달

- 영향: `NONE`
- 계약: `NONE`
- 사용 대상: 개발자와 운영자

## 건드리면 안 되는 계약

- 기존 `/api/system/health` 응답
- 공통 인증 오류와 추적 ID
- 기존 readiness와 배포 rollback 동작

## 리스크

| 리스크 | 대응 |
|---|---|
| 배포 빌드에서 SHA를 누락함 | 기본값과 테스트로 확인하고 Actions에서 명시적으로 전달 |
| 불필요한 운영정보 노출 | 응답을 Git SHA 하나로 제한 |
| PR 이미지 SHA와 main 배포 SHA 혼동 | 배포 workflow의 `github.sha`만 운영 기준으로 사용 |
