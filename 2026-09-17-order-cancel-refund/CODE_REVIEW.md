# 코드 리뷰 — 주문 취소 후 환불 처리

## 수정 완료 기록

2026-09-17에 아래 항목을 모두 반영했습니다.

- 준비 상태를 먼저 커밋하고 gateway를 DB 트랜잭션 밖에서 호출한 뒤 완료 상태를 별도 커밋하도록 분리
- 외부 성공 뒤 완료 SQL 실패 시 `CANCELING / REFUND_PENDING` 보존
- `REFUND_FAILED`만 재처리하고 결과 불명인 `REFUND_PENDING`은 외부 재호출 차단
- 처리 중 중복 cancel에 `PENDING` 응답, 완료된 중복 cancel에는 기존 완료 응답 반환
- 주문 pessimistic lock과 동시 실행 테스트로 동일 주문의 외부 환불 1회 보장
- 확정 거절과 결과 불명 예외를 분리하고 결과 불명은 HTTP 503 경로로 전달
- 목적 중심 도메인 상태 전이 메서드와 전이 검증 추가
- 삭제됐던 안전성 테스트 3개를 충돌 없는 상태 의미로 복원하고 동시 취소 테스트 추가
- README와 검증 기록을 현재 구현 및 20개 테스트 기준으로 갱신

최종 판정은 **수정 완료**입니다. 아래 내용은 수정 전 구현을 기준으로 남긴 리뷰 기록입니다.

리뷰 일자: 2026-09-17  
대상: 현재 `2026-09-17-order-cancel-refund` 작업 트리  
검증: Windows · Java 21 · `gradlew.bat test --no-daemon --console=plain`

## 결론

현재 남아 있는 테스트는 **16개 모두 통과**한다. 구성은 acceptance 7개, controller 5개, infrastructure 4개다.

그러나 운영에서 돈이 두 번 환불되거나, 외부에서는 환불됐는데 DB가 다시 `PAID`로 보이는 경로가 남아 있다. 삭제된 세 테스트가 바로 이 경로를 검증하고 있었다. 현재 상태는 학습 구현으로 정상 흐름을 확인한 단계이며, 실제 환불 시스템에 적용하거나 완료로 판단하기에는 핵심 복구·중복 방지 조건이 부족하다.

**판정: 수정 필요 — 현재 상태로 merge 권장하지 않음**

## 잘된 점

- 정상 취소에서 DB의 `paymentKey`와 `amount`를 gateway에 전달하고 최종적으로 Order=`CANCELED`, Payment=`REFUNDED`를 만든다.
- 이미 `CANCELED`인 주문의 순차 중복 요청에서는 gateway를 다시 호출하지 않는다.
- Fake gateway가 확실히 거절한 경로에서 Order=`CANCELING`, Payment=`REFUND_FAILED`가 남고 이후 재처리할 수 있다.
- `REFUND_FAILED` 재처리 성공 후 완료된 결제에 다시 `processRefund`를 호출하면 외부 환불 전에 차단한다.
- `findByIdWithOrder`의 fetch join으로 재처리 경로의 Order 접근을 명확하게 처리했다.
- 테스트 fixture는 별도 트랜잭션에서 commit되고, 검증은 새로운 Repository 조회로 수행된다. 단순히 테스트 트랜잭션 안의 영속성 컨텍스트만 검사하지 않는다.
- H2 trigger 기반 장애 주입 장치와 gateway의 시도/성공 기록이 잘 분리되어 있다.
- OSIV가 꺼져 있고 SQL/bind logging이 준비되어 있어 트랜잭션과 SQL을 관찰하기 좋다.

## Critical — 반드시 수정

### 1. 외부 환불 성공 후 완료 SQL이 실패하면 DB가 `PAID`로 rollback된다

위치: `src/main/java/study/refund/service/CancelOrderService.java:31-66`, `75-96`

`cancel()`은 하나의 `@Transactional` 안에서 다음 작업을 모두 수행한다.

```text
CANCELING / REFUND_PENDING 변경
→ flush
→ 외부 gateway 환불
→ CANCELED / REFUNDED 변경
→ 메서드 종료 후 transaction commit
```

`em.flush()`는 commit이 아니다. 54~57행에서 DB에 UPDATE가 실행돼도 같은 트랜잭션이 rollback되면 원래 `PAID / PAID`로 돌아간다.

또한 61~62행의 완료 상태는 try 블록 안에서 변경되지만, 실제 UPDATE와 commit은 `cancel()`이 반환된 뒤 Spring transaction interceptor에서 실행될 수 있다. 그 시점의 DB 예외는 63행의 catch가 잡지 못한다.

따라서 다음 결과가 가능하다.

```text
외부 결제사: 환불 성공
우리 DB: PAID / PAID
다음 취소 요청: 외부 환불 다시 호출
```

`processRefund()`의 92~96행도 동일한 문제가 있다.

삭제된 `externalSuccessThenDatabaseFailureDoesNotEraseRecoveryEvidence`가 이 결함을 실제 SQL 실패로 재현하던 테스트였다. 외부 호출 전 준비 상태가 독립적으로 확정되도록 경계를 설계하고, 외부 성공 후 완료 기록 실패가 준비 상태를 지우지 않는지 다시 검증해야 한다.

### 2. 결과를 알 수 없는 `REFUND_PENDING`도 외부 환불을 다시 호출한다

위치: `src/main/java/study/refund/service/CancelOrderService.java:81-90`

현재 재처리 차단 조건은 Payment=`REFUNDED` 또는 Order=`CANCELED`뿐이다. Payment가 `PAID`, `REFUND_PENDING`, `REFUND_FAILED`인 경우 모두 88~90행에서 gateway를 호출한다.

`REFUND_PENDING`은 외부 환불이 성공했지만 응답이나 로컬 완료 기록을 잃은 상태일 수 있다. 이 상태를 `REFUND_FAILED`와 동일하게 재호출하면 중복 환불 가능성이 생긴다.

상태 의미를 최소한 다음처럼 분리해야 한다.

```text
REFUND_FAILED  = 외부 효과가 없다고 확인됨, 재호출 가능
REFUND_PENDING = 결과 불명 또는 확인 필요, 자동 재호출 금지
```

삭제된 `pendingPaymentIsNotBlindlyRetried`가 이 경로를 검증했다. `processRefund()`가 안전한 실패만 처리하도록 조건을 좁히고, PENDING에서는 외부 호출이 0회인지 다시 테스트해야 한다.

### 3. `CANCELING` 주문을 환불 완료로 응답한다

위치: `src/main/java/study/refund/service/CancelOrderService.java:43-49`

`CANCELED`와 `CANCELING`을 같은 분기로 묶어 둘 다 `refundStatus=COMPLETED`를 반환한다. `CANCELING / REFUND_PENDING`은 환불 완료가 확인되지 않은 상태이므로 이 응답은 사실과 다르다.

처리 중 중복 요청의 API 정책은 둘 다 가능하다.

- 예외로 거절
- `CANCELING / PENDING` 의미의 미완료 응답 반환

어느 정책을 선택해도 `COMPLETED` 응답과 gateway 재호출은 허용하면 안 된다. 삭제된 `cancelingOrderRejectsNewCancelWithoutCallingGateway`는 예외 정책만 강제한 점은 과했지만, 현재의 잘못된 완료 응답을 발견하는 검증 자체는 필요하다.

또한 Order=`CANCELED`만 확인하고 Payment 상태를 확인하지 않으므로 `CANCELED / REFUND_PENDING`처럼 불일치한 데이터도 완료로 응답한다.

### 4. 동시 취소 요청에서 외부 환불이 두 번 실행될 수 있다

위치: `src/main/java/study/refund/service/CancelOrderService.java:37-59`

두 요청이 동시에 시작하면 둘 다 Order=`PAID`를 조회하고 eligibility 검사를 통과할 수 있다. 현재 엔티티에 `@Version`이 없고 조회 lock, 원자적 claim, 외부 중복 방지 장치도 없다. `paymentKey`의 DB unique 제약은 외부 API 호출 횟수를 막지 못한다.

순차 중복 테스트의 성공은 동시 요청 안전성을 증명하지 않는다. 두 요청을 실제로 겹쳐 실행해 다음을 검증하는 테스트와 그에 맞는 설계가 필요하다.

```text
gateway 성공 호출 수 = 1
최종 Order           = CANCELED
최종 Payment         = REFUNDED
```

외부 gateway가 멱등성을 보장하지 않는 현재 계약에서는 DB 쪽 선점만으로 모든 crash gap을 없앨 수 없다는 한계도 함께 기록해야 한다.

## Important — 수정 권장

### 5. 최대 5초 걸리는 외부 호출 동안 DB transaction과 connection을 보유한다

위치: `src/main/java/study/refund/service/CancelOrderService.java:31`, `57-59`, `75`, `89-90`  
관련 설정: `src/main/resources/application.yml:7`

조회와 flush 후 같은 transaction 안에서 gateway를 호출한다. 따라서 외부 응답을 기다리는 동안 DB connection과 변경 row의 lock을 보유할 수 있다. pool 크기가 5이므로 느린 환불 요청 5개만 겹쳐도 다른 DB 작업이 connection을 기다릴 수 있다.

현재 구조는 긴 transaction 비용을 지불하면서도 외부 환불과 DB commit의 원자성을 얻지 못한다. 준비 상태 기록, 외부 호출, 완료 기록의 경계를 분리하고 각 단계에서 무엇이 durable한지 설명할 수 있어야 한다.

### 6. 모든 gateway 예외를 `REFUND_FAILED`로 분류한다

위치: `src/main/java/study/refund/service/CancelOrderService.java:63-65`, `94-96`

`catch (Exception)`은 확실한 거절, timeout, connection reset, 영속성 오류, 프로그래밍 오류를 모두 같은 실패로 처리한다.

timeout은 결제사가 환불한 뒤 응답만 유실된 것일 수 있다. 이를 `REFUND_FAILED`로 기록하면 재처리기가 안전한 실패로 오해하고 환불을 다시 호출한다. 현재 `PaymentGateway` 계약에는 결과를 구분할 반환값이나 명시적인 예외 분류가 없어 서비스가 이 차이를 판단할 근거도 부족하다.

적어도 구현에서 보장할 수 있는 실패와 결과 불명을 분리하고, 판단할 수 없는 경우 자동 재호출하지 않는 방향이 필요하다. gateway 계약을 바꾸지 않는 제약 아래에서는 실제 adapter가 던지는 예외 의미를 명확히 문서화하거나 결과 확인을 수동 절차로 남겨야 한다.

### 7. 실제 서비스의 환불 실패 HTTP 계약과 controller 테스트가 다르다

위치: `src/main/java/study/refund/service/CancelOrderService.java:63-65`  
`src/main/java/study/refund/exception/ApiExceptionHandler.java:16-18`

기본 gateway는 `RefundProcessingException`을 던지고 advice는 이를 HTTP 503으로 변환하도록 되어 있다. 그러나 실제 `cancel()`은 이 예외를 잡아 HTTP 200의 FAILED DTO로 바꾼다. 503 controller 테스트는 서비스를 Mock으로 만들어 직접 예외를 던지므로 실제 서비스 경로에서는 발생하지 않는다.

200 + FAILED 정책과 503 정책 중 하나를 선택하고 서비스, advice, README, 통합 테스트를 일치시켜야 한다.

### 8. `processRefund()` 실패가 호출자에게 전달되지 않는다

위치: `src/main/java/study/refund/service/CancelOrderService.java:87-96`

재처리 gateway가 실패해도 예외를 삼키고 `void`로 정상 종료한다. DB의 `REFUND_FAILED` 상태는 남지만 호출자나 scheduler는 이번 시도가 실패했는지 즉시 알 수 없다. 운영 재처리 작업의 성공/실패 집계, backoff, 알림 정책을 설계하기 어렵다.

상태 저장과 호출자 결과 전달을 분리해서 결정하고, 적어도 로그·metric 또는 명시적인 예외 정책을 마련하는 편이 좋다.

## Minor — 문서와 유지보수성

### 9. README와 검증 기록이 현재 테스트 스냅샷과 다르다

위치: `README.md`, `VERIFICATION.md`

README는 테스트 수정·삭제 금지라고 안내하면서 삭제된 세 테스트를 여전히 목록과 완료 기준에 포함한다. `VERIFICATION.md`는 서비스가 TODO이고 총 18개 테스트라고 기록되어 있다. 현재 실제 상태는 서비스가 구현되어 있고 16개 테스트가 통과한다.

삭제한 테스트와 삭제 이유를 명시하거나, 정책 중립적인 형태로 복구한 뒤 문서와 숫자를 최신화해야 한다. 특히 안전성 테스트를 삭제해 초록색이 된 사실이 가려지면 테스트 결과의 의미를 잘못 해석하기 쉽다.

### 10. 비즈니스 상태 전이 규칙이 서비스의 임의 변경에 의존한다

위치: `src/main/java/study/refund/domain/Order.java:30-33`, `Payment.java:43-46`

`changeStatus()`가 모든 전이를 허용한다. 현재는 서비스가 상태 규칙을 전부 기억해야 하며, 새로운 진입점이 추가되면 `REFUNDED → REFUND_PENDING` 같은 역전이를 만들기 쉽다.

상태 전이 메서드를 목적 중심으로 표현하거나 최소한 허용 전이를 검증하면 도메인 불변식을 한곳에서 유지할 수 있다. 다만 transaction/외부 원자성 문제를 먼저 해결한 뒤 진행하는 것이 좋다.

## 삭제된 테스트가 남긴 공백

현재 acceptance test 7개는 정상 환불, 순차 중복, 이미 완료된 취소, 없는 주문, 확실한 gateway 거절, 실패 재처리, gateway 복구를 검증한다.

삭제된 다음 세 시나리오는 현재 구현의 실제 결함을 발견하고 있었다.

| 삭제된 시나리오 | 현재 구현에서 드러나는 문제 |
|---|---|
| CANCELING 중복 cancel | `CANCELING / COMPLETED`라는 모순된 응답 |
| gateway 성공 후 완료 DB 저장 실패 | 외부 환불 성공 후 DB가 `PAID / PAID`로 rollback |
| REFUND_PENDING 재처리 | 결과 불명 환불을 gateway에 다시 호출 |

기존 assertion이 구현 정책을 과하게 고정했다면 테스트를 삭제하기보다 다음 observable outcome만 남기는 편이 안전하다.

- CANCELING 요청: 예외 또는 미완료 응답, gateway 0회, 기존 상태 유지
- 외부 성공 후 완료 저장 실패: gateway 성공 1회, `REFUND_PENDING` 유지, `PAID`/`REFUND_FAILED` 금지
- PENDING 재처리: 정상 반환 또는 예외 보류, gateway 0회, 상태 유지

## 권장 수정 순서

1. `REFUND_FAILED`와 `REFUND_PENDING`의 의미와 자동 재처리 조건을 먼저 확정한다.
2. 정책 중립적인 형태로 삭제된 세 테스트를 복구해 현재 결함을 다시 재현한다.
3. 외부 호출 전 준비 상태와 외부 호출 후 완료 상태가 어떤 transaction에서 durable해지는지 설계한다.
4. `processRefund()`가 `REFUND_FAILED`만 자동 처리하도록 제한한다.
5. 동시 취소 테스트를 추가하고 이중 외부 호출을 방지한다.
6. gateway 예외 의미와 HTTP 실패 계약을 일치시킨다.
7. 느린 gateway 환경에서 connection pool과 lock 보유 시간을 관찰한다.
8. 전체 테스트와 장애 주입 테스트를 다시 실행하고 README/VERIFICATION을 최신화한다.

## 면접·회고 질문

- `flush()`와 `commit()`은 무엇이 다르며, 현재 `em.flush()`가 외부 호출 전 상태를 보존하지 못하는 이유는 무엇인가?
- 외부 환불 성공 후 DB commit이 실패하면 다음 요청은 어떤 정보로 중복 환불을 피할 수 있는가?
- timeout과 확실한 거절을 현재 `PaymentGateway` 계약만으로 구분할 수 있는가?
- 외부 호출을 transaction 밖으로 옮기면 새로 생기는 crash gap은 무엇인가?
- 두 요청이 동시에 `PAID`를 읽었을 때 gateway 호출을 한 번으로 제한하려면 어떤 보장이 필요한가?
- `CANCELING / REFUND_PENDING` 요청에 HTTP 200을 반환한다면 응답 DTO는 어떤 의미여야 하는가?

## 최종 평가

정상 경로와 확실한 실패 재처리 흐름은 읽기 쉽고 테스트도 실제 DB 상태와 gateway 호출을 확인한다. 하지만 현재 초록색 테스트는 삭제된 안전성 시나리오를 포함하지 않는다. 환불처럼 외부 금전 효과가 있는 기능에서는 happy path보다 결과 불명, commit 실패, 동시 요청에서의 중복 방지가 더 중요하다.

Critical 4건을 해결하고 삭제된 안전성 검증을 정책 중립적으로 복구한 뒤 다시 리뷰하는 것을 권장한다.
