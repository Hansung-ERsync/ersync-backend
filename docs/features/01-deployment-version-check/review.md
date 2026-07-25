# 배포 버전 확인 구현 검수

```text
Feature: deployment-version-check
Implemented By: Codex
Related PR:
Frontend Impact: NONE
Frontend Contract: NONE
```

## 구현 요약

- `GET /api/system/version` 공개 API를 추가했습니다.
- GitHub Actions가 Docker 빌드에 `github.sha`를 전달합니다.
- Docker 이미지가 SHA를 `ERSYNC_GIT_SHA`로 Spring Boot에 전달합니다.
- SHA 미주입 로컬 실행은 `local`을 기본값으로 사용합니다.

## 요구사항 체크

| 요구사항 | 결과 | 근거 |
|---|---|---|
| Git SHA 이미지 주입 | PASS | build arg 이미지 환경변수 확인 |
| 공개 버전 API | PASS | 미인증 HTTP 200 통합·실행 검증 |
| 로컬 기본값 | PASS | `ERSYNC_GIT_SHA` 기본값 `local` |

## 변경 API·DB

| 구분 | 변경 내용 | 호환성 |
|---|---|---|
| API | `GET /api/system/version` | 기존 API 변경 없는 추가 API |
| DB | 없음 | 영향 없음 |

## 프론트엔드 전달

| 영향 | 계약 |
|---|---|
| `NONE` | `NONE` |

## Spec 이후 정책 변경

- 없음

## 범위 확인

- spec 범위를 넘어 추가한 작업: 없음
- 의도적으로 제외한 후속 작업: 배포 스크립트의 SHA 자동 비교

## 테스트 결과

| 테스트 | 결과 | 근거 |
|---|---|---|
| `./gradlew clean check` | PASS | 12개 테스트, 실패·스킵 없음 |
| local 실행·readiness | PASS | 주입 SHA 응답, readiness `UP` |
| Docker SHA 주입 | PASS | 이미지와 실행 API에서 `docker-verification-sha` 확인 |

## 남은 리스크

| 리스크 | 영향 | 대응 |
|---|---|---|
| 배포 성공 후에도 사람이 SHA를 비교해야 함 | 확인 누락 가능 | 필요할 때 배포 스크립트 자동 비교 추가 |

## 다음 작업 추천

1. 필요할 때 배포 스크립트에 SHA 자동 비교를 추가합니다.
