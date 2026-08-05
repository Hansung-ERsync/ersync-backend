# 병원 내 정보·수신 상태 조회 구현 검수

```text
Feature: hospital-profile-receiving-status
Implemented By: AI-assisted backend
Related PR: NONE
Frontend Impact: REACT_HOSPITAL_ADMIN
Flutter Handoff: NONE
React Handoff: docs/handoffs/09-hospital-profile-receiving-status/react-hospital-admin.md
```

> 2026-08-05 현재 작업 브랜치의 실제 코드와 로컬 검증 결과를 기준으로
> 작성했습니다. React 병원 웹 코드는 변경하지 않았습니다.

## 구현 요약

- 인증된 병원 공용 계정이 `GET /api/v1/hospitals/me`로 자기 계정·조직·응급실 정보와 서버의 실제 수신 상태를 조회할 수 있게 했습니다.
- JWT 계정·역할·조직을 현재 DB와 다시 대조하고 병원 프로필의 계정·조직 연결까지 검증합니다.
- 응답 전용 DTO에 허용된 12개 필드만 두어 비밀번호·토큰·가입 코드와 환자·구급대원 정보가 섞이지 않게 했습니다.
- 조회는 읽기 전용이며 상태와 감사 기록을 변경하지 않습니다.
- 기존 수신 상태 PUT 계약을 변경하지 않고, PUT으로 `ON` 변경한 뒤 GET이 같은 DB 상태를 반환하는 흐름을 검증했습니다.
- 충돌 테스트에서 발견한 동시 PUT의 낙관적 잠금 오류를 상태 변경 전용 행 잠금으로 보완했습니다.
- 기존 스키마를 유지해 migration을 추가하지 않았습니다.

## 요구사항 체크

| 요구사항 | 결과 | 근거 |
|---|---|---|
| 병원 본인 정보·실제 수신 상태 조회 | PASS | `HospitalProfileIntegrationTest`의 12개 응답 필드·가입 직후 `OFF` 검증 |
| 기존 PUT 후 GET 상태 일치 | PASS | PUT `ON` 성공 뒤 같은 토큰의 GET `receivingStatus: ON` 검증 |
| 서버 DB를 단일 상태 기준으로 사용 | PASS | 요청에 병원 ID가 없고 JWT 계정으로 `HospitalProfile` 조회 |
| 역할·조직·소유권 검증 | PASS | 미인증·구급대원·관리자·비활성 계정·조직 불일치·프로필 누락 통합 테스트 |
| 민감정보 최소화 | PASS | 전용 DTO와 비밀번호·해시·토큰·가입 코드 필드 부재 단언 |
| 읽기 무변경 | PASS | 반복 GET 전후 감사 이벤트 수 불변 검증, 읽기 전용 트랜잭션 |
| 동일 상태 동시 PUT | PASS | 같은 병원 행을 순서대로 변경하며 두 요청 성공·감사 기록·최종 상태 검증 |
| 반대 상태 동시 PUT | PASS | `ON/OFF` 요청 모두 성공하고 마지막 반영 상태를 후속 GET으로 검증 |
| GET·PUT 동시 실행 | PASS | 두 요청 모두 오류 없이 완료되고 후속 GET이 최종 `ON` 반환 |
| 기존 가입·인증·수신·탐색 호환 | PASS | 병원 집중 회귀와 전체 회귀 159개 통과 |
| React 단독 연동 문서 | PASS | 실제 GET·기존 PUT·오류·상태 복구 계약을 09 핸드오프에 기록 |

## 변경 API·DB

| 구분 | 변경 내용 | 호환성 |
|---|---|---|
| API | `GET /api/v1/hospitals/me` 추가 | 신규 조회 API, 기존 계약 영향 없음 |
| API | 기존 `PUT /api/v1/hospitals/me/receiving-status` 유지 | 요청·응답·감사 계약 변경 없음 |
| API | 로그인·Refresh·병원 가입 응답 유지 | 기존 계약 변경 없음 |
| DB | 없음 | V1~V8 스키마와 기존 데이터 유지 |

## Spec 이후 변경

- 제품 정책 변경: 없음
- 구현 중 추가된 설계 결정:
  - 별도 fetch repository 메서드를 추가하지 않고 읽기 전용 트랜잭션 안에서 기존 `findByAccountPublicId` 결과를 매핑했습니다.
  - 계정 조직이 실제 `HOSPITAL` 유형인지와 프로필 조직·계정 연결을 스펙보다 구체적으로 방어 검증했습니다.
  - 실제 충돌 테스트에서 동시 상태 변경의 `ObjectOptimisticLockingFailureException`을 재현해 기존 PUT의 프로필 조회에 비관적 쓰기 잠금을 추가했습니다.

## 범위 확인

- spec 밖 추가 작업: 없음
- 의도적으로 제외한 작업:
  - 병원 정보 수정
  - React 병원 웹 코드 수정
  - 수신 상태 변경 SSE
  - 관리자 병원 프로필 조회

## 테스트 결과

| 테스트 | 결과 | 근거 |
|---|---|---|
| 최초 집중 테스트 | PASS | 신규 프로필 5개와 기존 수신 상태 2개 통과 |
| 충돌 집중 테스트 | PASS | 동일 상태 PUT, 반대 상태 PUT, GET·PUT 동시 실행 3개 통과 |
| MySQL 8.4 동시 PUT | PASS | Testcontainers MySQL에서 `ON/OFF` 동시 변경 모두 성공하고 최종 유효 상태 검증 |
| 병원 계약 집중 회귀 | PASS | 가입 6, 인증 4, 프로필 5, 수신 2, 병원 탐색 5개로 총 22개 통과 |
| `./gradlew clean check` | PASS | 2026-08-05 전체 159개, 실패·건너뜀 0, 컴파일·Javadoc·Spotless 포함 |
| MySQL 8.4·Flyway | PASS | 로컬 부팅에서 MySQL 8.4.11 연결, 기존 migration 8개 검증, schema V8 최신 확인 |
| local 실행·readiness | PASS | `./scripts/dev-start.sh`, `GET /actuator/health/readiness` → `{"status":"UP"}` |
| 로컬 종료·데이터 유지 | PASS | Spring Boot와 MySQL 컨테이너만 종료, Docker volume 삭제 없음 |

## 프론트 핸드오프

| 대상 | 문서 | 실제 코드와 일치 |
|---|---|---|
| Flutter 구급대원 앱 | `NONE` | N/A |
| React 병원·관리자 웹 | `docs/handoffs/09-hospital-profile-receiving-status/react-hospital-admin.md` | YES |

React 병원 웹에서 필요한 후속 연동은 다음과 같습니다.

- 백엔드 프록시 allowlist에 `GET hospitals/me` 허용
- 로그인·새로고침 뒤 자기 병원 GET 호출
- GET의 `receivingStatus`를 실제 화면 상태로 사용
- 브라우저 `localStorage`의 마지막 수신 값을 서버 상태 기준으로 사용하지 않음
- 계정 정보 화면에 조직명·주소·연락처 등 필요한 응답 필드 연결

## 남은 리스크

| 리스크 | 영향 | 대응 |
|---|---|---|
| React 병원 웹이 아직 새 GET을 호출하지 않음 | 현재 화면은 브라우저별 마지막 값 또는 `UNKNOWN`을 표시 | 09 핸드오프 기준으로 프론트 연동 후 Dev 통합 테스트 |
| Dev 서버 배포 전 | 공개 Base URL에서는 아직 새 API를 사용할 수 없음 | Ready PR merge 후 배포 SHA·readiness 확인 |
| Dev 서버가 HTTP | 실제 병원 연락처와 계정정보 전송에 부적합 | HTTPS 적용 전 테스트 데이터만 사용 |
