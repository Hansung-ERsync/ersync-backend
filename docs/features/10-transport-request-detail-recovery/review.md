# 구급대원 이송 상세 복구 구현 검수

```text
Feature: transport-request-detail-recovery
Implemented By: AI-assisted backend
Related PR: NONE
Frontend Impact: FLUTTER_PARAMEDIC
Flutter Handoff: docs/handoffs/10-transport-request-detail-recovery/flutter-paramedic.md
React Handoff: NONE
```

> 2026-08-05 현재 작업 브랜치의 실제 코드와 로컬 검증 결과를 기준으로
> 작성했습니다. Flutter 앱 코드는 변경하지 않았습니다.

## 구현 요약

- `GET /api/v1/transport-requests/{requestId}`를 추가해 앱 재실행 뒤 진행 중
  자기 이송의 최초 환자·발생정보와 최신 임상 snapshot을 복구할 수 있게
  했습니다.
- JWT 계정·역할·조직의 현재 상태, 요청 소유권과 다섯 `ACTIVE` 상태를 서버에서
  검증합니다. 타인 소유·완료·취소 요청은 존재 여부를 구분하지 않고
  `TRANSPORT_001`로 처리합니다.
- 허용 필드 전용 응답 DTO를 사용해 회신 연락처·좌표·병원 탐색정보·내부 ID·
  토큰 등 새 화면 복구에 불필요한 정보를 제외했습니다.
- 기존 `ClinicalTimelineQueryService`의 임상 DTO 변환을
  `ClinicalSnapshotResponseMapper`로 추출해 새 상세와 기존 timeline의
  `latestSnapshot` JSON 의미가 같도록 했습니다.
- 읽기 전용 트랜잭션을 사용하며 이송 상태·`updatedAt`·audit·SSE outbox를
  변경하지 않습니다.
- 기존 테이블을 조회하므로 migration과 실행 설정은 추가하지 않았습니다.

## 요구사항 체크

| 요구사항 | 결과 | 근거 |
|---|---|---|
| 앱 재실행용 자기 이송 상세 조회 | PASS | 요청 생성 뒤 상세 GET 200과 환자·발생·임상 응답 통합 테스트 |
| 최초 환자·발생정보 복구 | PASS | 나이 상태·나이·성별·발생 유형·주증상·부증상·발생 시각 단언 |
| 최신 임상 snapshot 복구 | PASS | Pre-KTAS·의식·다섯 활력징후·처치·마지막 갱신 시각 단언 |
| 기존 timeline과 최신값 일치 | PASS | 두 API의 `latestSnapshot` JSON 동등성 검증 |
| 늦은 과거 임상 기록 처리 | PASS | 과거 맥박 원본은 timeline에 남고 상세는 최신 맥박·시각 유지 |
| 다섯 진행 상태 조회 | PASS | `SEARCHING`부터 `HANDOFF_REQUESTED`까지 각각 200과 상태 일치 |
| 완료·취소 상세 차단 | PASS | 두 상태 모두 `TRANSPORT_001` 404 |
| 역할·조직·소유권 검증 | PASS | 미인증·변조 토큰·병원·관리자·비활성 계정·비활성 조직·JWT 조직 불일치·타 계정 차단 |
| 민감정보 최소화 | PASS | 회신 연락처·좌표·내부 ID·비밀번호·토큰 필드 부재 단언 |
| 읽기 무변경 | PASS | GET 전후 요청 `updatedAt`, audit와 outbox 건수 불변 |
| 임상 갱신과 GET 경합 | PASS | 이전 또는 새 완전한 다섯 활력징후만 반환하고 후속 GET은 새 값에 수렴 |
| 취소와 GET 경합 | PASS | 활성 상세 또는 404만 허용하고 취소 commit 뒤 후속 GET은 404 |
| 상세·임상 갱신·취소 3중 경합 | PASS | 부분 snapshot·500 없이 활성 상세 또는 404, 최종 `CANCELLED`·후속 404 |
| 소유자·타 구급대원 동시 조회 | PASS | 소유자만 200, 타 계정은 `TRANSPORT_001`, 환자정보 누출 없음 |
| 동시 상세 조회 무부작용 | PASS | 10건 응답의 환자·발생·snapshot 일치, 상태·감사·outbox 불변 |
| 기존 API 호환 | PASS | 전체 자동 테스트 170개 통과 |
| Flutter 단독 연동 문서 | PASS | 실제 응답·enum·오류·복구 순서를 10번 핸드오프에 기록 |

## 변경 API·DB

| 구분 | 변경 내용 | 호환성 |
|---|---|---|
| API | `GET /api/v1/transport-requests/{requestId}` 추가 | 신규 읽기 API, 기존 계약 영향 없음 |
| API | 기존 `clinical-timeline` 공개 JSON 유지 | 내부 DTO mapper만 공통화 |
| API | 기존 목록·임상 갱신·탐색·위치·취소·인계 계약 유지 | 요청·응답·상태 의미 변경 없음 |
| DB | 없음 | V1~V8 스키마와 기존 데이터 유지 |

## Spec 이후 변경

- 제품 정책 변경: 없음
- 구현 중 추가된 설계 결정:
  - `CurrentPatientSnapshotRepository`의 기존 EntityGraph를 그대로 사용하고
    요청 소유자·조직 조회만 전용 Repository 조건으로 추가했습니다.
  - 손상 부위와 부증상 집합은 enum 이름 순으로 반환해 같은 상태의 응답 순서를
    안정화했습니다.
  - 토큰 발급 뒤 비활성화된 계정은 Controller 전에 공통 JWT 인증 계층에서
    거절되므로 외부 오류는 기존 계약과 같은 `401/AUTH_002`임을 테스트로
    확인하고 spec·implementation·핸드오프에 반영했습니다.

## 범위 확인

- spec 밖 추가 작업: 없음
- 의도적으로 제외한 작업:
  - 환자 기본정보·발생정보 수정
  - 완료·취소 요청의 환자 상세
  - 병원 탐색·ETA·위치와 전체 임상 이력을 한 응답에 통합
  - 병원 관계자·슈퍼 관리자용 상세 조회
  - Flutter 앱 코드 수정

## 테스트 결과

| 테스트 | 결과 | 근거 |
|---|---|---|
| 상세 API 집중 테스트 | PASS | 정상·최신값·권한·다섯 상태·종료 상태·불변식 누락 6개 통과 |
| 상세 동시성 테스트 | PASS | 임상 갱신, 취소, 3중 경합, 소유자·타인 동시 접근, 10건 조회 경합 5개 통과 |
| 기존 임상 timeline 회귀 | PASS | 공통 mapper 적용 뒤 기존 갱신·timeline 테스트 통과 |
| `./gradlew clean check` | PASS | 2026-08-05 전체 170개, 실패·건너뜀 0, 컴파일·Javadoc·Spotless 포함 |
| MySQL 8.4·Flyway | PASS | Testcontainers와 로컬 MySQL 8.4.11, 기존 migration 8개 검증, schema V8 최신 |
| local 실행·readiness | PASS | `./scripts/dev-start.sh`, `GET /actuator/health/readiness` → `{"status":"UP"}` |
| 로컬 종료·데이터 유지 | PASS | Spring Boot와 MySQL 컨테이너만 종료, Docker volume 삭제 없음 |

## 프론트 핸드오프

| 대상 | 문서 | 실제 코드와 일치 |
|---|---|---|
| Flutter 구급대원 앱 | `docs/handoffs/10-transport-request-detail-recovery/flutter-paramedic.md` | YES |
| React 병원·관리자 웹 | `NONE` | N/A |

Flutter에서 필요한 후속 연동은 다음과 같습니다.

- 로그인·토큰 복구 뒤 `ACTIVE` 목록의 요청 ID로 새 상세 GET 호출
- 환자 입력·이송 진행 화면에 `patient`, `incident`, `latestSnapshot` 연결
- `TRANSPORT_001`이면 현재 환자 화면을 유지하지 않고 `ACTIVE`·`RECENT` 재조회
- 병원 후보·목적지·ETA와 위치는 기존 전용 API에서 별도 복구
- SSE 재연결 전에 REST 조회로 누락된 상태 복구

## 남은 리스크

| 리스크 | 영향 | 대응 |
|---|---|---|
| Flutter 앱이 아직 새 상세 GET을 호출하지 않음 | 앱 프로세스 재생성 뒤 최초 환자 입력값을 서버에서 복구하지 못함 | 10번 핸드오프 기준으로 화면별 연동 후 Dev 통합 테스트 |
| Dev 서버 배포 전 | 공개 Base URL에서는 아직 새 API를 사용할 수 없음 | Ready PR merge 후 배포 SHA·readiness 확인 |
| Dev 서버가 HTTP | 실제 환자정보 전송에 부적합 | HTTPS 적용 전 가짜 환자·위치 데이터만 사용 |
