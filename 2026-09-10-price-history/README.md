# 상품 가격 변경 이력 조회

Java 21 · Spring Boot 3.5.16 · Gradle 8.14.3 · Web/JPA/Validation/H2

## 목표
현재 가격만 있는 상품에 가격 변경 이력을 추가하세요. 가격 변경과 이력 저장은
하나의 논리적 작업이며, 실패하면 둘 모두 반영되지 않아야 합니다.

## 시작
IntelliJ에서 이 폴더의 build.gradle을 프로젝트로 엽니다.
Project SDK와 Settings > Build, Execution, Deployment > Build Tools > Gradle의
Gradle JVM을 Java 21로 설정하고 Gradle 동기화를 실행합니다.

Windows PowerShell:
```powershell
.\gradlew.bat test
.\gradlew.bat build
.\gradlew.bat bootRun
```
JAVA_HOME은 로컬 JDK 21 경로로 설정하세요. 최초 실행은 인터넷이 필요합니다.
H2는 메모리 DB이며 종료하면 데이터가 사라집니다. 상품 생성 API와 초기 데이터는
제공하지 않습니다. 테스트에서 ProductFixture.product()를 repository로 저장해
반환된 ID를 사용하세요. 수동 API 실습용 데이터는 직접 초기화 코드를 추가하세요.

## API 계약
PATCH /api/products/{productId}/price
```json
{"price":42000,"reason":"공급가 인상"}
```
성공은 204(응답 본문 없음). 실제 가격이 달라질 때만 이력을 남기고,
같은 가격이면 가격·updatedAt·이력을 변경하지 않습니다.
스타터 입력 정책은 가격 0 이상, 정수 최대 17자리/소수 최대 2자리,
사유 필수(공백 불가, 최대 500자)입니다.

GET /api/products/{productId}/price-histories?from=2026-09-01T00:00:00&to=2026-09-10T23:59:59
```json
[{"previousPrice":39000,"changedPrice":42000,"reason":"공급가 인상","changedAt":"2026-09-10T09:30:00"}]
```
최신순 배열이며 이력이 없으면 []입니다. from/to는 각각 생략할 수 있습니다.
날짜는 시간대 없는 LocalDateTime입니다. 경계 포함 여부, from > to 처리,
동일 시각 정렬 기준은 직접 결정하고 테스트와 문서에 남기세요.
미존재 상품은 404, 잘못된 입력은 400을 목표로 합니다.
유효한 요청도 서비스 구현 전에는 UnsupportedOperationException으로 실패합니다.

## 직접 해결할 부분
- service/PriceHistoryService의 changePrice와 getPriceHistories
- 트랜잭션 경계와 실패 시 롤백
- BigDecimal 가격 비교 정책
- Entity 내부 변경 또는 Service에서의 변경 책임
- dirty checking과 explicit save 사용 여부
- PriceHistory 생성자/팩토리 및 생성 책임
- 기간 검색 구현 방식, 정렬과 DTO 변환
- Product ID 참조 유지 또는 JPA 연관관계 전환

두 서비스 메서드에는 트랜잭션 애노테이션과 해결 로직이 없습니다.
Product는 fixture용 생성자와 getter만, PriceHistory는 JPA 기본 생성자와 getter만
제공합니다. 필요한 변경/생성 인터페이스를 직접 설계하세요.
Repository에는 기본 JpaRepository만 있습니다.

## 테스트 순서
ApplicationSmokeTest는 설정/컨텍스트 기동 확인용으로 활성화되어 있습니다.
PriceHistoryMissionTest에는 활성화된 검증 테스트 8개가 있습니다.
현재 서비스 핵심 로직은 의도적으로 미구현이라, 정상·동일 가격·조회·404·롤백
테스트는 실패합니다. 구현을 시작하기 전에 이 실패를 확인하고, 구현 후 전체 테스트를 통과시키세요.

1. 정상 변경: 상품과 이력의 모든 필드 확인.
2. 동일 가격: 이력 미생성, updatedAt 불변, 소수 scale 차이 확인.
3. 여러 번 변경: 이력 개수와 최신순, 같은 시각 tie-break 확인.
4. 기간: from/to 각각 생략, 경계 포함 및 제외, 빈 결과.
5. 잘못된 기간 정책.
6. HTTP validation 오류.
7. 미존재 상품의 PATCH/GET.
8. 이력 저장 실패 시 롤백.

롤백 검증은 실제 Spring 서비스 프록시를 호출해야 합니다.
테스트 메서드에 @Transactional을 붙여 서비스의 누락을 보완하지 마세요.
상품 fixture는 먼저 커밋하고, 이력 저장 경로에 실패를 주입하거나
실제 DB 제약조건 오류를 유도하세요. 선택한 save/flush/commit 경로에서
실패가 정말 발생했는지도 확인하세요. 예외 뒤 별도 트랜잭션/새 영속성
컨텍스트에서 상품 가격과 updatedAt 및 이력 개수를 다시 조회하세요.
같은 관리 객체만 확인하거나 mock 상태만 확인하면 DB 롤백 증거가 아닙니다.

## SQL 관찰
application.yml에서 org.hibernate.SQL=DEBUG, org.hibernate.orm.jdbc.bind=TRACE와
format_sql을 설정했습니다. 변경/동일 가격/실패 시 UPDATE와 INSERT,
flush 시점을 비교하세요. 바인딩 로그에는 입력 데이터가 출력됩니다.

## IntelliJ TODO 목록
View > Tool Windows > TODO를 엽니다.
메뉴를 찾기 어렵다면 Ctrl+Shift+A에서 TODO를 검색하세요.
Project 탭으로 프로젝트 전체, Current File로 현재 파일의 TODO를 볼 수 있습니다.
필터로 TODO가 숨겨지지 않았는지 확인하세요.

## 회고
- 선택한 트랜잭션 경계와 근거:
- dirty checking / save 선택과 관찰한 SQL:
- 이력 생성 책임:
- 기간 경계와 정렬 정책:
- 롤백 테스트가 실제 DB를 검증하는 근거:
- 추가 고민: 동시 가격 변경에서 이전 가격과 이력 일관성.

## 참고
https://docs.spring.io/spring-boot/3.5/system-requirements.html
