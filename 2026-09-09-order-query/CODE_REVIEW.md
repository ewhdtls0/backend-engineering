# 주문 내역 조회 API 코드리뷰

- 리뷰일: 2026-09-11
- 대상: `2026-09-09-order-query`
- 검증 환경: Java 21, Spring Boot 3.5.16, H2
- 검증 결과: 전체 테스트 15개 통과
- 성능 테스트 결과: 20 orders × 5 items 조회 시 SQL 5개

## 종합 평가

과제에서 요구한 핵심 기능은 올바르게 구현되었다.

- 존재하지 않는 회원은 `MemberNotFoundException`으로 처리한다.
- 주문은 `orderedAt DESC, id DESC`로 안정적으로 정렬한다.
- 주문 조회에 데이터베이스 페이지네이션이 적용된다.
- Entity를 직접 응답하지 않고 DTO로 변환한다.
- 상품명과 주문 항목을 응답에 포함한다.
- 총액은 상품의 현재 가격이 아니라 주문 당시 `unitPrice × quantity`의 합으로 계산한다.
- `open-in-view: false` 환경에서 서비스 트랜잭션 안에 DTO 변환을 완료한다.
- 컬렉션 fetch join을 사용하지 않고 batch fetching으로 N+1을 제한한다.

치명적인 기능 오류는 발견되지 않았다. 특히 처음 시도했던 컬렉션 fetch join과 `Pageable` 조합에서 벗어나, 주문 페이지 조회와 batch fetching을 조합한 판단이 적절하다.

## 난이도

이 과제는 단순 CRUD보다 한 단계 높은 중급 초입 수준이며, 체감 난이도는 약 6.5~7/10이다.

다음 내용을 동시에 이해해야 하기 때문이다.

- JPA 지연 로딩과 N+1
- 컬렉션 fetch join과 페이지네이션의 충돌
- batch fetching의 동작 방식
- content query와 count query의 분리
- 동일 시각 데이터를 위한 보조 정렬
- OSIV와 트랜잭션 경계
- 실제 SQL 개수 측정

스타터 코드와 테스트가 제공되었으므로 서비스를 처음부터 설계하는 과제보다는 범위가 좁다. 다만 페이지네이션이 실제 DB에서 수행되는지 확인하고 N+1을 제한하는 부분은 실무에서도 자주 실수하는 지점이다.

## 잘 구현한 부분

### 1. 조회 트랜잭션 경계

`OrderQueryService`에 다음 설정을 적용했다.

```java
@Transactional(readOnly = true)
```

DTO 변환 과정에서 `Order.items`와 `OrderItem.product`에 접근하므로 영속성 컨텍스트가 필요하다. 서비스 안에서 DTO 변환을 마치기 때문에 `open-in-view: false`에서도 정상 HTTP 응답을 만들 수 있다.

### 2. 데이터베이스 페이지네이션 유지

주문 쿼리에서는 컬렉션인 `items`를 fetch join하지 않는다. 따라서 Hibernate가 전체 결과를 메모리로 읽은 다음 잘라내는 문제를 피하고, 생성 SQL에 `OFFSET/FETCH`가 적용된다.

### 3. 안정적인 최신순 정렬

```java
order by o.orderedAt desc, o.id desc
```

`orderedAt`이 같은 주문도 `id DESC`로 순서가 결정된다. 페이지를 이동할 때 동일 시각 주문의 순서가 매번 바뀌는 문제를 줄인다.

### 4. 주문 당시 가격으로 총액 계산

```java
item.getUnitPrice()
        .multiply(BigDecimal.valueOf(item.getQuantity()))
```

과거 주문 금액은 Product의 현재 가격이 아니라 OrderItem에 저장된 주문 당시 가격으로 계산해야 한다. 이 요구사항을 정확히 반영했다.

### 5. N+1 제한

```yaml
hibernate:
  default_batch_fetch_size: 100
```

현재 테스트에서는 다음 5개의 SQL로 20개 주문과 100개 항목을 조회한다.

1. 회원 존재 확인
2. 주문 페이지 조회
3. 전체 주문 수 count
4. OrderItem batch 조회
5. Product batch 조회

주문 수만큼 SQL이 증가하지 않으므로 N+1 형태의 폭증을 막았다.

## 보완할 부분

### 1. 불필요한 Member fetch join 제거

대상: `OrderRepository.findAllByMemberId()`

현재 쿼리:

```java
select o
from Order o
join fetch o.member
where o.member.id = :memberId
order by o.orderedAt desc, o.id desc
```

응답을 만들 때 Member 데이터를 사용하지 않고, 서비스에서 회원 존재 여부도 이미 별도로 확인한다. 따라서 `join fetch o.member`는 불필요한 조인과 컬럼 조회를 만든다.

다음처럼 단순화할 수 있다.

```java
select o
from Order o
where o.member.id = :memberId
order by o.orderedAt desc, o.id desc
```

기능상 오류는 아니며 작은 최적화와 의도 정리 수준의 개선이다.

### 2. batch size 100의 근거 기록

`default_batch_fetch_size`는 전역 Hibernate 설정이므로 모든 batch-fetch 가능한 지연 로딩 연관관계에 영향을 준다.

이번 API의 최대 페이지 크기가 100이므로 다음과 같은 근거를 남길 수 있다.

> 한 페이지에서 조회 가능한 주문이 최대 100건이므로 Order.items를 한 번의 IN 쿼리로 조회하기 위해 batch size를 100으로 설정했다.

실제 서비스에서는 DB의 IN 절 제한, 평균 페이지 크기, 항목 수, 메모리 사용량을 측정해 값을 조정해야 한다.

### 3. 페이지 변수명 개선

현재 이름:

```java
Page<Order> allOrdersByMemberId
```

모든 주문이 아니라 현재 페이지의 주문만 담으므로 다음 이름이 더 정확하다.

```java
Page<Order> orderPage
```

### 4. Page.map 활용

현재 구현은 `Page<Order>`를 stream으로 변환한 뒤 페이지 메타데이터를 수동으로 복사한다. `Page.map()`을 사용하면 데이터와 메타데이터의 관계가 더 명확해진다.

```java
Page<OrderResponse> responsePage = orderRepository
        .findAllByMemberId(memberId, pageable)
        .map(OrderResponse::from);

return new OrderPageResponse(
        responsePage.getContent(),
        responsePage.getNumber(),
        responsePage.getSize(),
        responsePage.getTotalElements(),
        responsePage.getTotalPages()
);
```

### 5. DTO 변환 책임

현재 `OrderResponse.from(Order)`은 간결하고 작은 프로젝트에 적합하다. 다만 API DTO가 JPA Entity에 직접 의존한다.

프로젝트가 커지고 같은 Entity로 여러 종류의 응답을 만들게 되면 매핑을 서비스 내부의 private 메서드나 별도 Mapper로 분리하는 방식을 고려할 수 있다. 현재 과제에서는 별도 Mapper까지 추가할 필요는 없다.

### 6. items 중복 순회

`OrderResponse.from()`에서 items를 DTO로 변환할 때 한 번, 총액을 계산할 때 한 번 순회한다. 현재 페이지 크기와 항목 수에서는 문제가 되지 않으며 추가 SQL도 발생하지 않는다.

성능보다 가독성이 더 중요한 구간이므로 지금 형태도 허용 가능하다. 한 번의 반복으로 합치더라도 코드가 복잡해지면 현재 구현을 유지하는 편이 낫다.

### 7. 조회용 복합 인덱스

실제 데이터가 많다면 다음 조회 조건과 정렬을 지원하는 인덱스가 필요하다.

```text
(member_id, ordered_at, id)
```

H2의 작은 테스트 데이터에서는 차이가 드러나지 않는다. 운영 DB를 사용한다면 실행 계획을 확인한 후 마이그레이션으로 인덱스를 추가해야 한다.

### 8. 코드 정리

`OrderResponse`에서 다음 항목을 정리할 수 있다.

- 사용하지 않는 `Collectors` import 제거
- `from()` 메서드 뒤의 불필요한 세미콜론 제거
- 구현이 끝난 클래스의 TODO 주석 제거

## 권장 수정 우선순위

1. 불필요한 `join fetch o.member` 제거
2. 변수명을 `orderPage`로 변경하고 `Page.map()` 적용
3. 사용하지 않는 import, 세미콜론, 완료된 TODO 제거
4. batch size 100의 선택 근거를 README에 기록
5. 운영 DB를 도입할 때 복합 인덱스와 실행 계획 확인

1~3은 코드 가독성과 조회 의도를 개선하는 작은 리팩터링이다. 4~5는 구현 오류 수정이 아니라 설계 근거와 운영 성능을 보완하는 작업이다.

## 최종 판단

현재 구현은 과제 요구사항을 충족하며 테스트로 검증되었다. 페이지네이션과 컬렉션 연관 조회의 충돌을 피하고, batch fetching으로 SQL 증가를 제한한 전략도 타당하다.

다음 단계에서는 단순히 테스트를 통과하는 데서 끝내지 않고 다음 질문에 답할 수 있으면 좋다.

- 왜 컬렉션 fetch join을 제거했는가?
- 왜 batch size를 100으로 선택했는가?
- SQL 5개가 각각 어떤 역할을 하는가?
- 데이터가 수백만 건으로 늘어날 때 어떤 인덱스가 필요한가?
- offset pagination이 깊어질 때 어떤 대안을 고려할 수 있는가?

이 내용을 설명할 수 있다면 이번 과제의 핵심을 제대로 이해한 것이다.
