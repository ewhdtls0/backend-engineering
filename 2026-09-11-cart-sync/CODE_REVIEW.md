# 장바구니 항목 동기화 코드리뷰

- 리뷰일: 2026-09-15
- 대상: `2026-09-11-cart-sync`
- 검증 환경: Java 21, Spring Boot 3.5.16, H2
- 검증 결과: 전체 테스트 23개 통과

## 종합 평가

과제의 핵심 요구사항은 충족한다.

- 기존 상품은 `CartItem` 엔티티의 수량만 변경하므로 행 ID를 유지한다.
- 새 상품은 `Cart.items` 컬렉션에 추가하고, 요청에서 빠진 상품은 같은 컬렉션에서 제거한다.
- `orphanRemoval = true`와 `cascade = ALL` 조합으로 항목 삭제와 추가가 flush 시점에 반영된다.
- 빈 요청은 전체 비우기로 처리된다.
- 중복 상품, 수량 0 이하, null 구조, 없는 장바구니와 상품을 구분해 예외 처리한다.
- 서비스 전체가 하나의 트랜잭션 안에서 실행되므로 검증·변경 중 실패하면 기존 장바구니 상태가 롤백된다.
- 동일한 요청을 보냈을 때 수량 변경이 없으므로 CartItem 쓰기 SQL이 발생하지 않는다.

처음에 일반 조회 결과 리스트를 수정하던 문제를 `cartEntity.getItems()`를 수정하는 방식으로 바로잡은 판단이 핵심이다. 이제 컬렉션의 변경이 영속성 컨텍스트에 추적되고, 테스트의 추가·수정·삭제·전체 비우기 시나리오가 모두 통과한다.

## 난이도

이 과제는 중급 초입 수준이며, 체감 난이도는 약 6.5/10이다.

- JPA 연관관계의 주인과 양방향 참조
- `cascade`와 `orphanRemoval`의 차이
- 변경 감지와 컬렉션 변경의 차이
- 입력 검증 순서와 트랜잭션 롤백
- 기존 행 ID를 유지하는 동기화 알고리즘
- 지연 로딩과 SQL 수 관찰

단순 CRUD처럼 `save()` 한 번으로 끝나는 문제가 아니다. 현재 상태와 요청 상태를 비교해 업데이트·추가·삭제를 구분해야 하며, 중간 실패 시 이전 상태를 지키는 것도 요구사항에 포함된다.

## 잘 구현한 부분

### 1. 검증을 변경 전에 수행

`CartService.syncItems()`는 요청 자체, 목록, 항목, 상품 ID, 수량을 먼저 확인한다.

```java
if (request == null || request.items() == null) {
    throw new MissionException(MissionException.Code.INVALID_REQUEST, "잘못된 요청입니다.");
}
```

이후 중복 상품과 `quantity <= 0`도 검사한다. 따라서 null 접근으로 `NullPointerException`이 나거나, 잘못된 요청 때문에 일부 변경이 먼저 반영되는 문제를 막는다.

### 2. 기존 항목은 수량만 변경

```java
if (item.quantity() != cartItem.getQuantity()) {
    cartItem.changeQuantity(item.quantity());
}
```

기존 `CartItem`을 삭제하고 새로 만들지 않기 때문에 ID가 보존된다. 동일 수량이면 setter조차 호출하지 않아 Hibernate dirty checking이 UPDATE를 만들지 않는다.

### 3. 컬렉션 동기화 방식의 선택

```java
allWithProduct.removeIf(
    cartItem -> !requestItemIds.contains(cartItem.getProduct().getId())
);
```

여기서 `allWithProduct`는 조회 결과의 복사본이 아니라 `cartEntity.getItems()` 자체다. 이 차이 때문에 제거한 항목이 고아(orphan)가 되고, `orphanRemoval = true`에 따라 DB DELETE 대상이 된다.

신규 항목도 같은 컬렉션에 넣으므로 `cascade = ALL`에 의해 INSERT 대상으로 포함된다.

### 4. 트랜잭션 경계

```java
@Transactional
public CartResponse syncItems(Long cartId, SyncCartItemsRequest request)
```

요청에서 빠진 항목을 제거한 뒤 신규 상품을 찾는 과정에서 없는 상품이 발견되어도, 예외가 밖으로 전파되면 해당 트랜잭션은 롤백된다. 그래서 기존 장바구니의 DB 행과 ID가 보존된다.

### 5. 응답 계산이 변경 후 상태를 기준으로 함

합계와 item 응답을 동기화가 끝난 `CartItem` 목록에서 만든다. 수량과 상품 가격이 응답과 DB 상태 사이에서 달라지는 문제를 피한다.

## 보완할 부분

### 1. 빈 `data.sql` 때문에 애플리케이션 실행이 실패한다 — 우선 수정

대상: `src/main/resources/data.sql`, `src/main/resources/application.yml`

현재 `data.sql`은 0바이트지만 Spring Boot는 자동 SQL 초기화를 시도한다. 따라서 `bootRun` 시 다음 오류로 ApplicationContext가 시작하지 못한다.

```text
java.lang.IllegalArgumentException: 'script' must not be null or empty
```

테스트는 아래 설정으로 SQL 초기화를 끄기 때문에 통과한다.

```java
"spring.sql.init.mode=never"
```

하지만 앱을 직접 실행할 때는 같은 설정이 없다. 빈 `data.sql`을 삭제하거나, 시드 데이터가 필요 없다면 `application.yml`에 다음 설정을 추가해야 한다.

```yaml
spring:
  sql:
    init:
      mode: never
```

데이터를 자동으로 넣을 목적이었다면 빈 파일 대신 유효한 INSERT 문을 작성하는 편이 낫다.

### 2. `Cart.setItems()`는 제거하는 편이 안전하다

대상: `Cart.setItems(List<CartItem> cartItems)`

이 메서드는 Hibernate가 관리하는 `PersistentCollection`을 일반 `List`로 통째로 바꿀 수 있다. 이 경우 컬렉션 변경 감지와 orphanRemoval이 예상대로 동작하지 않을 수 있다. 현재 사용처도 없다.

대신 도메인 메서드로 의도를 드러내는 편이 좋다.

```java
public void addItem(CartItem item) {
    items.add(item);
    item.setCart(this);
}

public void removeItem(CartItem item) {
    items.remove(item);
    item.setCart(null);
}
```

현재 `CartItem.setCart`는 null을 받을 수 있으므로 위 형태도 가능하다. 다만 이번 매핑에서는 `orphanRemoval`로 삭제할 항목의 cart 참조를 반드시 null로 만들어야 하는지는 JPA 동작을 확인하면서 선택해야 한다. 단순히 setter를 제거하고 현재처럼 컬렉션만 수정하는 것도 이번 과제에서는 충분하다.

### 3. 사용하지 않는 `CartItemRepository` 의존성 제거

대상: `CartService`

현재 서비스는 `CartItemRepository`를 주입받지만 호출하지 않는다. 이전의 별도 fetch join 조회 방식을 컬렉션 방식으로 바꾸면서 남은 의존성이다.

```java
private final CartItemRepository cartItems;
```

생성자와 import를 함께 제거하면 현재 서비스의 의도가 더 분명해진다.

### 4. 합계 수량 타입을 `long`으로 계산

대상: `CartService`의 `AtomicInteger totalQuantity`

응답의 `totalQuantity` 타입은 `long`인데 계산에는 `AtomicInteger`를 사용한다. 서비스는 단일 스레드로 실행되고 있으므로 Atomic 타입이 필요하지 않다. 항목과 수량이 커지면 int 합계가 넘칠 수도 있다.

```java
long totalQuantity = 0L;
for (CartItem cartItem : allWithProduct) {
    totalQuantity += cartItem.getQuantity();
}
```

### 5. 조회 성능의 근거를 남기기

`CartItem.product`는 LAZY이고, 현재 서비스는 `cartEntity.getItems()`를 읽은 뒤 각 항목의 상품 정보에 접근한다. `default_batch_fetch_size: 100`이 있어 이번 30개 상품 테스트에서 N+1 폭증은 제한된다.

다만 이 값이 왜 100인지 README에 근거를 남기는 편이 좋다. 예를 들어 다음과 같이 기록할 수 있다.

> 장바구니 항목과 신규 상품을 최대 100개 단위로 다룬다고 가정하고, Product 지연 로딩을 IN 쿼리로 묶기 위해 batch size를 100으로 설정했다.

한 번의 API에서 장바구니와 상품을 항상 함께 사용한다면 `CartRepository`에 `items`와 `items.product`를 fetch join 하는 전용 조회를 둘지도 검토할 수 있다. 다만 이번 과제는 쿼리 수 상한을 강제하지 않으므로 현재 방식도 요구사항을 만족한다.

### 6. 함수 이름과 주석 정리

`allWithProduct`는 더 이상 fetch join 결과가 아니라 `cartEntity.getItems()`다. `items` 또는 `cartItems`가 더 정확하다. `// 신규 상품 추가 및 요청 없는 상품 제거` 주석도 실제로는 제거가 먼저 일어나므로 처리 순서와 다르다.

## 권장 수정 우선순위

1. 빈 `data.sql`을 삭제하거나 SQL 초기화를 끄고 `bootRun` 확인
2. `Cart.setItems()` 제거
3. 사용하지 않는 `CartItemRepository`, import, `AtomicInteger` 제거
4. `allWithProduct` 이름과 주석 정리
5. batch size 100의 근거와 관찰한 SQL 수를 README에 기록

1번은 앱 시작 실패를 해결하는 기능 수정이다. 2~4번은 도메인 변경의 안전성과 코드 의도를 개선한다. 5번은 운영 성능을 설명하기 위한 기록이다.

## 최종 판단

테스트 기준으로는 기능 요구사항을 충족한다. 특히 기존 항목의 ID를 지키며 필요한 변경만 적용하고, 실패하면 롤백하는 핵심 목표를 달성했다.

다음 단계에서는 아래 질문을 설명할 수 있으면 좋다.

- 왜 `cartItems.findAllWithProduct()` 결과를 수정하는 대신 `cart.getItems()`를 수정해야 했는가?
- `cascade = REMOVE`와 `orphanRemoval = true`는 각각 언제 DELETE를 발생시키는가?
- 새 항목을 추가할 때 `CartItem.cart`와 `Cart.items`를 모두 맞춰야 하는 이유는 무엇인가?
- 없는 상품이 마지막에 발견되어도 기존 삭제가 DB에 남지 않는 이유는 무엇인가?
- 데이터가 30개, 300개로 늘어날 때 어떤 SQL이 증가하는가?
