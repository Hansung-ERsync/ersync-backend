# ERSync Backend

구급대원이 응급환자 정보를 주변 응급실에 전달하고, 병원 응답과 이송 상태를 관리하는 ERSync 백엔드입니다.

## 기술 스택

- Java 25
- Spring Boot 4.1
- Spring Security
- Gradle
- Docker

## 실행

```bash
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

현재는 공통 오류, 로그, RBAC 골격, health check와 ECR 배포 기반까지 구성되어 있습니다. 도메인 기능은 기능별 명세를 팀에서 승인한 후 구현합니다.
