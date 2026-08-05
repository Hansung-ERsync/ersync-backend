# 병원 내 정보·수신 상태 조회 구현 계획

```text
Feature: hospital-profile-receiving-status
Author: AI-assisted backend
Handoff Targets: REACT_HOSPITAL_ADMIN
```

> `Policy Decision Status: NONE`인 `spec.md`, 기존 병원 가입·수신 상태 변경
> 코드와 React 병원 웹의 현재 화면을 기준으로 작성했습니다. 기존 API와 DB를
> 변경하지 않고 인증된 병원 본인 조회 API만 추가합니다.

## 설계 요약

- 선택한 방식:
  - `GET /api/v1/hospitals/me` 전용 Controller와 읽기 전용 Query Service를 추가합니다.
  - Query Service가 JWT에서 확인된 계정 ID로 `UserAccount`와 `HospitalProfile`을 조회하고 계정·역할·조직·프로필 연결을 다시 검증합니다.
  - 응답 전용 DTO가 계정·조직·응급실 정보와 서버의 실제 `receivingStatus`만 반환합니다.
  - 기존 `PUT /api/v1/hospitals/me/receiving-status`와 `HospitalReceivingStatusResponse`는 변경하지 않습니다.
  - 기존 테이블과 `findByAccountPublicId` 조회를 사용하므로 migration을 추가하지 않습니다.
- 선택 이유:
  - 구급대원 `GET /api/v1/paramedics/me`와 같은 본인 조회 패턴을 사용해 역할별 계약을 일관되게 유지할 수 있습니다.
  - 로그인 응답에 병원 도메인 정보를 섞지 않아 공통 인증 계약과 Refresh Token 회전 계약을 변경하지 않습니다.
  - React 병원 웹이 로그인·새로고침 때 한 번 조회해 계정 화면과 수신 상태를 함께 복구할 수 있습니다.
  - 병원 ID를 요청으로 받지 않으므로 다른 병원 프로필을 추측해 조회할 경로가 없습니다.
- 검토한 대안과 제외 이유:
  - `GET /api/v1/hospitals/me/receiving-status`만 추가하는 방식은 수신 상태는 해결하지만 현재 웹의 계정 정보 화면에 조직명·주소·연락처를 제공하지 못해 제외합니다.
  - 로그인·토큰 갱신 응답에 병원 정보를 추가하는 방식은 모든 역할이 공유하는 인증 DTO를 병원 전용 정보와 결합하고 기존 계약 변경 범위를 넓혀 제외합니다.
  - 브라우저 `localStorage`를 계속 서버 상태처럼 사용하는 방식은 다른 브라우저·기기와 실제 DB 상태가 어긋날 수 있어 제외합니다.
  - 병원 정보 수정 API까지 함께 추가하는 방식은 현재 요청과 MVP 범위를 넘어가므로 제외합니다.

## API 계약 계획

### `GET /api/v1/hospitals/me`

- 인증·역할: Bearer JWT, `HOSPITAL_STAFF`
- 요청 Path·Query·Body: 없음
- 성공 HTTP: `200 OK`
- 트랜잭션: `readOnly = true`
- 상태·감사 변경: 없음

성공 응답:

```json
{
  "accountId": "ACCOUNT_UUID",
  "loginId": "hospital01",
  "role": "HOSPITAL_STAFF",
  "organizationId": "ORGANIZATION_UUID",
  "organizationName": "한성대학교병원",
  "hospitalId": "HOSPITAL_UUID",
  "address": "서울특별시 성북구 삼선교로 16길",
  "latitude": 37.5821000,
  "longitude": 127.0105000,
  "contact": "02-1234-5678",
  "receivingStatus": "ON",
  "updatedAt": "2026-08-05T01:00:00Z"
}
```

| 필드 | 서버 출처 | 비고 |
|---|---|---|
| `accountId`, `loginId`, `role` | `UserAccount` | 비밀번호·해시 제외 |
| `organizationId`, `organizationName` | 인증 계정의 `Organization` | 병원 조직만 허용 |
| `hospitalId`, `address`, `latitude`, `longitude`, `contact` | `HospitalProfile` | 자기 병원 가입 정보 |
| `receivingStatus`, `updatedAt` | `HospitalProfile` | 현재 DB 상태와 프로필 최종 변경 시각 |

### 유지할 기존 계약

- `PUT /api/v1/hospitals/me/receiving-status`
  - 요청 `{ "status": "ON|OFF" }` 유지
  - 응답의 `status` 필드명 유지
  - 상태 변경 감사 기록 유지
- `POST /api/v1/auth/login`, `POST /api/v1/auth/tokens/refresh`
  - 인증 응답 DTO 변경 없음
- 병원 가입과 병원 요청 탐색
  - 가입 직후 `OFF`, `ON` 병원만 신규 후보라는 기존 동작 유지

## 권한·데이터 검증 계획

조회 서비스는 다음 순서로 검사합니다.

1. 인증 주체의 역할이 `HOSPITAL_STAFF`인지 확인합니다.
2. JWT 계정 공개 ID로 현재 `UserAccount`를 다시 조회합니다.
3. 계정이 활성 상태이고 실제 DB 역할도 `HOSPITAL_STAFF`인지 확인합니다.
4. 계정 조직이 존재하고 활성 `HOSPITAL` 조직인지 확인합니다.
5. JWT 조직 ID와 현재 계정 조직 ID가 같은지 확인합니다.
6. 계정에 연결된 `HospitalProfile` 한 건을 조회합니다.
7. 프로필의 계정·조직이 인증 계정·조직과 같은지 다시 확인합니다.
8. 모든 검증이 끝난 뒤 응답 DTO로 필요한 필드만 매핑합니다.

오류는 기존 `ErrorCode`를 재사용합니다.

| 조건 | 오류 | HTTP |
|---|---|---:|
| 인증 없음 | `AUTH_001` | 401 |
| 유효하지 않은 토큰·비활성 계정 | `AUTH_002` | 401 |
| 병원 관계자가 아닌 역할 | `AUTH_003` | 403 |
| 계정·조직·JWT·프로필 연결 불일치 | `COMMON_004` | 403 |
| 병원 프로필 없음 | `HOSPITAL_001` | 404 |

## 구현 Step

| Step | 작업 | 완료 기준 |
|---:|---|---|
| 1 | `HospitalProfileResponse` 추가 | 스펙의 12개 필드를 정확한 타입으로 제공하고 민감 필드가 DTO에 존재하지 않음 |
| 2 | `HospitalProfileQueryService` 추가 | 읽기 전용 트랜잭션에서 계정·역할·조직·프로필 연결을 검증하고 자기 프로필만 반환 |
| 3 | `HospitalProfileController` 추가 | `GET /api/v1/hospitals/me`와 `HOSPITAL_STAFF` 메서드 보안이 적용되고 Path·Query로 대상 ID를 받지 않음 |
| 4 | 정상·상태 일치 통합 테스트 | 가입 직후 `OFF`, 기존 PUT으로 `ON` 변경 후 GET 재조회, 전체 응답 필드를 실제 DB 값으로 검증 |
| 5 | 권한·오류·정보 노출 테스트 | 미인증·구급대원·관리자·조직 불일치·프로필 누락 차단과 비밀번호·토큰·가입 코드 미노출 검증 |
| 6 | 동시 변경·기존 계약 회귀 검증 | 같은 계정의 동일·반대 상태 PUT과 GET/PUT 동시 실행을 검증하고 병원 가입, 로그인, 수신 상태 변경, `OFF/ON` 탐색 자격과 기존 전체 테스트가 그대로 통과 |
| 7 | React 핸드오프·review 작성 | 웹의 조회 시점, 응답·오류, PUT 후 재조회와 `localStorage` 대체 기준을 실제 코드에 맞게 기록 |
| 8 | 전체 로컬 검증 | `./gradlew clean check`, Docker MySQL 기동과 readiness가 통과하고 결과를 `review.md`에 기록 |

## 변경 범위

| 패키지·파일 | 변경 내용 |
|---|---|
| `hospital/api/HospitalProfileController.java` | 인증된 병원 본인 조회 GET 진입점 추가 |
| `hospital/api/HospitalProfileResponse.java` | 프론트에 공개할 계정·조직·병원·수신 상태 응답 정의 |
| `hospital/application/HospitalProfileQueryService.java` | 활성 계정·병원 역할·조직·프로필 소유권 검증과 응답 조립 |
| `hospital/infrastructure/HospitalProfileRepository.java` | GET은 기존 조회를 유지하고, 상태 PUT은 같은 병원 행의 동시 변경을 직렬화하는 잠금 조회 사용 |
| `hospital/application/HospitalReceivingService.java` | 상태 변경 시 병원 프로필을 비관적 쓰기 잠금으로 조회하여 동시 PUT의 버전 충돌 방지 |
| `src/test/.../hospital/HospitalProfileIntegrationTest.java` | 정상·PUT 연계·권한·오류·민감 필드 미노출 통합 테스트 |
| `src/test/.../hospital/HospitalProfileConcurrencyIntegrationTest.java` | 동일·반대 상태 PUT 및 GET/PUT 동시 실행 통합 테스트 |
| `docs/handoffs/09-hospital-profile-receiving-status/react-hospital-admin.md` | React 병원 웹 전용 실제 연동 계약 |
| `docs/features/09-hospital-profile-receiving-status/review.md` | 구현·회귀·로컬 실행 결과 기록 |

## DB 변경

- 없음
- 신규 GET은 기존 `user_accounts`, `organizations`, `hospital_profiles`만 읽습니다.
- 기존 상태 PUT은 `hospital_profiles` 행을 잠근 뒤 수정하며 별도 조회 캐시·상태 테이블을 만들지 않습니다.
- 현재 관계가 모두 단건이므로 새 인덱스가 필요하지 않습니다.

## 테스트 계획

- [x] 가입 직후 병원 프로필 GET이 `OFF`와 계정·조직·응급실 전체 공개 필드를 반환
- [x] 기존 PUT으로 `ON` 변경 후 GET이 최신 `ON`과 갱신 시각을 반환
- [x] 같은 계정의 반복 조회가 상태·감사·DB 데이터를 변경하지 않음
- [x] 인증 없는 요청은 `AUTH_001`
- [x] `PARAMEDIC`, `SUPER_ADMIN`은 `AUTH_003`
- [x] 비활성 계정·조직, JWT 조직 불일치와 프로필 연결 불일치 차단
- [x] 병원 프로필 누락은 `HOSPITAL_001`
- [x] 응답에 `password`, `passwordHash`, `accessToken`, `refreshToken`, `invitationCode`가 없음
- [x] 기존 `HospitalReceivingIntegrationTest`와 병원 탐색 관련 테스트 회귀 없음
- [x] 같은 계정이 동일 상태를 동시에 PUT해도 두 요청이 성공하고 최종 목표 상태 유지
- [x] 같은 계정이 반대 상태를 동시에 PUT하면 순서대로 처리되고 최종 상태가 유효한 `ON/OFF` 중 하나로 일치
- [x] GET과 PUT 동시 실행 시 부분 상태·영속성 오류 없이 완료되고 후속 GET이 최종 상태 반환
- [x] MySQL 8.4에서 반대 상태 동시 PUT이 실제 행 잠금으로 직렬화됨
- [x] MySQL 호환성: 스키마 변경 없음; 전체 Testcontainers·로컬 MySQL 회귀로 확인
- [x] `./gradlew clean check`
- [x] `./scripts/dev-start.sh` 후 `/actuator/health/readiness`가 `UP`

## React 웹 연동 계획

- 로그인 세션 복구 뒤 `GET /api/ersync/hospitals/me`를 호출할 수 있도록 병원 웹의 백엔드 프록시 allowlist에 `GET hospitals/me`가 필요합니다.
- 대시보드 첫 진입과 새로고침 때 `receivingStatus`를 서버 기준 초기값으로 사용합니다.
- 기존 `localStorage` 값은 실제 상태의 기준으로 사용하지 않습니다.
- 수신 상태 PUT 성공 시 응답으로 즉시 화면을 갱신하고, 새로고침·재연결 시 GET으로 다시 복구합니다.
- 계정 정보 화면은 `organizationName`, `address`, `contact`, `loginId` 등 응답 필드를 표시할 수 있습니다.
- `AUTH_002`이면 기존 Refresh Token 회전을 한 번 시도하고, 복구 불가 인증 오류는 로그인 화면으로 이동합니다.
- 이 백엔드 작업에서는 `/Users/pangdasian/Desktop/inteliJ/ersync-front-web` 코드를 수정하지 않습니다.

## 프론트 핸드오프

- 대상: `REACT_HOSPITAL_ADMIN`
- Flutter: `NONE`
- React: `docs/handoffs/09-hospital-profile-receiving-status/react-hospital-admin.md`
- 구현과 로컬 검증 후 실제 코드 기준으로 작성

## 유지할 계약

- `spec.md`의 제품 동작과 완료 조건
- 공통 오류 응답과 `X-Trace-Id`
- JWT 계정·역할·조직과 현재 DB의 재검증
- 병원은 자기 프로필만 조회하며 다른 병원 ID를 입력받지 않음
- 슈퍼 관리자는 환자 임상정보와 위치정보를 조회할 수 없음
- 기존 병원 가입·로그인·토큰 갱신·수신 상태 변경 API 호환성
- 기존 수신 `OFF` 전환은 이미 수락·이동 중 요청을 변경하지 않음

## 리스크

| 리스크 | 대응 |
|---|---|
| 웹의 브라우저 저장값과 서버 상태가 다름 | GET 응답을 단일 기준으로 사용하고 최초 조회 전에는 `UNKNOWN` 또는 로딩 상태 유지 |
| 다른 병원 또는 역할에 프로필이 노출됨 | 대상 ID를 입력받지 않고 JWT·DB 계정·조직·프로필 연결을 다중 검증 |
| 응답에 자격정보나 불필요한 개인정보가 포함됨 | Entity 직접 반환을 금지하고 허용 필드만 가진 전용 DTO 및 부재 단언 테스트 사용 |
| 조회 추가가 기존 PUT 계약을 깨뜨림 | 기존 Controller·요청·응답 DTO를 유지하고 PUT→GET 회귀 통합 테스트 추가 |
| Lazy 연관 조회가 트랜잭션 밖에서 실패하거나 쿼리가 늘어남 | 읽기 전용 트랜잭션 안에서 매핑하고 필요할 때만 계정·조직 fetch 조회를 명시적으로 추가 |
| 같은 병원 공용 계정을 여러 화면에서 동시에 변경해 버전 충돌 발생 | 상태 PUT에만 병원 프로필 행의 비관적 쓰기 잠금을 적용하고 동일·반대 값 동시 테스트로 검증 |
| 초기 GET 응답이 사용자 PUT 응답보다 늦게 도착해 화면이 과거 상태로 돌아감 | 프론트는 초기 조회 중 토글을 잠그거나 요청 순서를 관리하고, 상태가 불명확하면 GET으로 재조회 |
