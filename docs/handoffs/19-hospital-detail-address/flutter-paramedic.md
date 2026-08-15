# 수락 병원 목적지 정보 Flutter 구급대원 앱 핸드오프

```text
Feature: hospital-detail-address
Backend Feature: docs/features/19-hospital-detail-address/
Related PR: NONE
Available After: MAIN_MERGE
Breaking Change: NO
```

## 변경 요약

- 기존 병원 탐색 응답에 병원 주소·상세주소·좌표 네 필드가 추가됩니다.
- 네 필드는 제안 상태가 `ACCEPTED`인 경우에만 제공됩니다.
- 현재 목적지 병원도 `ACCEPTED`이므로 같은 필드를 사용합니다.
- 새 지도 API는 없습니다. 앱이 반환 좌표로 외부 지도 앱을 실행합니다.

## 사용자 흐름

| 순서 | 앱 동작 | API | 결과 |
|---:|---|---|---|
| 1 | 병원 응답 대기 | 병원 탐색 GET | 주소·좌표 없이 상태 표시 |
| 2 | 병원이 수락 | SSE 후 병원 탐색 GET | 수락 카드에 주소·좌표 표시 |
| 3 | 구급대원이 목적지 선택·변경 | 기존 목적지 선택 POST | 선택 카드의 좌표로 길찾기 가능 |
| 4 | 병원이 수락 철회 | SSE 후 병원 탐색 GET | 철회 카드의 주소·좌표 제거 |

## API

### `GET /api/v1/transport-requests/{requestId}/hospital-search`

- 인증: `Authorization: Bearer {accessToken}`
- 역할: 활성 `PARAMEDIC`
- 소유권: 로그인한 구급대원이 생성한 이송 요청만 조회
- 성공: `200 OK`

`offers[]`의 추가 필드:

```json
{
  "offerId": "OFFER_UUID",
  "hospitalName": "한성대학교병원",
  "hospitalContact": "02-1234-5678",
  "hospitalAddress": "서울특별시 성북구 삼선교로16길 116",
  "hospitalDetailAddress": "본관 1층 응급의료센터",
  "hospitalLatitude": 37.5821,
  "hospitalLongitude": 127.0105,
  "status": "ACCEPTED",
  "currentDestination": true
}
```

| 필드 | 타입 | Nullable | 설명 |
|---|---|---:|---|
| `hospitalAddress` | string | YES | 제안 생성 당시 기본주소 |
| `hospitalDetailAddress` | string | YES | 제안 생성 당시 선택 상세주소 |
| `hospitalLatitude` | number | YES | 제안 생성 당시 응급실 위도 |
| `hospitalLongitude` | number | YES | 제안 생성 당시 응급실 경도 |

서버의 JSON 설정에 따라 `null` 필드는 생략될 수 있습니다. 앱은 필드 누락과
명시적 `null`을 같은 상태로 처리합니다.

## 상태별 공개 계약

| 제안 상태 | 주소·좌표 | 앱 처리 |
|---|---|---|
| `PENDING` | 모두 `null` 또는 생략 | 응답 대기만 표시 |
| `ACCEPTED` | 기본주소·좌표 제공, 상세주소는 nullable | 수락 카드·목적지 후보·길찾기에 사용 |
| `REJECTED` | 모두 `null` 또는 생략 | 거절 상태와 사유만 표시 |
| `ACCEPTANCE_WITHDRAWN` | 모두 `null` 또는 생략 | 위치 정보 제거, 다시 선택 금지 |
| `NO_RESPONSE` | 모두 `null` 또는 생략 | 주소·좌표 표시 금지 |

- `currentDestination=true`인 제안은 항상 `ACCEPTED`입니다.
- 수락 병원이 여러 곳이면 각 카드의 주소와 좌표를 비교할 수 있습니다.
- 목적지 변경 후에도 다른 `ACCEPTED` 병원의 주소·좌표는 유지됩니다.
- 주소는 진행 중 요청에 저장된 스냅샷입니다. 앱에서 병원 프로필을 따로 조회하지 않습니다.

## 지도 실행

앱은 `hospitalLatitude`, `hospitalLongitude`를 외부 지도 앱의 목적지 좌표로
전달합니다. 상세주소는 사용자 안내 문구이며 좌표를 대체하지 않습니다.
좌표가 없으면 지도 이동 버튼을 활성화하지 않습니다.

## 오류와 복구

| 상황 | 서버 계약 | 앱 처리 |
|---|---|---|
| 통신 실패 | 기존 오류 응답 | 마지막 화면 유지 후 GET 재시도 |
| 앱 재실행 | GET이 권위 상태 반환 | 전체 제안 목록과 현재 목적지 복구 |
| SSE 재연결 | 이벤트에 주소·좌표 없음 | GET을 다시 호출 |
| 다른 대원의 요청 | `404` | 해당 요청 화면 종료 |
| 역할 불일치 | `403 AUTH_003` | 인증 상태 확인 |

## 연동 확인

- [ ] `ACCEPTED` 카드에 기본주소와 nullable 상세주소 표시
- [ ] 좌표로 지도 앱 실행
- [ ] `PENDING`·거절·철회 카드에서 네 필드 미표시
- [ ] 복수 수락과 목적지 변경 후 주소 유지
- [ ] 철회 후 주소·좌표 즉시 제거
- [ ] SSE 수신·앱 복구 뒤 병원 탐색 GET 재조회
