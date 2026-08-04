# 구급대원 회원가입·프로필 연동 보완 구현 검수

```text
Feature: signup-profile-integration
Implemented By: AI-assisted backend
Related PR: NONE
Frontend Impact: FLUTTER_PARAMEDIC
Flutter Handoff: docs/handoffs/07-signup-profile-integration/flutter-paramedic.md
React Handoff: NONE
```

## 구현 요약

- 가입 코드를 소비하지 않고 소속·역할·만료 시각·필수 동의 버전을 반환하는 공개 확인 API를 추가했습니다.
- 구급대원 가입 요청에 표시 이름과 수집·이용·병원 제공 동의 2개를 추가했습니다.
- 최종 가입에서는 기존 비관적 잠금으로 개인별 일회용 코드를 다시 검증하고 한 번만 소비합니다.
- 구급대원 프로필에 표시 이름을 저장하고, JWT 본인의 계정·소속·연락처·동의를 조회하는 API를 추가했습니다.
- 기존 통합 동의를 사실 그대로 보존하면서 신규 목적별 동의와 함께 사용할 수 있도록 동의 유형을 추가했습니다.
- 기존 통합 동의 계정과 신규 병원 제공 동의 계정 모두 이송 요청을 생성할 수 있게 호환했습니다.
- V6 데이터가 있는 MySQL 8.4에서 V7 backfill과 제약 적용을 검증했습니다.

## 요구사항 체크

| 요구사항 | 결과 | 근거 |
|---|---|---|
| 가입 코드 확인은 코드를 소비하지 않음 | PASS | `InvitationValidationIntegrationTest`, 확인 후 `AVAILABLE`·사용 감사 0건 |
| 사용·만료·폐기·대소문자 오류 구분 | PASS | `InvitationValidationIntegrationTest` 상태별 공개 오류 검증 |
| 사용자는 가입 코드를 한 번만 입력 | PASS | 확인 응답에 원문 미포함, 최종 가입은 앱이 보관한 원문을 동일 요청 필드로 전송하는 계약 |
| 최종 가입에서 코드 재검증·단일 소비 | PASS | `AccountSignupConcurrencyIntegrationTest`, 동시 요청 중 1건만 성공 |
| 표시 이름 2~50자 저장 | PASS | `AccountSignupIntegrationTest`, 공백 정규화와 잘못된 이름 롤백 검증 |
| 필수 동의 2개 분리 기록 | PASS | `CONTACT_COLLECTION_USE`, `HOSPITAL_PROVISION` 유형·버전 검증 |
| 실패 시 가입 전체 원자적 롤백 | PASS | 이름·동의 실패 후 계정·프로필·동의 0건, 코드 `AVAILABLE` 검증 |
| 구급대원 본인 프로필 조회 | PASS | `ParamedicProfileIntegrationTest`, 본인·역할·계정·조직 검증 |
| 기존 Dev 계정 호환 | PASS | 로그인 ID 표시 이름, 통합 동의 `legacyCombined: true` 응답 검증 |
| 기존 이송 요청 생성 호환 | PASS | 통합 동의와 신규 병원 제공 동의 두 흐름 모두 요청 생성 검증 |
| 전체 Flutter 흐름 | PASS | 코드 확인 → 가입 → 로그인 → 프로필 조회 통합 테스트 |

## 변경 API·DB

| 구분 | 변경 내용 | 호환성 |
|---|---|---|
| API | `POST /api/v1/auth/invitations/validate` 추가 | 신규 공개 API |
| API | `POST /api/v1/auth/signups/paramedic`에 `displayName`과 동의 2개 추가 | 구급대원 가입 요청 Breaking Change, 07 핸드오프로 전환 필요 |
| API | `GET /api/v1/paramedics/me` 추가 | 신규 `PARAMEDIC` 본인 조회 API |
| API | 병원 가입·로그인·토큰 갱신·기존 `SignupResponse` 유지 | 기존 계약 호환 |
| DB | `paramedic_profiles.display_name VARCHAR(50) NOT NULL` 추가 | 기존 행은 `user_accounts.login_id`로 보완 |
| DB | `contact_sharing_consents.consent_type` 추가 | 기존 행은 `CONTACT_COLLECTION_AND_PROVISION`으로 보존 |
| DB | 동의 유니크 키를 계정·유형·버전 조합으로 변경 | 목적별 동의 이력 저장 가능 |

## Spec 이후 변경

- 제품 정책 변경: 없음
- 구현 중 추가된 설계 결정:
  - 병원과 기존 통합 동의의 DB 유형명은 계속 유효한 의미를 갖도록 `CONTACT_COLLECTION_AND_PROVISION`으로 정했습니다.
  - 비활성 계정 Access Token은 기존 JWT 변환 계층에서 `AUTH_002` 401로 거부하는 인증 계약을 유지했습니다.
  - 현재 구급대원 동의 2개는 같은 서버 시각으로 기록하고 프로필에서 하나의 `consentedAt`으로 반환합니다.

## 범위 확인

- spec 밖 추가 작업: 이송 요청의 기존·신규 동의 호환 확인만 수행
- 의도적으로 제외한 작업:
  - 프로필 이름·연락처 수정
  - 로그아웃과 Refresh Token 폐기
  - 최근 이송 목록
  - 병원 회원가입·프로필 계약 변경
  - 실제 운영용 개인정보 문구·보존·삭제 정책 확정

## 테스트 결과

| 테스트 | 결과 | 근거 |
|---|---|---|
| `./gradlew clean check` | PASS | 전체 139개, 실패 0, 오류 0, 건너뜀 0 |
| 코드 형식·Javadoc | PASS | `spotlessCheck`, `javadoc` 성공 |
| H2 API·권한·동시성 회귀 | PASS | 전체 Spring 통합 테스트 성공 |
| MySQL 8.4 fresh migration | PASS | V1~V7 Flyway·Hibernate validate 성공 |
| MySQL 8.4 V6→V7 upgrade | PASS | 기존 이름·연락처·동의 사실 보존과 새 유니크 키 검증 |
| local 실행·readiness | PASS | MySQL 8.4.11, Flyway V7, `{"status":"UP"}` 확인 |
| 주요 기능 시나리오 | PASS | 확인→가입→로그인→내 프로필 흐름 성공 |

## 프론트 핸드오프

| 대상 | 문서 | 실제 코드와 일치 |
|---|---|---|
| Flutter 구급대원 앱 | `docs/handoffs/07-signup-profile-integration/flutter-paramedic.md` | YES |
| React 병원·관리자 웹 | `NONE` | N/A |

## 남은 리스크

| 리스크 | 영향 | 대응 |
|---|---|---|
| Flutter가 기존 단일 동의 가입 본문을 계속 사용함 | 구급대원 가입이 `COMMON_001`로 실패 | 07 핸드오프를 최신 가입 계약으로 사용 |
| Flutter가 가입 코드를 대문자로 변경함 | 실제 혼합 대소문자 코드가 `INVITATION_001`로 실패 | 원문 보존, 자동 대문자화 제거를 핸드오프에 명시 |
| Dev 동의 문구 버전이 운영 확정본이 아님 | 운영 개인정보 동의로 사용할 수 없음 | 운영 전 실제 문구·버전·보존·삭제 정책 확정 필요 |
| Dev 서버가 HTTP를 사용함 | 실제 개인정보·토큰 전송에 부적합 | HTTPS 적용 전 가짜 연락처·테스트 계정만 사용 |
