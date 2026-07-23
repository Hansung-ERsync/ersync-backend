# ERSync 오류 코드 규칙

## 1. 오류 응답

모든 4xx와 5xx는 다음 구조를 사용합니다.

```json
{
  "code": "HOSPITAL_001",
  "message": "병원을 찾을 수 없습니다.",
  "fieldErrors": [],
  "traceId": "01JABC"
}
```

- 프론트엔드는 HTTP 상태와 `code`로 분기한다.
- `message`는 사용자에게 공개 가능한 고정 문구다.
- `fieldErrors`는 DTO 검증 실패에 사용한다.
- `traceId`는 사용자 오류와 서버 로그를 연결한다.
- Stack Trace와 내부 예외 메시지는 응답에 포함하지 않는다.

## 2. 개발자가 오류를 던지는 방법

```java
throw new CustomException(ErrorCode.HOSPITAL_NOT_FOUND);
```

임의 코드나 임의 사용자 메시지를 Controller와 Service에서 직접 만들지 않습니다.

새 오류를 추가할 때:

1. 기능 명세에 발생 조건을 적는다.
2. 기존 문자열 코드와 중복되지 않는지 확인한다.
3. `ErrorCode`에 enum을 추가한다.
4. HTTP 상태와 사용자 메시지를 검토한다.
5. 발생 조건 테스트를 추가한다.
6. 이 문서의 현재 오류 코드 표를 갱신한다.

`GlobalExceptionHandler`는 새 `CustomException` 때문에 수정하지 않습니다.

## 3. 코드 형식

```text
{영역}_{3자리 번호}
```

예시:

```text
COMMON_001
AUTH_002
TRANSPORT_004
```

Enum 상수는 발생 조건이 드러나게 작성합니다.

```text
HOSPITAL_NOT_FOUND
TRANSPORT_STATUS_CANNOT_CHANGE
```

## 4. HTTP 상태

| 상태 | 사용 기준 |
|---|---|
| 400 | 요청 형식·값·검증 오류 |
| 401 | 인증 필요, 토큰 만료·위조 |
| 403 | 인증됐지만 역할·조직 권한 부족 |
| 404 | 리소스가 존재하지 않음 |
| 405 | 지원하지 않는 HTTP 메서드 |
| 409 | 중복 또는 상태 전이 충돌 |
| 429 | 요청 제한 초과 |
| 500 | 예상하지 못한 내부 오류 |
| 502 | 외부 서비스가 잘못된 응답을 반환 |
| 503 | 외부 서비스 또는 핵심 의존성 일시 장애 |

## 5. 현재 오류 코드

### COMMON

| 코드 | HTTP | 발생 조건 |
|---|---:|---|
| `COMMON_001` | 400 | DTO, 파라미터, 타입 검증 실패 |
| `COMMON_002` | 405 | 지원하지 않는 HTTP 메서드 |
| `COMMON_003` | 500 | 처리되지 않은 서버 예외 |
| `COMMON_004` | 403 | 공통 접근 권한 부족 |
| `COMMON_005` | 409 | Unique Key 또는 중복 요청 충돌 |
| `COMMON_006` | 404 | 매핑되거나 제공할 리소스가 없음 |

### AUTH

| 코드 | HTTP | 발생 조건 |
|---|---:|---|
| `AUTH_001` | 401 | 인증 정보가 없는 요청 |
| `AUTH_002` | 401 | 형식, 서명 또는 유효기간이 잘못된 Access Token |
| `AUTH_003` | 403 | 필요한 역할 또는 권한이 없음 |

### USER

| 코드 | HTTP | 발생 조건 |
|---|---:|---|
| `USER_001` | 404 | 사용자 계정을 찾을 수 없음 |
| `USER_002` | 403 | 비활성화된 사용자 계정 |

### HOSPITAL

| 코드 | HTTP | 발생 조건 |
|---|---:|---|
| `HOSPITAL_001` | 404 | 병원을 찾을 수 없음 |
| `HOSPITAL_002` | 409 | 병원이 현재 요청을 받을 수 없음 |
| `HOSPITAL_003` | 409 | 응급실 운영 상태를 확인할 수 없음 |
| `HOSPITAL_004` | 409 | 병상, 의료진 또는 장비 수용 여력이 없음 |

### TRANSPORT

| 코드 | HTTP | 발생 조건 |
|---|---:|---|
| `TRANSPORT_001` | 404 | 이송 요청을 찾을 수 없음 |
| `TRANSPORT_002` | 409 | 수락하지 않은 병원을 목적지로 선택함 |
| `TRANSPORT_003` | 409 | 이송 요청이 만료됨 |
| `TRANSPORT_004` | 409 | 현재 상태에서 요청 상태를 변경할 수 없음 |
| `TRANSPORT_005` | 404 | 병원에 전달된 수신 요청을 찾을 수 없음 |
| `TRANSPORT_006` | 409 | 병원이 이미 수락 또는 거절을 완료함 |

### PROTOCOL

| 코드 | HTTP | 발생 조건 |
|---|---:|---|
| `PROTOCOL_001` | 404 | 프로토콜을 찾을 수 없음 |
| `PROTOCOL_002` | 409 | 비활성화된 프로토콜 버전 |
| `PROTOCOL_003` | 500 | 서버의 프로토콜 평가 처리 실패 |

## 6. LogScope 로그

개발자가 던진 오류는 다음처럼 직접 확인할 수 있어야 합니다.

```text
WARN event=BUSINESS_ERROR traceId=01JABC code=HOSPITAL_001 status=404 method=GET path=/api/v1/hospitals/{hospitalId} message="병원을 찾을 수 없습니다." exception=CustomException
```

예상하지 못한 서버 오류:

```text
ERROR event=SYSTEM_ERROR traceId=01JXYZ code=COMMON_003 status=500 method=POST path=/api/v1/transports message="서버 내부 오류입니다." exception=IllegalStateException
```

로그 수준:

| 이벤트 | 수준 |
|---|---|
| `BUSINESS_ERROR` | WARN |
| `VALIDATION_ERROR` | WARN |
| `AUTH_ERROR` | WARN |
| `SYSTEM_ERROR` | ERROR와 Stack Trace |

로그에 환자정보, 요청 본문, 토큰, 비밀번호, 가입 코드, 정확한 GPS를 기록하지 않습니다.
