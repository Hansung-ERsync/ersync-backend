# ERSync 백엔드 기반 컨텍스트

- 대상: 이후 기능을 구현하는 백엔드 개발자와 AI 에이전트
- 범위: 공통 오류, 로그, Security, JWT 계약, CI/CD

## 1. 구현된 기반

- 모든 요청에 서버가 `traceId`를 생성한다.
- 응답 헤더 `X-Trace-Id`와 오류 응답에 같은 값을 반환한다.
- 개발자는 `CustomException(ErrorCode)`로 등록된 오류만 던진다.
- Validation 오류는 `fieldErrors`로 반환한다.
- 401, 403, 4xx, 5xx는 같은 오류 응답 형식을 사용한다.
- 오류 로그는 LogScope가 읽는 `key=value` 형식을 사용한다.
- Actuator health와 readiness 기반을 제공한다.
- Security는 stateless이며 health 외 API는 기본적으로 인증이 필요하다.

## 2. 오류 응답 계약

```json
{
  "code": "HOSPITAL_001",
  "message": "병원을 찾을 수 없습니다.",
  "fieldErrors": [],
  "traceId": "01JABC"
}
```

규칙:

- `code`는 `ErrorCode`에 등록한다.
- `message`에는 사용자에게 공개 가능한 문구만 넣는다.
- 내부 예외 메시지와 Stack Trace를 응답에 넣지 않는다.
- 오류 코드 문자열은 중복될 수 없다.

## 3. 로그 계약

```text
WARN event=BUSINESS_ERROR traceId=01JABC code=HOSPITAL_001 status=404 method=GET path=/api/v1/hospitals/{hospitalId} message="병원을 찾을 수 없습니다." exception=CustomException
```

이벤트:

```text
BUSINESS_ERROR
VALIDATION_ERROR
AUTH_ERROR
SYSTEM_ERROR
```

규칙:

- 4xx는 WARN으로 기록한다.
- 예상하지 못한 5xx는 ERROR와 Stack Trace를 기록한다.
- 실제 동적 ID 대신 가능한 경우 Spring 경로 패턴을 기록한다.
- 요청·응답 본문을 기록하지 않는다.
- 환자정보, 토큰, 비밀번호, 가입 코드, 정확한 GPS를 기록하지 않는다.

LogScope는 현재 `method + path + status` 집계 방식이다. 요청 단위 분석이 필요하면 LogScope에 `traceId` 파싱과 매칭을 추가한다.

## 4. 역할 계약

```text
SUPER_ADMIN
PARAMEDIC
HOSPITAL_STAFF
```

- `SUPER_ADMIN`은 조직·가입 코드를 관리하고 환자정보를 조회하지 않는다.
- `PARAMEDIC`은 자신이 담당하는 이송 요청만 접근한다.
- `HOSPITAL_STAFF`는 자기 병원에 전달된 요청만 접근한다.
- URL 역할 검사만 믿지 않는다. 서비스 계층에서 조직과 담당 요청 소유권을 검사한다.

## 5. JWT 계약

```text
Access Token: 15분
Refresh Token: 7일
```

- JWT에는 `userId`, `role`, `organizationId`만 넣는다.
- 환자정보를 JWT에 넣지 않는다.
- Refresh Token은 DB에 해시로 저장한다.
- 재발급 시 기존 Refresh Token을 폐기하고 새 토큰을 발급한다.
- JWT Secret은 환경변수 또는 Secrets Manager에서 주입한다.

JWT 발급, 로그인, Refresh Token 저장은 아직 구현하지 않았다.

## 6. 구현하지 않은 기능

- 회원가입과 로그인 API
- 초대 코드 발급·검증
- JWT 발급·재발급
- 사용자·조직 엔티티
- 병원·구급대원 도메인 권한 검사

기능 개발자는 기존 오류·로그·역할 계약을 유지하면서 위 기능을 추가한다.

## 7. CI/CD 계약

- Pull Request에서는 Gradle 검사와 Docker 빌드를 수행한다.
- `main` push에서는 Gradle 검사 후 ECR에 이미지를 push하고 EC2에 배포한다.
- ECR 이미지 태그는 Git Commit SHA다.
- GitHub Actions는 Access Key가 아니라 OIDC Role을 사용한다.
- GitHub Actions는 SSM Run Command로 지정된 EC2에만 배포 명령을 전달한다.
- EC2는 새 컨테이너의 readiness가 확인된 뒤에만 이전 컨테이너를 제거한다.
- readiness가 실패하면 새 컨테이너를 제거하고 이전 컨테이너를 복구한다.
- 배포 Workflow는 동시에 두 개가 실행되지 않도록 순차 처리한다.
- EC2는 IAM Role로 `ersync/dev/backend` Secret을 읽는다.
- Secret은 Docker 환경변수나 이미지에 넣지 않고 권한이 제한된 런타임 설정 파일로 마운트한다.
- EC2 재부팅 시 systemd가 Docker보다 먼저 런타임 Secret 파일을 다시 생성하며, 실패하면 Docker 시작을 차단하고 재시도한다.
- 컨테이너 교체 중 EC2가 중단되면 부팅 후 복구 서비스가 준비된 현재 컨테이너 또는 이전 컨테이너를 선택한다.
- JPA, Flyway와 MySQL Connector/J 기반이 구성되어 있다.
- JDBC는 RDS CA truststore와 `sslMode=VERIFY_IDENTITY`를 사용한다.
- readiness에는 DB 상태가 포함되므로 DB 연결에 실패한 이미지는 배포되지 않는다.
- 애플리케이션 컨테이너는 읽기 전용 루트 파일시스템과 권한 제거 상태로 실행한다.
- 실제 도메인 엔티티와 Flyway migration은 기능 구현 시 추가한다.
