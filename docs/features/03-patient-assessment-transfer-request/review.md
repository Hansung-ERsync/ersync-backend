# 환자 평가 및 이송 요청 생성 구현 검수

```text
Feature: patient-assessment-transfer-request
Implemented By: backend AI collaboration
Related PR: NONE
Frontend Impact: BOTH
Flutter Handoff: docs/handoffs/03-patient-assessment-transfer-request/flutter-paramedic.md
React Handoff: docs/handoffs/03-patient-assessment-transfer-request/react-hospital-admin.md
```

> `spec.md`의 개발용 MVP 범위와 실제 코드·자동 테스트·로컬 E2E 결과를
> 기준으로 작성했습니다. 커밋·푸시·PR과 dev 서버 배포는 수행하지 않았습니다.

## 구현 요약

- 병원·구급대원 가입에 연락처 제공 동의 여부와 문구 버전을 필수로 추가하고,
  계정별 동의 시각을 서버에서 기록합니다.
- 구급대원 연락처는 `ParamedicProfile`에 저장하고 이송 요청 생성 당시 값만
  서버가 `callbackContact` 스냅샷으로 복사합니다.
- `ERSYNC_MVP_1.0` 개발 프로토콜 조회 API와 나이·손상·발생 시각·Pre-KTAS·
  AVPU·활력징후·처치의 교차 필드 검증기를 추가했습니다.
- 인증된 `PARAMEDIC`만 최초 평가와 출발 위치로 `SEARCHING` 요청을 생성합니다.
  계정·조직·연락처는 요청 본문으로 받지 않습니다.
- 환자 기본정보, 발생정보, Pre-KTAS, 의식, 다섯 활력징후와 처치를 구조화된
  원본으로 저장하고 환자·발생·프로토콜·최신 평가·현재 처치를 연결한
  `CurrentPatientSnapshot`을 생성합니다.
- 최신 임상 갱신 순서는 클라이언트 시각이 아니라 신뢰할 수 있는 서버 수신
  시각으로 판정합니다.
- 계정 행 잠금, 계정·멱등성 키 DB 고유 제약과 SHA-256 요청 지문으로 재시도를
  처리합니다. 같은 내용은 기존 결과, 다른 내용은 `COMMON_005`입니다.
- 감사 이벤트에는 안전한 요청 공개 ID만 기록하며 연락처·임상 원문·좌표는
  로그와 생성 응답에 포함하지 않습니다.

## 요구사항 체크

| 요구사항 | 결과 | 근거 |
|---|---|---|
| 가입 연락처·동의 저장 | PASS | 병원·구급대원 가입 통합·동시성 테스트 |
| `PARAMEDIC` 전용 생성 | PASS | JWT 역할, 활성 계정, DB 조직 유형·토큰 조직 재검증 및 권한 테스트 |
| 서버 등록 연락처 자동 연결 | PASS | 생성 요청 DTO에 연락처 필드가 없고 DB 스냅샷 값 검증 |
| 개발 프로토콜 조회·검증 | PASS | 조회 API 및 정상·오류 조합 validator 테스트 |
| 완료 또는 긴급 미완료 Pre-KTAS | PASS | 두 흐름의 validator 및 영속화 통합 테스트 |
| 다섯 활력징후와 공식 미측정 상태 | PASS | 정확히 다섯 종류, 값·측정 불가·환자 거부 통합 테스트 |
| 구조화 임상 원본·현재 요약 | PASS | 환자·발생·프로토콜·최신 평가·현재 처치 포인터와 원본 repository 값 검증 |
| 임상·입력·서버 시각 분리 | PASS | 각 Entity의 별도 열과 snapshot의 서버 수신 시각 기준 갱신 검증 |
| 비정상 배열 요소 검증 | PASS | 활력징후·처치 배열의 `null` 요소가 `COMMON_001` 400인지 검증 |
| 멱등 재시도·동시성 | PASS | 동일 재시도 200, 변경 payload 409, 동시 생성 하나 테스트 |
| 민감정보 비로그 | PASS | 연락처와 정확한 좌표가 캡처 로그에 없는지 검증 |
| MySQL 8.4 호환 | PASS | Testcontainers 및 격리 로컬 DB에서 V1→V3, JPA validate, 실제 생성 성공 |

## 변경 API·DB

| 구분 | 변경 내용 | 호환성 |
|---|---|---|
| API | `POST /api/v1/auth/signups/hospital`에 동의 여부·버전 추가 | 기존 요청은 `COMMON_001`; React 동시 변경 필요 |
| API | `POST /api/v1/auth/signups/paramedic`에 연락처·동의 여부·버전 추가 | 기존 요청은 `COMMON_001`; Flutter 동시 변경 필요 |
| API | `GET /api/v1/assessment-protocols/active` 추가 | 신규, `PARAMEDIC` 전용 |
| API | `POST /api/v1/transport-requests` 추가 | 신규, Bearer와 `Idempotency-Key` 필수 |
| CORS | `Idempotency-Key` 요청 헤더 허용 | 기존 계약 유지 |
| DB | `V2__create_patient_assessment_transport_schema.sql`, `V3__complete_current_patient_snapshot.sql` 추가 | 적용된 migration 변경 없음, 14개 신규 테이블 |

## Spec 이후 변경

- 제품 정책 변경: 없음
- 구현 중 추가된 설계 결정:
  - 요청 지문은 record 필드명과 값을 정규화하고 collection 순서를 정렬한 뒤
    SHA-256으로 계산합니다.
  - 최초 성공은 `201 Created`, 같은 요청 재시도는 `200 OK`로 구분합니다.
  - 생성 응답에는 환자정보·위치·연락처를 넣지 않습니다.
  - 현재 환자 요약은 환자·발생·프로토콜·최신 평가·현재 처치 포인터를 포함하고
    마지막 갱신 순서는 서버 수신 시각으로 판정합니다.
  - 배열 내부 `null`은 Bean Validation으로 거절하고 MVP에 없는 최대 나이 제한은 제거했습니다.

## 범위 확인

- spec 밖 추가 작업: 없음
- 의도적으로 제외한 작업:
  - 병원 후보 탐색·전송, 병원 응답, 목적지 선택
  - 이송 중 임상·위치·ETA 갱신과 인계 완료
  - 상황별 추가 평가와 공식 의료 프로토콜
  - 연락처·환자정보의 운영 보존·삭제 작업

## 테스트 결과

| 테스트 | 결과 | 근거 |
|---|---|---|
| `./gradlew clean check` | PASS | 전체 컴파일, 포맷 검사와 자동 테스트 63개 성공 |
| Testcontainers MySQL 8.4 | PASS | 20개 전체 테이블, V1→V3, CHECK·FK·고유 제약, JPA validate, 요청 생성 확인 |
| local 실행·readiness | PASS | 로컬 MySQL 8.4와 포트 18080에서 `{"status":"UP"}` |
| 로컬 E2E | PASS | 관리자 로그인→조직·코드→구급대원 가입·로그인→프로토콜→요청 생성·재시도 |
| 생성 결과 | PASS | `ERSYNC_MVP_1.0`, `SEARCHING`, 재시도 HTTP 200, 같은 공개 ID |

## 프론트 핸드오프

| 대상 | 문서 | 실제 코드와 일치 |
|---|---|---|
| Flutter 구급대원 앱 | `docs/handoffs/03-patient-assessment-transfer-request/flutter-paramedic.md` | YES |
| React 병원·관리자 웹 | `docs/handoffs/03-patient-assessment-transfer-request/react-hospital-admin.md` | YES |

## 남은 리스크

| 리스크 | 영향 | 대응 |
|---|---|---|
| 개발 프로토콜은 공식 의료 기준이 아님 | 실제 환자 판단에 사용하면 안 됨 | 응답에 `DEVELOPMENT`, Pre-KTAS에 `DEV_UNCONFIRMED` 표시; 테스트 데이터만 사용 |
| 개인정보 동의 문구·보존기간 미확정 | 실제 연락처·환자정보 운영 불가 | 운영 전 법적·업무 검토와 새 동의 버전 확정 |
| dev 서버 배포 미검증 | AWS 환경 값과 실제 배포 상태는 아직 모름 | PR 병합 후 main 배포 readiness·commitSha와 실제 API를 별도 확인 |
