# 중복 요청에도 한 번만 처리되는 결제 승인 코드리뷰

- 리뷰일: 2026-09-16
- 대상: `2026-09-15-payment-request`
- 검증 환경: Java 21, Spring Boot 3.5.16, H2
- 테스트 결과: 통합 테스트 20개 통과

## 종합 평가

결제 승인, 순차 재요청 결과 재사용, 같은 주문의 동시 요청, Gateway의 명확한 실패 후 재시도라는 핵심 흐름은 잘 구현했다.

- 멱등 키를 `Order`에서 분리해 전역 유일 키를 가진 별도 Entity로 모델링했다.
- `OrderRepository.findById()`에 비관적 쓰기 락을 적용해 같은 주문에 대한 요청을 DB 수준에서 직렬화했다.
- 같은 키와 같은 요청은 저장된 Payment를 조회해 최초 결과를 반환한다.
- 같은 주문의 동시 요청 10건에서 모든 호출자의 성공 결과, Payment 1건, Gateway 호출 1건을 함께 검증한다.
- Gateway가 승인 전에 명확히 실패한 경우 Payment와 PAID 상태를 남기지 않아 같은 키 재시도가 가능하다.
- 테스트마다 무엇을 검증하는지 `@DisplayName`으로 설명해 학습 과정에서 실패 원인을 읽기 쉬워졌다.

다만 이 과제의 핵심인 **전역 멱등 키의 예외 우선순위와 동시성 범위**에는 아직 보완할 부분이 있다. 현재 통합 테스트는 모두 통과하지만, 테스트에 없는 입력과 경쟁 조건에서는 요구사항을 어길 수 있다.

## 잘 구현한 부분

### 1. 주문과 멱등 키의 책임 분리

`Order`는 주문 금액과 결제 상태만 가진다. 멱등 키는 `OrderIdempotencyKey`가 보관한다.

```java
@Column(nullable = false, unique = true)
private String idempotencyKey;
```

주문 생성 시점과 결제 요청 시점을 분리한 판단이 적절하다. 테스트 fixture도 키 없는 주문을 만들고, `PaymentService.pay()`가 호출될 때 키 기록을 처리할 수 있다.

### 2. 같은 주문의 중복 승인 방지

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<Order> findById(Long id);
```

같은 주문으로 들어온 요청은 하나씩 처리된다. Gateway 호출까지 락을 유지하므로 처리량과 대기 시간의 trade-off는 있지만, 이번 과제의 “동시 10건도 모두 같은 결과를 반환” 계약을 직관적으로 만족한다.

### 3. 재시도와 Gateway 실패 정책

동일 키의 성공 요청은 Payment를 재조회해 결과를 재사용한다. Gateway가 승인 전에 실패하면 트랜잭션이 롤백되어 Payment·키 기록·주문 PAID 상태가 남지 않는다. 따라서 같은 키 재시도가 새 승인 시도로 진행된다.

### 4. 요구사항을 드러내는 통합 테스트

정상 승인, 같은 키 재요청, 키와 주문·금액의 충돌, 동시 요청, Gateway 실패 재시도, HTTP 오류 계약을 실제 H2와 Spring 서비스로 검증한다. 특히 동시 테스트는 worker의 예외를 모두 모으므로 일부 호출이 실패해도 성공 결과만 골라 통과하는 문제가 없다.

## 보완할 부분

### 1. 기존 키 충돌을 주문 조회보다 먼저 판단해야 함

대상: `PaymentService.pay()`

현재는 입력 기본 검증 뒤 주문을 락으로 조회하고, 그 다음에 기존 멱등 키를 조회한다.

```java
Order order = orders.findById(orderId)
        .orElseThrow(...);

Optional<OrderIdempotencyKey> existing = keys.findByIdempotencyKey(idempotencyKey);
```

요구사항은 같은 키에 다른 요청이 오면 `CONFLICT`가 신규 주문의 존재 여부나 금액 검증보다 우선한다고 정한다. 따라서 이미 성공한 키 `K`에 대해 다음 요청은 `CONFLICT`여야 한다.

```text
pay(없는 주문 ID, K, 같은 금액)
pay(다른 주문 ID, K, 음수 금액)
```

현재 구현은 각각 `NOT_FOUND`, `INVALID_REQUEST`가 될 수 있다. 공백 키·null처럼 키 자체가 유효하지 않은 경우만 먼저 거부하고, 유효한 키의 기존 기록은 주문 조회와 신규 요청 금액 검증보다 먼저 비교해야 한다.

추가 테스트도 필요하다.

```text
이미 승인된 key를 없는 주문 ID와 함께 요청 → CONFLICT
이미 승인된 key를 음수 또는 다른 금액과 함께 요청 → CONFLICT
```

### 2. 서비스의 0원 결제 검증

대상: `PaymentService.pay()`

현재 서비스는 `request.amount() < 0`만 검사한다.

```java
request == null || request.amount() < 0
```

과제 계약은 양수 금액이므로 `<= 0`이어야 한다. HTTP 요청은 `@Positive`가 막아도 서비스 직접 호출에서 주문 금액이 0인 경우 승인까지 갈 수 있다.

### 3. 다른 주문에서 같은 키가 동시에 들어오는 경쟁 조건

대상: `PaymentService.pay()`

현재 락은 Order에 걸린다. 서로 다른 주문 A와 B에 같은 키가 동시에 오면 각 요청은 서로 다른 Order 락을 얻는다. 둘 다 키 기록이 없다고 보고 Gateway를 호출할 수 있다. 이후 키의 UNIQUE 제약에서 한 요청만 DB 저장에 실패하지만, Gateway 승인 두 건은 이미 발생했을 수 있다.

```text
요청 A: 주문 A 락 → 키 없음 → Gateway 승인
요청 B: 주문 B 락 → 키 없음 → Gateway 승인
둘 중 하나: 키 UNIQUE 제약 오류
```

전역 키를 안전하게 처리하려면 외부 Gateway 호출 전에 키를 DB에 선점해야 한다. `PROCESSING`·`APPROVED`·`FAILED` 같은 처리 상태와 Payment ID를 키 Entity에 두고, 같은 키 요청은 결과를 기다리거나 기존 결과를 재사용하도록 설계할 수 있다. 명확한 승인 전 Gateway 실패면 해당 키 기록을 제거하거나 재시도 가능한 상태로 바꾸면 된다.

추가로 다음 통합 테스트가 필요하다.

```text
서로 다른 주문에 같은 키를 동시에 요청 → Gateway 호출 1회, 한 요청은 성공하고 다른 요청은 CONFLICT
```

### 4. 모든 예외를 Gateway 실패로 변환하는 catch

대상: `PaymentService.pay()`

```java
catch (Exception e) {
    throw new MissionException(MissionException.Code.GATEWAY_FAILED, "요청에 실패했습니다.");
}
```

이 범위에는 Gateway 예외뿐 아니라 DB 제약 오류와 프로그래밍 오류도 포함된다. 예를 들어 위의 전역 키 경쟁에서 발생한 UNIQUE 제약 오류는 Gateway가 성공한 뒤 발생하지만, 호출자에게는 `GATEWAY_FAILED`로 보인다.

`GatewayException`만 잡아 `GATEWAY_FAILED`로 변환하고, DB 제약 오류는 키 선점 경쟁을 복구하는 별도 경로에서 의도적으로 처리하는 것이 좋다.

### 5. 주문당 Payment 1건의 DB 안전장치

대상: `Payment.orderId`

Order 비관적 락이 현재 서비스 경로에서는 중복 결제를 막는다. 다만 주문당 승인 Payment 1건은 핵심 불변식이므로 `payments.order_id`에도 유일 제약을 두면 다른 코드 경로, 데이터 보정 작업, 락 회귀가 생겨도 DB가 마지막 방어선이 된다.

```java
@Column(nullable = false, unique = true)
private Long orderId;
```

## 예외 순서 정리

이번 과제에서 권장하는 판단 순서는 다음과 같다.

1. key가 null 또는 공백이면 `INVALID_REQUEST`
2. 유효한 key의 기존 기록이 있으면 요청의 orderId·amount를 비교
   - 다르면 `CONFLICT`
   - 같고 승인 완료면 기존 결과 반환
   - 같고 재시도 가능 실패면 재시도 흐름으로 진행
3. 신규 키인 경우 orderId·amount를 검증
   - 주문 없음이면 `NOT_FOUND`
   - 금액이 0 이하이거나 주문 금액과 다르면 `INVALID_REQUEST`
   - 이미 결제된 주문에 새 키면 `CONFLICT`
4. 키 선점 후 Gateway 승인과 Payment 저장 수행

이 순서를 테스트로 고정하면 예외 우선순위가 구현 중 흔들리지 않는다.

## 권장 수정 우선순위

1. 기존 멱등 키 조회를 주문 조회·신규 금액 검증보다 앞에 배치하고 예외 순서 테스트 추가
2. 서비스 금액 검증을 `<= 0`으로 수정
3. 전역 키 선점 상태 모델을 도입하고 다른 주문의 같은 키 동시 요청 테스트 추가
4. `GatewayException`만 변환하도록 catch 범위 축소
5. `Payment.orderId` DB 유일 제약 추가

## 최종 판단

현재 20개 테스트를 통과했고, 같은 주문 안에서의 멱등 재시도와 동시 요청 처리는 잘 검증됐다. 그러나 전역 키의 충돌 우선순위와 다른 주문 간 동시 키 재사용에는 빈틈이 있다.

위 보완을 마치면 단순한 “테스트 통과”를 넘어, 멱등 키가 전역 식별자라는 모델을 실제 경쟁 조건에서도 지키는 결제 처리로 완성된다.
