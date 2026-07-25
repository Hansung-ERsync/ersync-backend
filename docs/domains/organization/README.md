# Organization 도메인

## 책임

ERSync를 사용하는 병원과 구급대 조직의 기준 정보를 관리합니다.

## 포함

- 조직 식별자, 이름, 유형과 활성 상태
- `HOSPITAL`, `EMS_UNIT` 조직 구분
- 계정과 조직의 소속 관계

## 제외

- 병원 응급실 주소와 운영 상태
- 가입 코드 발급과 사용
- 사용자 로그인과 토큰

## 핵심 계약

- 일반 계정은 하나의 조직에 소속됩니다.
- `SUPER_ADMIN`의 조직 소속 예외는 인증 정책을 따릅니다.
- 조직 유형은 기능별 권한과 데이터 접근 범위의 기준입니다.
- 조직 상세 정책은 제품 요구사항과 승인된 기능 명세에서 확정합니다.

## 관련 문서

- [프로젝트 공통 컨텍스트](../../ai/project-context.md)
- [백엔드 컨텍스트](../../ai/backend-context.md)
- [Hospital 도메인](../hospital/README.md)
- [Invitation 도메인](../invitation/README.md)
