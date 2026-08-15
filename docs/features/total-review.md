# 백엔드 MVP 통합 리스크 리뷰

```text
기준: origin/main 9adf3d8 + 기능 18 작업 내용
검수 일시: 2026-08-15 KST
자동 테스트: PASS
현재 브랜치 Dev 배포: PENDING
Next Feature Readiness: READY
```

정상 구현된 기능 설명은 생략합니다. 이 문서는 해결된 차단 문제와 후속 확인
항목만 기록합니다.

## 1. 해결된 차단 문제

| 심각도 | 문제 | 결과 | 근거 |
|---|---|---|---|
| P1 | 거절 병원의 환자정보 조회 범위 충돌 | RESOLVED | 기능 18에서 HISTORY를 최소 응답으로 통일하고 상세·임상·위치를 `404 TRANSPORT_005`로 차단 |

현재 유효한 정책은 다음과 같습니다.

```text
병원이 요청 거절
→ 제안은 HISTORY로 이동
→ 상태·거절 사유·처리 시각만 조회
→ 환자 상세·임상 이력·위치 조회 차단
```

## 2. 후속 리스크

| 심각도 | 문제 | 필요한 조치 |
|---|---|---|
| P2 | 공개 가입 코드 확인 API에 요청 제한과 실패 감시가 없음 | IP·코드 기준 제한과 감시 정책 추가 |
| P2 | 롤백 시 이전 이미지도 새 Secret을 사용해 설정 오류는 복구하지 못할 수 있음 | 이미지 실패와 설정 실패의 복구 정책 분리 |
| P2 | 기능 05 핸드오프에서 최신 15~17 계약으로 바로 이동할 수 없음 | 후속 핸드오프 링크 추가 |
| P2 | HTTP Dev 서버의 가짜 데이터 전용 제한이 최신 핸드오프에 없음 | 실제 환자·계정·GPS 사용 금지 명시 |
| P2 | Nginx SSE, Naver ETA, Cloudflare 로그인과 프론트 E2E 미실행 | 클라이언트별 Dev E2E 수행 |
| P3 | 프로토콜 문서는 오류 두 종류를 적지만 구현은 `PROTOCOL_002`만 반환 | 문서 또는 오류 분기 정렬 |
| P3 | MySQL 테스트가 31개 테이블 중 한 개를 직접 확인하지 않음 | `transport_lifecycle_commands` 검증 목록 추가 |

## 3. 기능 18 검증 상태

| 검증 | 결과 |
|---|---|
| 대상 API·lifecycle 테스트 | PASS - 13 tests, 실패 0 |
| `./gradlew clean check` | PASS - 213 tests, 실패·오류·생략 0 |
| MySQL 8.4 집중 테스트 | PASS - 7 tests, 실패·오류·생략 0 |
| Flyway·JPA | PASS - V1~V12와 JPA validate 통과 |
| 로컬 readiness | `UP` |
| 현재 브랜치 Dev 배포 | PENDING - main 병합 전 |
| 프론트 실제 E2E | NOT_RUN |
| 운영 적합성 | NOT_ASSESSED - HTTPS·법률·의료 검토 범위 밖 |

P2·P3는 별도 작업으로 분리할 수 있으며 다음 기능 진행을 차단하지 않습니다.
