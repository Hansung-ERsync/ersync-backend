# ERSync Backend

구급대원이 응급환자 정보를 주변 응급실에 전달하고, 병원 응답과 이송 상태를 관리하는 ERSync 백엔드입니다.

## 기술 스택

- Java 25
- Spring Boot 4.1
- Spring Security
- Gradle
- Docker

## 실행

로컬 MySQL에 `ersync` 데이터베이스와 개발 계정을 준비한 뒤 실행합니다.

```bash
SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:3306/ersync?sslMode=DISABLED' \
SPRING_DATASOURCE_USERNAME='local_user' \
SPRING_DATASOURCE_PASSWORD='local_password' \
./gradlew bootRun
```

검사:

```bash
./gradlew clean check
```

Docker 이미지 빌드:

```bash
docker build -t ersync-api:local .
```

## 문서

요구사항, 컨벤션, AI 컨텍스트와 DevOps 구성은 [문서 안내](docs/README.md)에서 확인합니다.

현재는 공통 오류, 로그, RBAC 골격, JPA·Flyway, DB readiness와 ECR·EC2·RDS 배포 기반까지 구성되어 있습니다. 도메인 기능은 기능별 명세를 팀에서 승인한 후 구현합니다.
