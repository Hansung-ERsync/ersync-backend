# 웹 전용 응답 DTO 최소화 구현 검수

```text
Feature: 21-web-dto-response-minimization
Implemented By: Codex
Related PR: NONE
Frontend Impact: REACT_HOSPITAL_ADMIN
Flutter Handoff: NONE
React Handoff: docs/handoffs/21-web-dto-response-minimization/react-hospital-admin.md
```

> AI가 실제 코드와 테스트 결과를 기준으로 작성합니다.
> 구현 완료 직후 작성하며 별도의 구현 승인 단계로 사용하지 않습니다.
> 사람은 애자일 주기 종료 시 완료된 review 문서를 모아 검수할 수 있습니다.

## 구현 요약

- 병원·슈퍼 관리자 웹 전용 record에서 스펙에 확정한 미사용 component만 제거했습니다.
- DTO 정적 팩터리와 서비스 매핑을 함께 축소하고 Endpoint, 요청, 권한, 상태 전이와 DB 모델은 유지했습니다.
- 공용·Flutter DTO, SSE·오류 DTO와 수락·거절·인계 확인 명령 응답은 변경하지 않았습니다.
- 제거 필드 부재와 유지 필드를 API 통합 테스트로 검증하고 최신 React 전환 계약을 작성했습니다.

## 요구사항 체크

| 요구사항 | 결과 | 근거 |
|---|---|---|
| 웹 전용 제거 필드가 JSON에 없음 | PASS | 프로필·수신 상태·제안 목록·상세·철회·관리자 조직·가입 코드 통합 테스트의 `doesNotExist` assertion |
| 웹 사용 필드와 기존 기능 유지 | PASS | 상태·사유·시각·페이징·환자정보·명령 가능 여부 및 저장 결과 assertion |
| 공용·Flutter 계약 비변경 | PASS | `git diff --name-only`에서 지정 공용·구급대원 DTO 변경 없음, 전체 기존 테스트 통과 |
| Endpoint·권한·상태·DB 유지 | PASS | 권한·소유권·상태·멱등·감사 테스트 및 MySQL 통합 테스트 통과, migration 없음 |
| React 최신 계약 제공 | PASS | `docs/handoffs/21-web-dto-response-minimization/react-hospital-admin.md` |

## 변경 API·DB

| 구분 | 변경 내용 | 호환성 |
|---|---|---|
| API | 병원·관리자 웹 전용 성공 응답의 스펙 명시 미사용 필드 제거 | 응답 필드 제거이므로 호환되지 않는 변경, 기존 웹 실제 실행 경로는 미사용 확인 |
| DB | 없음 | Entity·Repository·Flyway와 저장 데이터 변경 없음 |

## Spec 이후 변경

- 제품 정책 변경: 없음
- 구현 중 추가된 설계 결정: 없음

## 범위 확인

- spec 밖 추가 작업: 없음
- 의도적으로 제외한 작업: 공용·Flutter DTO, 웹 미호출 API, 전체 명령 응답 제거, `supplementalAssessment`, 요청·DB 변경

## 테스트 결과

| 테스트 | 결과 | 근거 |
|---|---|---|
| `./gradlew clean check` | PASS | 2026-08-20, `BUILD SUCCESSFUL in 1m 31s` |
| local 실행·readiness | PASS | local 프로필·기존 MySQL schema version 13으로 기동, `GET /actuator/health/readiness` → `{"status":"UP"}` |
| 주요 기능 시나리오 | PASS | 관련 API 통합 테스트 9개 클래스 묶음 성공, 별도 `MySqlDatabaseIntegrationTest` 성공 |

## 프론트 핸드오프

| 대상 | 문서 | 실제 코드와 일치 |
|---|---|---|
| Flutter 구급대원 앱 | `NONE` | N/A |
| React 병원·관리자 웹 | `docs/handoffs/21-web-dto-response-minimization/react-hospital-admin.md` | YES / N/A |

## 남은 리스크

| 리스크 | 영향 | 대응 |
|---|---|---|
| 과거 핸드오프의 응답 예시에는 제거 필드가 남아 있을 수 있음 | 프론트가 과거 예시를 그대로 타입화하면 연동 오류 가능 | 21번 React 핸드오프를 최신 응답 계약으로 공유 |
| 응답 축소가 외부 미확인 소비자에게는 호환되지 않음 | 제거 필드 참조 시 `undefined` | 병원·관리자 웹 미사용 근거로 범위를 제한하고 Ready PR에서 최종 확인 |
