# Platform 도메인

## 책임

모든 도메인이 공유하는 보안, 오류, 로그, 운영과 배포 기반을 제공합니다.

## 포함

- 공통 Security와 역할 검사 기반
- 오류 응답, 추적 ID와 구조화 로그
- health check, DB migration과 런타임 설정
- CI, Docker와 AWS dev 배포 기반

## 제외

- 기능별 업무 상태와 정책
- 임상 프로토콜과 병원 검색 규칙
- 기능 도메인 데이터의 소유권

## 핵심 계약

- 공통 오류 형식과 추적 ID 계약을 유지합니다.
- 로그에 실제 환자정보, 인증정보, Secret과 정확한 GPS를 남기지 않습니다.
- 공통 기반은 기능별 권한을 임의로 결정하지 않습니다.
- 운영 설정과 비밀값은 저장소에 기록하지 않습니다.

## 관련 문서

- [개발 컨벤션](../../development/conventions.md)
- [오류 코드와 로그](../../development/error-codes.md)
- [백엔드 기반 컨텍스트](../../development/foundation-context.md)
- [DevOps 가이드](../../operations/devops-guide.md)
