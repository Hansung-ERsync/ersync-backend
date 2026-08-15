# 백엔드 기능 01~14 통합 검수

```text
Branch: docs/v1-review
Baseline Commit: 4974779c1ad9d73e66974538964792d3a39829c2
Reviewed At: 2026-08-13 17:54 KST
Policy Updated At: 2026-08-13
Feature Documents: 14/14
Referenced Handoffs: 22/22
Document Review: COMPLETE
Backend Local Verification: PASS
Delivery Verification: PENDING
Overall Decision: BLOCKED
```

이 문서는 01~14의 `spec.md`, `implementation.md`, `review.md`를 MVP 요구사항,
현재 코드, 테스트, Flyway migration과 프론트 핸드오프에 대조한 결과입니다.
기존 `implementation.md`, `review.md`와 핸드오프는 작성 시점의 구현 기록으로
보존합니다. 04·05·12 `spec.md`는 2026-08-13 확정 정책으로 갱신했으며, 현재
코드와의 차이는 이 문서에서 판정합니다.

## 1. 판정 기준

| 구분 | 의미 |
|---|---|
| `CONSISTENT` | 기능 문서와 현재 구현이 일치함 |
| `SUPERSEDED` | 후속 기능이 이전 계약을 의도적으로 대체함 |
| `DRIFT` | 문서·구현 또는 기능 문서 사이에 설명되지 않은 충돌이 있음 |
| `MISSING` | 구현·테스트·핸드오프 근거가 없음 |
| `LOCAL_VERIFIED` | 현재 Commit에서 백엔드와 MySQL 검증이 완료됨 |
| `DELIVERY_VERIFIED` | PR·CI·EC2·프론트 연동까지 확인됨 |
| `BLOCKED` | 정책 충돌 또는 필수 검증 실패로 통합 승인을 보류함 |

현재 코드는 전체 자동 검사를 통과했습니다. 05·12의 정책 충돌은 해결됐지만,
현재 코드와 핸드오프가 비목적지 제안을 숨기고 후보 소진·무응답·재전송을
제공하는 이전 계약을 따르므로 통합 승인은 `BLOCKED`입니다.
현재 Commit의 EC2 배포와 프론트 E2E는 확인하지 않아 전달 상태는 `PENDING`입니다.

## 2. 기능별 완료표

| 번호 | 기능 | 정책 | 백엔드 상태 | 배포·프론트 | 정합성 | 주요 근거 |
|---|---|---|---|---|---|---|
| 01 | 배포 버전 조회 | RESOLVED | LOCAL_VERIFIED | PENDING | CONSISTENT | [review](./01-deployment-version-check/review.md), 상태·버전 API 유지 |
| 02 | 계정·가입·인증 | NONE | LOCAL_VERIFIED | PENDING | SUPERSEDED | [review](./02-account-onboarding-auth/review.md), 로그인은 11·코드는 14가 대체 |
| 03 | 환자 평가·이송 생성 | RESOLVED | LOCAL_VERIFIED | PENDING | SUPERSEDED | [review](./03-patient-assessment-transfer-request/review.md), 최신 임상 순서는 06이 대체 |
| 04 | 자동 병원 탐색·응답 | RESOLVED | BLOCKED | PENDING | DRIFT | [review](./04-automatic-hospital-search-response/review.md), 후보 소진·무응답·전체 재전송 제거 필요 |
| 05 | 목적지 선택·변경·철회 | RESOLVED | BLOCKED | PENDING | DRIFT | [review](./05-destination-selection-change-acceptance-withdrawal/review.md), 비목적지 제안 유지·긴급 철회 재검색 반영 필요 |
| 06 | 이송 중 임상·위치 갱신 | NONE | LOCAL_VERIFIED | PENDING | CONSISTENT | [review](./06-in-transit-patient-location-updates/review.md), 임상 시각 우선 규칙 확정 |
| 07 | 구급대원 가입·프로필 | RESOLVED | LOCAL_VERIFIED | PENDING | SUPERSEDED | [review](./07-signup-profile-integration/review.md), 역할 로그인·8자리 코드는 11·14가 대체 |
| 08 | 취소·인계·이력 | NONE | LOCAL_VERIFIED | PENDING | CONSISTENT | [review](./08-transport-cancellation-handoff-history/review.md), V8 lifecycle 유지 |
| 09 | 병원 프로필·수신 상태 | NONE | LOCAL_VERIFIED | PENDING | CONSISTENT | [review](./09-hospital-profile-receiving-status/review.md), API·권한 유지 |
| 10 | 구급대원 상세 복구 | NONE | LOCAL_VERIFIED | PENDING | DRIFT | [review](./10-transport-request-detail-recovery/review.md), 완료 체크박스가 갱신되지 않음 |
| 11 | 역할별 로그인 ID | RESOLVED | LOCAL_VERIFIED | PENDING | CONSISTENT | [review](./11-role-scoped-login-id/review.md), `role` 필수·복합 유일성 유지 |
| 12 | 병원별 이력 결과 | RESOLVED | BLOCKED | PENDING | DRIFT | [review](./12-hospital-specific-history-status/review.md), 진행 중 `NOT_SELECTED` 전환 제거 필요 |
| 13 | 추가 환자 평가 | RESOLVED | LOCAL_VERIFIED | PENDING | CONSISTENT | [review](./13-supplemental-patient-assessment/review.md), V10·공개 범위 유지 |
| 14 | 8자리 가입 코드 | RESOLVED | LOCAL_VERIFIED | PENDING | CONSISTENT | [review](./14-eight-character-invitation-code/review.md), 현재 208개·skipped 0 재확인 |

## 3. 현재 정책과 대체 관계

| 영역 | 현재 기준 | 대체 관계 |
|---|---|---|
| 로그인 | `loginId`, `password`, `role` 필수; ID는 역할 범위에서 유일 | 02 → 11 |
| 가입 코드 | Base64 URL-safe 8자, 6바이트 난수, 해시 저장, 충돌 재시도 | 02·07 → 14 |
| 구급대원 가입 | 표시 이름·연락처·목적별 동의와 프로필 조회 | 03 → 07 |
| 임상 최신값 | 임상 발생 시각 우선, 같은 시각이면 서버 수신 시각으로 결정 | 03 → 06 |
| 이송 확장 | 목적지 이후 임상·위치 갱신, 취소, 인계와 종료 이력 지원 | 05 → 06 → 08 |
| 병원 탐색 종료 | 최소 한 병원이 수락한다고 가정; 무응답·후보 소진·전체 재전송·전화 연결 제외 | 04 정책 개정, 구현 정렬 필요 |
| 목적지 이후 병원 상태 | 비목적지 `PENDING`·`ACCEPTED` 활성 유지, 다른 병원 이동 중 표시 | 05 → 12 정책 통일, 구현 정렬 필요 |
| 긴급 철회 재검색 | 같은 요청·최신 위치 사용, 철회 병원 제외, 기존 수락 유지, `PENDING` 재알림, 신규 병원 추가 | 05 정책 개정, 구현 정렬 필요 |
| DB 기준 | Flyway V1~V10, MySQL 8.4, 애플리케이션 테이블 31개 | 09·10의 V8 표기는 작성 시점 기록 |

## 4. 교차 기능 검증

| ID | 시나리오 | 결과 | 자동화 근거 |
|---|---|---|---|
| J0 | readiness와 버전 API | PASS | `ApiFoundationIntegrationTest`, `MySqlDatabaseIntegrationTest` |
| J1 | 코드 발급·확인 → 역할 가입·로그인 → 프로필·수신 ON | PASS | `MvpJourneyIntegrationTest`, `MvpCollisionJourneyIntegrationTest` |
| J2 | 환자 평가 → 탐색 → 수락 → 목적지 → 임상·위치 → 인계 | PASS | 두 MVP journey와 이송·병원 통합 테스트 |
| J3 | 멱등 재시도 → SSE 신호 → REST 상세 복구 | PASS | outbox·SSE·상세 복구 통합 테스트 |
| J4 | 복수 수락 → 목적지 변경·철회 → 최신 위치 재탐색 | PARTIAL | 기존 테스트는 최신 위치 재탐색을 검증하지만 비목적지 수락 유지·`PENDING` 재알림은 미검증 |
| J5 | 수락·취소·철회·인계·위치 갱신 경합 | PASS | `MvpCollisionJourneyIntegrationTest`의 경합 시나리오 3건 |
| J6 | 슈퍼 관리자·타 조직·비소유자의 임상·위치 접근 차단 | PASS | 병원 검색·이송 상세·위치 권한 통합 테스트 |

J2~J6은 기존 테스트 조합을 통합 관점에서 재분류한 것입니다. 별도 E2E 테스트
개수로 중복 합산하지 않습니다. J4의 미검증 범위는 R1의 구현 정렬이 끝난 뒤
새 통합 테스트로 확인해야 합니다.

## 5. 현재 검증 증거

| 검증 | 결과 | 증거 |
|---|---|---|
| Docker | PASS | Docker Engine 29.6.2 실행 상태에서 검증 |
| `./gradlew clean check` | PASS | 208 tests, failures 0, errors 0, skipped 0; Javadoc·Spotless 포함 |
| MySQL migration 집중 실행 | PASS | 9 tests, failures 0, errors 0, skipped 0 |
| Flyway·테이블 | PASS | V1~V10 존재, `CREATE TABLE` 31개; 30개 fresh-schema와 V8 lifecycle 테스트로 검증 |
| API 경로 | PASS | 14개 review의 Controller 경로가 현재 코드에 존재 |
| 오류 코드 | PASS | review에서 사용한 8개 공개 코드가 35개 `ErrorCode`에 모두 존재 |
| 문서 구성 | PASS | 14개 기능 모두 spec·implementation·review 보유 |
| 핸드오프 참조 | PASS | review가 참조한 22개 파일 모두 존재 |
| AWS·Nginx SSE·Naver 실연동 | NOT_RUN | 저장소와 로컬 자동 검사만으로 현재 외부 환경을 증명할 수 없음 |
| Flutter·React E2E | NOT_RUN | 프론트 저장소와 Dev 배포 연동은 이번 검수 범위 밖 |

## 6. 불일치와 리스크

| ID | 심각도 | 내용 | 영향 | 상태·종료 조건 |
|---|---|---|---|---|
| R1 | P1 | 정책은 비목적지 `PENDING`·`ACCEPTED` 활성 유지를 확정했지만 현재 query·outcome은 이를 `NOT_SELECTED` HISTORY로 이동 | 05·12 | POLICY_RESOLVED; 코드·테스트·핸드오프 정렬 필요 |
| R2 | P2 | 공개 가입 코드 확인 API의 호출 제한·실패 시도 모니터링 계약이 없음 | 07·14 | OPEN; abuse 정책을 결정하고 테스트해야 함 |
| R3 | P2 | 현재 Commit의 EC2·Nginx SSE·Naver ETA·프론트 E2E를 검증하지 않음 | 01·04·06 및 프론트 영향 기능 | OPEN; Dev 배포 SHA와 외부 시나리오 확인 필요 |
| R4 | P3 | 03 review는 서버 수신 시각 우선이라고 적지만 06과 현재 코드는 임상 시각 우선임 | 03·06 | SUPERSEDED; 현재 계약은 06을 따름 |
| R5 | P3 | 04·07·10·12의 spec 또는 implementation 체크박스가 review의 PASS와 다름 | 04·07·10·12 | OPEN; 원본을 보존하면 작성 시점 기록임을 명시해야 함 |
| R6 | P3 | 일부 review의 테스트 수와 DB 최신 버전은 기능 작성 시점의 값임 | 02~13 | ACCEPTED; 통합 판정은 현재 208개·V10만 사용 |
| R7 | P1 | 정책은 무응답·후보 소진·전체 재전송·전화 연결을 MVP에서 제외했지만 현재 04 코드·API·핸드오프는 해당 계약을 제공 | 04·05 | POLICY_RESOLVED; 공개 동작과 상태 전이 제거·호환 처리 필요 |

현재 확인된 P0는 없습니다. R1·R7의 구현 정렬 전에는 테스트 성공과 별개로 통합
계약을 승인하지 않습니다. R2·R3는 해소하거나 데모 제한으로 명시해야 합니다.

## 7. 해결되었거나 재확인된 항목

- 03의 임상 순서 차이는 06의 확정 spec과 테스트가 대체했습니다.
- 05의 위치 미구현 범위는 06에서, 06의 취소·인계 미구현 범위는 08에서
  구현됐습니다.
- 09·10의 V8 표기는 과거 검증 기준이며 현재 DB 기준은 V10입니다.
- 14 review의 `208 tests, skipped 0`은 Docker를 켠 현재 Commit의 새 실행에서도
  동일하게 확인됐습니다.
- 목적지 선택 뒤 `PENDING`·`ACCEPTED` 처리 정책은 활성 유지로 통일됐습니다.
- 목적지 긴급 철회는 같은 요청과 최신 위치를 사용하고, 철회 병원만 제외하며,
  기존 수락 유지·`PENDING` 재알림·신규 병원 추가 방식으로 확정됐습니다.
- 무응답·후보 소진·전체 재전송·병원 전화 연결은 MVP 제외로 확정됐습니다.
- 01을 제외한 기능 review는 관련 PR이 `NONE`이므로 로컬 성공을 배포 완료로
  해석하지 않습니다.

## 8. 최종 결론

| 항목 | 판정 |
|---|---|
| 통합 리뷰 문서 | COMPLETE |
| 현재 백엔드 코드·MySQL 자동 검증 | PASS |
| 기능 간 정책 정합성 | RESOLVED |
| 정책과 현재 구현 정합성 | BLOCKED - R1·R7 구현 필요 |
| 현재 Commit 배포·프론트 전달 | PENDING |
| MVP 운영 준비 | NOT_ASSESSED |

통합 리뷰 자체는 모든 기능과 미실행 항목을 판정했으므로 완료입니다. 이는 제품
정책 충돌 해결, Dev 배포, 프론트 연동과 운영 준비가 완료됐다는 의미는 아닙니다.
