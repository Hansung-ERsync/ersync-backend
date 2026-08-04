# 구급대원 회원가입·프로필 연동 보완 구현 계획

```text
Feature: signup-profile-integration
Author: AI-assisted backend
Handoff Targets: FLUTTER_PARAMEDIC
```

> `spec.md`의 `Policy Decision Status: RESOLVED`를 기준으로 작성했습니다.
> 기존 개인별 일회용 가입 코드와 병원 회원가입 계약은 유지하며, 구급대원
> 가입 화면에 필요한 코드 확인·이름·동의 2개·내 프로필 조회만 구현합니다.

## 설계 요약

- 선택한 방식:
  - 가입 코드 사전 확인은 원문을 요청 본문으로 받는 공개 `POST` API로 추가합니다.
  - 사전 확인은 읽기 전용으로 수행하고, 최종 회원가입에서는 기존 비관적 잠금을 유지해 코드를 다시 검증한 뒤 한 번만 소비합니다.
  - `paramedic_profiles`에 `display_name`을 추가하고 기존 Dev 계정은 로그인 ID로 보완합니다.
  - 기존 `contact_sharing_consents` 테이블에 동의 유형을 추가해 신규 동의 2개와 기존 단일 동의를 함께 보관합니다.
  - 내 프로필은 인증된 JWT의 계정 ID로만 조회하고 요청 경로·본문에서 다른 계정 ID를 받지 않습니다.
- 선택 이유:
  - Flutter의 1단계 코드 확인 → 2단계 계정 정보 입력 화면을 그대로 지원합니다.
  - 확인과 가입 사이에 코드가 사용·만료·폐기되는 경쟁 상황에서도 일회용 정책을 지킬 수 있습니다.
  - 기존 Dev 계정, 병원 가입과 이미 생성된 이송 요청을 삭제하거나 깨뜨리지 않습니다.
  - 새 테이블로 개인정보 동의를 이중 관리하지 않고 기존 감사·조회 흐름을 확장할 수 있습니다.
- 검토한 대안과 제외 이유:
  - 사전 확인 성공만 믿고 최종 가입에서 코드를 검증하지 않는 방식은 동시 가입 시 두 계정이 생길 수 있어 제외합니다.
  - 확인 성공 시 별도 임시 토큰을 발급하는 방식은 현재 2단계 화면에 필요하지 않은 서버 상태와 만료 정책을 추가하므로 제외합니다.
  - 기존 동의 행을 신규 동의 2개로 복제하는 방식은 사용자가 새 문구에 동의한 것처럼 기록될 수 있어 제외합니다.
  - 기존 Dev 계정과 DB를 초기화하는 방식은 진행 중인 테스트 데이터와 이송 이력을 잃으므로 제외합니다.

## API 계약 계획

### 1. 가입 코드 사전 확인

```http
POST /api/v1/auth/invitations/validate
Content-Type: application/json
```

```json
{
  "invitationCode": "사용자가 1단계에서 입력한 코드"
}
```

성공 응답은 HTTP 200이며 가입 코드 원문을 되돌려주지 않습니다.

```json
{
  "organizationId": "조직 공개 ID",
  "organizationName": "강동소방서 3구급대",
  "role": "PARAMEDIC",
  "expiresAt": "2026-08-07T09:00:00Z",
  "requiredConsents": [
    {
      "type": "CONTACT_COLLECTION_USE",
      "policyVersion": "COLLECTION_USE_DEV_1.0"
    },
    {
      "type": "HOSPITAL_PROVISION",
      "policyVersion": "HOSPITAL_PROVISION_DEV_1.0"
    }
  ]
}
```

- 서버는 코드 앞뒤 공백만 제거하고 대소문자는 변경하지 않은 채 해시를 계산합니다.
- 조회는 잠금을 잡지 않고 `AVAILABLE`, 현재 시각 기준 만료 여부와 조직 상태를 확인합니다.
- `USED`, `EXPIRED`, `REVOKED`는 기존 `INVITATION_003`, `INVITATION_002`, `INVITATION_004`를 그대로 사용합니다.
- 사전 확인은 코드 상태, `usedByAccount`, 감사 기록을 변경하지 않습니다.
- Flutter는 입력한 코드 원문을 회원가입 흐름 동안 메모리에만 보관하고 응답의 조직·역할을 화면에 표시합니다.

### 2. 구급대원 회원가입 변경

기존 경로와 성공 상태는 유지합니다.

```http
POST /api/v1/auth/signups/paramedic
Content-Type: application/json
```

```json
{
  "invitationCode": "1단계에서 앱이 보관한 코드",
  "displayName": "김민준",
  "loginId": "paramedic01",
  "password": "8~64자 비밀번호",
  "contact": "010-0000-0000",
  "collectionUseConsentAccepted": true,
  "collectionUseConsentVersion": "COLLECTION_USE_DEV_1.0",
  "hospitalProvisionConsentAccepted": true,
  "hospitalProvisionConsentVersion": "HOSPITAL_PROVISION_DEV_1.0"
}
```

- `displayName`은 앞뒤 공백을 제거한 2~50자이며 공백만으로 구성할 수 없습니다.
- 로그인 ID, 비밀번호와 연락처 검증은 기존 정책을 유지합니다.
- 두 동의가 모두 `true`이고 각 버전이 서버의 현재 버전과 일치해야 합니다.
- 이름·계정·프로필·동의 2개·가입 코드 `USED` 전환·감사 기록을 하나의 트랜잭션에서 처리합니다.
- 기존 `SignupResponse`의 경로·HTTP 201·계정 및 조직 응답은 유지하며 표시 이름은 로그인 후 내 프로필 API에서 조회합니다.
- 기존 구급대원 가입 본문의 단일 `contactSharingConsentAccepted`, `contactSharingConsentVersion`은 신규 요청에서 사용하지 않습니다.
- 병원 가입 요청의 기존 단일 동의 필드는 변경하지 않습니다.

### 3. 내 프로필 조회

```http
GET /api/v1/paramedics/me
Authorization: Bearer {accessToken}
```

```json
{
  "accountId": "계정 공개 ID",
  "loginId": "paramedic01",
  "displayName": "김민준",
  "organizationId": "조직 공개 ID",
  "organizationName": "강동소방서 3구급대",
  "role": "PARAMEDIC",
  "callbackContact": "010-0000-0000",
  "privacyConsent": {
    "collectionUsePolicyVersion": "COLLECTION_USE_DEV_1.0",
    "hospitalProvisionPolicyVersion": "HOSPITAL_PROVISION_DEV_1.0",
    "consentedAt": "2026-08-04T09:00:00Z",
    "legacyCombined": false
  }
}
```

- 새 가입에서는 두 동의를 같은 서버 시각에 저장하므로 프로필 응답은 공통 `consentedAt`을 반환합니다.
- 기존 단일 동의 계정은 두 버전 자리에 기존 `CONTACT_SHARING_DEV_1.0`을 반환하고 `legacyCombined: true`로 구분합니다.
- 비밀번호 해시, 가입 코드, 토큰, 내부 DB ID와 다른 계정의 정보는 반환하지 않습니다.
- `HOSPITAL_STAFF`와 `SUPER_ADMIN`은 `AUTH_003`으로 거부합니다.

## DB 변경

새 migration `V7__add_paramedic_profile_and_consent_types.sql`을 추가합니다.
기존 V1~V6 migration은 수정하지 않습니다.

### `paramedic_profiles`

1. `display_name VARCHAR(50)` nullable 컬럼을 먼저 추가합니다.
2. 기존 행은 연결된 `user_accounts.login_id`로 `display_name`을 채웁니다.
3. 누락 행이 없는지 확인할 수 있는 형태로 갱신한 뒤 `NOT NULL`로 변경합니다.
4. 신규 가입부터 사용자가 제출한 정규화된 표시 이름을 저장합니다.

### `contact_sharing_consents`

1. `consent_type VARCHAR(40)` nullable 컬럼을 추가합니다.
2. 기존 행은 `CONTACT_COLLECTION_AND_PROVISION`으로 채웁니다.
3. `consent_type`을 `NOT NULL`로 변경합니다.
4. 허용 유형은 다음 세 값으로 제한합니다.
   - `CONTACT_COLLECTION_AND_PROVISION`
   - `CONTACT_COLLECTION_USE`
   - `HOSPITAL_PROVISION`
5. 기존 유니크 키 `(account_id, policy_version)`를 제거합니다.
6. 새 유니크 키 `(account_id, consent_type, policy_version)`를 추가해 한 계정에 동의 유형별 이력을 보관합니다.
7. 기존 행의 문구 버전과 동의 시각은 변경하지 않습니다.

### 설정

기존 병원·레거시 계약용 설정은 유지하고 구급대원 동의 버전 2개를 추가합니다.

```yaml
ersync:
  privacy:
    contact-sharing-consent-version: CONTACT_SHARING_DEV_1.0
    collection-use-consent-version: COLLECTION_USE_DEV_1.0
    hospital-provision-consent-version: HOSPITAL_PROVISION_DEV_1.0
```

- Secret 값이 아니라 버전 식별자이므로 애플리케이션 설정으로 관리합니다.
- 운영 문구가 확정되면 기존 값을 덮어써 과거 동의를 바꾸지 않고 새 버전으로 올립니다.

## 기존 동의 호환 방식

- `ContactSharingConsent`에 동의 유형을 추가하고 같은 테이블·감사 흐름을 유지합니다.
- 병원 회원가입은 계속 `CONTACT_COLLECTION_AND_PROVISION`과 기존 정책 버전을 기록합니다.
- 신규 구급대원 회원가입은 `CONTACT_COLLECTION_USE`, `HOSPITAL_PROVISION` 두 행을 동일한 서버 시각으로 기록합니다.
- 이송 요청 생성 시 연락처 제공 자격은 다음 중 하나를 만족하면 인정합니다.
  - 현재 `HOSPITAL_PROVISION` 버전 동의가 있음
  - 기존 계정에 유효한 `CONTACT_COLLECTION_AND_PROVISION` 동의가 있음
- 신규 수집·이용 동의가 없더라도 기존 단일 동의 계정을 차단하지 않습니다. 이는 Dev 호환을 위한 것이며 응답에서 `legacyCombined`로 구분합니다.
- 기존 동의를 신규 동의로 복제하거나 동의 시각을 현재 시각으로 바꾸지 않습니다.

## 구현 Step

| Step | 작업 | 구현 후 검증 | 완료 기준 |
|---:|---|---|---|
| 1 | API DTO와 정책 객체 정의 | 요청 검증 단위 테스트, 응답에서 비밀 필드 제외 확인 | 코드 확인·회원가입·프로필 응답 필드와 동의 유형이 `spec.md`와 일치함 |
| 2 | V7 migration과 도메인 확장 | H2 전체 컨텍스트 및 MySQL 8.4 migration·JPA validate, V6 데이터 backfill 검증 | 기존 프로필 이름과 단일 동의가 손실 없이 보완되고 신규 제약이 적용됨 |
| 3 | 동의 정책·저장·이송 호환 구현 | 동의별 버전 일치·거부·레거시 이송 생성 테스트 | 신규 두 동의와 기존 단일 동의가 각자의 조건으로 정상 동작함 |
| 4 | 가입 코드 사전 확인 구현 | 유효·사용·만료·폐기·대소문자·비소비 테스트 | 공개 확인 API가 상태를 바꾸지 않고 필요한 조직·역할·동의 버전만 반환함 |
| 5 | 구급대원 회원가입 확장 | 정상 가입, 이름·동의 실패 롤백, 같은 코드 동시 가입 테스트 | 이름·동의 2개가 원자적으로 저장되고 코드는 한 번만 소비됨 |
| 6 | 내 프로필 조회 구현 | 본인 조회, 레거시 조회, 무인증·병원·관리자·비활성·조직 불일치 테스트 | JWT 본인의 화면 정보만 반환하고 다른 역할·계정 접근을 차단함 |
| 7 | 전체 회귀·실행 검증 | `./gradlew clean check`, 로컬 MySQL 실행, readiness, 핵심 API 시나리오 | 기존 병원 가입·인증·이송 요청을 포함한 전체 검사가 통과함 |
| 8 | review와 Flutter 핸드오프 작성 | 문서의 요청·응답·오류를 실제 MockMvc 결과와 대조 | `review.md`와 Flutter 문서가 구현·테스트 결과와 일치함 |

## 변경 범위

| 패키지·파일 | 변경 내용 |
|---|---|
| `db/migration/V7__add_paramedic_profile_and_consent_types.sql` | 표시 이름 추가·기존 값 보완·동의 유형과 유니크 키 확장 |
| `invitation/api` | 공개 확인 요청·응답 DTO와 Controller 추가 |
| `invitation/application` | 읽기 전용 코드 확인과 공통 상태 판정 추가 |
| `invitation/infrastructure/InvitationCodeRepository` | 조직을 함께 읽는 비잠금 다이제스트 조회 추가 |
| `account/api/ParamedicSignupRequest` | 표시 이름과 분리된 동의 2개 필드로 변경 |
| `account/application/AccountSignupService` | 이름·동의 2개 저장과 최종 잠금 재검증 유지 |
| `paramedic/domain/ParamedicProfile` | 정규화된 `displayName` 저장 필드와 생성 계약 추가 |
| `paramedic/api` | `GET /api/v1/paramedics/me` Controller와 응답 DTO 추가 |
| `paramedic/application` | 인증 계정·역할·조직·상태를 검증하는 본인 프로필 조회 서비스 추가 |
| `paramedic/infrastructure/ParamedicProfileRepository` | 계정·조직을 함께 조회하는 본인 프로필 쿼리 유지·보완 |
| `privacy/domain` | `ConsentType`과 유형이 포함된 동의 기록 확장 |
| `privacy/application` | 레거시·수집 이용·병원 제공 버전 검증과 이송 자격 판정 확장 |
| `privacy/infrastructure` | 계정별 동의 목록·유형 및 버전 존재 조회 추가 |
| `global/security/SecurityConfig` | 가입 코드 확인 `POST` 경로만 미인증 접근 허용 |
| `application.yaml`, 테스트 설정 | 구급대원 동의 버전 2개 추가 |
| `src/test` | API·동시성·권한·호환·MySQL migration 회귀 테스트 추가·수정 |
| `docs/handoffs/07-signup-profile-integration/flutter-paramedic.md` | 실제 Flutter 연동 계약 작성 |
| `docs/features/07-signup-profile-integration/review.md` | 실제 구현과 검증 결과 기록 |

## 트랜잭션·동시성

- 사전 확인은 `@Transactional(readOnly = true)`와 비잠금 조회를 사용해 코드를 소비하지 않습니다.
- 최종 회원가입은 현재처럼 코드 다이제스트 행을 `PESSIMISTIC_WRITE`로 잠급니다.
- 잠금 후 상태와 만료 시각, 역할·조직 유형을 다시 확인합니다.
- 계정·프로필·동의 2개 저장 또는 감사 기록 중 하나라도 실패하면 전체 트랜잭션을 롤백해 코드를 `AVAILABLE`로 유지합니다.
- 같은 코드로 두 가입 요청이 동시에 오면 하나만 성공하고 다른 하나는 잠금 해제 후 `INVITATION_003`을 받습니다.
- 사전 확인 직후 관리자가 코드를 폐기하거나 만료 시각이 지나면 최종 가입은 각각 `INVITATION_004`, `INVITATION_002`로 실패합니다.

## 보안·개인정보

- 가입 코드 확인은 원문이 URL·쿼리 문자열·응답에 남지 않도록 `POST` 요청 본문만 사용합니다.
- Controller, Service, 예외와 감사 기록에는 가입 코드 원문·다이제스트를 기록하지 않습니다.
- 프로필 응답은 인증된 본인의 회신 연락처만 제공하고 슈퍼 관리자와 병원 역할을 차단합니다.
- 비밀번호 해시와 토큰은 어떤 신규 DTO에도 포함하지 않습니다.
- 테스트와 핸드오프 예시는 가짜 전화번호와 가짜 조직만 사용합니다.
- 공개 코드 확인 API는 고엔트로피 일회용 코드 전제를 유지하며, 오류 메시지에 조직명이나 코드 일부를 포함하지 않습니다.

## 테스트 계획

### 가입 코드 사전 확인

- [ ] 유효한 `PARAMEDIC` 코드가 조직·역할·만료 시각·동의 버전 2개를 반환함
- [ ] 확인 전후 코드가 `AVAILABLE`이고 감사 기록이 추가되지 않음
- [ ] 대소문자를 바꾼 코드가 `INVITATION_001`로 실패함
- [ ] 사용·만료·폐기 코드가 각각 기존 오류 코드로 실패함
- [ ] 응답에 코드 원문·다이제스트가 없음

### 구급대원 회원가입

- [ ] 2~50자 표시 이름과 동의 2개로 가입하면 프로필·동의 2행·감사 기록이 생성됨
- [ ] 이름 앞뒤 공백이 제거되어 저장됨
- [ ] 이름 누락·1자·51자·공백만 입력이 `COMMON_001`로 실패함
- [ ] 각 동의 거부, 누락 또는 버전 불일치가 `COMMON_001`로 실패함
- [ ] 실패 시 계정·프로필·동의·가입 코드 상태가 모두 롤백됨
- [ ] 같은 일회용 코드 동시 가입은 정확히 하나만 성공함
- [ ] 병원 가입의 기존 요청·응답과 단일 동의 저장은 유지됨

### 내 프로필

- [ ] 신규 구급대원이 로그인 후 이름·소속·로그인 ID·연락처·동의 버전을 조회함
- [ ] 기존 계정은 로그인 ID가 표시 이름으로 반환되고 `legacyCombined: true`임
- [ ] 인증정보가 없으면 401, 병원·슈퍼 관리자는 `AUTH_003` 403임
- [ ] 비활성 계정, 조직 불일치와 프로필 누락이 표준 오류로 처리됨
- [ ] 요청에 다른 계정 ID를 지정할 수 없고 응답에 내부 ID·비밀정보가 없음

### DB·회귀

- [ ] V6까지 적용된 스키마에 기존 프로필·단일 동의를 넣고 V7 적용 후 backfill 값을 확인함
- [ ] MySQL 8.4에서 Flyway와 Hibernate `ddl-auto=validate`가 통과함
- [ ] 기존 단일 동의 계정이 이송 요청을 계속 생성할 수 있음
- [ ] 신규 병원 제공 동의 계정이 이송 요청을 생성할 수 있음
- [ ] 기존 로그인·토큰 갱신·병원 가입·이송 기능 테스트가 통과함
- [ ] `./gradlew clean check`
- [ ] `./scripts/dev-start.sh`
- [ ] `curl http://127.0.0.1:8080/actuator/health/readiness`

## 프론트 핸드오프

- 대상: `FLUTTER_PARAMEDIC`
- Flutter: `docs/handoffs/07-signup-profile-integration/flutter-paramedic.md`
- React: `NONE`
- 구현과 로컬 검증 후 실제 코드 기준으로 다음 내용을 직접 기록합니다.
  - 가입 코드 입력은 한 번이며 최종 가입 요청에 앱이 원문을 자동 포함하는 흐름
  - 코드 대소문자 보존과 목 테스트 코드 제거 주의사항
  - 가입 코드 확인·회원가입·로그인·토큰 갱신·내 프로필 조회 순서
  - 이름·로그인 ID·비밀번호·연락처·동의 버전 제한
  - 사용·만료·폐기·동시 소비 오류 처리와 코드 입력 화면 복귀 조건
  - Access Token 만료 시 갱신 후 프로필을 다시 조회하는 조건
  - 실제 개인정보·가입 코드·토큰을 로그나 오류 문의 내용에 포함하지 않는 규칙

## 유지할 계약

- 개인별 가입 코드는 한 계정 가입에만 사용할 수 있는 일회용 코드입니다.
- 가입 코드 원문은 발급 응답에서만 한 번 노출되고 DB에는 해시만 저장합니다.
- 사용자는 Flutter 1단계에서 가입 코드를 한 번만 입력합니다.
- 최종 가입은 서버가 코드를 잠그고 재검증해야만 성공합니다.
- 병원 가입, 로그인, JWT 발급·갱신과 기존 `SignupResponse` 계약은 유지합니다.
- 공통 오류 응답과 `X-Trace-Id` 계약을 유지합니다.
- 역할, 조직과 본인 소유권을 서버에서 검증합니다.
- 기존 Dev 계정과 이송 데이터는 삭제하지 않습니다.

## 리스크

| 리스크 | 대응 |
|---|---|
| 사전 확인 뒤 다른 사용자가 같은 코드를 먼저 소비함 | 최종 가입 트랜잭션에서 비관적 잠금과 상태 재검증을 수행하고 `INVITATION_003`을 반환 |
| 기존 단일 동의를 신규 두 동의로 잘못 간주함 | DB에는 `CONTACT_COLLECTION_AND_PROVISION`으로 그대로 보존하고 응답에 `legacyCombined`를 표시하며 새 동의 행을 위조하지 않음 |
| V7 backfill 또는 제약이 기존 Dev DB에서 실패함 | nullable 추가 → 값 보완 → NOT NULL·유니크 키 적용 순서로 migration하고 V6→V7 MySQL 테스트 수행 |
| 구급대원 가입 요청 변경으로 Flutter 연동이 실패함 | 실제 요청·오류 예시가 포함된 07 핸드오프를 작성하고 기존 단일 필드에서 새 두 필드로 전환 방법 명시 |
| 공개 확인 API를 통해 코드나 조직정보가 노출됨 | 고엔트로피 코드, POST 본문, 무로그·무응답 원문 원칙을 유지하고 유효한 코드 보유자에게만 최소 조직정보 반환 |
