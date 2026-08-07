# 8자리 가입 코드 발급 구현 검수

```text
Feature: eight-character-invitation-code
Implemented By: backend AI with engineer decisions
Related PR: NONE
Frontend Impact: BOTH
Flutter Handoff: docs/handoffs/14-eight-character-invitation-code/flutter-paramedic.md
React Handoff: docs/handoffs/14-eight-character-invitation-code/react-hospital-admin.md
Status: IMPLEMENTED_AND_LOCALLY_VERIFIED
```

> `spec.md`의 확정 범위를 구현하고 전체 자동 테스트, MySQL 8.4와 로컬 실행을
> 검증했습니다. 아직 커밋·푸시·PR·Dev 배포는 하지 않았습니다.

## 구현 요약

- 가입 코드 전용 `InvitationCodeGenerator`를 추가했습니다.
- 6바이트 `SecureRandom`을 패딩 없는 Base64 URL로 변환해 정확히 8자리
  `[A-Za-z0-9_-]{8}` 코드를 생성합니다.
- 생성 후보의 SHA-256 다이제스트가 기존 코드와 겹치면 최대 10회 다시
  생성하며 DB 유니크 제약을 최종 방어선으로 유지합니다.
- `InvitationService`만 새 생성기를 사용하고 Refresh Token용
  `SecretDigester.generate()`는 변경하지 않았습니다.
- 신규 8자리 코드와 배포 전에 발급된 기존 43자리 코드를 모두 사전 확인하고
  회원가입에 사용할 수 있습니다.
- API 경로·DTO·오류·코드 상태·감사 계약과 DB 스키마는 변경하지 않았습니다.

## 요구사항 체크

| 요구사항 | 결과 | 근거 |
|---|---|---|
| 신규 코드 정확히 8자리 | PASS | 발급 API와 생성기 단위 테스트에서 정규식 확인 |
| 기존 Base64 URL 문자 유지 | PASS | Java URL-safe Base64 without padding 사용 |
| 원문 1회 반환·digest 저장 | PASS | 발급 응답·DB digest·목록 비노출 통합 테스트 |
| 기존 긴 코드 호환 | PASS | 43자리 코드 사전 확인과 구급대원 회원가입 성공 |
| 대소문자 구분·trim | PASS | 대소문자 변경 거절, 앞뒤 공백 코드 확인 성공 |
| 중복 후보 재생성 | PASS | 결정적 난수로 첫 digest 충돌 뒤 두 번째 후보 반환 |
| Refresh Token 무변경 | PASS | 로그인과 회전 토큰 모두 43자리 확인 |
| MySQL 8.4 | PASS | 8자리 발급·BINARY(32) 저장·회원가입 소비 성공 |

## 변경 API·DB

| 구분 | 변경 내용 | 호환성 |
|---|---|---|
| 관리자 발급 | `POST /api/v1/admin/invitation-codes`의 신규 `code`가 정확히 8자리 | 응답 구조 동일, 값 길이만 변경 |
| 코드 사전 확인 | 새 8자리와 기존 긴 코드 모두 허용 | 기존 API 호환 |
| 병원·구급대원 가입 | 새 8자리와 기존 긴 코드 모두 허용 | 기존 API 호환 |
| 목록·폐기 | 변경 없음, `invitationCodeId`는 기존 UUID | 기존 API 호환 |
| Refresh Token | 변경 없음, 기존 43자리 | 기존 인증 계약 유지 |
| DB | migration 없음, SHA-256 `BINARY(32)`와 unique 유지 | 기존 데이터 변경 없음 |

## Spec 이후 변경

- 제품 정책 변경: 없음
- 기술 구현 구체화:
  - 저장된 모든 상태의 코드와 후보 digest를 비교해 과거 원문도 다시 발급하지 않습니다.
  - 동일 후보가 10회 반복되면 원문을 노출하지 않고 내부 오류로 중단합니다.

## 범위 확인

- spec 밖 제품 기능: 없음
- 의도적으로 제외:
  - 숫자 전용 코드, 대소문자 무시, 체크섬, 수동 코드 설정
  - 기존 긴 코드 일괄 변경
  - 유효기간·상태·1회 사용 정책 변경
  - Flutter·React 코드 수정

## 테스트 결과

| 테스트 | 결과 | 근거 |
|---|---|---|
| `./gradlew clean check` | PASS | 208 tests, failures 0, errors 0, skipped 0 |
| 생성기 단위 테스트 | PASS | 8자리·digest·충돌 재시도·최대 시도 실패 3개 |
| 관리자 발급 | PASS | 8자리 원문, digest 저장, 목록 원문 비공개 |
| 사전 확인·회원가입 | PASS | 신규·legacy, trim·case, 상태·소비 계약 통과 |
| Refresh Token 회귀 | PASS | 로그인·회전 모두 `[A-Za-z0-9_-]{43}` |
| MySQL 8.4 | PASS | 기존 V1~V10 validate와 DB 통합 테스트 6개 통과 |
| local 실행·readiness | PASS | MySQL schema 10, readiness `UP`, version `local` |
| 정적 확인 | PASS | Spotless·Javadoc·`git diff --check` 통과 |

## 프론트 핸드오프

| 대상 | 문서 | 실제 코드와 일치 |
|---|---|---|
| Flutter 구급대원 앱 | `docs/handoffs/14-eight-character-invitation-code/flutter-paramedic.md` | YES |
| React 병원·관리자 웹 | `docs/handoffs/14-eight-character-invitation-code/react-hospital-admin.md` | YES |

## 남은 리스크

| 리스크 | 영향 | 대응 |
|---|---|---|
| 프론트가 입력 길이를 즉시 8자로 고정 | 기존 AVAILABLE 긴 코드 입력 불가 | 전환 기간에는 기존 긴 코드도 입력·전송하도록 핸드오프 명시 |
| 프론트가 대문자·소문자로 변환 | 다른 digest가 되어 코드 거절 | 입력 원문 대소문자 유지 명시 |
| Dev 미배포 | 프론트가 아직 8자리 발급을 확인할 수 없음 | PR merge 후 배포 SHA와 실제 발급 응답 확인 |
| 짧은 코드 노출 | 제3자가 유효 코드 사용 가능 | 원문 비로그·1회 표시·만료·폐기·1회 사용 정책 유지 |
