# 역할별 로그인 아이디 및 로그인 역할 지정 구현 검수

```text
Feature: role-scoped-login-id
Implemented By: backend
Related PR: NONE
Frontend Impact: BOTH
Flutter Handoff: docs/handoffs/11-role-scoped-login-id/flutter-paramedic.md
React Handoff: docs/handoffs/11-role-scoped-login-id/react-hospital-admin.md
```

> 2026-08-05 작업 브랜치의 실제 코드, 자동 테스트와 로컬 MySQL 8.4 실행
> 결과를 기준으로 작성했습니다. 커밋·푸시·PR과 Dev 서버 배포는 수행하지
> 않았습니다.

## 구현 요약

- V9에서 `user_accounts.login_id` 단일 고유 제약을
  `(login_id, role)` 복합 고유 제약으로 교체했습니다.
- JPA와 Repository도 역할 복합 계약으로 맞추고 모호한 `findByLoginId`와
  `existsByLoginId`를 제거했습니다.
- 병원·구급대원 가입은 가입 코드가 확정한 실제 역할 안에서만 아이디 중복을
  검사합니다.
- 슈퍼 관리자 bootstrap은 다른 역할이 같은 아이디를 사용해도 관리자 계정을
  만들 수 있으며 관리자 한 계정 정책은 유지합니다.
- 로그인 요청에 필수 `role`을 추가하고 `loginId + role`로 조회한 실제 DB
  계정의 비밀번호·상태·조직·역할로 토큰을 발급합니다.
- 역할이 없거나 JSON enum이 잘못되면 `COMMON_001`, 존재하지 않는 아이디·역할
  조합과 비밀번호 오류는 모두 `AUTH_004`로 처리합니다.
- 기존 Access Token, Refresh Token 회전과 보호 API 인가 구조는 변경하지
  않았습니다.
- Flutter·React 11번 핸드오프를 작성하고 기존 02·03·07 문서의 로그인 예시와
  아이디 중복 의미가 충돌하지 않게 최신 문서 안내를 추가했습니다.

## 요구사항 체크

| 요구사항 | 결과 | 근거 |
|---|---|---|
| 역할별 로그인 아이디 고유성 | PASS | V9 migration, JPA 복합 unique, H2·MySQL 실제 insert 테스트 |
| 기존 V8 계정 데이터 보존 | PASS | V8 계정 삽입 후 V9 migration, 공개 UUID·조직·역할·상태 동일성 검증 |
| 역할 간 같은 아이디 가입 | PASS | 순차 API/service 가입과 실제 두 스레드 동시 가입 모두 두 계정 생성 |
| 같은 역할의 아이디 중복 차단 | PASS | 다른 두 구급대 조직의 동일 아이디 동시 가입에서 성공 1건·`USER_003` 1건 |
| 로그인 필수 역할 | PASS | 정상 세 역할 enum, 누락·알 수 없는 enum `COMMON_001` 검증 |
| 아이디·역할 복합 인증 | PASS | 같은 `loginadmin` 관리자·병원 계정을 역할별 비밀번호로 분리 로그인 |
| 자격정보 비노출 | PASS | 다른 역할의 올바른 비밀번호·없는 역할·잘못된 비밀번호 모두 `AUTH_004` |
| 실제 DB 계정 기반 토큰 | PASS | 요청 역할별 응답 accountId·organizationId·role이 각 DB 계정과 일치 |
| 슈퍼 관리자 bootstrap | PASS | 같은 아이디의 `PARAMEDIC`을 둔 상태에서 별도 `SUPER_ADMIN` 생성·멱등성 검증 |
| 기존 토큰·업무 회귀 | PASS | Refresh 회전·재사용 차단, 가입·프로필·병원 수신·MVP 여정과 전체 182개 테스트 |
| 대상별 최신 핸드오프 | PASS | Flutter `PARAMEDIC`, React 병원·관리자 고정 역할과 기존 문서 범위 구분 |

## 변경 API·DB

| 구분 | 변경 내용 | 호환성 |
|---|---|---|
| 로그인 API | `POST /api/v1/auth/login` 요청에 필수 `role` 추가 | 기존 역할 없는 요청은 `COMMON_001`; 프론트 동시 전환 필요 |
| 로그인 응답 | 기존 토큰·계정·조직·역할 필드 유지 | 응답 호환 |
| 회원가입 API | 요청·응답 필드 유지, 아이디 중복 범위를 가입 코드 역할 단위로 변경 | 다른 역할 동일 아이디 가입 가능 |
| Refresh·보호 API | 변경 없음 | 기존 토큰과 회전·인가 계약 유지 |
| DB | V9에서 `uk_user_accounts_login_id` 제거, `uk_user_accounts_login_id_role(login_id, role)` 추가 | 기존 행과 외래키 데이터 유지 |

## Spec 이후 변경

- 제품 정책 변경: 없음
- 구현 중 추가된 설계 결정:
  - 기존 핸드오프의 오래된 로그인 예시와 전역 아이디 문구에도 11번 최신 기준
    안내를 추가해 프론트가 서로 다른 계약을 동시에 적용하지 않게 했습니다.
  - 단일 아이디 Repository 메서드는 호환용으로 남기지 않고 제거해 이후 코드가
    역할 없는 조회를 새로 사용하면 컴파일 단계에서 발견되게 했습니다.

## 범위 확인

- spec 밖 추가 작업: 없음
- 의도적으로 제외한 작업:
  - Flutter·React 저장소 코드 수정
  - 계정 DB 분리, 다중 역할 계정, 역할 변경·병합
  - 로그아웃, 비밀번호 변경·복구, 로그인 시도 제한
  - JWT claim·수명과 Refresh 요청 변경

## 테스트 결과

| 테스트 | 결과 | 근거 |
|---|---|---|
| `./gradlew clean check` | PASS | Javadoc·Spotless·전체 자동 테스트 182개 성공, 실패·건너뜀 0 |
| 역할별 인증 집중 테스트 | PASS | 같은 아이디 관리자·병원 정상 로그인, 교차 비밀번호·없는 역할·입력 검증 |
| 가입·bootstrap 집중 테스트 | PASS | 역할 간 동일 아이디, 역할 내 중복, 관리자 동일 아이디 생성 |
| 동시성 테스트 | PASS | 같은 역할 동일 아이디 성공 1·충돌 1, 다른 역할 동일 아이디 두 가입 성공 |
| MySQL 8.4 V8→V9 | PASS | 기존 계정 보존, 역할 간 insert 성공, 같은 역할 unique 위반, 인덱스 두 열 확인 |
| local 실행·readiness | PASS | MySQL 8.4.11에 V9 적용, JPA validate, `{"status":"UP"}` 확인 |
| 기존 MVP 회귀 | PASS | 구급대원 가입→로그인→프로필, 병원 수신, Refresh, 전체 이송 여정 통과 |

### 분기형 전체 여정 검증

| 시나리오 | 충돌·분기 | 최종 결과 |
|---|---|---|
| 역할별 동일 아이디 정상 이송 | 같은 아이디의 구급대원·병원 로그인, 역할 교차 비밀번호와 API 접근, 생성·수락·임상·위치·인계 명령 재전송 | 권한 혼합 없이 `COMPLETED`, 재전송은 기존 결과 반환 |
| 목적지 철회 후 대체 병원 | 두 병원 수락 후 선택 병원 철회, 최신 구급차 위치에서 재탐색, 기존 수락 병원 재선택 | 재탐색 중지 후 대체 병원에서 `COMPLETED` |
| 인계 요청·목적지 철회 경합 | 구급대원 인계 요청과 목적지 병원 철회를 두 스레드에서 동시 실행 | 먼저 잠근 명령만 성공하고, 그 분기에서 안전하게 `COMPLETED` |
| 이송 취소·병원 수락 경합 | 구급대원 취소와 병원 수락을 두 스레드에서 동시 실행 | 최종 `CANCELLED`, 제안 닫힘, 목적지·인계 재개 차단 |
| 인계 확인·위치 갱신 경합 | 병원 인계 확인과 구급차 위치 패킷을 두 스레드에서 동시 실행 | 최종 `COMPLETED`, 완료 뒤 임상·위치·취소 변경 차단 |

- 새 시나리오는
  `src/test/java/com/hansungteam/ersync/mvp/MvpCollisionJourneyIntegrationTest.java`에
  추가했습니다.
- 다섯 시나리오와 기존 가입·목적지·탐색·이송 갱신 동시성 테스트를 묶은 집중
  회귀 실행도 통과했습니다.

## 프론트 핸드오프

| 대상 | 문서 | 실제 코드와 일치 |
|---|---|---|
| Flutter 구급대원 앱 | `docs/handoffs/11-role-scoped-login-id/flutter-paramedic.md` | YES |
| React 병원·관리자 웹 | `docs/handoffs/11-role-scoped-login-id/react-hospital-admin.md` | YES |

- 로그인 요청은 11번 문서가 최신 기준입니다.
- 회원가입·가입 코드·프로필·토큰 갱신의 변경되지 않은 계약은 핸드오프에 적힌
  기존 02·07·09 문서를 계속 사용합니다.

## 남은 리스크

| 리스크 | 영향 | 대응 |
|---|---|---|
| Flutter·React가 아직 필수 `role`을 보내지 않음 | 백엔드 배포 뒤 기존 로그인 요청이 `COMMON_001` 400 | PR 배포 뒤 11번 핸드오프 기준으로 화면별 고정 역할 전환 |
| 현재 브랜치는 Dev 서버에 미배포 | 공개 Base URL은 아직 기존 전역 아이디·역할 없는 로그인 계약 | main 병합·배포 SHA 확인 뒤 가짜 계정으로 역할별 로그인 smoke test |
| Dev 서버가 HTTP | 비밀번호와 토큰 전송에 부적합 | HTTPS 적용 전 테스트 전용 자격정보만 사용 |
