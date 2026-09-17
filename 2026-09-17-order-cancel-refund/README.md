# 주문 취소 후 환불 처리

결제가 끝난 주문을 취소하고 외부 결제사를 통해 전액 환불하는 과제입니다.

핵심은 단순한 상태 변경이 아닙니다. 외부 환불은 성공했는데 우리 DB 저장이 실패할 수 있고, 사용자가 취소 버튼을 두 번 누를 수도 있습니다. 어느 시점에 실패해도 현재 상태를 설명하고 필요한 작업을 다시 처리할 수 있도록 구현해야 합니다.

## 가장 먼저 확인할 것

`src/test` 아래 코드는 수정하지 않습니다. 테스트가 이 과제의 실행 가능한 요구사항입니다.

다음 방식으로 테스트를 통과시키면 안 됩니다.

- 테스트 삭제 또는 수정
- `@Disabled` 추가
- assertion 완화
- fixture나 `FakePaymentGateway` 동작 변경
- 테스트에서만 동작하는 분기 추가
- 서비스나 Repository를 Mock으로 교체해 acceptance test 우회

main 코드는 자유롭게 수정하거나 새 클래스를 추가할 수 있습니다. 단, 아래의 고정 메서드 시그니처와 `PaymentGateway` 인터페이스는 유지해야 합니다.

## 구현할 코드

시작 파일은 다음과 같습니다.

```text
src/main/java/study/refund/service/CancelOrderService.java
```

현재 두 메서드는 `UnsupportedOperationException`을 던집니다.

```java
public CancelOrderResponse cancel(Long orderId)

public void processRefund(Long paymentId)
```

두 메서드 내부에 모든 코드를 넣을 필요는 없습니다. 필요한 service, component, repository method, domain method를 main 코드에 추가하고 두 메서드에서 위임해도 됩니다.

### 1. `cancel(Long orderId)`

주문 취소 요청을 처리합니다.

구현 결과는 다음 조건을 만족해야 합니다.

1. 주문과 결제를 조회합니다.
2. 주문 상태에 따라 신규 취소, 완료된 취소, 처리 중인 취소를 구분합니다.
3. 신규 취소라면 `PaymentGateway.refund(paymentKey, amount)`를 호출합니다.
4. 환불 성공 후 최종 DB 상태를 다음과 같이 만듭니다.

```text
Order.status   = CANCELED
Payment.status = REFUNDED
```

5. 정상 응답은 정확히 다음 의미를 가져야 합니다.

```java
new CancelOrderResponse(
    orderId,
    OrderStatus.CANCELED,
    CancelOrderResponse.RefundStatus.COMPLETED
)
```

6. 외부 환불이 실패하거나 환불 성공 후 DB 저장이 실패하면 완료 응답을 반환하면 안 됩니다.
7. 실패 후 DB를 조회했을 때 안전하게 재처리할 수 있는 실패와 외부 결과를 확인해야 하는 작업을 구분할 수 있어야 합니다.

고정 테스트에서 결제 상태의 의미는 다음과 같습니다.

```text
REFUND_FAILED  = 외부 환불 효과가 없다고 확인된 실패, 자동 재시도 가능
REFUND_PENDING = 외부 환불 결과가 불명확하거나 로컬 완료 기록이 실패한 상태, 자동 재시도 금지
```

실패 처리 시 예외를 던질지, 미완료 응답을 반환할지는 직접 선택할 수 있습니다. 어느 쪽을 선택해도 DB 상태와 gateway 호출 결과가 요구사항을 만족해야 합니다.

### 2. `processRefund(Long paymentId)`

실패했던 환불을 다시 처리하는 고정 진입점입니다.

구현 결과는 다음 조건을 만족해야 합니다.

1. `REFUND_FAILED` 결제를 조회합니다.
2. 해당 결제의 `paymentKey`와 `amount`로 외부 환불을 호출합니다.
3. 성공하면 Payment를 `REFUNDED`, 연결된 Order를 `CANCELED`로 만듭니다.
4. 이미 `REFUNDED`인 결제를 다시 처리하면 정상 반환하거나 예외로 거절할 수 있습니다. 어느 정책이든 외부 환불은 호출하지 않고 완료 상태를 유지해야 합니다.
5. `REFUND_PENDING`인 결제는 정상 반환하거나 예외로 보류할 수 있습니다. 외부 결과 확인 없이 gateway를 다시 호출하면 안 됩니다.

재처리 로직을 별도 클래스에 구현해도 되지만, 테스트는 `CancelOrderService.processRefund(paymentId)`를 호출합니다. 따라서 이 메서드에서 실제 재처리 로직으로 연결해야 합니다.

## 상태별 동작

### 신규 취소

시작 상태:

```text
Order   = PAID
Payment = PAID
```

성공 결과:

```text
gateway refund 호출 = 정확히 1회
Order                = CANCELED
Payment              = REFUNDED
응답                 = CANCELED / COMPLETED
```

gateway에는 요청이나 클라이언트가 보낸 값이 아니라 DB에 저장된 `paymentKey`와 `amount`를 전달해야 합니다.

### 이미 완료된 취소

시작 상태:

```text
Order   = CANCELED
Payment = REFUNDED
```

결과:

```text
gateway refund 호출 = 0회
응답                 = CANCELED / COMPLETED
```

이미 처리된 요청은 성공 결과를 다시 반환해야 합니다.

### 취소 처리 중인 주문

시작 상태:

```text
Order   = CANCELING
Payment = REFUND_PENDING
```

`cancel(orderId)`는 `OrderNotCancelableException`을 던지고 gateway를 호출하지 않아야 합니다. `REFUND_PENDING`은 외부 결과를 알 수 없는 상태이므로 신규 cancel이나 일반 재처리로 환불을 다시 호출하지 않습니다. 결제사 결과 확인 또는 별도의 운영 복구 절차가 필요합니다.

### 존재하지 않는 주문

`OrderNotFoundException`을 던지고 gateway를 호출하지 않아야 합니다. 새로운 Order나 Payment가 생성되어서도 안 됩니다.

### 외부 환불 실패

Fake gateway가 환불 효과를 만들기 전에 예외를 던집니다.

결과:

```text
gateway 시도 기록 = 존재
gateway 성공 기록 = 없음
Order              = CANCELED가 아님
Payment            = REFUND_FAILED
```

gateway 예외 때문에 준비 상태까지 전부 rollback되어 Payment가 다시 `PAID`가 되면 테스트를 통과하지 못합니다. 이 Fake의 거절은 외부 효과가 없다고 확인된 실패이므로 `REFUND_FAILED`로 남겨 안전한 재처리 대상임을 표현해야 합니다.

### 외부 환불 성공 후 DB 저장 실패

테스트는 gateway가 성공한 직후 H2 trigger를 활성화합니다. 이후 다음 완료 상태를 저장하는 SQL은 실패합니다.

```text
Payment -> REFUNDED
Order   -> CANCELED
```

이때 요구되는 결과는 다음과 같습니다.

```text
gateway 성공 기록 = 정확히 1회
DB 장애 발생 기록 = 존재
Order              = CANCELED가 아님
Payment            = REFUND_PENDING
```

외부 환불은 이미 성공했으므로 이 Payment를 `REFUND_FAILED`로 표시하거나 자동 재호출하면 안 됩니다. `REFUND_PENDING` 상태로 보류하고 외부 결과 확인 또는 별도 운영 복구 대상으로 다뤄야 합니다. 이 테스트는 특정 어노테이션이나 패턴을 검사하지 않고, 실제 gateway 호출 결과와 새 트랜잭션에서 조회한 DB 상태를 확인합니다.

## 제공된 도메인

### Order

```text
id
memberId
status: PAID, CANCELING, CANCELED
totalAmount
```

### Payment

```text
id
order: Order와 LAZY 1:1 연관관계
paymentKey
amount
status: PAID, REFUND_PENDING, REFUNDED, REFUND_FAILED
```

`Order.changeStatus`와 `Payment.changeStatus`는 단순한 상태 변경 메서드입니다. 상태 전이 규칙을 어느 계층에서 검사할지는 직접 결정합니다.

Payment의 `order_id`와 `paymentKey`에는 unique 제약이 있습니다. 이 제약만으로 외부 환불의 중복 호출이 방지되는 것은 아닙니다.

## 외부 결제 인터페이스

```java
public interface PaymentGateway {
    void refund(String paymentKey, long amount);
}
```

인터페이스는 변경하지 않습니다. 운영용 구현은 제공하지 않으며, 기본 Bean은 호출 시 명시적으로 실패합니다. 테스트에서는 호출 횟수, 파라미터, 실패와 성공을 기록하는 `FakePaymentGateway`가 자동으로 주입됩니다.

`FakePaymentGateway`는 순차 테스트용이며 thread-safe하지 않습니다.

## 테스트가 확인하는 내용

| 테스트 | 통과 조건 |
|---|---|
| `paidOrderIsCanceledAndRefundedExactlyOnce` | 정상 응답, 정확한 환불 1회, Order/Payment 완료 |
| `gatewayRejectionLeavesDurableRecoveryEvidence` | 외부 성공 없음, 완료 상태 금지, Payment=REFUND_FAILED |
| `twoSequentialRequestsDoNotRefundTwice` | cancel 2회에도 외부 환불은 총 1회 |
| `previouslyCanceledOrderDoesNotCallGateway` | 이미 완료된 주문은 외부 호출 없이 성공 응답 |
| `missingOrderFailsWithoutCallingGateway` | `OrderNotFoundException`, 외부 호출 및 데이터 생성 없음 |
| `cancelingOrderRejectsNewCancelWithoutCallingGateway` | `OrderNotCancelableException`, 상태 유지, 외부 호출 없음 |
| `externalSuccessThenDatabaseFailureDoesNotEraseRecoveryEvidence` | 외부 성공 후 실제 완료 SQL 실패, Payment=REFUND_PENDING 유지 |
| `knownFailedPaymentCanBeRetriedAndFinalized` | 실패 Payment 재처리 성공, 완료 후 재처리는 거절하거나 무시하며 중복 환불 없음 |
| `pendingPaymentIsNotBlindlyRetried` | 결과 불명인 REFUND_PENDING은 gateway 재호출 없이 보류 |
| `rejectedCancelCanBeRecoveredAfterGatewayBecomesAvailable` | 최초 실패 후 gateway 복구 시 재처리하여 최종 완료 |

테스트는 트랜잭션 어노테이션, 별도 서비스 이름, 이벤트 사용 여부 같은 구현 방식을 검사하지 않습니다. 외부 호출과 최종 DB 상태처럼 밖에서 관찰할 수 있는 결과만 검사합니다.

## 구현 순서 제안

다음 순서로 하나씩 테스트를 통과시키면 원인 파악이 쉽습니다.

1. 없는 주문과 취소 불가능 상태 처리
2. 정상 취소와 응답 DTO
3. 이미 완료된 주문의 중복 요청 처리
4. gateway의 확실한 거절 후 `REFUND_FAILED` 상태 보존
5. `processRefund`를 통한 실패 환불 재처리
6. gateway 성공 후 DB 저장 실패 처리
7. 전체 테스트 실행과 SQL 확인

특정 테스트 하나만 실행하려면 다음과 같이 실행합니다.

```powershell
.\gradlew.bat test --tests '*CancelOrderAcceptanceTest.paidOrderIsCanceledAndRefundedExactlyOnce'
```

전체 테스트:

```powershell
.\gradlew.bat test
```

main/test 소스 컴파일만 확인:

```powershell
.\gradlew.bat testClasses
```

Unix에서는 `gradlew.bat` 대신 `./gradlew`를 사용합니다. Java 21이 필요합니다.

## 반드시 직접 결정할 설계 문제

외부 API는 평균 300ms, 최대 5초가 걸릴 수 있습니다. 다음 질문에 답할 수 있도록 코드를 설계하고 본인의 선택을 기록하세요.

### 트랜잭션과 커넥션

- gateway 호출 중 DB 트랜잭션이 열려 있는가?
- 그동안 connection pool의 커넥션이나 DB lock을 보유하는가?
- 외부 호출이 5초 동안 멈추면 다른 요청에는 어떤 영향이 생기는가?
- flush와 commit 중 어느 시점의 실패를 현재 코드가 감지할 수 있는가?

### 외부 시스템과 DB 사이의 간격

다음 두 작업을 하나의 일반적인 DB 트랜잭션으로 묶을 수는 없습니다.

```text
외부 결제사의 환불
우리 DB의 상태 저장
```

특히 다음 상황에서 무엇이 남고 다음에 무엇을 해야 하는지 설명할 수 있어야 합니다.

| 실패 시점 | 고민할 내용 |
|---|---|
| 환불 요청 전 서버 중단 | 재시작 후 처리할 작업을 어떻게 찾는가? |
| gateway timeout | 외부 환불이 실행되지 않은 것인지, 응답만 잃은 것인지 알 수 있는가? |
| gateway 성공 후 DB 저장 실패 | 돈은 반환됐지만 DB가 미완료일 때 무조건 재호출해도 되는가? |
| gateway 성공 직후 서버 중단 | catch 블록도 실행되지 않았다면 어떤 상태가 남는가? |
| DB 완료 후 HTTP 응답 전 중단 | 고객의 재요청에 중복 환불 없이 같은 결과를 줄 수 있는가? |

제공된 `void refund(paymentKey, amount)`에는 외부 결과 조회 기능이나 멱등성 보장이 없습니다. 따라서 timeout처럼 결과를 알 수 없는 상황에서는 우리 DB만 보고 외부 환불 실행 여부를 확정할 수 없습니다. 확실히 실패한 작업의 자동 재처리와 결과를 알 수 없는 작업의 확인 절차를 구분해야 합니다.

## 제약사항

- XA 또는 분산 트랜잭션 사용 금지
- `PaymentGateway` 메서드 시그니처 변경 금지
- 외부 결제사의 DB 직접 접근 금지
- Redis와 Kafka는 선택 사항이며 필수 아님
- 테스트 코드 수정 금지
- 기본 테스트는 순차 중복 요청을 검증하며 동시 요청 안전성을 증명하지 않음

## SQL과 H2 설정

`application.yml`에서 SQL과 bind 값을 볼 수 있도록 설정되어 있습니다.

```text
org.hibernate.SQL = DEBUG
org.hibernate.orm.jdbc.bind = TRACE
```

H2 콘솔 정보:

```text
URL      /h2-console
JDBC URL jdbc:h2:mem:refund
User     sa
Password 비어 있음
```

OSIV는 꺼져 있고 애플리케이션 종료 시 데이터가 사라집니다. 테스트 성공이 운영 DB의 lock과 장애 동작까지 보장하지는 않습니다.

## 완료 기준

- [ ] `cancel(Long orderId)` 구현
- [ ] `processRefund(Long paymentId)` 구현
- [ ] 정상 환불 시 gateway가 정확한 파라미터로 1회 호출됨
- [ ] Order=CANCELED, Payment=REFUNDED가 새 DB 조회에서도 확인됨
- [ ] 순차 중복 cancel과 완료 Payment 재처리에서 외부 환불이 중복되지 않음
- [ ] 외부 효과 없는 gateway 거절 후 Payment가 REFUND_FAILED로 남음
- [ ] gateway 성공 후 DB 저장 실패에서는 Payment가 REFUND_PENDING으로 남음
- [ ] REFUND_PENDING 결제를 자동 재처리해 외부 환불을 중복 호출하지 않음
- [ ] 없는 주문과 취소 불가능 상태에서 gateway가 호출되지 않음
- [ ] `src/test`를 한 줄도 수정하지 않음
- [ ] `.\gradlew.bat testClasses` 성공
- [ ] `.\gradlew.bat test` 전체 성공

프로젝트 생성 시 테스트와 fixture 자체의 검증 내역은 [VERIFICATION.md](VERIFICATION.md)에 기록되어 있습니다.
