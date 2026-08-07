# 8자리 가입 코드 발급 구현 계획

```text
Feature: eight-character-invitation-code
Author: backend AI
Handoff Targets: BOTH
Status: IMPLEMENTED_AND_VERIFIED
```

> `Policy Decision Status: RESOLVED`인 `spec.md`와 백엔드 `main`의
> `14a3d1e`를 기준으로 작성했습니다.

## 설계 요약

- 선택한 방식:
  - 가입 코드 전용 `InvitationCodeGenerator`를 추가합니다.
  - `SecureRandom`으로 6바이트를 생성하고 패딩 없는 Base64 URL 문자열로
    변환해 정확히 8자를 만듭니다.
  - 새 원문을 기존 `SecretDigester.digest()`로 SHA-256 처리합니다.
  - 저장된 다이제스트와 겹친 후보는 제한된 횟수만큼 다시 생성합니다.
  - `InvitationService`만 새 생성기를 사용하고 `AuthService`의 Refresh Token
    발급은 기존 `SecretDigester.generate()`를 그대로 사용합니다.
- 선택 이유:
  - Base64 URL은 6바이트를 정확히 8자로 표현하므로 문자열을 임의로 자르지
    않아도 48비트 난수 공간을 균등하게 사용할 수 있습니다.
  - 가입 코드 정책을 인증 토큰 생성과 분리해 짧은 사용자 입력값이 Refresh
    Token 보안을 낮추지 않게 합니다.
  - DB의 `BINARY(32)` SHA-256 다이제스트와 유니크 제약을 그대로 사용할 수 있어
    migration과 기존 데이터 변경이 필요하지 않습니다.
- 검토한 대안과 제외 이유:
  - `SecretDigester.SECRET_BYTES`를 6으로 변경: Refresh Token도 8자로 줄어 보안
    회귀가 발생하므로 제외합니다.
  - 기존 43자 원문이나 SHA-256 앞 8자 절단: 불필요한 중간 문자열과 정책 혼동이
    생기므로 제외합니다.
  - UUID 앞 8자 사용: 현재 Base64 URL 형식을 유지하지 않고 32비트만 제공하므로
    제외합니다.
  - 숫자 전용 8자리: 가능한 조합이 `10^8`로 크게 줄어들어 제외합니다.
  - 기존 긴 코드를 8자로 변환: 원문을 DB에 저장하지 않아 변환할 수 없고 기존
    전달값을 깨뜨리므로 제외합니다.

## 코드 구조

### `InvitationCodeGenerator`

위치:

```text
src/main/java/com/hansungteam/ersync/invitation/application/InvitationCodeGenerator.java
```

책임:

```text
6바이트 SecureRandom 생성
→ Base64 URL without padding 변환
→ 정확히 8자인지 내부 불변식 확인
→ SecretDigester로 SHA-256 다이제스트 계산
→ 저장된 다이제스트 존재 여부 확인
→ 중복이면 새 후보 생성, 아니면 GeneratedSecret 반환
```

- 상수:
  - 난수 바이트 수: `6`
  - 결과 문자 수: `8`
  - 중복 후보 최대 시도: `10`
- 기본 생성자는 Spring에서 사용할 `SecureRandom`을 내부 생성합니다.
- package-private 생성자는 테스트가 결정적인 난수 순서를 주입할 수 있게 합니다.
- 반환 타입은 기존 `GeneratedSecret`을 재사용해 원문과 다이제스트를 함께
  전달하되, 원문을 필드나 로그에 보관하지 않습니다.
- 10회 모두 기존 다이제스트와 겹치면 예상하지 못한 시스템 불변식 실패로
  처리합니다. 공개 응답에 후보 코드나 다이제스트를 포함하지 않습니다.

### `SecretDigester`

- `generate()`의 32바이트 생성과 약 43자 Base64 URL 반환은 변경하지 않습니다.
- `digest(String)`만 가입 코드 전용 생성기에서 재사용합니다.
- `AuthService.login()`과 Refresh Token 회전은 기존 호출 경로를 그대로 유지합니다.

### `InvitationCodeRepository`

다음 존재 여부 조회를 추가합니다.

```text
boolean existsByCodeDigest(byte[] codeDigest)
```

- 생성 후보가 이미 저장된 원문과 같은지 SHA-256 다이제스트로만 확인합니다.
- 원문 조회나 전체 코드 목록 로딩은 하지 않습니다.
- 기존 `uk_invitation_codes_digest`가 동시 생성 경합의 최종 방어선입니다.

### `InvitationService`

- 기존 `SecretDigester` 의존성을 `InvitationCodeGenerator`로 교체합니다.
- `issue()`의 조직·역할·유효기간·권한·감사 흐름은 그대로 유지합니다.
- 새 생성기가 반환한 원문의 다이제스트만 `InvitationCode`에 저장하고 원문은
  `IssuedInvitationResponse`에서 한 번 반환합니다.
- 목록·폐기·만료 로직은 변경하지 않습니다.

## 생성 알고리즘

```text
repeat up to 10 times
  randomBytes = SecureRandom 6 bytes
  plainText = Base64UrlWithoutPadding(randomBytes)
  assert plainText.length == 8
  digest = SHA-256(plainText UTF-8)
  if invitation_codes has no same digest
    return GeneratedSecret(plainText, digest)
throw internal invariant failure
```

- Base64 URL 알파벳은 `A-Z`, `a-z`, `0-9`, `-`, `_`입니다.
- 6바이트는 48비트이고 Base64 문자 8개에 손실 없이 대응합니다.
- 대소문자를 변환하거나 구분자를 삽입하지 않습니다.
- 생성 시 DB에 저장된 코드 상태를 구분하지 않습니다. `USED`, `EXPIRED`,
  `REVOKED`를 포함해 과거에 한 번이라도 사용한 동일 원문은 다시 발급하지 않습니다.

## API·DTO 영향

### 관리자 발급

```text
POST /api/v1/admin/invitation-codes
```

- 요청 DTO와 상태 코드는 변경하지 않습니다.
- 발급 성공 응답의 `code`만 신규 발급분부터 `[A-Za-z0-9_-]{8}`입니다.
- `invitation` 메타데이터와 `invitationCodeId`는 변경하지 않습니다.

### 사전 확인·회원가입

```text
POST /api/v1/auth/invitations/validate
POST /api/v1/auth/signups/paramedic
POST /api/v1/auth/signups/hospital
```

- 요청 DTO 길이를 정확히 8자로 제한하지 않습니다.
- 기존 `.trim()`과 SHA-256 조회를 그대로 사용해 새 코드와 기존 긴 코드를 모두
  허용합니다.
- 내부 문자, 대소문자와 `-`, `_`는 변환하지 않습니다.
- 오류 코드와 코드 소비 트랜잭션은 변경하지 않습니다.

### 목록·폐기

- 관리자 목록은 기존처럼 원문과 다이제스트를 반환하지 않습니다.
- 폐기 Path의 `invitationCodeId`는 관리용 UUID이므로 8자리로 변경하지 않습니다.

## DB 변경

- 새 Flyway migration: 없음
- 이유:
  - 원문 길이는 DB에 저장하지 않습니다.
  - SHA-256 결과는 길이와 관계없이 기존 `BINARY(32)`입니다.
  - 기존 `uk_invitation_codes_digest`를 그대로 사용합니다.
- 기존 V1~V10 migration은 수정하지 않습니다.
- MySQL 8.4에서 기존 스키마 validate와 신규 코드 저장·조회·소비를 확인합니다.

## 보안·로그

- 가입 코드 생성은 `java.security.SecureRandom`을 사용합니다.
- 가능한 신규 원문은 `64^8 = 2^48`개입니다.
- 생성 후보와 다이제스트를 INFO·WARN·ERROR 로그에 기록하지 않습니다.
- 원문은 발급 API 성공 응답에만 존재하며 감사 이벤트에는 관리용
  `invitationCodeId`만 기록합니다.
- 목록과 사전 확인 응답은 가입 코드 원문·다이제스트를 반환하지 않습니다.
- Refresh Token은 계속 32바이트 난수와 약 43자 원문을 사용합니다.
- 비정상적으로 중복 후보가 반복돼도 공개 오류는 공통 서버 오류이며 원문이나
  저장 여부를 노출하지 않습니다.

## 호환성

- 신규 발급: 정확히 8자리
- 기존 발급: DB 다이제스트가 유지되므로 원래 전달된 긴 원문으로 계속 검증 가능
- 신규·기존 모두:
  - 앞뒤 공백 제거
  - 대소문자 구분
  - 한 번의 성공한 회원가입만 소비
  - 기존 만료·폐기·감사 정책 유지
- 프론트는 전환 기간에 입력창을 정확히 8자로 강제하지 않습니다. 신규 발급
  화면 표시와 안내만 8자리 기준으로 변경합니다.

## 구현 단계

| Step | 작업 | 완료 기준 |
|---:|---|---|
| 1 | 가입 코드 전용 생성기와 결정적 단위 테스트 추가 | 6바이트가 정확히 8자·허용 문자·SHA-256으로 변환됨 |
| 2 | 다이제스트 존재 조회와 중복 후보 재생성 연결 | 첫 후보 중복 시 다음 후보 반환, 반복 충돌 시 안전하게 실패 |
| 3 | `InvitationService.issue()` 생성기 교체 | 신규 발급 API가 8자리 원문을 한 번만 반환하고 DB에는 digest만 저장 |
| 4 | 사전 확인·회원가입 호환 검증 | 새 코드와 직접 만든 기존 43자 코드가 모두 검증·소비됨 |
| 5 | Refresh Token 회귀 검증 | 로그인·회전 토큰이 기존 43자와 32바이트 digest를 유지 |
| 6 | 권한·상태·동시성 회귀 검증 | 비관리자 발급 차단, USED·EXPIRED·REVOKED와 동시 가입 계약 유지 |
| 7 | 전체 검증과 문서 마무리 | clean check, MySQL 8.4, readiness, review와 양쪽 핸드오프 완료 |

## 테스트 계획

### 단위 테스트

`InvitationCodeGeneratorTest`

- 6바이트 난수가 예상한 8자리 Base64 URL 문자열이 되는지 확인
- 반환 다이제스트가 `SecretDigester.digest(plainText)`와 같은지 확인
- 저장된 다이제스트와 같은 첫 후보를 건너뛰고 두 번째 후보를 반환하는지 확인
- 최대 시도 횟수가 모두 충돌이면 원문 없이 내부 실패하는지 확인

### 관리자 발급 통합 테스트

`AdminInvitationIntegrationTest`

- 응답 `code`가 정확히 `[A-Za-z0-9_-]{8}`인지 확인
- 저장된 값이 원문이 아니라 SHA-256 다이제스트인지 확인
- 목록과 감사 이벤트에 원문·다이제스트가 없는 기존 검증 유지
- 조직·역할 불일치, 폐기와 만료 회귀 확인

### 사전 확인·회원가입 통합 테스트

`InvitationValidationIntegrationTest`

- 새 8자리 코드 사전 확인 성공과 비소비 상태 확인
- 앞뒤 공백이 있는 새 코드도 기존처럼 확인되는지 검증
- 대소문자를 바꾼 코드는 `INVITATION_001`인지 검증
- 직접 생성한 기존 약 43자 코드가 계속 사전 확인되는지 검증

`AccountSignupIntegrationTest`

- 신규 8자리 코드로 병원·구급대원 가입과 1회 소비 확인
- 직접 생성한 기존 긴 코드로 회원가입이 성공하는지 확인
- 기존 연락처·동의·프로필 생성 트랜잭션이 회귀하지 않는지 확인

### 인증 회귀 테스트

`AuthIntegrationTest`

- 로그인과 회전에서 반환한 Refresh Token 길이가 기존 43자인지 확인
- Refresh Token이 가입 코드용 8자리 생성기를 사용하지 않는지 외부 결과로 증명

### 전체·실행 검증

```text
./gradlew clean check
./scripts/dev-start.sh
curl http://127.0.0.1:8080/actuator/health/readiness
```

- 전체 테스트와 정적 검사를 통과합니다.
- MySQL 8.4 기존 V1~V10 schema validate와 가입 코드 흐름을 확인합니다.
- 로컬 readiness `UP` 확인 뒤 Spring 애플리케이션만 종료하고 MySQL 데이터는 유지합니다.

## 위험과 대응

| 위험 | 영향 | 대응 |
|---|---|---|
| 공통 생성기 길이 변경 | Refresh Token이 8자로 줄어듦 | 가입 코드 전용 생성기를 분리하고 Refresh Token 길이 회귀 테스트 추가 |
| 기존 긴 코드 입력을 8자로 제한 | 배포 전 발급 코드 사용 불가 | 요청 DTO는 기존 길이를 유지하고 legacy 통합 테스트 추가 |
| 후보 코드 중복 | 다른 초대와 같은 원문 발급 가능 | digest 사전 조회·재생성과 DB unique 제약을 함께 유지 |
| 대소문자 자동 변환 | 정상 코드가 잘못된 digest로 조회됨 | 서버·핸드오프 모두 대소문자 구분 명시 |
| 짧은 코드 로그 노출 | 유효기간 안에 제3자가 사용할 수 있음 | 원문 비로그·1회 응답·상태·만료 정책 유지 |
| 프론트가 관리 ID도 8자로 오해 | 목록 폐기 API가 실패 | `code`와 `invitationCodeId` 차이를 핸드오프에 명시 |

## 구현 전 확인

- [x] `spec.md`의 `Policy Decision Status`가 `RESOLVED`
- [x] 신규 코드가 정확히 8자리인 제품 결정 확인
- [x] 기존 Base64 URL 문자와 대소문자 구분 유지 확인
- [x] 기존 긴 코드 호환 유지 확인
- [x] Refresh Token 생성 분리 확인
- [x] DB migration 불필요 확인
- [x] Flutter·React 양쪽 핸드오프 대상 확인
