# 병원·구급대원 자기 프로필 수정 구현 검수

```text
Feature: 20-self-profile-update
Implemented By: Codex
Related PR: NONE
Frontend Impact: BOTH
Flutter Handoff: docs/handoffs/20-self-profile-update/flutter-paramedic.md
React Handoff: docs/handoffs/20-self-profile-update/react-hospital-admin.md
```

## 구현 요약

- 기존 자기 프로필 경로에 병원·구급대원 전체 수정 `PUT` API를 추가했습니다.
- 병원 주소·상세주소 정규화 정책을 가입과 수정에서 공유하고 기존 이름·연락처 정책도 재사용했습니다.
- 역할·활성 계정·조직·프로필 소유권과 구급대원 연락처 동의를 서버에서 다시 확인합니다.
- 프로필 쓰기 잠금과 단일 트랜잭션으로 동시 전체 수정 및 병원 수신 상태 변경과의 경합을 직렬화했습니다.
- 기존 이송 요청의 구급대원 연락처와 기존 병원 제안의 위치·연락처 스냅샷은 유지하고 이후 생성 데이터부터 수정값을 사용합니다.
- 수정 성공 시 민감한 값 없이 역할별 프로필 변경 감사 이벤트를 저장합니다.

## 요구사항 체크

| 요구사항 | 결과 | 근거 |
|---|---|---|
| 병원 위치·연락처 전체 수정 | PASS | 병원 API 정상·trim·상세주소 제거 테스트 |
| 구급대원 이름·연락처 전체 수정 | PASS | 구급대원 API 정상·동의 유지 테스트 |
| 가입과 같은 검증 정책 | PASS | 공통 병원 주소 정책, `ParamedicProfilePolicy`, `ContactPolicy` 재사용 |
| 수신 상태·계정·조직·동의 유지 | PASS | 응답·재조회와 경합 통합 테스트 |
| 기존/신규 이송 스냅샷 분리 | PASS | `ProfileUpdateSnapshotIntegrationTest` |
| 역할·조직·프로필·동의 접근 제어 | PASS | 양쪽 API 401·403·404·409 테스트 |
| 실패 전체 롤백·감사 원자성 | PASS | 검증·동의 실패 후 프로필·감사 불변 검증 |
| 동시 수정의 완전한 필드 묶음 | PASS | 병원·구급대원·수신 상태 3개 경합 테스트 |
| 민감정보 비로그·감사 최소화 | PASS | 감사 Entity에는 action·공개 ID·시각·traceId만 저장 |
| 기존 전체 기능 회귀 없음 | PASS | 전체 225 tests |

## 변경 API·DB

| 구분 | 변경 내용 | 호환성 |
|---|---|---|
| API | `PUT /api/v1/hospitals/me` 추가 | Additive |
| API | `PUT /api/v1/paramedics/me` 추가 | Additive |
| 기존 조회 | 두 `GET /me` 요청·응답 변경 없음 | 호환 |
| 병원 수신 상태 | 기존 별도 `PUT .../receiving-status` 변경 없음 | 호환 |
| 감사 | `HOSPITAL_PROFILE_UPDATED`, `PARAMEDIC_PROFILE_UPDATED` 추가 | 내부 additive |
| DB | 새 migration 없음, 기존 프로필·감사 테이블 사용 | 호환 |

## Spec 이후 변경

- 제품 정책 변경: 없음
- 구현 중 추가된 설계 결정: 구급대원 조회와 수정이 같은 연락처 동의 해석을 사용하도록 응답 조립 책임을 공통 구성으로 분리했습니다.

## 범위 확인

- spec 밖 추가 작업: 병원 가입 주소 정규화 코드를 공통 정책으로 이동했으나 외부 계약은 변경하지 않았습니다.
- 의도적으로 제외한 작업: 서버 Geocoding, 로그인 ID·조직·역할·비밀번호 수정, 프로필 변경 이력 전용 화면·테이블, 기존 이송 스냅샷 수정

## 테스트 결과

| 테스트 | 결과 | 근거 |
|---|---|---|
| 대상 프로필 API·동시성·스냅샷 | PASS | 19 tests, 실패·오류·생략 0 |
| `./gradlew clean check` | PASS | 225 tests, 실패·오류·생략 0; Javadoc·Spotless 포함 |
| MySQL 8.4·Flyway V1~V13·JPA validate | PASS | `MySqlDatabaseIntegrationTest` 7 tests, 생략 0 |
| local 실행·readiness | PASS | 기존 로컬 DB V10→V13 정상 적용, `{"status":"UP"}` |
| 로컬 종료 상태 | PASS | Spring Boot만 종료, `ersync-mysql-local` healthy 유지 |
| 프론트 E2E | NOT_RUN | main 병합 전 로컬 백엔드 구현 |

## 프론트 핸드오프

| 대상 | 문서 | 실제 코드와 일치 |
|---|---|---|
| Flutter 구급대원 앱 | `docs/handoffs/20-self-profile-update/flutter-paramedic.md` | YES |
| React 병원·관리자 웹 | `docs/handoffs/20-self-profile-update/react-hospital-admin.md` | YES |

## 남은 리스크

| 리스크 | 영향 | 대응 |
|---|---|---|
| 주소와 좌표의 실제 일치 여부를 서버가 확인하지 않음 | 잘못된 지도 핀은 병원 탐색·목적지 표시 정확도에 영향 | 웹에서 지도 확인 후 전체 묶음 제출, 서버는 형식·범위 검증 |
| 기존 이송에는 수정 전 연락처·위치가 유지됨 | 사용자가 현재 프로필 변경을 진행 중 이송 변경으로 오해 가능 | 양쪽 핸드오프에 스냅샷 적용 시점 명시 |
| 실제 Flutter·React 연동 미실행 | 화면 입력·오류 표시 상태는 아직 미확인 | main 배포 후 가짜 데이터로 클라이언트 E2E 수행 |
| Dev 서버는 HTTP 구간이 남아 있음 | 실제 연락처·정확한 좌표 테스트 부적합 | HTTPS 완료 전 가짜 연락처·테스트 좌표만 사용 |
