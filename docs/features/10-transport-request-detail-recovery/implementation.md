# 구급대원 이송 상세 복구 구현 계획

```text
Feature: transport-request-detail-recovery
Author: AI-assisted backend
Handoff Targets: FLUTTER_PARAMEDIC
```

> `Policy Decision Status: NONE`인 `spec.md`, 현재 이송 목록·임상 timeline·병원
> 탐색·위치 복구 API와 Flutter의 진행 중 이송 화면 복구 요청을 기준으로
> 작성했습니다. 기존 DB와 공개 API를 변경하지 않고 구급대원 본인용 읽기
> API만 추가합니다.

## 설계 요약

- 선택한 방식:
  - 기존 `TransportRequestController`에
    `GET /api/v1/transport-requests/{requestId}`를 추가합니다.
  - 새 `TransportRequestDetailQueryService`가 JWT 계정·역할·조직의 현재 상태를
    검증하고, 인증 계정이 직접 생성한 `ACTIVE` 요청만 읽습니다.
  - 새 `TransportRequestDetailResponse`가 요청 상태·평가 프로토콜 버전·최초
    환자 기본정보·발생정보·최신 임상 snapshot·생성 시각·서버 시각만
    반환합니다.
  - `CurrentPatientSnapshotRepository.findByTransportRequestPublicId(...)`의 기존
    EntityGraph로 `PatientDemographics`, `IncidentAssessment`, 최신 Pre-KTAS·의식·
    활력징후와 현재 처치 목록을 읽습니다.
  - 기존 `ClinicalTimelineQueryService`의 최신 임상 DTO 변환을
    `ClinicalSnapshotResponseMapper`로 추출하고 새 상세 조회에서도 재사용합니다.
  - 기존 테이블과 관계를 그대로 조회하므로 Flyway migration과 설정 변경은
    추가하지 않습니다.
- 선택 이유:
  - `ACTIVE` 목록은 요청 ID·상태·병원명만 반환하여 앱이 나이·성별·주증상을
    복구할 수 없고, 기존 timeline은 최신 임상정보는 주지만 최초 환자·발생정보가
    없습니다. 두 계약 사이에 실제로 빠진 읽기 기능을 채웁니다.
  - 병원용 `HospitalOfferDetailResponse`를 재사용하지 않아 병원 제안 ID·회신
    연락처·거리·ETA 같은 병원 전용 정보가 구급대원 계약에 섞이지 않습니다.
  - 최신 임상 매핑을 공통화하면 새 상세와 기존 timeline이 같은 DB snapshot을
    서로 다른 JSON으로 반환하는 오류를 막을 수 있습니다.
  - 읽기 전용 API를 기존 명령 API와 분리하여 이송 상태·감사·SSE outbox를
    변경하지 않습니다.
- 검토한 대안과 제외 이유:
  - Flutter가 최초 요청 body를 기기 저장소에 계속 보관하는 방식은 재설치·캐시
    삭제·다른 기기 로그인에 복구할 수 없고 서버 확정 상태와 달라질 수 있어
    제외합니다.
  - 기존 `clinical-timeline` 응답에 최초 환자·발생정보를 추가하는 방식은 전체
    이력 조회와 단순 화면 복구 책임을 섞고 기존 응답 범위를 불필요하게 넓혀
    제외합니다.
  - 병원 제안 상세 API를 구급대원에게 허용하는 방식은 역할·리소스 식별자와
    개인정보 공개 범위가 다르므로 제외합니다.
  - 병원 탐색·위치·ETA까지 하나의 거대한 상세 응답으로 합치는 방식은 갱신
    주기와 권한이 다른 데이터를 중복하고 기존 복구 API를 약화하므로 제외합니다.
  - GET에 비관적 쓰기 잠금이나 `Idempotency-Key`를 사용하는 방식은 상태를
    변경하지 않는 조회에 불필요한 직렬화와 프론트 상태를 추가하므로 제외합니다.

## API 계약 계획

### `GET /api/v1/transport-requests/{requestId}`

- 인증·역할: Bearer Access Token, `PARAMEDIC`
- 요청 Path: 기존 이송 요청 공개 UUID
- Query·Body·`Idempotency-Key`: 없음
- 성공 HTTP: `200 OK`
- 트랜잭션: `@Transactional(readOnly = true)`
- 허용 상태:
  - `SEARCHING`
  - `CANDIDATES_EXHAUSTED`
  - `ACCEPTED_AVAILABLE`
  - `EN_ROUTE`
  - `HANDOFF_REQUESTED`
- 상태·감사·outbox 변경: 없음

성공 응답의 계획 형태는 다음과 같습니다.

```json
{
  "transportRequestId": "REQUEST_UUID",
  "status": "EN_ROUTE",
  "assessmentProtocolVersion": "ERSYNC_MVP_1.0",
  "patient": {
    "ageStatus": "ESTIMATED",
    "ageYears": 70,
    "sex": "MALE"
  },
  "incident": {
    "occurrenceType": "DISEASE",
    "occurrenceDetail": null,
    "injuryMechanism": null,
    "injurySites": [],
    "primarySymptom": "DYSPNEA",
    "primarySymptomDetail": null,
    "secondarySymptoms": ["FEVER_INFECTION"],
    "onsetTimeStatus": "EXACT",
    "onsetAt": "2026-08-05T01:00:00Z"
  },
  "latestSnapshot": {
    "preKtas": {
      "classificationStatus": "COMPLETED",
      "level": 2,
      "exceptionReason": null,
      "exceptionDetail": null,
      "assessedAt": "2026-08-05T01:03:00Z",
      "standardVersion": "DEV_UNCONFIRMED"
    },
    "consciousness": {
      "avpu": "V",
      "unassessableReason": null,
      "unassessableDetail": null,
      "observedAt": "2026-08-05T01:02:00Z"
    },
    "vitalSigns": {
      "measuredAt": "2026-08-05T01:04:00Z",
      "measurements": []
    },
    "treatments": [],
    "lastClinicalUpdateAt": "2026-08-05T01:04:05Z"
  },
  "createdAt": "2026-08-05T01:05:00Z",
  "serverNow": "2026-08-05T01:10:00Z"
}
```

### 응답 필드 출처

| 응답 영역 | 서버 출처 | 규칙 |
|---|---|---|
| `transportRequestId`, `status`, `assessmentProtocolVersion`, `createdAt` | `TransportRequest` | 내부 PK·멱등성 fingerprint·회신 연락처·출발 좌표 제외 |
| `patient` | `PatientDemographics` | `ageStatus`, 조건부 `ageYears`, `sex`만 반환 |
| `incident` | `IncidentAssessment` | 최초 발생·증상정보를 반환하고 직접 식별정보 없음 |
| `latestSnapshot.preKtas` | `CurrentPatientSnapshot.latestPreKtasAssessment` | 기존 timeline 최신 요약과 동일한 필드·enum 의미 |
| `latestSnapshot.consciousness` | `CurrentPatientSnapshot.latestConsciousnessAssessment` | AVPU와 조건부 평가 불가 사유·관찰 시각 |
| `latestSnapshot.vitalSigns` | `CurrentPatientSnapshot.latestVitalSignSet` | 다섯 측정 타입의 값 또는 상태·측정 시각 |
| `latestSnapshot.treatments` | `CurrentPatientSnapshot.currentTreatments` | 최초 `NONE` 제거 등 기존 현재 처치 규칙 유지 |
| `latestSnapshot.lastClinicalUpdateAt` | `CurrentPatientSnapshot` | 최신 임상 수신 시각, 과거 기록으로 감소하지 않음 |
| `serverNow` | 주입된 `Clock` | 단말이 응답 시각과 데이터 시각을 비교할 기준 |

### 배열·null·시간 계약

- enum은 현재 다른 이송 API와 같은 대문자 문자열로 반환합니다.
- `injurySites`, `secondarySymptoms`는 값이 없으면 빈 배열이며 `null`로
  반환하지 않습니다.
- 두 집합은 enum 이름 기준의 안정된 순서로 변환해 같은 DB 상태의 응답 JSON이
  호출마다 흔들리지 않게 합니다.
- `ageYears`는 `ageStatus=UNKNOWN`일 때 `null`이고 나머지는 저장된 값을
  반환합니다.
- 손상 기전·부위, 발생 상세, 증상 상세와 `onsetAt`은 기존 조건부 입력 규칙에
  따라 `null` 또는 빈 배열일 수 있습니다.
- `latestSnapshot`의 Pre-KTAS·의식·활력징후는 최초 요청 생성 시 필수 Entity가
  저장되므로 정상 데이터에서는 객체가 항상 존재합니다.
- 처치가 없으면 기존 snapshot 규칙에 맞는 `NONE` 또는 빈 목록 상태를 그대로
  매핑하되, 실제 처치가 추가된 후에는 `NONE`을 현재 목록에 섞지 않습니다.
- 모든 시간은 기존 계약과 같은 ISO-8601 UTC 문자열입니다.

## 앱 복구 호출 순서

Flutter는 기존 API와 새 API를 다음 책임으로 사용합니다.

| 순서 | API | 복구 데이터 |
|---:|---|---|
| 1 | `GET /api/v1/transport-requests?view=ACTIVE` | 진행 중 요청 ID·현재 상태·목적지 병원명 요약 |
| 2 | `GET /api/v1/transport-requests/{requestId}` | 최초 환자·발생정보와 최신 임상 snapshot |
| 3 | `GET /api/v1/transport-requests/{requestId}/hospital-search` | 병원 후보·응답·현재 목적지·거리·ETA |
| 4 | `GET /api/v1/transport-requests/{requestId}/location` | 마지막 위치·`CURRENT/STALE/NOT_RECEIVED` |
| 5 | `GET /api/v1/realtime/events` | 이후 변경 신호 구독; 이벤트 뒤 해당 REST 재조회 |

- Access Token이 만료되면 기존 Refresh Token 회전을 수행한 뒤 1번부터 다시
  조회합니다.
- 새 상세 API가 `TRANSPORT_001`을 반환하면 목록 조회 이후 요청이 종료됐거나
  자기 요청이 아니므로 환자 화면을 유지하지 않고 `ACTIVE`와 `RECENT`를 다시
  조회합니다.
- 새 상세 API는 전체 임상 이력을 반환하지 않습니다. 시간순 이력 화면은 기존
  `clinical-timeline?page=0&size=...`을 별도로 사용합니다.

## 애플리케이션 설계

### Controller

- 기존 `TransportRequestController`의 클래스 단위
  `@PreAuthorize("hasRole('PARAMEDIC')")`를 유지합니다.
- `@GetMapping("/{requestId}")` 메서드는 Path 값을 Query Service에 전달하고
  Controller에서 Entity를 직접 조회·반환하지 않습니다.
- `GET /api/v1/transport-requests` 목록, `POST /{requestId}/cancel`,
  `POST /{requestId}/handoff-request`와 경로 충돌이 없는지 MockMvc로 확인합니다.

### `TransportRequestDetailQueryService`

읽기 전용 트랜잭션에서 다음 순서로 처리합니다.

1. 인증 주체의 JWT 역할이 `PARAMEDIC`이고 조직 ID가 있는지 확인합니다.
2. JWT 계정 공개 ID로 현재 `UserAccount`를 조회합니다.
3. 계정 활성 여부, DB 역할, 활성 `EMS_UNIT` 조직과 JWT 조직 일치를 확인합니다.
4. 요청 공개 ID·소유 계정 공개 ID·`ACTIVE` 상태 집합을 모두 조건으로
   `TransportRequest`를 조회합니다.
5. 요청의 저장 조직이 현재 계정 조직과 같은지 최종 확인합니다.
6. `CurrentPatientSnapshotRepository`로 요청의 최초 환자·발생정보와 최신 임상
   포인터를 조회합니다.
7. 전용 응답 DTO와 공통 임상 mapper로 허용된 필드만 조립합니다.

- 4번 조건에 맞지 않으면 요청 존재 여부·소유자·종료 상태를 구분해 노출하지
  않고 모두 `TRANSPORT_001`로 반환합니다.
- snapshot이 없으면 정상 이송 생성 트랜잭션의 데이터 불변식이 깨진 것이므로
  `COMMON_INTERNAL_SERVER_ERROR`, 즉 공개 코드 `COMMON_003`으로 처리합니다.
- 서비스는 조회 전후 audit, outbox, 이송 `updatedAt`과 snapshot을 변경하지
  않습니다.

### Repository 조회

- `TransportRequestRepository`에 다음 조건의 읽기 메서드를 추가합니다.

```text
publicId
+ ownerAccount.publicId
+ status IN ACTIVE statuses
```

- `ownerAccount`, `organization`은 조회 트랜잭션 안에서 검증 가능하도록 필요한
  EntityGraph를 사용합니다. `currentDestinationOffer`는 새 응답에서 사용하지
  않으므로 fetch하지 않습니다.
- `CurrentPatientSnapshotRepository.findByTransportRequestPublicId(...)`의 기존
  EntityGraph는 다음 연관을 이미 제공합니다.
  - 요청
  - 최초 환자 기본정보
  - 최초 발생정보
  - 최신 Pre-KTAS·의식·활력징후
  - 현재 처치 목록
- 발생정보의 복수 선택 집합과 활력징후 측정 목록은 읽기 전용 트랜잭션 안에서
  DTO로 변환합니다. Entity를 Controller로 반환하지 않아 Lazy 연관이 응답 직렬화
  중 추가로 열리지 않게 합니다.
- 단건 복구 조회이므로 pagination이나 새 인덱스를 추가하지 않습니다. 요청
  `public_id`는 이미 unique이고 소유 계정·상태는 결과 검증 조건으로 사용합니다.

### 공통 임상 snapshot mapper

- `ClinicalTimelineQueryService`의 다음 private DTO 변환 책임을
  `ClinicalSnapshotResponseMapper`로 이동합니다.
  - Pre-KTAS
  - 의식 상태
  - 활력징후와 측정 항목
  - 처치와 typed 상세정보
  - `LatestSnapshot`
- 기존 timeline의 페이지 item 조립은 유지하되 각 타입의 DTO 변환은 공통
  mapper를 호출합니다.
- 새 상세 응답의 `latestSnapshot`은 기존
  `ClinicalTimelineResponse.LatestSnapshot` 타입을 그대로 사용해 JSON 필드명과
  enum·null 의미를 일치시킵니다.
- 병원 `HospitalOfferDetailResponse`는 병원 전용 축약 처치·경로·요청자 계약이
  있으므로 이번 공통화 대상으로 넓히지 않습니다.
- 리팩터링 전후 기존 clinical timeline JSON이 같은지 회귀 테스트로 고정합니다.

## 권한·상태·동시성

### 권한 오류

| 조건 | 처리 | HTTP |
|---|---|---:|
| 인증 없음 | `AUTH_001` | 401 |
| Access Token 형식·서명·만료 오류 | `AUTH_002` | 401 |
| `HOSPITAL_STAFF`, `SUPER_ADMIN` | `AUTH_003` | 403 |
| 토큰 발급 뒤 비활성화된 계정 | 공통 JWT 인증 계층의 `AUTH_002` | 401 |
| 비활성·비구급대 조직, JWT와 DB 조직 불일치 | `COMMON_004` | 403 |
| 없는 요청, 다른 계정 소유, `COMPLETED`·`CANCELLED` | `TRANSPORT_001` | 404 |
| 필수 snapshot 관계 누락 | `COMMON_003` | 500 |

### 읽기 동시성

- GET은 상태를 변경하지 않으므로 request 비관적 쓰기 잠금을 잡지 않습니다.
- 이송 종료·임상 갱신 명령은 기존처럼 request lock과 한 트랜잭션으로 상태·
  snapshot을 확정합니다.
- 상세 조회가 명령보다 먼저 DB 상태를 읽으면 명령 직전의 완전한 활성 snapshot,
  명령이 먼저 commit되면 갱신된 snapshot 또는 종료 요청 `404`를 반환합니다.
- 상세 응답 하나에 이전 환자 기본정보와 반쪽만 저장된 새 활력징후가 섞이지
  않도록 같은 읽기 전용 트랜잭션 안에서 request와 snapshot을 조립합니다.
- 종료와 GET이 정확히 경합하면 활성 응답 또는 `404` 중 하나가 가능하지만,
  종료 commit 뒤 후속 GET은 반드시 `404`여야 합니다. Flutter는 SSE 또는 후속
  목록 재조회로 최종 종료 상태에 수렴합니다.
- 임상 갱신과 GET이 경합하면 이전 또는 새 `latestSnapshot` 전체 중 하나를
  반환해야 하며, 새 기록의 일부 필드만 섞인 응답은 허용하지 않습니다.

## DB 변경

- 없음
- 다음 기존 테이블만 읽습니다.
  - `user_accounts`, `organizations`
  - `transport_requests`
  - `patient_demographics`, `incident_assessments`
  - `incident_injury_sites`, `incident_secondary_symptoms`
  - `current_patient_snapshots`
  - `pre_ktas_assessments`, `consciousness_assessments`
  - `vital_sign_sets`, `vital_sign_measurements`
  - `treatment_events`, `current_patient_snapshot_treatments`
- 기존 V1~V8 migration은 수정하지 않습니다.
- 새 캐시·복구 snapshot·프론트 세션 테이블을 만들지 않습니다. 현재 DB의
  `CurrentPatientSnapshot`이 이미 서버 권위 최신 요약입니다.
- `spring.jpa.hibernate.ddl-auto=validate`와 전체 MySQL 테스트로 기존 매핑이
  유지되는지 확인합니다.

## 구현 Step

| Step | 작업 | 구현 후 검증 | 완료 기준 |
|---:|---|---|---|
| 1 | `TransportRequestDetailResponse`와 API 계약 정의 | DTO 직렬화 단위·MockMvc 계약 테스트, 금지 필드 부재 확인 | spec의 상태·patient·incident·latestSnapshot·시각 필드와 enum·null 계약이 일치함 |
| 2 | `ClinicalSnapshotResponseMapper` 추출 및 기존 timeline 적용 | 기존 timeline 정상·페이징·최신값 회귀 테스트 | 리팩터링 전 공개 JSON 의미가 유지되고 상세와 timeline 최신 snapshot이 동일함 |
| 3 | 활성 소유 요청 Repository 조회와 `TransportRequestDetailQueryService` 구현 | 정상 자기 요청, 다른 대원·조직·종료 상태·snapshot 누락 서비스 테스트 | JWT·DB 계정·조직·소유권·ACTIVE 상태를 통과한 요청만 응답함 |
| 4 | 기존 `TransportRequestController`에 상세 GET 추가 | 생성→목록→상세 MockMvc 시나리오와 기존 목록·취소·인계 경로 회귀 | `GET /{requestId}`가 200을 반환하고 기존 경로와 충돌하지 않음 |
| 5 | 임상 갱신·앱 재실행 복구 시나리오 검증 | 활력징후·Pre-KTAS 갱신, 늦은 과거 기록, 상세·timeline 비교 테스트 | 최초정보는 유지되고 상세는 최신 snapshot만 반환함 |
| 6 | 권한·민감정보·상태 경합 검증 | 미인증·병원·관리자·비활성·타 소유 차단, 임상 갱신/종료와 GET 경합 | 정보 누출·부분 응답·500 없이 이전/최신 또는 404의 허용 결과만 발생함 |
| 7 | 전체 회귀·로컬 실행 검증 | `./gradlew clean check`, Docker MySQL 실행과 readiness | 기존 기능·MySQL 호환성·실행 설정이 모두 통과함 |
| 8 | `review.md`와 Flutter 핸드오프 작성 | 문서의 요청·응답·오류·복구 순서를 실제 MockMvc 결과와 대조 | 구현된 계약만 독립적으로 설명하고 테스트 결과를 기록함 |

각 Step은 구현 → 해당 범위 자동 검증 → 발견 문제 수정 후 다음 Step으로
이동합니다. 커밋·푸시·PR은 전체 구현과 검수 문서가 끝난 뒤 사용자가 요청할
때만 수행합니다.

## 변경 범위

| 패키지·파일 | 변경 내용 |
|---|---|
| `transport/api/TransportRequestController.java` | 구급대원 본인 이송 상세 `GET /{requestId}` 추가 |
| `transport/api/TransportRequestDetailResponse.java` | 상태·최초 환자·발생정보·최신 임상 snapshot 응답 정의 |
| `transport/application/TransportRequestDetailQueryService.java` | 계정·조직·소유권·ACTIVE 상태 검증과 상세 응답 조립 |
| `transport/application/ClinicalSnapshotResponseMapper.java` | timeline과 새 상세가 공유하는 최신 임상 DTO 변환 |
| `transport/application/ClinicalTimelineQueryService.java` | 공개 계약을 유지하면서 공통 snapshot mapper 사용 |
| `transport/infrastructure/TransportRequestRepository.java` | 공개 ID·소유 계정·ACTIVE 상태 조건 단건 조회 추가 |
| `src/test/.../transport/TransportRequestDetailIntegrationTest.java` | 정상 복구·응답·권한·상태·민감정보·기존 경로 통합 테스트 |
| `src/test/.../transport/TransportRequestDetailConcurrencyIntegrationTest.java` | 임상 갱신·종료와 GET 경합 및 최종 재조회 테스트 |
| `docs/handoffs/10-transport-request-detail-recovery/flutter-paramedic.md` | Flutter 전용 실제 응답·복구 순서·오류 계약 |
| `docs/features/10-transport-request-detail-recovery/review.md` | 구현·회귀·로컬 실행 결과 기록 |

## 테스트 계획

### 정상 화면 복구

- [ ] 이송 요청 생성 직후 상세가 `SEARCHING`, 평가 프로토콜 버전과 생성 시각을 반환
- [ ] `EXACT`, `ESTIMATED`, `UNKNOWN` 나이 상태와 조건부 `ageYears` 반환
- [ ] 남성·여성·확인 불가 성별 enum 반환
- [ ] 질병·비질병·기타·확인 불가 발생 유형과 조건부 상세 반환
- [ ] 손상 기전·복수 부위, 주증상·상세·복수 부증상과 발생 시각 상태 반환
- [ ] 최초 Pre-KTAS·의식·다섯 활력징후·처치가 생성 응답의 저장값과 일치
- [ ] `ACTIVE` 목록의 요청 ID로 상세→병원 탐색→위치 조회가 이어지는 MockMvc 복구 시나리오
- [ ] 모든 다섯 ACTIVE 상태에서 자기 상세 조회 성공

### 최신 snapshot·기존 timeline 일치

- [ ] 이송 중 활력징후 추가 뒤 상세에 새 다섯 항목과 측정 시각 반영
- [ ] 의식·Pre-KTAS·처치 추가 뒤 각 최신값과 `lastClinicalUpdateAt` 반영
- [ ] 늦게 도착한 과거 임상 원본은 timeline에 존재하지만 상세 최신값을 되돌리지 않음
- [ ] 같은 임상 시각의 서버 수신 순서 규칙이 기존 snapshot과 동일함
- [ ] 실제 처치 추가 뒤 현재 처치에서 최초 `NONE` 제외
- [ ] 상세 `latestSnapshot`과 같은 시점 timeline `latestSnapshot` JSON이 동일함
- [ ] 공통 mapper 추출 뒤 기존 timeline 페이지 item·정렬·null 계약 회귀 없음

### 인증·조직·소유권·상태

- [ ] 인증 없는 상세 요청은 `AUTH_001`
- [ ] 만료·변조 Access Token은 `AUTH_002`
- [ ] `HOSPITAL_STAFF`, `SUPER_ADMIN`은 `AUTH_003`
- [ ] 토큰 발급 뒤 비활성화된 계정은 공통 JWT 인증 계층에서 `AUTH_002`
- [ ] 비활성 구급대 조직과 JWT 조직 불일치는 `COMMON_004`
- [ ] 같은 조직의 다른 구급대원과 다른 조직 구급대원 요청은 모두 `TRANSPORT_001`
- [ ] 존재하지 않는 UUID와 추측한 요청 ID는 `TRANSPORT_001`
- [ ] `COMPLETED`, `CANCELLED` 요청은 `TRANSPORT_001`이고 `ACTIVE` 재조회에서 제거됨
- [ ] snapshot 누락 불변식은 환자정보 없이 `COMMON_003`

### 개인정보·부작용

- [ ] 응답 JSON에 환자 이름·주민등록번호·환자 연락처·정확한 생년월일·상세 주소 없음
- [ ] 응답 JSON에 `callbackContact`, 최초·최신 좌표, 전체 경로, 내부 DB ID 없음
- [ ] 응답 JSON에 비밀번호·해시·Access·Refresh Token·가입 코드·멱등성 fingerprint 없음
- [ ] 상세 GET 전후 이송 상태·`updatedAt`·환자 원본·snapshot·audit·outbox 행 수가 바뀌지 않음
- [ ] 타 소유·종료 요청 오류가 환자·조직·상태 존재 여부를 응답 메시지로 구분하지 않음
- [ ] 테스트·문서 예시는 가짜 환자·좌표·연락처만 사용

### 동시성·회귀·실행

- [ ] 활력징후 갱신과 상세 GET 경합에서 이전 또는 새 완전한 snapshot만 반환
- [ ] 완료·취소와 상세 GET 경합에서 활성 응답 또는 `404`만 발생하고 후속 GET은 `404`
- [ ] 경합 중 LazyInitializationException, OptimisticLockException, 500 또는 부분 DTO 없음
- [ ] 기존 이송 생성·목록·임상 갱신·timeline·병원 탐색·위치·취소·인계 회귀 통과
- [ ] H2 빠른 검사와 Testcontainers MySQL 8.4 전체 검사 통과
- [ ] 스키마 변경 없이 Flyway V1→V8와 JPA validate 통과
- [ ] `./gradlew clean check`
- [ ] `./scripts/dev-start.sh` 후 `/actuator/health/readiness`가 `UP`

## Flutter 연동 계획

- 로그인·토큰 갱신·앱 재실행 뒤 `ACTIVE` 목록부터 조회하고, 받은 요청 ID로
  새 상세 API를 호출하는 순서를 기록합니다.
- `patient`, `incident`, `latestSnapshot`의 전체 필드·enum·조건부 null을 실제
  구현 응답 예제로 설명합니다.
- `TRANSPORT_001`이면 현재 환자 화면을 유지하지 말고 `ACTIVE`·`RECENT`를 다시
  조회하는 복구 조건을 기록합니다.
- 최신 환자 화면은 상세 `latestSnapshot`, 시간순 기록 화면은 기존
  `clinical-timeline`을 사용하도록 두 API의 책임을 구분합니다.
- 병원 응답·목적지·ETA와 위치는 기존 04·05·06 핸드오프의 전용 API를 함께
  사용하되, 새 문서만으로 새 상세 API 자체는 연동할 수 있게 작성합니다.
- Flutter의 상태관리 라이브러리·폴더·화면 컴포넌트 구현은 지시하지 않습니다.
- 이 백엔드 작업에서는 `/Users/pangdasian/Desktop/inteliJ/ersync-front-app` 코드를
  수정하지 않습니다.

## 프론트 핸드오프

- 대상: `FLUTTER_PARAMEDIC`
- Flutter: `docs/handoffs/10-transport-request-detail-recovery/flutter-paramedic.md`
- React: `NONE`
- 구현과 로컬 검증 후 실제 코드 기준으로 작성

## 유지할 계약

- `spec.md`의 제품 동작과 완료 조건
- 구급대원은 개인 계정으로 자신이 생성한 진행 중 요청만 상세 조회
- 슈퍼 관리자와 병원 관계자의 새 구급대원 상세 접근 차단
- 환자 이름·주민등록번호·연락처·정확한 생년월일·상세 주소 미수집·미반환
- 임상 원본 append-only와 `CurrentPatientSnapshot` 최신 포인터 규칙
- `ACTIVE`·`RECENT`·`HISTORY` 목록의 상태 범위와 최소 이력 계약
- 병원 탐색·목적지·ETA·위치·timeline·SSE의 기존 전용 API 계약
- 공통 오류 응답, `X-Trace-Id`, 민감정보 비로그
- 기존 V1~V8 Flyway migration 불변과 MySQL 8.4 호환성

## 리스크

| 리스크 | 대응 |
|---|---|
| 새 상세와 기존 timeline이 서로 다른 최신 임상값을 반환함 | 공통 snapshot mapper를 사용하고 두 응답의 `latestSnapshot` 동일성 통합 테스트 추가 |
| Lazy 연관 매핑 중 예외 또는 다수 쿼리로 복구 응답이 느려짐 | 기존 snapshot EntityGraph와 읽기 전용 트랜잭션 안에서 DTO 조립, 실제 통합 조회와 쿼리 구조 점검 |
| 요청 ID 추측이나 역할 오류로 다른 환자정보가 노출됨 | URL ID만 믿지 않고 JWT·DB 계정·활성 조직·소유권·상태를 모두 조회 조건과 서비스에서 검증 |
| 종료·임상 갱신과 GET 경합에서 오래되거나 부분적인 화면을 표시함 | 명령 원자성·읽기 트랜잭션을 유지하고 경합 테스트, SSE·목록·상세 재조회로 최종 상태 복구 |
| 상세 응답에 기존 전용 API 정보나 민감 필드가 과도하게 포함됨 | 허용 필드 전용 DTO, 금지 필드 JSON 부재 테스트, 탐색·위치·전체 timeline 분리 유지 |
