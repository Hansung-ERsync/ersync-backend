# 조건부 추가 환자 평가 React 병원·관리자 웹 핸드오프

```text
Feature: supplemental-patient-assessment
Backend Feature: docs/features/13-supplemental-patient-assessment/
Related PR: NONE
Available After: MAIN_MERGE
Breaking Change: NO
Hospital Impact: YES
Admin Impact: NONE
```

> 병원 수용 판단 권한이 유지되는 동안 제안 상세에서 혈당·동공·과거력·
> 알레르기·복용약·감염·격리 우려를 확인하는 계약입니다.

## 변경 요약

- `GET /api/v1/hospitals/me/offers/{offerId}`에 nullable
  `supplementalAssessment`가 추가됐습니다.
- 병원 제안 목록, 종료 최소 이력, clinical timeline, SSE payload는 바뀌지 않았습니다.
- 추가 평가가 없거나 임상 공개 권한이 끝났으면 값은 `null` 또는 생략입니다.
- 슈퍼 관리자에게 추가된 API나 환자정보 권한은 없습니다.

## 인증과 공개 범위

| 대상·상태 | 추가 평가 원문 |
|---|---|
| 활성 `HOSPITAL_STAFF`, 자기 병원 PENDING 제안, 목적지 미선택 | 공개 |
| 활성 `HOSPITAL_STAFF`, 자기 병원 ACCEPTED 제안, 목적지 미선택 | 공개 |
| 현재 목적지로 선택된 ACCEPTED 자기 병원 | 공개 |
| 거절·수락 철회 병원 | 비공개, `null` 또는 생략 |
| 목적지가 정해진 뒤 선택되지 않은 병원 | 기존 상세 정책에 따라 404 또는 비공개 |
| 취소·인계 완료 요청 | 기존 상세 정책에 따라 404, 종료 목록은 최소정보만 |
| 다른 병원 조직 | `404 TRANSPORT_005` |
| `SUPER_ADMIN` | 사용 불가 |

## 공통 계약

- Base URL: `http://13.124.194.249`
- 인증: `Authorization: Bearer {accessToken}`
- 시간: ISO-8601 UTC
- JSON nullable 필드는 서버 설정에 따라 `null`이면 생략될 수 있습니다.
- 실제 환자정보·개인 연락처·정확한 실제 위치가 아닌 테스트 데이터를 사용합니다.

## API. 병원 제안 상세 변경

### `GET /api/v1/hospitals/me/offers/{offerId}`

- 성공: `200 OK`
- Path `offerId`: 자기 병원 목록에서 받은 제안 UUID
- 기존 환자·발생·Pre-KTAS·의식·활력징후·처치·요청자·경로·시각 필드는
  그대로이며 다음 최상위 필드가 추가됩니다.

```json
{
  "supplementalAssessment": {
    "assessedAt": "2026-08-07T10:00:00Z",
    "enteredAt": "2026-08-07T10:01:00Z",
    "serverReceivedAt": "2026-08-07T10:01:02.123456Z",
    "glucoseMgDl": 85,
    "leftPupil": "NORMAL",
    "rightPupil": "SLUGGISH",
    "medicalHistory": "고혈압",
    "allergies": "확인된 알레르기 없음",
    "medications": "혈압약",
    "isolationConcern": false
  }
}
```

| 필드 | 타입 | Nullable | 의미 |
|---|---|---:|---|
| `supplementalAssessment` | object | O | 기록·현재 공개 권한이 없으면 null/생략 |
| `assessedAt` | datetime | X | 실제 확인·측정 시각 |
| `enteredAt` | datetime | X | 구급대원 앱 입력 시각 |
| `serverReceivedAt` | datetime | X | 서버 수신 시각 |
| `glucoseMgDl` | integer | O | 혈당, 단위 mg/dL |
| `leftPupil`, `rightPupil` | enum | O | 둘 다 함께 존재하거나 둘 다 없음 |
| `medicalHistory` | string | O | 주요 과거력 |
| `allergies` | string | O | 알레르기 |
| `medications` | string | O | 복용 약물 |
| `isolationConcern` | boolean | O | `true` 우려 있음, `false` 확인 결과 없음 |

### PupilResponse

| 값 | 표시 의미 |
|---|---|
| `NORMAL` | 정상 반응 |
| `SLUGGISH` | 느린 반응 |
| `FIXED` | 고정 |
| `UNASSESSABLE` | 평가 불가 |

`null`인 개별 항목은 정상이나 없음으로 바꾸지 말고 `기록 없음`으로 처리합니다.

## 목록·상태 변경 처리

- `GET /api/v1/hospitals/me/offers` item에는 추가 평가 원문이 없습니다.
- 목록 카드에는 추가 임상 원문을 표시하지 않고 상세 진입 뒤 응답을 사용합니다.
- 목적지 선택·변경, 수락 철회, 취소, 인계 완료 SSE를 받으면 기존처럼 목록과
  필요 상세을 다시 조회합니다.
- 상태 전환 사이에 권한이 끝나면 상세이 404가 되거나
  `supplementalAssessment`가 null이 될 수 있으므로 캐시한 원문을 화면에서 제거합니다.
- SSE payload 자체에는 추가 평가가 없으며 REST 상세이 최종 기준입니다.

## 오류 처리

이번 변경으로 새 오류 코드는 추가되지 않았습니다.

| 코드 | HTTP | 조건 | 웹 처리 |
|---|---:|---|---|
| `AUTH_001`, `AUTH_002` | 401 | 인증 없음·토큰 오류 | 토큰 갱신 뒤 재조회 |
| `AUTH_003`, `COMMON_004`, `USER_002` | 403 | 병원 역할·조직·계정 상태 오류 | 접근 차단·운영자 확인 |
| `HOSPITAL_001` | 404 | 현재 계정의 병원 프로필 없음 | 가입·프로필 확인 |
| `TRANSPORT_005` | 404 | 다른 조직·숨겨진 제안 상세 | 상세 닫고 자기 목록 재조회 |

공통 오류 문의에는 API 경로, HTTP 상태, `code`, `traceId`만 전달하고 환자정보·
연락처·토큰 원문은 공유하지 않습니다.

## 연동 확인

- [ ] 자기 병원 후보 상세에서 nullable 추가 평가 표시
- [ ] 혈당 단위 mg/dL와 동공 enum 표시
- [ ] 개별 null을 `기록 없음`으로 처리
- [ ] `false` 격리 우려를 null과 구분
- [ ] 거절 뒤 추가 원문 제거
- [ ] 현재 목적지 병원은 계속 조회 가능
- [ ] 목록·종료 이력 카드에 원문을 기대하지 않음
- [ ] 404 또는 권한 종료 뒤 캐시한 환자 추가정보 제거
- [ ] 슈퍼 관리자 화면·권한 변화 없음
