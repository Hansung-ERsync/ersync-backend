# ERSync 백엔드 개발 컨벤션

- 대상: 백엔드 개발자와 AI 에이전트
- 범위: Java, Spring, JPA, Flyway, 테스트와 Git 협업 규칙
- 적용 시점: 공통·백엔드 컨텍스트와 팀 검토가 끝난 기능 문서를 읽은 뒤 구현 전 확인

## 1. 네이밍

| 대상 | 규칙 | 예시 |
|---|---|---|
| 클래스·인터페이스 | PascalCase, 명사 | `TransportService` |
| 메서드 | camelCase, 동사로 시작 | `createTransportRequest` |
| 변수 | camelCase | `hospitalOffer` |
| 상수 | UPPER_SNAKE_CASE | `MAX_SEARCH_RADIUS` |
| 패키지 | 소문자 | `com.hansungteam.ersync.transport` |
| Enum 타입 | PascalCase | `UserRole` |
| Enum 값 | UPPER_SNAKE_CASE | `HOSPITAL_STAFF` |
| 요청 DTO | `Request` 접미사 | `CreateTransportRequest` |
| 응답 DTO | `Response` 접미사 | `TransportResponse` |

의미 없는 축약어와 `data`, `info`, `temp` 같은 포괄적인 이름을 피합니다.

## 2. 코드 포맷

- 들여쓰기는 공백 4칸을 사용한다.
- Java 코드에서 탭을 사용하지 않는다.
- 중괄호는 K&R 스타일을 사용한다.
- 클래스와 메서드 사이에는 빈 줄 한 줄을 둔다.
- 사용하지 않는 import와 코드는 제거한다.
- `./gradlew check`로 Spotless와 테스트를 함께 실행한다.
- 주석은 구현 내용보다 이유와 제약조건을 설명한다.

## 3. 패키지

기능 우선 구조를 사용합니다.

```text
transport/
  api/
  application/
  domain/
  infrastructure/
```

역할:

| 패키지 | 역할 |
|---|---|
| `api` | Controller, Request, Response |
| `application` | 유스케이스와 트랜잭션 경계 |
| `domain` | 상태, 정책, 도메인 타입 |
| `infrastructure` | JPA Entity, Repository 구현, 외부 API |
| `global` | 오류, Security, 로그처럼 전 기능이 공유하는 기반 |

기능 패키지는 `auth`, `organization`, `invitation`, `hospital`, `transport`, `clinical`, `offer`, `location`, `handoff`, `notification`, `audit`를 기준으로 합니다.

## 4. Spring Web

- Controller는 요청 변환, 검증 실행, 응답 변환만 담당한다.
- 비즈니스 로직은 application 또는 domain 계층에 둔다.
- Request DTO에는 Bean Validation을 사용한다.
- JPA Entity를 API 응답으로 직접 반환하지 않는다.
- 불변 DTO는 Java record를 우선 사용한다.
- API 경로는 명사를 사용하고 HTTP 메서드로 행위를 표현한다.
- 오류 응답은 `CustomException`, `ErrorCode`, `GlobalExceptionHandler`를 사용한다.

## 5. Javadoc

다음 대상에는 Javadoc을 작성합니다.

- `src/main`의 public 클래스, 인터페이스, enum, record
- 다른 패키지에서 사용하는 public 메서드
- Security, 로그, 상태 전이처럼 계약과 제약이 중요한 코드
- 외부 API 어댑터와 공통 유틸리티

다음 대상은 생략할 수 있습니다.

- `@Override` 메서드가 인터페이스 설명을 그대로 따르는 경우
- 의미가 분명한 private 메서드
- 단순 getter, setter, record 접근자
- 테스트 전용 클래스

작성 규칙:

- 무엇을 하는지보다 왜 존재하고 어떤 계약을 보장하는지 적는다.
- `@param`, `@return`, `@throws`는 의미가 있을 때 작성한다.
- 구현과 맞지 않는 Javadoc은 코드 결함으로 본다.
- 환자정보처럼 보이는 실제 값을 예시에 사용하지 않는다.

예시:

```java
/**
 * 클라이언트 오류 응답과 서버 로그를 연결할 추적 ID를 반환합니다.
 *
 * @return 현재 요청의 추적 ID
 */
public static String currentTraceId() {
    // ...
}
```

## 6. JPA

- 외부에 노출되는 식별자는 UUID 또는 ULID 같은 비순차 ID를 사용한다.
- 내부 숫자 PK를 사용하더라도 API에 그대로 노출하지 않는다.
- 기본 생성자는 `protected`로 제한한다.
- Setter를 남발하지 않고 의미 있는 도메인 메서드로 상태를 변경한다.
- 연관관계는 기본 LAZY를 사용한다.
- 양방향 연관관계는 꼭 필요한 경우에만 사용한다.
- 목록 API는 페이징을 고려한다.
- N+1은 Fetch Join, EntityGraph 또는 배치 전략으로 해결한다.

## 7. 트랜잭션과 Repository

- 트랜잭션 경계는 application service에 둔다.
- 조회 전용 메서드는 `@Transactional(readOnly = true)`를 사용한다.
- Controller에 `@Transactional`을 사용하지 않는다.
- 외부 API 호출을 DB 트랜잭션 안에서 오래 수행하지 않는다.
- Repository는 DB 접근만 담당하고 비즈니스 정책을 두지 않는다.
- 값이 없을 수 있는 단건 조회는 `Optional<T>`를 사용한다.

## 8. Flyway

- 모든 스키마 변경을 Flyway SQL로 관리한다.
- 파일명은 `V{버전}__{설명}.sql`을 사용한다.
- 적용된 migration 파일은 수정하지 않는다.
- 변경은 다음 버전의 additive migration으로 추가한다.
- 운영 DB를 콘솔에서 임의 수정하지 않는다.

이름 예시:

```text
V1__init_schema.sql
fk_hospital_offer_transport_request
uk_hospital_offer_request_id_hospital_id
idx_transport_request_status
```

## 9. 테스트

- domain과 application 정책은 단위 테스트로 검증한다.
- Repository 매핑과 쿼리는 `@DataJpaTest`로 검증한다.
- Controller 계약은 `@WebMvcTest` 또는 통합 테스트로 검증한다.
- 권한, 조직 소유권, 중복 요청, 동시성, 재시도를 테스트한다.
- 첫 Flyway migration부터 MySQL 8.4 Testcontainers 테스트를 추가해 실제 DDL 호환성을 검증한다.
- H2 MySQL 모드는 빠른 컨텍스트 검사에만 사용하며 실제 MySQL 호환성 증명으로 취급하지 않는다.
- 테스트 이름은 시나리오와 기대 결과가 드러나게 작성한다.

예시:

```text
acceptOffer_success
acceptOffer_alreadyDecided_throwsConflict
selectDestination_otherOrganization_forbidden
```

## 10. Git 협업

- `main`은 배포 가능한 상태를 유지한다.
- 모든 작업 브랜치는 최신 `main`에서 생성한다.
- 브랜치 하나는 PR 하나만 담당하며 병합 후 재사용하지 않는다.
- 이전 작업 브랜치에서 다음 작업 브랜치를 만들지 않는다.
- 기능 spec의 확정 정책을 기록하고 결정 필요 사항을 모두 해소한 뒤 구현을 시작한다.
- 하나의 PR은 하나의 기능 또는 하나의 명확한 변경을 다룬다.
- API 계약 변경은 기능 문서와 코드에 같이 반영한다.
- PR에는 테스트 결과와 미해결 위험을 적는다.
- 모든 커밋 메시지와 PR 제목은 `[유형] 변경 목적` 형식으로 작성한다.
- 유형은 변경한 파일 종류가 아니라 커밋과 PR의 주된 목적을 기준으로 선택한다.
- 기능 구현에 테스트와 문서가 함께 포함되어도 주된 목적이 기능이면 `feature`를 사용한다.
- 브랜치 이름은 `{유형}/{짧은-kebab-case-목적}` 형식으로 작성한다.
- 브랜치 유형은 PR의 주된 유형과 일치시키고 목적은 영문 소문자 kebab-case로 작성한다.

작업 시작:

```bash
git status --short
git switch main
git pull --ff-only origin main
git switch -c feature/admin-invitation-code
```

PR 병합 후 다음 작업:

```bash
git switch main
git pull --ff-only origin main

# GitHub에서 PR 병합을 확인한 뒤에만 실행한다.
git branch -D feature/admin-invitation-code

git switch -c feature/next-feature
```

- `git pull --ff-only`이 실패하면 강제로 이력을 바꾸지 않고 원인을 확인한다.
- working tree에 기존 변경이 있으면 임의로 삭제하거나 stash하지 않는다.
- PR 생성 전 기능 문서, 코드, 테스트, `review.md`와 필요한 프론트 계약을
  같은 브랜치에서 완료한다.
- 다음 브랜치 생성 전 로컬 `main`과 `origin/main`의 일치 여부를 확인한다.

| 유형 | 사용 기준 | 브랜치 예시 | 커밋·PR 제목 예시 |
|---|---|---|---|
| `feature` | 사용자 기능 추가 | `feature/admin-invitation-code` | `[feature] 초대 코드 발급 기능 추가` |
| `fix` | 잘못된 동작 수정 | `fix/expired-invitation-validation` | `[fix] 만료 코드 검증 오류 수정` |
| `refactor` | 동작 변경 없는 구조 개선 | `refactor/invitation-validation` | `[refactor] 초대 코드 검증 책임 분리` |
| `chore` | 설정, 빌드, 운영 변경 | `chore/update-pr-template` | `[chore] PR 템플릿 개선` |
| `docs` | 문서만 변경 | `docs/hospital-acceptance-policy` | `[docs] 병원 수락 정책 보완` |

오류 응답과 로그 계약은 [백엔드 에이전트 컨텍스트](agents/backend.md)를 따릅니다.
