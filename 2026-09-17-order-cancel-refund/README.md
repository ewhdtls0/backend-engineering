# 주문 취소 후 환불 처리

외부 결제 환불과 로컬 DB 변경 사이의 원자성 차이를 다루는 Spring Boot 과제의 완성 구현입니다.

## 실행

요구 환경은 Java 21입니다.

```powershell
.\gradlew.bat testClasses
.\gradlew.bat test
```

Unix 계열에서는 `./gradlew testClasses`, `./gradlew test`를 사용합니다.

## API

```http
POST /api/orders/{orderId}/cancel
```

완료 응답 예시:

```json
{
  "orderId": 1,
  "status": "CANCELED",
  "refundStatus": "COMPLETED"
}
```

이미 처리 중인 주문은 외부 환불을 다시 호출하지 않고 `CANCELING / PENDING`을 반환합니다. 결과를 알 수 없는 외부 장애는 HTTP 503으로 전달됩니다.

## 상태 의미

| Order | Payment | 의미 | 자동 외부 재호출 |
|---|---|---|---|
| `PAID` | `PAID` | 취소 시작 전 | 신규 취소에서 1회 |
| `CANCELING` | `REFUND_PENDING` | 외부 처리 중 또는 결과 확인 필요 | 금지 |
| `CANCELING` | `REFUND_FAILED` | 외부 효과가 없다고 확인된 거절 | `processRefund`에서 가능 |
| `CANCELED` | `REFUNDED` | 환불 완료 | 금지 |

`REFUND_PENDING`과 `REFUND_FAILED`를 구분하는 이유는 timeout이나 서버 중단 시 외부에서는 이미 환불됐을 수 있기 때문입니다. 결과를 모르는 상태에서 환불 API를 다시 호출하면 이중 환불이 될 수 있습니다.

## 구현 구조

`CancelOrderService`는 전체 흐름을 조정하고 `RefundStateService`는 짧은 DB 트랜잭션만 담당합니다.

```text
1. 준비 트랜잭션
   PAID / PAID -> CANCELING / REFUND_PENDING
   commit

2. 트랜잭션 밖에서 PaymentGateway.refund 호출

3. 완료 트랜잭션
   CANCELING / REFUND_PENDING -> CANCELED / REFUNDED
   commit
```

이 경계 덕분에 최대 5초인 외부 호출 동안 DB connection과 row lock을 계속 점유하지 않습니다. 외부 성공 뒤 완료 저장이 실패해도 1단계에서 커밋한 `CANCELING / REFUND_PENDING`은 남습니다.

동시 신규 취소는 준비 단계의 주문 pessimistic lock으로 직렬화합니다. 첫 요청은 외부 호출 전에 준비 상태를 커밋하고 잠금을 해제합니다. 뒤따른 요청은 `CANCELING / REFUND_PENDING`을 보고 외부 환불을 호출하지 않습니다.

도메인의 `startCancellation`, `completeCancellation`, `startRefund`, `completeRefund`, `rejectRefund` 메서드는 허용된 상태 전이를 검사합니다. 테스트 fixture 생성을 위한 기존 `changeStatus`는 유지했지만 서비스 로직에서는 사용하지 않습니다.

## 실패 처리

- `RefundRejectedException`: 결제사가 환불을 수행하지 않았다고 확정한 경우입니다. Payment를 `REFUND_FAILED`로 기록하며 재처리할 수 있습니다.
- 그 밖의 gateway 예외: 성공 여부를 알 수 없습니다. `REFUND_PENDING`을 유지하고 `RefundProcessingException`을 던집니다.
- gateway 성공 후 DB 완료 저장 실패: `REFUND_PENDING`을 유지하고 `RefundProcessingException`을 던집니다.
- `processRefund(paymentId)`: `REFUND_FAILED`만 다시 호출합니다. `REFUND_PENDING`과 `REFUNDED`는 외부 호출 전에 거절합니다.

실제 결제사 adapter는 외부 효과가 없다고 명확히 확인한 응답에만 `RefundRejectedException`을 사용해야 합니다. timeout, 연결 종료, 해석할 수 없는 응답을 이 예외로 바꾸면 안 됩니다.

## 남아 있는 원자성 한계

XA와 외부 결제사의 멱등성 키·결과 조회 기능이 없으므로 다음 구간을 하나의 원자 작업으로 만들 수는 없습니다.

```text
gateway 환불 성공
        ↓
완료 DB commit 전에 서버 중단
```

이때 DB에는 `REFUND_PENDING`이 남습니다. 현재 구현은 안전을 위해 자동 재호출하지 않습니다. 운영 환경에서는 결제사 관리자 조회나 별도 결과 조회 기능으로 실제 환불 여부를 확인한 후 완료 상태를 복구해야 합니다.

## 테스트

모든 테스트에는 한글 `@DisplayName`이 있으며 구현 방식 대신 외부에서 관찰 가능한 결과를 검증합니다.

| 분류 | 개수 | 주요 검증 |
|---|---:|---|
| 인수 테스트 | 11 | 정상 환불, 순차·동시 중복, 처리 중 응답, 거절과 재처리, 결과 불명 차단, 완료 SQL 장애 |
| Controller 테스트 | 5 | routing, JSON, 404, 409, 503, ID validation |
| 기반 테스트 | 4 | context, fixture commit, fake 기록, H2 failure trigger |

외부 성공 뒤 DB 저장 실패 테스트는 H2 trigger로 실제 완료 `UPDATE`를 실패시킵니다. 동시 취소 테스트는 첫 gateway 호출을 latch로 멈춘 동안 두 번째 요청을 실행해 외부 호출이 총 1회인지 확인합니다.

테스트 코드는 과제의 고정 명세입니다. 삭제, `@Disabled`, assertion 완화, fake 동작 변경 없이 main 코드를 수정해야 합니다.

## 주요 파일

- `service/CancelOrderService.java`: 외부 호출을 포함한 orchestration
- `service/RefundStateService.java`: 준비·완료·실패 상태의 짧은 트랜잭션
- `service/PaymentGateway.java`: 외부 결제사 인터페이스
- `repository/OrderRepository.java`: 동시 신규 취소를 막는 주문 잠금 조회
- `support/FakePaymentGateway.java`: 호출 기록·거절·지연·성공 후 장애 주입용 테스트 대역
- `support/CompletionWriteFailure.java`: 완료 SQL 실패를 만드는 H2 trigger

SQL과 bind parameter는 `application.yml` 설정으로 확인할 수 있고 OSIV는 꺼져 있습니다. 상세 실행 결과는 [VERIFICATION.md](VERIFICATION.md), 이전 구현의 문제와 수정 내역은 [CODE_REVIEW.md](CODE_REVIEW.md)를 참고하세요.
