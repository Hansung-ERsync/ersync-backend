# 병원 프로필·응급실 수신 상태 구현 계획

```text
Feature: hospital-profile-receiving-state
Author: Codex
Frontend Contract: docs/contracts/03-hospital-profile-receiving-state.md
```

> 병원 조직 프로필과 수신 상태 변경을 한 PR에서 완성합니다.

## 구현 Step

| Step | 작업 | 완료 기준 |
|---:|---|---|
| 1 | 병원 프로필 Flyway migration 추가 | `hospital_profiles` 테이블 생성 |
| 2 | hospital 도메인·JPA·Repository 추가 | 수신 상태와 프로필 영속 모델 작성 |
| 3 | 병원 프로필 application service 작성 | 조직 유형, 권한, 좌표 검증 적용 |
| 4 | 병원 프로필 API 작성 | `GET/PUT /api/v1/hospital/profile` |
| 5 | 수신 상태 API 작성 | `PUT /api/v1/hospital/receiving-status` |
| 6 | 통합·권한·검증 테스트 작성 | 성공/실패/권한/상태 변경 검증 |
| 7 | 로컬 검사 실행 | `clean check`, local readiness 통과 |
| 8 | review와 프론트 계약 작성 | 실제 구현 기준 문서 갱신 |

## 변경 패키지

| 패키지·파일 | 변경 내용 |
|---|---|
| `hospital` | 병원 프로필과 수신 상태 API·서비스·영속성 |
| `db/migration` | 병원 프로필 테이블 migration |
| `docs/contracts` | 프론트 연동 계약 |

## DB 변경

- `hospital_profiles`

## 테스트 목록

- [x] 단위 테스트
- [x] 통합 테스트
- [x] 권한·조직 테스트
- [x] 동시성·멱등성 테스트
- [x] `./gradlew clean check`

## 프론트엔드 전달

- 영향: `YES`
- 계약: `docs/contracts/03-hospital-profile-receiving-state.md`
- 완료 조건: 구현·검증 후 계약 작성

## 건드리면 안 되는 계약

- 계정·조직·가입 코드 인증 계약
- OFF가 기존 수락·이송 요청을 닫지 않는 정책
- 병원 탐색·offer 도메인 상태 변경
- 환자 임상 정보와 위치 접근 범위

## 리스크

| 리스크 | 대응 |
|---|---|
| 병원 프로필 없는 상태에서 ON 변경 | `HOSPITAL_001`로 거부 |
| 좌표 정밀도와 향후 거리 검색 | numeric 좌표와 인덱스로 시작하고 검색 기능에서 거리 쿼리 검증 |
| 이전 PR 의존성 | 계정·조직 PR 병합 후 이 브랜치 PR을 main 기준으로 정리 |
