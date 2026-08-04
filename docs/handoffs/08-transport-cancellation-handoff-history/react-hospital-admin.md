# 이송 취소·인계 완료 및 이력 React 병원·관리자 웹 핸드오프

```text
Feature: transport-cancellation-handoff-history
Backend Feature: docs/features/08-transport-cancellation-handoff-history/
Related PR: NONE
Available After: MAIN_MERGE
Breaking Change: NO
Hospital Impact: YES
Admin Impact: NONE
```

> 현재 목적지 병원은 구급대원의 인계 요청을 확인해 이송을 완료할 수 있습니다.
> 기존 제안 목록·상세 응답에는 종료 상태 필드가 추가됐고, 종료된 요청의 임상·
> 연락처·정확한 위치는 다시 제공하지 않습니다.

## 변경 요약

- 현재 목적지 병원 공용 계정이 인계 요청을 확인하는 API가 추가됐습니다.
- `HANDOFF_REQUESTED` 현재 목적지 카드는 확인 전까지 `ACTIVE`에 유지됩니다.
- `COMPLETED`·`CANCELLED` 요청의 자기 병원 제안은 `HISTORY`로 이동합니다.
- 기존 목록·상세 응답에 `canConfirmHandoff`, 종료 시각과 취소 사유가 추가됐습니다.
- 병원 수신 상태가 `OFF`여도 이미 도착한 현재 이송의 인계 확인은 가능합니다.
- 슈퍼 관리자 API와 화면 변경은 없습니다.

## 역할별 사용자 흐름

| 역할 | 순서 | 사용자·웹 동작 | API 호출 | 성공 후 상태 |
|---|---:|---|---|---|
| 병원 | 1 | 활성 제안 확인 | `GET .../offers?view=ACTIVE` | 인계 대기 카드와 확인 가능 여부 표시 |
| 병원 | 2 | `canConfirmHandoff: true`인 카드에서 인계 확인 | `POST .../{offerId}/confirm-handoff` | 요청 `COMPLETED` |
| 병원 | 3 | SSE 수신 또는 명령 성공 뒤 목록 갱신 | ACTIVE·HISTORY 재조회 | 활성 카드 제거, 이력 표시 |
| 관리자 | - | 변경 없음 | 없음 | 환자·이송 정보 접근 없음 |

## 인증과 접근 범위

| 역할 | 인증 | 허용 작업 | 조직·정보 접근 범위 |
|---|---|---|---|
| 병원 관계자 | Bearer Access Token, 활성 `HOSPITAL_STAFF` | 자기 제안 목록·허용된 상세·현재 목적지 인계 확인 | JWT의 자기 병원 조직만, 다른 병원 제안은 404 |
| 슈퍼 관리자 | Bearer Access Token | 이 기능의 병원 API 사용 불가 | 환자 임상·연락처·위치·목적지·취소 이력 조회 불가 |

## API

공통:

- Base URL: `http://13.124.194.249`
- Content-Type: `application/json`
- 시간: ISO-8601 UTC
- 명령 API의 `Idempotency-Key`: 8~100자, `[A-Za-z0-9._:-]`
- JSON의 nullable 필드는 서버 설정에 따라 값이 `null`이면 생략될 수 있습니다.

### `POST /api/v1/hospitals/me/offers/{offerId}/confirm-handoff`

- 목적: 구급대원이 요청한 환자 인계를 현재 목적지 병원이 확인
- 인증·역할: Bearer, 활성 `HOSPITAL_STAFF`
- 조직·정보 접근 범위: `HANDOFF_REQUESTED`인 현재 목적지 제안의 병원 조직만
- 성공 HTTP: `200 OK`
- 요청 본문: 없음

#### 파라미터

| 위치 | 이름 | 타입 | 필수 | Nullable | 제약 |
|---|---|---|---:|---:|---|
| Path | `offerId` | string | O | X | 병원 제안 공개 ID |
| Header | `Authorization` | string | O | X | Bearer Access Token |
| Header | `Idempotency-Key` | string | O | X | 8~100자, 허용 문자만 사용 |

#### 성공 응답

```json
{
  "offerId": "OFFER_UUID",
  "transportRequestId": "REQUEST_UUID",
  "status": "COMPLETED",
  "completedAt": "2026-08-05T01:22:00Z",
  "idempotentReplay": false
}
```

| 필드 | 타입 | Nullable | 설명 |
|---|---|---:|---|
| `offerId` | string | X | 확인한 현재 목적지 제안 공개 ID |
| `transportRequestId` | string | X | 완료된 요청 공개 ID |
| `status` | enum | X | 항상 `COMPLETED` |
| `completedAt` | string(datetime) | X | 서버 완료 시각 |
| `idempotentReplay` | boolean | X | 같은 명령 재시도 응답 여부 |

#### 오류

| 오류 코드 | HTTP | 발생 조건 | 웹에서 필요한 처리 |
|---|---:|---|---|
| `COMMON_001` | 400 | 키 형식 오류 | 요청 전 키 검증 |
| `AUTH_001`, `AUTH_002` | 401 | 인증 없음·토큰 오류 | 토큰 갱신 후 같은 키 재시도 |
| `AUTH_003`, `COMMON_004`, `USER_002` | 403 | 병원 역할·조직·계정 상태 오류 | 접근 차단·운영자 확인 |
| `TRANSPORT_005` | 404 | 제안 없음·다른 조직·현재 목적지가 아님 | 상세 닫고 목록 재조회, 타 병원 정보 미표시 |
| `TRANSPORT_004` | 409 | 인계 요청 상태가 아니거나 취소·완료 등 다른 전이가 먼저 확정 | ACTIVE·HISTORY 재조회 |
| `COMMON_005` | 409 | 같은 lifecycle 키를 다른 명령에 재사용 | 최초 명령 복구 또는 새 동작에 새 키 |

### `GET /api/v1/hospitals/me/offers?view=ACTIVE|HISTORY&page=0&size=20`

- 기존 API 경로·페이징 계약을 유지합니다.
- `HANDOFF_REQUESTED`의 현재 목적지 제안은 확인 전까지 `ACTIVE`입니다.
- `COMPLETED`·`CANCELLED` 요청의 자기 제안은 `HISTORY`입니다.
- 기존 응답 필드 끝에 아래 필드가 추가됐습니다.

```json
{
  "items": [
    {
      "offerId": "OFFER_UUID",
      "transportRequestId": "REQUEST_UUID",
      "transportRequestStatus": "HANDOFF_REQUESTED",
      "offerStatus": "ACCEPTED",
      "currentDestination": true,
      "canWithdraw": false,
      "canConfirmHandoff": true,
      "handoffRequestedAt": "2026-08-05T01:20:00Z",
      "completedAt": null,
      "cancelledAt": null,
      "cancellationReason": null
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "serverNow": "2026-08-05T01:20:01Z"
}
```

> 예시는 이번 기능과 관련된 필드만 표시합니다. 기존 전체 필드는 04~06 병원
> 연동 문서의 계약을 그대로 유지합니다.

| 추가 필드 | 타입 | Nullable | 설명 |
|---|---|---:|---|
| `canConfirmHandoff` | boolean | X | 현재 조직의 현재 목적지·인계 대기 제안일 때만 `true` |
| `handoffRequestedAt` | string(datetime) | O | 구급대원 인계 요청 시각 |
| `completedAt` | string(datetime) | O | 목적지 병원 확인 완료 시각 |
| `cancelledAt` | string(datetime) | O | 이송 취소 시각 |
| `cancellationReason` | enum | O | 취소 사유, 상세 사유는 노출하지 않음 |

종료 이력 item의 기존 임상·거리·ETA·연락처 관련 필드는 `null` 또는 생략됩니다.
병원이 과거에 보낸 `offerStatus`, `respondedAt`, 철회 이력은 사실 그대로 유지됩니다.

### `GET /api/v1/hospitals/me/offers/{offerId}`

- 활성·허용된 기존 상세 응답에 목록과 같은 다섯 lifecycle 필드가 추가됐습니다.
- `canConfirmHandoff: true`일 때만 확인 버튼을 활성화합니다.
- 요청이 `COMPLETED` 또는 `CANCELLED`가 되면 상세·임상 timeline·위치 조회는
  `TRANSPORT_005`로 차단됩니다. 종료 정보는 `HISTORY` 목록의 최소 item을 사용합니다.

## 화면 상태 조건

| 대상 | 조건 | 웹에서 필요한 처리 |
|---|---|---|
| 인계 대기 카드 | `transportRequestStatus: HANDOFF_REQUESTED`, `currentDestination: true` | ACTIVE에 유지, 인계 요청 시각 표시 |
| 확인 버튼 활성화 | `canConfirmHandoff: true` | 중복 클릭 방지, 성공 확인 전 같은 키 유지 |
| 확인 버튼 비활성화 | `canConfirmHandoff: false` | 비목적지·다른 상태에서 호출하지 않음 |
| 활성 카드 제거 | 요청 `COMPLETED` 또는 `CANCELLED` | ACTIVE에서 제거하고 HISTORY 갱신 |
| 종료 이력 | HISTORY의 종료 상태 item | 최소 상태·응답·종료 시각만 표시, 상세 링크 비활성화 |
| 수신 상태 OFF | 이미 진행 중인 현재 목적지 `HANDOFF_REQUESTED` | 확인 버튼 유지 |
| 정렬·페이징 | 기존 `page`, `size` | 상태 변경 뒤 첫 페이지부터 재조회 |

## 상태와 Enum

### 요청 상태

| 값 | 의미 | 웹에서 필요한 처리 |
|---|---|---|
| `HANDOFF_REQUESTED` | 구급대원이 목적지 병원에 인계를 요청함 | 현재 목적지만 ACTIVE, 확인 버튼 조건 검사 |
| `COMPLETED` | 목적지 병원이 인계를 확인함 | 모든 관련 카드 HISTORY 이동 |
| `CANCELLED` | 구급대원이 이송을 취소함 | 모든 관련 카드 HISTORY 이동, 취소 배지·사유 표시 |

### 취소 사유

```text
PATIENT_REFUSED_TRANSPORT
GUARDIAN_SELF_TRANSPORT
SCENE_RESOLVED
OTHER
```

병원 응답에는 `OTHER` 상세 내용이 포함되지 않습니다.

## 오류 처리

공통 오류 응답:

```json
{
  "code": "TRANSPORT_004",
  "message": "변경할 수 없는 이송 요청 상태입니다.",
  "fieldErrors": [],
  "traceId": "TRACE_ID"
}
```

- 오류 문의 시 API 경로, HTTP 상태, `code`, `traceId`를 전달합니다.
- 환자정보·구급대원 연락처·정확한 위치·토큰 원문은 공유하지 않습니다.

## 실시간 이벤트와 재조회

### `GET /api/v1/realtime/events`

병원 조직이 추가로 받을 수 있는 `type`:

```text
TRANSPORT_CANCELLED
HANDOFF_REQUESTED
HANDOFF_COMPLETED
```

| 이벤트 | 수신 대상 | 재조회 |
|---|---|---|
| `TRANSPORT_CANCELLED` | 취소 시점에 아직 활성 관련 제안이 있던 병원 조직 | ACTIVE와 HISTORY |
| `HANDOFF_REQUESTED` | 현재 목적지 병원 조직 | ACTIVE, 허용된 상세 |
| `HANDOFF_COMPLETED` | 요청을 받았던 관련 병원 조직 | ACTIVE와 HISTORY |

```text
id: EVENT_UUID
event: update
data: {"eventId":"EVENT_UUID","type":"HANDOFF_REQUESTED","aggregateType":"TRANSPORT_LIFECYCLE","aggregateId":"COMMAND_UUID","occurredAt":"2026-08-05T01:20:00Z"}
```

- SSE에는 취소 상세·환자 임상정보·구급대원 연락처·정확한 좌표가 없습니다.
- 연결은 약 14분 뒤 종료될 수 있습니다. 토큰 갱신 뒤 재연결하고 두 목록을 재조회합니다.
- 이벤트 중복·누락을 허용하고 REST 목록·허용된 상세를 최종 기준으로 사용합니다.

## 관리자 접근

- `SUPER_ADMIN`이 병원 제안·인계 확인·SSE API를 호출하면 `AUTH_003`입니다.
- 관리자는 목적지·취소·인계 이력, 임상정보, 연락처와 위치를 조회하지 않습니다.
- 이 기능으로 변경되는 관리자 전용 API는 없습니다.

## 웹 상태 복구

| 상황 | 서버 계약 | 웹에서 필요한 처리 |
|---|---|---|
| 확인 응답 유실 | 같은 키·같은 명령은 최초 완료 결과 재생 | 성공 확인 전까지 키 유지 |
| 확인과 다른 종료 명령 경합 | 요청 잠금으로 한 최종 상태만 확정 | 성공·404·409 뒤 두 목록 재조회 |
| 상세가 404로 바뀜 | 종료·권한 변경 뒤 민감 상세 차단 | 상세 닫고 HISTORY 최소 item 사용 |
| 수신 OFF | 기존 목적지 인계 확인 권한 유지 | 진행 중 카드 확인 허용 |
| SSE 누락·중복 | SSE는 권위 상태가 아님 | 화면 진입·복귀 때 두 목록 재조회 |
| Access Token 만료 | REST·SSE 인증 실패 | 토큰 갱신, SSE 재연결, 두 목록 재조회 |

## 연동 확인

- [ ] 현재 목적지의 `HANDOFF_REQUESTED` ACTIVE 유지와 `canConfirmHandoff: true`
- [ ] 비목적지·다른 병원 조직의 인계 확인 차단
- [ ] 병원 수신 OFF에서도 진행 중 인계 확인 성공
- [ ] 확인 성공 뒤 ACTIVE 제거와 모든 관련 제안 HISTORY 이동
- [ ] 취소 시 ACTIVE 제거, 취소 상태·사유 표시와 상세 차단
- [ ] 종료 이력에서 임상·연락처·정확한 위치·동적 ETA 미노출
- [ ] 응답 유실 시 같은 키로 완료 결과 복구
- [ ] SSE 수신·재연결 뒤 ACTIVE와 HISTORY 재조회
- [ ] 슈퍼 관리자 접근 차단
- [ ] 실제 환자정보·개인 연락처·정확한 실제 위치가 아닌 테스트 데이터로 Dev 연동
