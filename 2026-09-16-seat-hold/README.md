# 2026-09-16 — 만료되는 좌석 선점

## 실제 상황
공연 좌석을 선택한 사용자가 결제를 진행하는 동안 5분간 좌석을 선점한다.
09:00에 A가 A-10을 선점하면 09:04의 B는 거절되지만, 정확히 09:05부터 B는 새로 선점할 수 있다.
과거 선점 데이터는 이력으로 DB에 남을 수 있다. 배치 실행 여부가 예약 가능성을 결정해서는 안 된다.

## 시작하기
Java 21, Spring Boot 3.5.16, Gradle 8.14.3 독립 프로젝트이다. IntelliJ에서 이 폴더의 build.gradle을 연다.
Gradle JVM은 Java 21로 설정한다. CLI에서는 JAVA_HOME을 설치된 JDK 21로 지정하고 bin을 PATH에 추가한다.
최초 실행에는 Gradle/의존성 다운로드를 위한 네트워크가 필요하다.

Windows PowerShell:
```powershell
.\gradlew.bat testClasses
.\gradlew.bat test --tests "*InfrastructureTest"
.\gradlew.bat test
.\gradlew.bat bootRun
```

Unix:
```sh
chmod +x gradlew
./gradlew testClasses
./gradlew test --tests '*InfrastructureTest'
./gradlew test
./gradlew bootRun
```

**스타터의 전체 test는 실패하는 것이 정상이다.** hold/confirm이 UnsupportedOperationException인 상태다.
InfrastructureTest는 성공해야 한다. 핵심 테스트는 @Disabled 없이 실행되고 구현 후 통과해야 한다.
testClasses는 컴파일만 검증하며 Spring context 검증은 InfrastructureTest가 담당한다.
테스트 보고서: build/reports/tests/test/index.html.
H2는 메모리 DB이고 재시작 시 초기화된다. 운영용 설정이 아니다.
별도의 서버 초기 데이터는 없으며 테스트는 좌석과 선점을 직접 준비한다.
bootRun 후 H2 콘솔 /h2-console에서 JDBC URL jdbc:h2:mem:seat-hold, 사용자 sa, 빈 암호로 연결해 좌석을 준비할 수 있다:
```sql
insert into seats (seat_number, status) values ('A-10', 'AVAILABLE');
```
SQL 및 bind 값은 application.yml의 Hibernate 로그에서 관찰한다.

## 제공 구조
- domain: Seat, SeatHold, SeatStatus, SeatHoldStatus — 매핑과 생성자/getter만 제공
- repository: 기본 JpaRepository, 조회/잠금 쿼리는 직접 설계
- service: SeatHoldService.hold / confirm — 구현 과제
- controller/dto: HTTP 연결, 요청 양수 memberId 검증, 응답 형태
- exception: SeatHoldException과 HTTP advice
- config: 주입 가능한 표준 Clock (Asia/Seoul)
- test: 실제 H2 통합 테스트, MutableClock, 테스트 전용 장애 Trigger

## 도메인 및 데이터 조건
Seat: id, 고유 seatNumber, status(AVAILABLE/RESERVED).
SeatHold: id, 필수 Seat 연관관계, memberId, status(HELD/CONFIRMED/EXPIRED), expiresAt, createdAt.
좌석은 이미 존재한다. memberId는 외부 회원 식별자로 양수이며 이번 과제에서는 인증/회원 테이블을 구현하지 않는다.
확정 요청의 memberId가 선점 소유자와 일치해야 한다. 실제 서비스의 인증된 사용자와 요청값 검증은 확장 과제다.
유효한 선점은 HELD이며 **now < expiresAt**인 선점이다. **now >= expiresAt**이면 만료다.
예제와 테스트의 시각은 Asia/Seoul 기준이다.
만료 시 과거 상태를 EXPIRED로 바꿀지, 시간으로 만료를 해석할지는 선택할 수 있다.

## API 계약
### POST /api/seats/{seatId}/holds
요청: `{"memberId":100}`.
성공: 201 Created
```json
{"holdId":10,"seatId":1,"memberId":100,"status":"HELD","expiresAt":"2026-09-16T09:05:00"}
```
- 현재 유효 선점이 없고 좌석이 AVAILABLE이면 5분 선점.
- 다른 사용자의 유효 선점이 있으면 409 Conflict.
- RESERVED 좌석이면 409 Conflict.
- 만료된 과거 row가 남아 있어도 새로운 선점 가능. 이 과제의 이력 보존 테스트에서는 과거 row를 삭제하지 않는다.
- 같은 사용자의 재요청 정책은 직접 결정하고 추가 테스트와 README에 기록한다.

### POST /api/seat-holds/{holdId}/confirm
요청: `{"memberId":100}`.
성공: 200 OK, 동일 응답 구조에서 status는 CONFIRMED.
- 소유자가 일치하는 유효 HELD만 확정 가능.
- SeatHold=CONFIRMED와 Seat=RESERVED를 하나의 비즈니스 작업으로 원자적으로 반영.
- 정확히 만료 시각 또는 이후 요청은 409. Seat를 RESERVED로 만들면 안 된다.
- 소유자가 다르면 403 Forbidden.
- 존재하지 않는 좌석/선점은 404로 처리하는 것을 기본 계약으로 한다. 추가 테스트를 작성하라.
- 누락/0/음수 memberId는 400.
- 이미 확정된 요청의 재전송 정책은 직접 정하고 문서화하라.
- 예상된 충돌을 500으로 그대로 노출하지 않는다. TODO 예외를 업무 충돌로 숨기지 않는다.

## 비기능 요구사항 및 금지 사항
- 같은 좌석에 동시에 선점 2건이 도착하면 success=1, failure=1, activeHold=1.
- 여러 애플리케이션 인스턴스가 동일 DB를 사용해도 정합성이 유지되어야 한다.
- **synchronized를 최종 해결책으로 사용하지 않는다.**
- **Thread.sleep()으로 시간을 진행시키는 테스트를 작성하지 않는다.**
- **만료 row를 반드시 DELETE하거나 배치가 정리해야만 새 선점이 가능한 구조를 금지한다.**
- 서비스 곳곳에 인자 없는 LocalDateTime.now()를 직접 넣지 않는다.
  제공 Clock을 사용하거나 대체 시간 공급 추상화를 설계하라. 대체 시 테스트의 공급원 연결도 함께 조정하라.
- 트랜잭션 경계, 동시성 전략, DB 제약, 상태 변경 책임은 직접 결정한다. 스타터에는 해결 코드가 없다.
- confirm과 새 hold가 만료 경계에서 경쟁할 때도 모순된 상태가 남으면 안 된다.

## 구현 TODO
1. 두 서비스 메서드의 요구사항을 정리한다.
2. 현재 시각을 언제 읽을지, 기다림이 생기는 경우 어느 시각을 기준으로 판단할지 결정한다.
3. 필요한 도메인 메서드와 Repository 조회를 추가한다.
4. 과거 row와 현재 유효 선점을 구분한다.
5. 중복 요청이 충돌할 때 처리 결과와 예외를 설계한다.
6. confirm의 원자성과 실패 복구를 구현한다.
7. SQL을 관찰하고 선택의 근거를 기록한다.

## 완성된 테스트 시나리오
| 테스트 | 관찰 결과 |
|---|---|
| 09:00 hold | HELD, 09:05 만료, createdAt 09:00, DB 1건 |
| A hold 후 09:04 B | HTTP 409, A 유지, DB 1건 |
| A hold 후 정확히 09:05 B | B 성공, 09:10 만료, A row 존재, DB 2건/유효 1건 |
| 09:03 confirm | CONFIRMED와 RESERVED 모두 DB에 반영 |
| 09:05/09:06 confirm | HTTP 409, 좌석 AVAILABLE |
| RESERVED 선점 | HTTP 409, 추가 row 없음 |
| 다른 소유자의 confirm | HTTP 403, 두 상태 유지 |
| 두 thread 동시 hold | HTTP 201/409 각 1건, DB 전체/유효 1건 |
| confirm DB 쓰기 장애 (각 테이블) | 실제 장애 지점 도달, AVAILABLE/HELD 유지 |
| 인프라 | context, Clock, fixture, 장애 주입, 입력 validation 검증 |

테스트 클래스에는 외부 @Transactional을 걸지 않는다. 서비스가 자신의 트랜잭션 책임을 가져야 한다.
fixture는 저장 후 commit되어 작업자 thread에서 보이며, 상태 검증은 JDBC로 DB를 새로 읽는다.
confirm 테스트는 hold 구현에 의존하지 않고 직접 HELD fixture를 만든다.
concurrency는 CountDownLatch로 두 작업자를 준비시킨 뒤 동시에 출발시킨다.
future의 예외/타임아웃은 정상적인 실패 1건으로 세지 않고 테스트 실패로 드러낸다.
H2 풀 크기 6, lock timeout 10초, future timeout 20초이며 JUnit 병렬 실행은 끈다.
DB 잠금으로 막힐 수 있는 위치에 barrier를 삽입하지 않으므로 특정 잠금 전략을 강제하지 않는다.
한 번의 동시 출발 테스트만으로 모든 스케줄을 증명할 수 없다. 반복 실행 및 목표 운영 DB에서 추가 검증하라.
H2 통과는 다중 인스턴스나 다른 DB의 격리/잠금 동작을 증명하지 않는다.

원자성 테스트는 테스트 전용 H2 BEFORE UPDATE trigger로 seats와 seat_holds 각각에 장애를 주입한다.
save 호출 여부나 dirty checking 방식에 의존하지 않고 실제 UPDATE를 막는다.
두 테이블을 각각 막아 어느 쓰기 순서에서도 부분 commit을 발견하도록 구성한다.
장애가 실제 발생했는지도 확인하므로 TODO나 사전 검증 예외만 던져서는 통과하지 않는다.
테이블/상태 저장 모델을 변경한다면 동일한 의미의 장애 지점으로 테스트를 조정하라.
InfrastructureTest의 장애 검증은 실패한 단일 SQL의 검증이며 서비스 원자성 통과를 대신하지 않는다.

## 완료 기준
- 전체 테스트 통과, core 테스트 비활성화/빈 assertion 없음.
- HTTP와 DB 최종 상태가 계약에 부합.
- 시간 경계, 남아 있는 이력, rollback, 동시 요청을 설명 가능.
- 프로세스 내부 잠금 없이 다중 인스턴스 정합성 근거를 제시.
- 추가 사례: 없는 ID, 동일인 재요청, confirm 재전송, confirm/hold 경쟁 테스트.
- 선택한 설계와 운영 DB에서 달라질 수 있는 점을 회고에 기록.

## 고민 포인트
- HELD 상태지만 시간이 지난 row를 어떻게 해석하는가?
- 좌석은 AVAILABLE인데 현재 유효 hold가 있다는 사실을 어디서 판별하는가?
- 대기 중 시간이 만료되면 어느 시각을 사용해야 하는가?
- 어느 변경이 먼저 flush되어도 원자성이 유지되는가?
- DB 예외를 업무 충돌로 바꿀 범위와 재시도 정책은 무엇인가?
- 서버들의 시간 차이와 운영 DB의 시간대 정책은 어떻게 다룰 것인가?

