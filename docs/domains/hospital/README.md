# Hospital 도메인

## 책임

응급실 후보 검색과 연락에 필요한 병원 프로필과 수신 상태를 관리합니다.

## 포함

- 응급실 주소, 검증된 좌표와 연락처
- 응급실 운영·수신 상태
- 병원 조직과 병원 프로필 연결

## 제외

- 이송 요청 검색과 병원 응답 상태
- 환자 임상정보 작성과 수정
- 구급차 위치·ETA 계산

## 핵심 계약

- 병원 프로필은 `HOSPITAL` 유형 조직에만 연결합니다.
- 새 병원의 수신 상태 기본값은 제품 요구사항을 따릅니다.
- 후보 검색에 사용할 위치는 확인된 병원 좌표입니다.
- 병원 내부 수용 가능성 판단은 시스템 밖의 병원 절차입니다.

## 관련 문서

- [제품 요구사항](../../requirements/product-requirements.md)
- [백엔드 컨텍스트](../../ai/backend-context.md)
- [Organization 도메인](../organization/README.md)
- [Transport 도메인](../transport/README.md)
