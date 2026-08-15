# 병원 상세주소 및 목적지 정보 구현 계획

```text
Feature: 19-hospital-detail-address
Author: Backend AI Agent
Handoff Targets: BOTH
```

## 설계 요약

- 선택한 방식: 병원 프로필에 nullable 상세주소를 추가하고 병원 제안에 주소·상세주소를 스냅샷합니다.
- 선택 이유: 기존 프론트 계약과 필드명을 맞추고 진행 중 이송의 목적지 정보를 고정합니다.
- 검토한 대안과 제외 이유: 병원 프로필을 실시간 참조하면 향후 주소 변경 시 진행 중 요청 정보가 바뀌므로 제외합니다.

## 구현 Step

| Step | 작업 | 완료 기준 |
|---:|---|---|
| 1 | V13에 프로필 상세주소와 제안 주소 스냅샷 추가 | 기존 데이터 backfill과 JPA validate 통과 |
| 2 | 병원 가입 DTO·서비스·도메인 확장 | trim·빈 값 null·200자 검증 |
| 3 | 병원 자기 프로필 응답 확장 | `detailAddress` nullable 반환 |
| 4 | 병원 제안 생성 시 주소 스냅샷 | 새 제안에 주소·상세주소 고정 |
| 5 | 구급대원 병원 검색 응답 확장 | `ACCEPTED`에만 주소·좌표 공개 |
| 6 | 가입·프로필·검색·MySQL 테스트 추가 | 정상·실패·권한·스냅샷 검증 |
| 7 | MVP·컨텍스트·핸드오프·review 갱신 | 문서와 실제 계약 일치 |

## 변경 범위

| 패키지·파일 | 변경 내용 |
|---|---|
| account·hospital | 가입 입력, 프로필 저장·조회 |
| hospital search | 제안 주소 스냅샷, 구급대원 응답 공개 |
| Flyway V13 | 컬럼 추가와 기존 기본주소 backfill |
| 통합 테스트·문서 | 계약과 회귀 검증 |

## DB 변경

```text
hospital_profiles.detail_address VARCHAR(200) NULL
hospital_offers.hospital_address_snapshot VARCHAR(255) NOT NULL
hospital_offers.hospital_detail_address_snapshot VARCHAR(200) NULL
```

## 테스트 계획

- [ ] 상세주소 저장·trim·생략·길이 검증
- [ ] 병원 자기 프로필 반환
- [ ] 제안 주소 스냅샷과 상태별 공개 범위
- [ ] 역할·조직·요청 소유권 회귀
- [ ] MySQL V1~V13와 JPA validate
- [ ] `./gradlew clean check`

## 프론트 핸드오프

- 대상: `BOTH`
- Flutter: `docs/handoffs/19-hospital-detail-address/flutter-paramedic.md`
- React: `docs/handoffs/19-hospital-detail-address/react-hospital-admin.md`
- 구현과 로컬 검증 후 실제 코드 기준으로 작성

## 유지할 계약

- 기존 병원 가입 필드와 가입 코드·동의 정책
- 병원 자기 프로필의 역할·조직 검증
- 구급대원 병원 검색의 소유권·상태·거리·ETA 계약
- 공통 오류 응답과 `X-Trace-Id`

## 리스크

| 리스크 | 대응 |
|---|---|
| 기존 병원 상세주소가 비어 있음 | nullable 유지, 자동 생성하지 않음 |
| 진행 중 제안 주소가 바뀜 | 제안 생성 시 주소 스냅샷 저장 |
| 응답 전 병원 위치 노출 | `ACCEPTED` 상태에서만 DTO 필드 설정 |
| migration 이후 NOT NULL 실패 | 기존 제안을 프로필 기본주소로 먼저 backfill |
