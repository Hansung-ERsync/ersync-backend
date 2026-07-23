# ERSync 문서 안내

이 디렉터리는 기존 `custom/` 문서를 옮겨 정리한 프로젝트 문서의 기준 위치입니다.

## 프로젝트

ERSync는 구급대원이 응급환자 정보를 프로토콜에 따라 입력하고 주변 응급실에 전달하는 시스템입니다. 병원은 수락 또는 거절할 수 있으며, 구급대원은 수락한 병원 중 이송 목적지를 선택합니다.

현재 백엔드는 공통 오류, 구조화 로그, 역할 기반 접근 제어 골격, health check, Docker와 ECR 배포 기반을 제공합니다. 인증과 이송 도메인 기능은 아직 구현 전입니다.

## 문서 구조

| 경로 | 내용 |
|---|---|
| `requirements/` | 사람이 검토하는 제품 요구사항 |
| `ai/` | AI 작업자가 사용하는 공통·백엔드·프론트엔드 컨텍스트 |
| `development/` | 개발 절차, 코드 컨벤션, 오류 코드와 현재 기반 |
| `features/` | 팀이 승인한 기능별 명세 |
| `operations/` | Docker, ECR, AWS와 배포 운영 문서 |
| `templates/` | 기능 명세 작성 템플릿 |

## 사람이 먼저 읽을 문서

1. [제품 요구사항](requirements/product-requirements.md)
2. [개발 절차](development/workflow.md)
3. [코드 컨벤션](development/conventions.md)
4. [오류 코드 규칙](development/error-codes.md)
5. [DevOps 가이드](operations/devops-guide.md)

## AI 작업자 읽기 순서

모든 작업:

1. [프로젝트 공통 컨텍스트](ai/project-context.md)
2. [제품 요구사항](requirements/product-requirements.md)
3. [개발 절차](development/workflow.md)
4. [코드 컨벤션](development/conventions.md)

백엔드 작업:

1. [백엔드 컨텍스트](ai/backend-context.md)
2. [백엔드 기반 컨텍스트](development/foundation-context.md)
3. [오류 코드 규칙](development/error-codes.md)
4. 해당 [기능별 명세](features/README.md)

프론트엔드 작업:

1. [프론트엔드 컨텍스트](ai/frontend-context.md)
2. 해당 [기능별 명세](features/README.md)

DevOps 작업:

1. [DevOps 가이드](operations/devops-guide.md)
2. [백엔드 기반 컨텍스트](development/foundation-context.md)

## 문서 관리 원칙

- 정책이 바뀌면 구현보다 문서를 먼저 수정한다.
- 미확정 정책은 AI가 임의로 결정하지 않는다.
- 기능 문서는 `DRAFT → REVIEW → APPROVED → IMPLEMENTED` 순서로 관리한다.
- 실제 환자정보, 비밀번호, 토큰, 가입 코드와 Secret을 문서에 기록하지 않는다.
- 루트 `README.md`에는 시작 방법과 이 문서의 링크만 유지한다.
