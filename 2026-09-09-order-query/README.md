# 주문 내역 조회 API

## 실제 서비스 상황

쇼핑몰 마이페이지에서 회원이 최근 주문과 구매 상품을 확인합니다. 초기에는 빠르던 화면이 주문량이 늘면서 느려졌습니다. 주문별 상품명까지 내려주는 API를 구현하고, 페이지 크기와 연관 데이터가 늘어날 때 발생하는 SQL을 관찰하세요.

이 프로젝트는 학습용 스타터입니다. `OrderQueryService.findOrders(Long memberId, Pageable pageable)`는 `UnsupportedOperationException`을 던집니다. 애플리케이션은 시작되지만 해당 API는 구현 전 정상 응답하지 않습니다. 기본 데이터는 자동으로 삽입되지 않습니다.

## 실행

- JDK 21을 설치하고 IntelliJ에서 이 디렉터리의 `build.gradle`을 엽니다.
- Project SDK와 Gradle JVM을 21로 설정합니다. Gradle은 포함된 Wrapper 8.14.3을 사용합니다.
- Spring Boot 3.5.16, Web, Data JPA, H2, JUnit/AssertJ/MockMvc가 포함되어 있습니다.
- 첫 실행에는 Gradle 및 의존성 다운로드를 위한 인터넷 연결이 필요합니다.

Windows PowerShell:

```powershell
.\gradlew.bat clean build
.\gradlew.bat bootRun
```

macOS/Linux:

```bash
chmod +x gradlew
./gradlew clean build
./gradlew bootRun
```

H2 인메모리 DB는 종료 시 사라집니다. `ddl-auto: create-drop` 설정은 학습용입니다.
기능·HTTP·쿼리 성능 테스트가 활성화되어 있습니다. 구현 전에는 테스트 실패가 정상이며, 구현 코드를 수정해 하나씩 통과시키면 됩니다.

## 기능 요구사항

`GET /api/members/{memberId}/orders?page=0&size=20`

- page는 0부터 시작하며 기본 size는 20입니다. page >= 0, size 1~100을 허용합니다.
- 해당 회원의 주문만 조회합니다. 다른 회원의 주문은 포함하면 안 됩니다.
- `orderedAt` 내림차순으로 정렬합니다. 동일한 시각은 `id` 내림차순으로 결정해 페이지 경계를 안정화하세요.
- 각 주문에 항목 목록과 `productName`을 포함합니다.
- `totalPrice`는 항목별 **주문 당시 unitPrice × quantity의 합**입니다. Product의 현재 price를 사용하지 않습니다.
- 회원이 없으면 HTTP 404와 `MEMBER_NOT_FOUND` 오류를 반환합니다.
- 존재하지만 주문이 없는 회원은 HTTP 200, 빈 content, totalElements=0, totalPages=0입니다.
- 마지막 페이지 이후는 빈 content이며 전체 주문 수는 유지합니다.
- Entity를 직접 반환하지 않습니다. 제공된 DTO를 사용하거나 같은 계약의 DTO로 조정하세요.

응답 예시 (주문 1건, 항목 1개인 경우):

```json
{
  "content": [{
    "orderId": 1,
    "orderedAt": "2026-09-09T12:00:00",
    "items": [{"productId": 1, "productName": "키보드", "quantity": 2, "unitPrice": 1000.00}],
    "totalPrice": 2000.00
  }],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

## 비기능 요구사항

- 주문 수 증가에 따라 SQL이 N+1 형태로 폭증하지 않아야 합니다.
- SQL 개수뿐 아니라 조회 행 수, 메모리 사용, 실제 DB에서 페이지 제한이 적용되는지도 설명하세요.
- 전체 회원 주문을 메모리에 가져와 잘라내는 방식은 허용하지 않습니다.
- 연관 항목의 누락이나 중복 없이 페이지당 주문 수와 전체 주문 수가 정확해야 합니다.
- SQL 개수의 특정 정답은 제시하지 않습니다. 측정 결과를 바탕으로 상한과 근거를 직접 정하세요.

## 도메인과 데이터 조건

`Member → Order → OrderItem → Product`

- Member는 여러 Order를 가집니다. 매핑은 Order에서 Member를 참조하는 단방향입니다.
- Order는 orderedAt과 여러 OrderItem을 갖습니다. 양방향 연결은 `order.addItem(...)`으로 만듭니다.
- OrderItem은 상품, 양수 quantity, 0 이상 unitPrice를 저장합니다. 금액은 BigDecimal입니다.
- Product는 현재 상품명과 가격을 저장합니다. 이 과제의 productName은 현재 상품명입니다.
- 회원과 상품을 먼저 저장하고 주문을 저장하세요. 주문 저장 시 항목은 cascade로 저장됩니다.
- 학습 데이터는 주문당 최소 1개 항목을 사용하며, 통화는 하나로 가정합니다.
- 기본 관계는 LAZY입니다. 인증/결제/주문 생성 API는 과제 범위 밖입니다.

## 제약조건과 구현 TODO

- [ ] 서비스에서 회원 존재 여부 확인과 404 예외 연결
- [ ] 최신순 페이지 조회 및 전체 개수 처리
- [ ] 항목·상품 데이터 조회 전략 선택, 필요한 Repository 메서드 추가
- [ ] DTO 변환과 totalPrice 계산
- [ ] 트랜잭션 경계 설계 (`open-in-view: false` 유지)
- [ ] 제공된 테스트 전체 통과
- [ ] SQL 측정 결과와 선택 이유 기록

Repository는 기본 JpaRepository 뼈대만 제공합니다. 특정 조회 기술이나 연관 로딩 전략은 미리 적용되어 있지 않습니다. 페이지네이션과 컬렉션 조회를 함께 만족시키는 방법은 직접 선택하세요.

## 테스트 시나리오

`src/test/java/com/example/orderquery/order/query/OrderFixtures.java`의 helper는 데이터를 저장하고 flush/clear합니다.

| 시나리오 | 준비 | 기대값 |
|---|---|---|
| 정상 조회 | 1 order × 2 items | 상품명 2개, 수량 1/2, 주문 단가 1000, 총액 3000 (현재 상품 가격 1200은 무시) |
| 페이지 경계 | 25 orders × 1 item | 0페이지 20건 / 1페이지 5건, totalElements 25, totalPages 2, 중복 없음 |
| 최신순 | 12:00/12:01/12:02 주문 | 12:02 → 12:01 → 12:00, 동일 시각에는 id 내림차순 |
| 없는 회원 | 미등록 ID -1 | 서비스 예외와 HTTP 404 |
| SQL 관찰 | 20 orders × 5 items, 모두 다른 상품 | 항목 100개와 상품명 확인, SQL 개수 기록 및 상한 검증 |
| 회원 격리 | 회원 2명의 주문 | 다른 회원 주문 제외 |
| 빈 페이지 | 주문 없는 회원 / 범위 밖 page | 빈 content, 올바른 전체 개수 |
| 입력 제한 | page=-1, size=0 또는 101 | HTTP 400 |

서비스 테스트와 HTTP 테스트에는 데이터 준비와 기대값이 포함되어 있습니다. 테스트를 수정해서 통과시키지 말고 실패 메시지를 기준으로 서비스와 Repository 구현을 고치세요. 정상 HTTP 테스트는 fixture를 별도 트랜잭션에서 커밋한 뒤 요청하여 `open-in-view: false` 환경의 동작까지 확인합니다.

## SQL 관찰 방법

Hibernate SQL DEBUG 로그가 활성화되어 있습니다. 관찰 테스트에서는 Hibernate statistics도 활성화합니다.

1. 1/10/20개 주문에 각각 5개 항목을 준비합니다. 각 실행은 동일한 조건에서 시작하세요.
2. 데이터 준비를 flush하고 영속성 컨텍스트를 clear한 후 통계를 초기화합니다.
3. 조회 및 응답 데이터 접근을 완료한 뒤 prepared statement 수를 읽습니다.
4. 회원 확인, count, 주문, 항목, 상품 조회 SQL을 구분해서 관찰합니다.
5. 같은 상품을 공유하는 데이터와 서로 다른 상품 데이터도 비교하세요.
6. 페이지 크기는 20으로 고정하고 전체 주문 수만 늘린 경우도 비교하세요.

| 전체 주문 수 | 페이지 크기 | 주문당 항목 | SQL 수 | 조회 행/페이지 제한 관찰 |
|---|---|---|---|---|
| 1 | 20 | 5 | 직접 기록 | 직접 기록 |
| 10 | 20 | 5 | 직접 기록 | 직접 기록 |
| 20 | 20 | 5 | 직접 기록 | 직접 기록 |
| 100 | 20 | 5 | 직접 기록 | 직접 기록 |

통계는 SessionFactory 전체 단위이므로 측정 중 다른 요청이나 병렬 테스트를 실행하지 마세요. 쿼리 수가 적다는 사실만으로 효율성을 판정하지 마세요.

## 완료 기준

- [ ] 요구된 HTTP/서비스 테스트가 활성화되어 통과한다.
- [ ] 25건의 20/5 페이지, 최신순, 회원 격리, 빈 결과가 정확하다.
- [ ] productName과 주문 시점 단가 기반 totalPrice가 정확하다.
- [ ] Entity 직접 반환 없이 OSIV가 꺼진 정상 HTTP 요청이 성공한다.
- [ ] 20 × 5 데이터의 SQL 수와 증가 추세를 기록했다.
- [ ] 페이지 제한, 전체 개수, 연관 데이터 로딩의 trade-off를 설명할 수 있다.

## 생각해볼 함정

- 컬렉션 결합으로 SQL 행이 늘어나면 주문 기준 페이지 크기와 count는 어떻게 달라질까요?
- 경고 로그가 있는데도 테스트에서 20건이 나오면 DB 페이지네이션이 된 걸까요?
- 여러 주문이 같은 상품을 참조하거나 영속성 컨텍스트에 데이터가 남아 있으면 무엇이 가려질까요?
- 서비스가 반환된 뒤 DTO 변환이 일어나면 지연 로딩과 트랜잭션에 어떤 문제가 생길까요?
- 최신순의 시각이 같다면 페이지 사이 중복/누락을 어떻게 줄일까요?
- 쿼리가 1개여도 조회 행이 지나치게 많으면 빠를까요?
- 상품 가격이 바뀌었을 때 과거 주문 금액은 무엇을 기준으로 해야 할까요?

## 구현 후 회고

선택한 전략 / 대안 / SQL 측정 결과 / 페이지 처리 근거 / 남은 한계를 여기에 기록하세요.
