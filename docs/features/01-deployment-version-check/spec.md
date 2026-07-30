# 배포 버전 확인 요구사항

```text
Feature: deployment-version-check
Domain: system
Owner: backend
Related Issue: NONE
Frontend Impact: NONE
Policy Decision Status: RESOLVED
```

> `main`에 병합된 커밋이 EC2에서 실행 중인지 브라우저나 curl로 확인하는
> 운영 편의 기능입니다.

## 목적

- 실행 중인 백엔드가 어느 `main` 커밋으로 빌드됐는지 API로 확인합니다.

## 시나리오

| # | 상황 | 기대 결과 |
|---:|---|---|
| 1 | `main` 커밋으로 Docker 이미지를 빌드함 | 버전 API가 해당 전체 Git SHA를 반환함 |
| 2 | 로컬에서 별도 SHA 없이 실행함 | 버전 API가 `local`을 반환함 |
| 3 | 인증 없이 버전 API를 호출함 | 민감정보 없이 HTTP 200으로 SHA만 반환함 |

## API

| 행위 | Method·Path | 요청·응답 핵심 |
|---|---|---|
| 실행 버전 조회 | `GET /api/system/version` | 요청 없음, `{"commitSha":"..."}` |

## 권한

| 역할 | 허용 작업 | 접근 범위 |
|---|---|---|
| 공개 | 버전 조회 | 커밋 SHA만 조회 |

## 오류

| 조건 | 오류 코드 | HTTP |
|---|---|---:|
| 별도 SHA가 주입되지 않음 | 오류 아님, `local` 반환 | 200 |

## 완료 조건

- [x] GitHub Actions의 `main` 커밋 SHA가 Docker 이미지에 주입됨
- [x] 인증 없이 버전 API를 호출해 주입된 SHA를 확인할 수 있음
- [x] 로컬·통합·Docker 검증을 통과함

## 기능 내 결정 사항

| 쟁점 | 최종 결정 | 결정 이유·영향 |
|---|---|---|
| 공개 범위 | 인증 없이 전체 Git SHA만 반환 | 배포 확인은 쉽게 하고 운영정보 노출은 제한 |
| 기준 SHA | `main` push workflow의 `github.sha` | squash merge 후 실제 배포된 커밋과 일치 |
| 자동 비교 | 이번 기능에서 제외 | 우선 사람이 버전 API로 확인하는 최소 범위 구현 |

## 확인 필요 사항

- 없음

## 진행 기준

`Policy Decision Status`가 `RESOLVED`이고 결정 내용을 반영했으므로
`implementation.md` 계획에 따라 구현했습니다.
