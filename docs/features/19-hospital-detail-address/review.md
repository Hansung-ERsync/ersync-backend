# 병원 상세주소 및 목적지 정보 구현 검수

```text
Feature: 19-hospital-detail-address
Implemented By: Backend AI Agent
Related PR: NONE
Frontend Impact: BOTH
Flutter Handoff: docs/handoffs/19-hospital-detail-address/flutter-paramedic.md
React Handoff: docs/handoffs/19-hospital-detail-address/react-hospital-admin.md
```

## 구현 요약

- 병원 가입과 자기 프로필에 nullable 상세주소를 추가했습니다.
- V13에서 기존 제안 기본주소를 backfill하고 이후 제안의 주소를 스냅샷합니다.
- 구급대원 병원 탐색 응답은 `ACCEPTED` 제안에만 주소·상세주소·좌표를 제공합니다.

## 요구사항 체크

| 요구사항 | 결과 | 근거 |
|---|---|---|
| 상세주소 trim·선택 입력·200자 검증 | PASS | 가입 API 통합 테스트 |
| 자기 병원 프로필 상세주소 반환 | PASS | 병원 프로필 통합 테스트 |
| 제안 생성 시 주소 스냅샷 | PASS | H2·MySQL 실제 저장 검증 |
| 수락 병원만 주소·좌표 공개 | PASS | 대기·수락·목적지 변경·철회 시나리오 |
| 기존 권한·거리·ETA 계약 유지 | PASS | 전체 회귀 테스트 |

## 변경 API·DB

| 구분 | 변경 내용 | 호환성 |
|---|---|---|
| 병원 가입 | 선택 `detailAddress` 추가 | 하위 호환 |
| 병원 자기 프로필 | nullable `detailAddress` 추가 | 하위 호환 |
| 구급대원 병원 탐색 | 상태별 nullable 주소·좌표 4개 추가 | 하위 호환 |
| DB | V13 프로필 상세주소와 제안 주소 스냅샷 | 기존 기본주소 backfill |

## Spec 이후 변경

- 제품 정책 변경: 없음
- 구현 중 추가된 설계 결정: 기존 테스트 픽스처 호환을 위해 상세주소 없는 도메인 생성 함수를 유지

## 범위 확인

- spec 밖 추가 작업: 없음
- 의도적으로 제외한 작업: 기존 병원 주소 수정 API, 프론트 코드 변경, 별도 지도 API

## 테스트 결과

| 테스트 | 결과 | 근거 |
|---|---|---|
| 대상 가입·프로필·병원 검색 통합 테스트 | PASS | 2026-08-15 직접 실행 |
| MySQL 8.4·Flyway V1~V13·JPA validate | PASS | Testcontainers 7건, skipped 0 |
| `./gradlew clean check` | PASS | 216건, 실패·오류·생략 0 |
| local 실행·readiness | PASS | 기존 MySQL V12→V13 적용 후 `{"status":"UP"}` |
| `git diff --check` | PASS | 공백 오류 없음 |

## 프론트 핸드오프

| 대상 | 문서 | 실제 코드와 일치 |
|---|---|---|
| Flutter 구급대원 앱 | `docs/handoffs/19-hospital-detail-address/flutter-paramedic.md` | YES |
| React 병원·관리자 웹 | `docs/handoffs/19-hospital-detail-address/react-hospital-admin.md` | YES |

## 남은 리스크

| 리스크 | 영향 | 대응 |
|---|---|---|
| 기존 운영 병원의 상세주소는 `null` | 기본주소만 표시됨 | 필요 시 별도 운영 데이터 보정 |
| Flutter·React 실제 E2E 미실행 | 화면 표시·지도 실행은 미확인 | 프론트 핸드오프 기준으로 연동 검증 |
| EC2·RDS 배포 미실행 | V13 운영 적용 상태 미확인 | main 병합 workflow와 readiness 확인 |
