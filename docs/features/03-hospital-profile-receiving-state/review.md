# 병원 프로필·응급실 수신 상태 구현 검수

```text
Feature: hospital-profile-receiving-state
Implemented By: Codex
Related PR:
Frontend Impact: YES
Frontend Contract: docs/contracts/03-hospital-profile-receiving-state.md
```

## 구현 요약

- 병원 공용 계정의 자기 병원 프로필 등록·조회 API를 추가했습니다.
- 병원 프로필에는 응급실 주소, WGS84 좌표, 응급실 연락처, 위치 확인 시각을 저장합니다.
- 새 병원 프로필의 수신 상태는 `OFF`로 시작합니다.
- 병원 공용 계정은 자기 병원의 수신 상태를 `ON` 또는 `OFF`로 변경할 수 있습니다.
- `V2__hospital_profile_receiving_state.sql`로 병원 프로필 테이블을 추가했습니다.

## 요구사항 체크

| 요구사항 | 결과 | 근거 |
|---|---|---|
| 병원 프로필 등록·조회 | PASS | 병원 계정 통합 테스트에서 등록 후 조회 성공 |
| 수신 상태 기본값 `OFF` | PASS | 프로필 등록 응답 `receivingStatus=OFF` 검증 |
| 병원 계정 자기 조직 권한 | PASS | `HOSPITAL_STAFF` 토큰으로만 접근, `PARAMEDIC` 403 검증 |
| 좌표와 연락처 검증 | PASS | 범위 밖 위도 요청 `COMMON_001` 검증 |

## 변경 API·DB

| 구분 | 변경 내용 | 호환성 |
|---|---|---|
| API | `PUT /api/v1/hospital/profile`, `GET /api/v1/hospital/profile`, `PUT /api/v1/hospital/receiving-status` | 기존 API 변경 없는 추가 API |
| DB | `hospital_profiles` | 새 Flyway migration |

## 프론트엔드 전달

| 영향 | 계약 |
|---|---|
| `YES` | `docs/contracts/03-hospital-profile-receiving-state.md` |

## Spec 이후 정책 변경

- 없음

## 범위 확인

- spec 범위를 넘어 추가한 작업: 없음
- 의도적으로 제외한 후속 작업: 병원 검색, offer 생성, 병원 관리자 직접 수정

## 테스트 결과

| 테스트 | 결과 | 근거 |
|---|---|---|
| `./gradlew clean check` | PASS | 21개 테스트, 실패·스킵 없음 |
| local 실행·readiness | PASS | Docker MySQL healthy, V2 migration 적용, readiness `UP` |

## 남은 리스크

| 리스크 | 영향 | 대응 |
|---|---|---|
| 없음 |  |  |

## 다음 작업 추천

1. 임상 프로토콜과 환자 평가 DTO 검증 기반을 구현합니다.
