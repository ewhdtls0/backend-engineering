# 회원별 최근 활동 피드 조회

2026-09-18 백엔드 구현 과제 · Java 21 / Spring Boot 3.5.16 / Gradle 8.14.3

## 1. 서비스 상황

커뮤니티의 마이페이지에서 한 회원이 작성한 게시글과 댓글을 시간순으로 함께 보여주려 합니다. 지금은 게시글과 댓글이 서로 다른 테이블에 저장되어 있습니다. 오래 활동한 회원은 게시글 10,000개, 댓글 40,000개 이상을 보유할 수 있습니다. 화면에는 한 번에 20개 정도만 필요합니다.

서로 다른 데이터의 종류와 양, 같은 시각에 작성된 활동, 뒤쪽 페이지까지 고려하여 정확하고 설명 가능한 API를 구현하세요. 제공 코드는 실행 골격이며 조회 정답은 포함하지 않습니다.

## 2. 기능 요구사항

`GET /api/members/{memberId}/activities?page=0&size=20`

| 입력 | 규칙 |
|---|---|
| memberId | 양의 정수 Long |
| page | 0부터 시작하는 정수, 기본값 0, 최대 Integer.MAX_VALUE |
| size | 1~100, 기본값 20 |

- 해당 회원의 Post와 Comment만 하나의 목록으로 반환합니다.
- `createdAt DESC`로 정렬합니다. 같은 시각의 활동에도 **완전하고 결정적인 순서**가 있어야 합니다. 추가 정렬 규칙은 직접 결정하고 아래 구현 기록에 선언하세요.
- `page * size`부터 최대 `size`개의 활동을 정확하게 반환합니다. 마지막 페이지에는 남은 개수만 반환합니다.
- 회원은 있지만 활동이 없거나 끝을 넘어선 페이지이면 `activities: []`입니다. 매우 큰 page를 계산할 때 정수 overflow를 주의하세요.
- 존재하지 않는 회원은 `MemberNotFoundException`을 발생시켜 HTTP 404로 응답합니다. 회원 부재를 확인하기 위해 활동 테이블까지 조회하지 마세요.
- 잘못된 입력은 HTTP 400입니다. 이 경우 서비스 호출 전에 차단하는 Controller 검증은 제공되어 있습니다.

응답 예시(동일 시각의 순서를 규정하는 예시가 아닙니다):

```json
{
  "memberId": 1,
  "page": 0,
  "size": 20,
  "activities": [
    {"type": "COMMENT", "activityId": 8, "content": "좋은 글입니다", "createdAt": "2026-09-18T12:00:00"},
    {"type": "POST", "activityId": 8, "content": "오늘의 기록", "createdAt": "2026-09-18T11:00:00"}
  ]
}
```

POST의 content는 title, COMMENT의 content는 content입니다. `size`는 요청한 크기이며 실제 활동 수가 아닙니다. 전체 개수나 totalPages를 추가하는 것은 필수가 아닙니다. 예외는 Spring `ProblemDetail` 형식이며 `status`, `detail`을 제공합니다.

## 3. 비기능 요구사항

- 회원의 모든 Post/Comment를 각각 읽어 애플리케이션에서 전체 정렬 후 잘라내는 구현은 금지합니다. DTO나 원시 행으로 바꿔서 전부 읽는 방식도 허용하지 않습니다.
- 50,000개의 엔티티를 영속성 컨텍스트 또는 컬렉션에 적재한 뒤 20개만 반환하면 안 됩니다.
- 각 테이블에서 단순히 size개씩 읽어 합치는 방식을 고려한다면 **모든 page에서도 정확한지** 반례와 근거로 검증하세요. 첫 페이지만 통과하는 것은 충분하지 않습니다.
- page가 커질 때 DB가 탐색하거나 건너뛰는 행 수, 정렬 비용, 애플리케이션이 받는 결과 행 수를 구분해 설명하세요.
- 필요한 인덱스와 컬럼 순서를 스스로 정하고 이유를 설명하세요. 기본 PK/FK 외의 정답 인덱스는 제공하지 않습니다. H2가 자동 생성하는 인덱스도 확인하세요.
- OSIV는 꺼져 있습니다. 트랜잭션 경계와 LAZY 연관관계를 필요에 따라 설계하세요.

## 4. 도메인 / 데이터 조건

| 도메인 | 테이블 | 필드 |
|---|---|---|
| Member | members | id: Long, name: String |
| Post | posts | id: Long, member: Member(LAZY), title: String, createdAt: LocalDateTime |
| Comment | comments | id: Long, member: Member(LAZY), content: String, createdAt: LocalDateTime |

- ID는 테이블별로 독립적입니다. POST 8과 COMMENT 8은 서로 다른 활동입니다. 테스트도 `(type, activityId)`로 식별합니다.
- 제목/내용/작성 시각/회원 참조는 null이 아닙니다. 생성자는 fixture 작성에 이용할 수 있습니다.
- 작성 시각은 이 과제에서 동일한 업무 시간대의 LocalDateTime으로 다룹니다. ID 증가와 작성 시각의 증가가 같다고 가정하지 마세요.
- 같은 createdAt이 여러 테이블 및 한 테이블 안에 반복될 수 있습니다. 데이터가 변경되지 않는 동안에는 동일한 정렬 규칙을 모든 페이지와 크기에 적용해야 합니다.
- 존재하지만 활동이 없는 회원, 게시글만 있는 회원, 댓글만 있는 회원, 활동량이 크게 치우친 회원이 모두 가능합니다.
- 애플리케이션 시작 시 자동 데이터 입력은 없습니다. 비즈니스 테스트는 fixture를 먼저 커밋하고 테스트 트랜잭션 밖에서 서비스를 호출합니다. 실패한 경우에도 종료 훅에서 데이터를 삭제하여 격리하고, 다음 테스트 시작 시 빈 DB인지 확인합니다. 인프라 단독 테스트는 rollback으로 격리합니다. H2는 메모리 DB이고 종료 시 데이터가 사라집니다.

## 5. 제약조건

- Post와 Comment를 하나의 테이블로 합치는 스키마 변경은 금지합니다.
- 외부 API, 외부 DB, 사전 생성된 통합 활동 테이블에 의존하지 마세요.
- 공개 API 및 최소 응답 필드는 유지하세요. 특정 pagination 기술은 강제하지 않습니다. 선택한 설계에서도 주어진 page/size 계약을 만족해야 합니다.
- Repository 메서드, 쿼리, 추가 조회 컴포넌트 등은 필요에 따라 자유롭게 작성할 수 있습니다. 기존 최소 인터페이스의 기본 메서드가 존재한다고 해서 전량 조회가 허용되는 것은 아닙니다.
- 테스트를 비활성화하거나 테스트 전용 분기로 통과시키지 마세요. 테스트 계측을 우회하는 별도 DataSource/연결 생성도 하지 마세요.
- 기존 날짜의 미션과 상위 저장소 설정은 이 과제의 구현 대상이 아닙니다.

## 6. TODO 및 실행 방법

1. 테스트를 먼저 실행하고 실패 이유를 확인하세요.
2. 동일 시각의 정렬 규칙을 선언하세요.
3. `ActivityServiceImpl#getActivities(Long memberId, int page, int size)`를 구현하세요.
4. 필요한 조회 계약과 구현, 예외 처리, 트랜잭션 경계를 작성하세요.
5. 테스트를 통과시키고 SQL 및 조회 비용을 관찰하세요.
6. 인덱스와 아래 질문에 대한 답을 문서로 남기세요.

```text
src/main/java/study/activityfeed/
  ActivityFeedApplication.java
  domain/        Member, Post, Comment
  repository/    최소 JPA 인터페이스
  service/       ActivityService, ActivityServiceImpl (핵심 TODO)
  controller/    API 및 입력 검증
  dto/           응답 record 및 활동 종류
  exception/     회원 부재 예외 및 HTTP 매핑
src/test/java/study/activityfeed/
  ActivityServiceTest.java
  ActivityVolumeTest.java
  ActivityControllerTest.java
  StarterInfrastructureTest.java
  BusinessBoundaryTest.java
  support/       격리 fixture 및 JDBC 관찰 도구
```

IntelliJ에서는 이 디렉터리를 독립 Gradle 프로젝트로 여세요. Project SDK와 Gradle JVM을 Java 21로 지정하세요. 터미널의 `JAVA_HOME`도 JDK 21을 가리키게 설정합니다. Gradle 설치는 필요하지 않지만 최초 실행 시 wrapper/의존성 다운로드에 인터넷이 필요할 수 있습니다.

Windows PowerShell:

```powershell
.\gradlew.bat testClasses
.\gradlew.bat test
.\gradlew.bat test --tests '*StarterInfrastructureTest' --tests '*ActivityControllerTest' --tests '*BusinessBoundaryTest'
.\gradlew.bat bootRun
```

Unix:

```bash
chmod +x gradlew  # 실행 권한이 보존되지 않은 복사본에서 한 번만 실행
./gradlew testClasses
./gradlew test
./gradlew test --tests '*StarterInfrastructureTest' --tests '*ActivityControllerTest' --tests '*BusinessBoundaryTest'
./gradlew bootRun
```

기본 주소는 `http://localhost:8080`입니다. H2 console은 `/h2-console`, JDBC URL은 `jdbc:h2:mem:activityfeed`, 사용자명은 `sa`, 비밀번호는 비어 있습니다. 개발 관찰용 설정이며 배포 보안 설정이 아닙니다.

`application.yml`은 SQL DEBUG 및 bind TRACE 로그를 켭니다. 테스트 프로필은 대량 입력 로그를 줄이고 Hibernate 통계를 켭니다. 테스트에서 SQL도 보고 싶다면 `application-test.yml`의 로깅 수준을 변경하세요. `JdbcTemplate` 또는 직접 JDBC로 작성한 쿼리는 Hibernate 로그에 나타나지 않으므로 JDBC 관찰 도구의 SQL 목록도 확인하세요.

**초기 상태에서 전체 test는 실패하는 것이 정상입니다.** 핵심 서비스는 TODO의 `UnsupportedOperationException`을 던집니다. `testClasses`, Controller 및 인프라 테스트는 통과해야 합니다. HTML 결과는 `build/reports/tests/test/index.html`, XML은 `build/test-results/test/`에 생성됩니다.

## 7. 테스트 시나리오

| 테스트 | 검증 내용 |
|---|---|
| 혼합 활동 | 전체 DTO 값, createdAt 내림차순, POST 제목/COMMENT 내용, 다른 회원 제외 |
| 45개 / size 20 | 각각 20/20/5개, 정확한 각 페이지, 중복·누락 없음, 종료 뒤 빈 목록 |
| POST만 / COMMENT만 | 첫 페이지와 남은 페이지 모두 정확 |
| 동일 시각 45개 | 특정 tie-breaker를 강요하지 않고 전체 조회와 size 7 분할을 여러 번 비교 |
| 빈 회원 / 매우 큰 page | 빈 목록, page 계산 overflow 방지 |
| 없는 회원 | 실제 서비스의 예외 및 활동 테이블 SQL 미실행, 별도 MVC 404 매핑 |
| 잘못된 입력 | 음수 page, size 0/음수/101, 잘못된 문자·정수 범위, memberId 오류 → 400, 서비스 미호출 |
| 대량 데이터 | POST 10,000 + COMMENT 40,000, page 0/37의 정확한 DTO 및 조회량 제한 |
| 인프라 자체 검사 | Spring context, mapping/LAZY/fixture, JDBC 관찰기, Hibernate 통계, 응답 불변 복사 |

동일 시각 테스트는 작은 fixture 전체 응답을 기준 순서로 삼고 다른 크기의 모든 페이지를 다시 연결하여 비교합니다. 기준 응답 자체의 개수·내용도 fixture와 비교합니다. 이 테스트가 통과해도 DB의 우연한 반환 순서에 의존하는 구현까지 수학적으로 배제할 수는 없으므로 **선언된 완전한 정렬 규칙과 실제 쿼리를 함께 검토**해야 합니다. 최종 선택 후 그 규칙을 명시적으로 확인하는 테스트를 추가하세요.

### 조회량 관찰 도구의 범위

`JdbcReadProbe`는 테스트에서만 DataSource를 감싸며, 관찰 범위 안에서 JDBC `ResultSet.next()`가 true를 반환한 횟수와 실행 SQL을 기록합니다. JPA 엔티티, DTO projection, 일반 JDBC 결과 모두 이 경계를 통과합니다. 대량 fixture 생성과 기대값 준비는 측정 범위 밖에서 수행하고, 영속성 컨텍스트를 비우고 fixture 트랜잭션을 커밋한 후 실제 서비스 호출만 측정합니다. 서비스 호출에는 테스트 트랜잭션이 없으므로 필요한 트랜잭션 경계는 구현에서 제공해야 합니다. Hibernate entity-load 통계도 함께 확인합니다.

page 0과 37(size 20)에서 소비 행 수와 엔티티 적재 수는 각각 **5,000 이하**여야 합니다. 이는 최적 쿼리 수를 지정하는 조건이 아니라 50,000행 전량 적재 회귀를 막는 넉넉한 상한입니다. 응답만 맞추거나 모든 행을 DTO로 읽는 구현도 걸러냅니다. 관찰기 자체 테스트는 일부러 작은 상한을 초과해 계측이 실제 실패를 검출하는지 확인합니다.

이 수치는 **DB 내부 scan/offset/정렬 비용이 아닙니다.** DB가 내부적으로 50,000행을 읽고 20행만 반환하면 관찰되는 결과 행은 20개일 수 있습니다. 실행 계획과 더 큰 page의 비용은 별도 분석이 필요합니다. 또한 현재 동기 서비스 호출 스레드와 Spring DataSource를 대상으로 하므로 다른 스레드, unwrap한 raw 연결, 별도 DataSource, scrollable cursor 이동, 한 행에 대량 데이터를 압축하는 쿼리까지 일반적으로 증명하지는 못합니다. 그런 설계를 선택한다면 관찰 범위를 확장하고 이유를 설명해야 하며 우회해서는 안 됩니다.

## 8. 완료 기준

- [ ] 핵심 TODO를 구현하고 `testClasses` 및 전체 `test`가 통과한다.
- [ ] 정확한 정렬과 모든 페이지, 회원 격리, 빈 결과, 404, 400을 확인했다.
- [ ] 동일 시각 순서를 선언하고 구현 및 추가 테스트로 뒷받침했다.
- [ ] 전량 조회 및 전량 메모리 적재가 없음을 쿼리·계측·코드로 설명할 수 있다.
- [ ] 대량 데이터와 큰 page의 비용을 구분해 분석했다.
- [ ] 요청 사이 데이터 변경의 영향과 인덱스 선택을 문서로 정리했다.
- [ ] 아래 질문에 구현한 코드와 관찰 결과를 근거로 답했다.

## 9. 구현 후 답해야 할 질문

1. 서로 다른 두 테이블을 합친 결과의 각 page가 왜 정확한가요? 한쪽 데이터가 훨씬 많거나 최신 활동이 한쪽에 몰려 있어도 성립하나요?
2. page가 커지면 DB가 읽거나 건너뛰는 행 수와 정렬 비용은 어떻게 변하나요? 애플리케이션으로 전달되는 행 수와 어떤 차이가 있나요?
3. 전체 entity를 적재하지 않는다는 것을 어떤 코드와 SQL/계측 결과로 보장하나요? DTO로만 읽는 경우도 확인했나요?
4. 같은 createdAt의 활동은 어떤 추가 기준으로 정렬하나요? 테이블 사이에 같은 ID가 있어도 완전한 순서인가요? 페이지 크기가 바뀌어도 유지되나요?
5. page=0을 받은 후 새 활동이 추가되고 page=1을 요청하면 어떻게 되나요? offset 기반 페이지에서 중복/누락이 발생할 수 있는 상황을 작은 예시로 설명하세요. 서로 다른 요청이 동일한 데이터 시점을 보는지, 제품에서 허용할 일관성과 가능한 대응의 trade-off를 서술하세요. 이 과제는 특정 대안을 핵심 구현에 강제하지 않습니다.
6. 어떤 인덱스가 필요한가요? 조회 조건·정렬·동일 시각 규칙에 비추어 컬럼 순서와 비용을 설명하고 실행 계획으로 확인하세요.

### 구현 기록

- 동일 시각 정렬 규칙: (직접 작성)
- 정확성 근거와 반례 검토: (직접 작성)
- SQL / 결과 행 수 / entity 수 / DB 실행 계획: (직접 작성)
- 큰 page 및 요청 사이 insert 관찰: (직접 작성)
- 인덱스와 개선점: (직접 작성)

환경 호환성 참고: [Spring Boot 3.5 공식 요구사항](https://docs.spring.io/spring-boot/3.5/system-requirements.html)
