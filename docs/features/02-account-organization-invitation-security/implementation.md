# 계정·조직·가입 코드·인증 구현 계획

```text
Feature: account-organization-invitation-security
Author: Codex
Frontend Contract: docs/contracts/02-account-organization-invitation-security.md
```

> 계정 기반 진입 흐름과 관리자 조직·가입 코드 관리를 한 PR에서 완성합니다.

## 구현 Step

| Step | 작업 | 완료 기준 |
|---:|---|---|
| 1 | 계정·조직·가입 코드·Refresh Token 스키마 추가 | Flyway migration과 JPA 매핑 작성 |
| 2 | 도메인 enum과 Repository 추가 | 조직 유형, 역할, 코드 상태를 타입으로 관리 |
| 3 | JWT·Refresh Token 인증 기반 추가 | Bearer 토큰으로 보호 API 인증 |
| 4 | Bootstrap 슈퍼 관리자 생성 | 설정이 있을 때만 최초 관리자 생성 |
| 5 | 관리자 조직·가입 코드 API 구현 | `SUPER_ADMIN`만 접근 가능 |
| 6 | 가입 코드 확인·회원가입·로그인·내 계정 API 구현 | 공개 코드 확인·가입·로그인과 인증 조회 동작 |
| 7 | 단위·통합·권한 테스트 작성 | 성공, 실패, 권한, 해시 저장 검증 |
| 8 | review와 프론트 계약 작성 | 실제 코드와 테스트 기준 문서화 |

## 변경 패키지

| 패키지·파일 | 변경 내용 |
|---|---|
| `auth` | 로그인, 회원가입, 토큰, 인증 필터 |
| `organization` | 조직 등록·조회 도메인과 API |
| `invitation` | 가입 코드 발급·조회·폐기 도메인과 API |
| `global/security` | 인증 필터와 Security 설정 확장 |
| `db/migration` | 계정 기반 테이블 생성 |

## DB 변경

- `organizations`
- `user_accounts`
- `invitation_codes`
- `refresh_tokens`

## 테스트 목록

- [x] 단위 테스트
- [x] 통합 테스트
- [x] 권한·조직 테스트
- [x] 동시성·멱등성 테스트
- [x] `./gradlew clean check`

## 프론트엔드 전달

- 영향: `YES`
- 계약: `docs/contracts/02-account-organization-invitation-security.md`
- 완료 조건: 구현·검증 후 계약 작성

## 건드리면 안 되는 계약

- 공통 오류 응답과 추적 ID
- 역할·조직 데이터 접근 범위
- 가입 코드 원문 1회 노출과 해시 저장
- 환자 임상 정보와 위치 권한 제한

## 리스크

| 리스크 | 대응 |
|---|---|
| JWT 구현 오류 | 서명·만료·위조 토큰 테스트 추가 |
| 초대 코드 동시 사용 | DB 잠금과 상태 재검증으로 1회 사용 보장 |
| Bootstrap 계정 오남용 | 설정값이 있을 때만 생성하고 기본 비밀번호를 코드에 두지 않음 |
| 병원 가입 추가 정보 미구현 | hospital profile 기능으로 명시 분리 |
