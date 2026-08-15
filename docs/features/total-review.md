# 백엔드 MVP 통합 리스크 리뷰

```text
기준 Commit: b29f7d8
자동 테스트: PASS
Next Feature Readiness: READY
```

정상 기능과 해결 완료 항목은 적지 않습니다. 현재 남은 확인 사항만 유지합니다.

## 남은 리스크

| 등급 | 문제 | 필요한 조치 |
|---|---|---|
| P2 | 공개 가입 코드 확인 API에 요청 제한·실패 감시가 없음 | IP·코드 기준 제한과 감시 정책 추가 |
| P2 | 설정 오류는 이전 이미지 복구만으로 해결되지 않을 수 있음 | 이미지 실패와 설정 실패의 복구 정책 분리 |
| P2 | 일부 과거 핸드오프가 최신 계약으로 연결되지 않음 | 최신 기능 문서 링크 추가 |
| P2 | HTTP Dev 제한과 프론트 E2E 검증이 부족함 | 가짜 데이터만 사용하고 클라이언트별 E2E 수행 |
| P3 | 프로토콜 오류 문서와 구현 분기가 다름 | 문서 또는 오류 분기 정렬 |
| P3 | MySQL 테이블 검증 목록에 한 항목이 빠짐 | `transport_lifecycle_commands` 검증 추가 |

P2·P3는 다음 기능 진행을 차단하지 않습니다.

## 검증 상태

| 검증 | 결과 |
|---|---|
| 전체 백엔드 검사 | PASS - 213 tests, 실패·오류·생략 0 |
| MySQL 8.4 | PASS - 7 tests, 실패·오류·생략 0 |
| 로컬 readiness | `UP` |
| 현재 브랜치 Dev 배포 | PENDING - main 병합 전 |
| 프론트 E2E | NOT_RUN |
| 운영 적합성 | NOT_ASSESSED |

기능 18의 상세 근거는
[`review.md`](18-rejected-hospital-history-privacy/review.md)를 확인합니다.
