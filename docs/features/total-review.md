# 백엔드 MVP 통합 리스크 리뷰

```text
기준 Commit: c9ab032e309e689d28cd27350971cfff6e74dd0c
검수 범위: 기능 문서 01~19, Flyway V1~V13
Next Feature Readiness: READY
```

정상 기능과 해결된 항목은 제외하고 현재 남은 문제만 기록합니다.

## 차단 문제

P0·P1 문제는 확인되지 않았습니다.

## 남은 리스크

| 등급 | 문제 | 종료 조건 |
|---|---|---|
| P2 | 공개 가입 코드 확인 API에 요청 제한과 실패 감시가 없음 | IP·코드 기준 제한 및 실패 지표 추가 |
| P2 | 롤백 컨테이너도 최신 Secret을 사용하므로 잘못된 설정은 이전 이미지로 복구되지 않을 수 있음 | 이미지와 설정의 복구 기준 분리 |
| P2 | Cloudflare에서 EC2까지 HTTP이며 프론트 E2E를 실행하지 않음 | 가짜 데이터만 사용하고 클라이언트별 E2E 수행 |
| P3 | 프로토콜 문서는 `PROTOCOL_001` 가능성을 적지만 현재 버전 불일치는 모두 `PROTOCOL_002`이며 `PROTOCOL_003`은 미사용 | 문서와 오류 분기 정렬 |
| P3 | MySQL 테이블 검증 목록에서 `transport_lifecycle_commands`가 제외됨 | 테이블 존재 단언에 해당 항목 추가 |

P2·P3는 다음 기능 진행을 차단하지 않습니다.

## 검증 상태

| 검증 | 결과 |
|---|---|
| 전체 백엔드 검사 | PASS - 216 tests, 실패·오류·생략 0 |
| MySQL 8.4 | PASS - 7 tests, 실패·오류·생략 0, Flyway V13 |
| 로컬 readiness | `UP` |
| PR #28 Backend CI | PASS |
| Dev 자동 배포 | PASS - `c9ab032` |
| Dev readiness·버전 | `UP`, 서버 SHA와 기준 Commit 일치 |
| 프론트 E2E | NOT_RUN |
| 운영 적합성 | NOT_ASSESSED - HTTP 데모 환경 |
