# ERSync 도메인 안내

도메인 문서는 각 영역의 책임과 경계를 설명합니다. 구체적인 기능 정책은 제품 요구사항과 `docs/features/`의 승인된 기능 명세에서 관리합니다.

| 도메인 | 책임 |
|---|---|
| [Auth](auth/README.md) | 로그인, 토큰과 인증 컨텍스트 |
| [Organization](organization/README.md) | 병원·구급대 조직과 소속 |
| [Invitation](invitation/README.md) | 조직·역할 기반 일회성 가입 코드 |
| [Hospital](hospital/README.md) | 응급실 프로필, 위치와 수신 상태 |
| [Transport](transport/README.md) | 이송 요청, 병원 검색·응답, 목적지와 취소 |
| [Clinical](clinical/README.md) | 환자 평가, 처치, 프로토콜과 임상 이력 |
| [Location](location/README.md) | 최신 위치, 위치 신선도, 거리와 ETA |
| [Handoff](handoff/README.md) | 인계 완료 요청과 병원 확인 |
| [Notification](notification/README.md) | 실시간 이벤트, 아웃박스와 재연결 |
| [Audit](audit/README.md) | 보안·업무 감사 이력 |
| [Platform](platform/README.md) | 공통 보안, 오류, 로그, 운영과 배포 기반 |

## 사용 원칙

- 기능은 주 담당 도메인 한 곳에만 둡니다.
- 여러 도메인에 걸친 기능은 복제하지 않고 관련 문서를 링크합니다.
- 도메인 문서에는 개별 API나 기능 상태를 중복 정의하지 않습니다.
- 도메인 경계가 바뀌면 관련 기능 명세와 AI 컨텍스트를 함께 검토합니다.
