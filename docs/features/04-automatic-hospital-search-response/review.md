# 자동 병원 탐색 및 병원 응답 구현 검수

```text
Feature: automatic-hospital-search-response
Implemented By: backend AI collaboration
Related PR: NONE
Frontend Impact: BOTH
Flutter Handoff: docs/handoffs/04-automatic-hospital-search-response/flutter-paramedic.md
React Handoff: docs/handoffs/04-automatic-hospital-search-response/react-hospital-admin.md
```

> `spec.md`의 확정 MVP 정책과 실제 코드·자동 테스트 결과를 기준으로 작성했습니다.
> 커밋·푸시·PR과 Dev 서버 배포는 수행하지 않았습니다.

## 구현 요약

- 이송 요청 생성 트랜잭션에 최초 병원 탐색 작업을 함께 저장하고, 커밋 뒤 DB
  scheduler가 10km부터 최소 3곳을 찾을 때까지 즉시 반경을 평가합니다.
- 후보는 활성 병원 조직·활성 병원 공용 계정·수신 `ON`을 모두 만족해야 하며,
  Haversine 직선거리로 판정합니다. 최대 반경은 100km입니다.
- 수락이 없으면 60초마다 10km 확대하고 같은 탐색 회차에서 아직 제안을 받지
  않은 병원에만 새 제안을 만듭니다.
- 최대 반경 무후보·전원 거절·마지막 응답 창의 무응답을 구분해
  `CANDIDATES_EXHAUSTED`와 `NO_RESPONSE`를 확정합니다.
- 병원은 자기 조직의 제안 목록·상세만 조회하고 멱등하게 수락·사유 거절할 수
  있습니다. 첫 수락 뒤 확대만 중단하며 기존 제안의 추가 수락은 허용합니다.
- 구급대원은 자기 요청의 현재 회차·반경·후보 부족·병원별 응답을 조회하고,
  후보 소진 뒤 같은 요청에 새 탐색 회차를 멱등하게 만들 수 있습니다.
- 네이버 Directions 5는 후보 선정과 분리된 작업자로 호출합니다. 성공하면 도로
  거리·ETA를 저장하고, 실패해도 병원 제안은 유지한 채 ETA만 재시도하거나
  `UNAVAILABLE`로 종료합니다.
- 제안·응답·후보 소진·재전송·ETA 변경은 같은 트랜잭션에 최소 outbox 이벤트를
  저장하며, 인증 SSE는 공개 ID 기반 갱신 신호만 보냅니다.
- 검색 확대·병원 응답은 이송 요청 → 탐색 회차 → 병원 제안 순서로 잠가 타이머와
  수락이 경합해도 상태가 섞이지 않게 했습니다.

## 요구사항 체크

| 요구사항 | 결과 | 근거 |
|---|---|---|
| 요청 생성 뒤 자동 탐색 | PASS | 요청 생성 시 회차 1과 즉시 실행 시각 저장, scheduler 처리 통합 테스트 |
| 10~100km·최소 3곳 | PASS | 10km 1곳, 20km 2곳, 30km 4곳 시 30km 네 곳 제안 검증 |
| 후보 자격과 중복 방지 | PASS | 수신 OFF·비활성 조직 제외, 회차·병원 DB 고유 제약과 확대 테스트 |
| 60초 확대·재시작 복구 | PASS | DB `nextExpansionAt` 기반 10km 확대와 예정 시각 경과 작업 처리 |
| 무후보·거절·무응답 소진 | PASS | 100km 무후보, 마지막 거절, 최종 `NO_RESPONSE` 통합 테스트 |
| 병원 조직 격리 | PASS | 자기 조직 목록·상세 성공, 다른 병원 상세 `TRANSPORT_005` |
| 병원 수락·거절·멱등성 | PASS | 수락 재시도, OTHER 검증, 마지막 거절과 응답 이벤트 테스트 |
| 복수 수락·타이머 경합 | PASS | 복수 병원 수락 및 최종 타이머/수락 동시 실행 테스트 |
| 구급대원 현황·재전송 | PASS | 소유 현황 조회, 소진 사유·연락처, 재전송 201/멱등 재시도 200 |
| 연락처·위치 노출 제한 | PASS | 병원 종료 상세 연락처 마스킹, DTO에 출발 좌표 미포함 |
| 네이버 ETA 실패 격리 | PASS | 응답 변환 단위 테스트, 키 없는 로컬에서 제안 유지·ETA만 UNAVAILABLE |
| 실시간 최소 이벤트 | PASS | outbox 발행 완료, 대상별 broker와 민감정보 없는 이벤트 DTO 테스트 |
| MySQL 8.4 호환 | PASS | V1→V4 migration 4개, CHECK·FK·JPA validate·readiness 테스트 |
| 기존 기능 회귀 | PASS | 가입·인증·병원 수신·환자 평가·요청 생성을 포함한 전체 77개 테스트 |

## 변경 API·DB

| 구분 | 변경 내용 | 호환성 |
|---|---|---|
| API | `GET /api/v1/hospitals/me/offers` | 신규, 병원 전용 |
| API | `GET /api/v1/hospitals/me/offers/{offerId}` | 신규, 자기 병원 제안만 허용 |
| API | `POST /api/v1/hospitals/me/offers/{offerId}/accept` | 신규, `Idempotency-Key` 필수 |
| API | `POST /api/v1/hospitals/me/offers/{offerId}/reject` | 신규, `Idempotency-Key`·사유 필수 |
| API | `GET /api/v1/transport-requests/{requestId}/hospital-search` | 신규, 요청 소유 구급대원 전용 |
| API | `POST /api/v1/transport-requests/{requestId}/dispatch-attempts` | 신규, 후보 소진 후 재전송 |
| API | `GET /api/v1/realtime/events` | 신규, Bearer 인증 SSE |
| 기존 API | `POST /api/v1/transport-requests` | 응답 계약 유지; 저장 뒤 자동 탐색 작업 추가 |
| 기존 API | 조직 응답에 `status` 추가 | 선택 필드 추가로 기존 클라이언트와 호환 |
| DB | `organizations.status` 추가 | 기존 행은 `ACTIVE` 기본값 |
| DB | 탐색 회차·반경·제안·제안 이벤트·outbox 5개 테이블 추가 | 신규 V4 migration, 기존 migration 미수정 |
| 배포 | 네이버 Client ID·Secret 런타임 설정 변환 | `ersync/dev/backend`의 키 2개 필요 |

## Spec 이후 변경

- 제품 정책 변경: 없음
- 구현 중 확정한 기술 동작:
  - 생성 API가 DB 작업자를 기다리지 않도록 최초 회차와 즉시 실행 시각만 요청
    트랜잭션에 저장하고, 실제 후보 계산은 커밋 뒤 수행합니다.
  - 네이버 호출과 SSE 발행은 DB 트랜잭션 밖에서 실행하고 짧은 DB lease로 중복
    작업을 줄입니다.
  - SSE 연결 시간은 기본 Access Token 15분보다 짧은 14분이며 재연결 뒤 권위
    REST API를 조회합니다.
  - 종료된 병원 상세의 구급대원 연락처는 마지막 네 자리만 남겨 반환합니다.

## 범위 확인

- spec 밖 추가 작업: 조직 활성 상태를 실제 요청 생성·병원 수신 변경·SSE 연결
  권한에도 적용해 비활성 조직이 새 동작을 시작하지 못하게 했습니다.
- 의도적으로 제외한 작업:
  - 수락 병원의 목적지 선택·철회
  - 이송 중 위치·임상정보·ETA 갱신과 인계 완료
  - 병상·전문의·장비 외부 데이터 연동
  - 프론트 지도 SDK 키와 지도 화면

## 테스트 결과

| 테스트 | 결과 | 근거 |
|---|---|---|
| `./gradlew clean check` | PASS | 컴파일·Javadoc·Spotless·전체 83개 테스트 성공 |
| Testcontainers MySQL 8.4 | PASS | V1→V4 적용, JPA validate, readiness `UP`, 실제 이송 요청 저장 |
| 격리 로컬 MySQL 실행 | PASS | 임시 스키마 V1→V4, 포트 18080 readiness `{"status":"UP"}`, `commitSha: local` 확인 후 정리 |
| 후보·시간·응답 API | PASS | 탐색 서비스·API 통합 테스트 7개와 거리 단위 테스트 2개 |
| 동시성 | PASS | 최종 타이머와 병원 수락 동시 실행에서 일관된 단일 상태 확인 |
| 네이버 adapter | PASS | 테스트 HTTP 서버로 두 인증 헤더·좌표 순서·m·ms→초 변환과 연결 실패·429·5xx·인증 오류·잘못된 JSON 분류 확인 |
| 로컬 키 없음 | PASS | 병원 제안 유지, ETA만 `UNAVAILABLE`, 양쪽 ETA outbox 생성 |
| outbox·SSE | PASS | 미접속 상태 발행 완료와 구급대원 계정 대상 구독 확인 |

## 프론트 핸드오프

| 대상 | 문서 | 실제 코드와 일치 |
|---|---|---|
| Flutter 구급대원 앱 | `docs/handoffs/04-automatic-hospital-search-response/flutter-paramedic.md` | YES |
| React 병원·관리자 웹 | `docs/handoffs/04-automatic-hospital-search-response/react-hospital-admin.md` | YES |

## 배포 전 확인

| 항목 | 상태 | 확인 방법 |
|---|---|---|
| 네이버 Maps 애플리케이션·Directions 5 | 사용자 준비 완료 전달받음 | Dev 배포 뒤 ETA `AVAILABLE` smoke test |
| `naverMapsClientId`·`naverMapsClientSecret` | 사용자 측 Secret 등록 전달받음 | 배포 스크립트 schema 검사와 readiness |
| 실제 Nginx SSE 지연·heartbeat | NOT_RUN | main 배포 뒤 공개 Base URL에서 확인 |
| Dev 실제 ETA | NOT_RUN | 테스트 좌표·가짜 환자정보로 확인 |

## 남은 리스크

| 리스크 | 영향 | 대응 |
|---|---|---|
| 네이버 Dev 키·권한·쿼터가 실제로 맞지 않을 수 있음 | ETA가 `UNAVAILABLE`이지만 요청 전달·응답은 계속됨 | 배포 직후 테스트 좌표 smoke test와 네이버 콘솔 사용량 확인 |
| 현재 SSE broker는 단일 인스턴스 메모리 기반 | 다중 EC2에서 다른 인스턴스 연결에 즉시 신호가 안 갈 수 있음 | 현재 단일 Dev 인스턴스에서 사용; 확장 시 공유 broker 도입 |
| 실제 Nginx의 buffering·idle timeout 미검증 | 실시간 신호가 3초 목표를 넘거나 연결 종료 가능 | main 배포 뒤 공개 주소에서 이벤트 지연·14분 재연결 확인 |
