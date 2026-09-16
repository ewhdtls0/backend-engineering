# 2026-09-15 (화) — 중복 요청에도 한 번만 처리되는 결제 승인

Java 21 / Spring Boot 3.5.16 / Gradle 8.14.3 / Web, JPA, Validation, H2.
목표 시간 30~60분. 핵심은 **재시도와 동시 요청에서도 비즈니스 결제가 중복되지 않도록 설계하기**입니다.

## 상황

서버가 결제를 승인했지만 응답 전송 중 네트워크가 끊겼습니다. 클라이언트는 실패로 판단해 같은 요청을 다시 보냅니다.
매번 새 결제를 만들면 중복 청구가 발생합니다. 같은 멱등 키와 같은 요청에는 최초 승인 결과를 재사용해야 합니다.

## 실행

이 폴더를 IntelliJ 독립 Gradle 프로젝트로 열고 Java 21을 Project SDK와 Gradle JVM으로 지정하세요.
Gradle wrapper가 포함되어 있으며 최초 실행 시 인터넷으로 의존성을 다운로드합니다.

```powershell
.\gradlew.bat testClasses
.\gradlew.bat test
.\gradlew.bat bootRun
```

```sh
./gradlew testClasses
./gradlew test
./gradlew bootRun
```

Windows에서 가져온 파일에 실행 권한이 없다면 `chmod +x gradlew`를 실행하세요. `sh gradlew test`도 가능합니다.
포트 8080, H2 콘솔 `http://localhost:8080/h2-console`.
JDBC URL `jdbc:h2:mem:2026-09-15-payment-request`, 사용자 `sa`, 비밀번호 없음.
SQL 및 바인딩 값 출력이 켜져 있습니다. 메모리 DB는 재시작하면 초기화됩니다.
기본 Gateway는 실제 결제 없이 새 승인 번호를 반환하는 데모입니다.

직접 API 실습할 때 H2 콘솔에서 주문을 만들고 생성된 ID를 조회하세요.

```sql
insert into purchase_orders(total_amount, status) values (35000, 'PENDING');
select * from purchase_orders;
```

## API

`POST /api/orders/{orderId}/payments`

```http
Idempotency-Key: payment-request-001
Content-Type: application/json
```

```json
{"amount":35000}
```

최초 성공 및 동일 요청 재시도 모두 HTTP 200:

```json
{"paymentId":10,"orderId":100,"amount":35000,"status":"APPROVED"}
```

## 도메인 조건

| 도메인 | 제공 필드 |
|---|---|
| Order | id, totalAmount, status(PENDING/PAID) |
| Payment | id, orderId, amount, status(APPROVED), approvedAt, approvalReference |
| PaymentGateway | approve(Long orderId, long amount) → PaymentApproval |

금액은 원 단위 양의 long입니다. 신규 요청 금액은 주문 totalAmount와 같아야 합니다.
실패한 외부 승인 시도는 비즈니스 Payment 행을 남기지 않습니다. 별도 시도 이력 저장은 자유입니다.
Gateway가 반환한 reference/approvedAt을 Payment에 보존하세요.
멱등 키 저장용 필드/테이블, DB 제약, 락, 상태 모델 확장은 제공하지 않으므로 직접 결정하세요.

## 기능·비기능 요구사항과 테스트 계약

1. 최초 성공은 Payment 1건 저장, Order를 PAID로 변경, 승인 결과 반환입니다.
2. 같은 key + 같은 orderId + 같은 amount를 순차 3회 요청해도 동일 paymentId와 비즈니스 결과를 반환합니다. Gateway 호출은 1회입니다.
3. 같은 key + 다른 amount 또는 다른 orderId는 `CONFLICT`. 이미 저장된 결제와 주문 상태를 변경하거나 Gateway를 다시 호출하지 않습니다.
4. 키의 범위는 이 실습 애플리케이션 전체입니다. 주문마다 따로 구분하는 키가 아닙니다.
5. **주문 정책:** 주문당 승인 결제는 1건입니다. 이미 승인된 주문에 다른 키가 오면 CONFLICT. 취소/분할 결제는 범위 밖입니다.
6. 같은 키의 10개 동시 요청은 모두 최종 성공 결과를 받아야 합니다. 동일 paymentId, DB Payment 1건, Gateway approve 1회입니다.
7. 동시 요청 중 처리 중임을 나타내는 별도 최종 오류를 반환하는 정책은 이 기본 테스트와 다릅니다. 기본 계약은 제한 시간 내 최초 결과 공유입니다.
8. `synchronized`, 인스턴스 내부 Map/락만으로 중복을 막는 구현에 의존하지 않습니다. 다중 애플리케이션 인스턴스가 같은 DB를 사용한다고 가정하세요.
9. 없는 주문, 빈 키, 잘못된 금액은 외부 승인 전에 거부합니다. 같은 키의 다른 요청은 신규 주문 금액 검증 오류 대신 CONFLICT가 우선합니다.

### 명확한 Gateway 실패 후 재시도 정책

Fake의 첫 실패는 **외부 승인 전에 확실하게 거절된 경우**입니다. `GatewayException`을 GATEWAY_FAILED로 변환하고
Payment 0건 / Order PENDING을 유지하세요. 같은 키 재시도는 허용하며 성공 후 추가 재시도는 결과를 재사용합니다.
이 시나리오 전체의 Gateway 호출은 2회(실패 1 + 성공 1), 실제 외부 승인 결과는 1개입니다.
실패 상태를 어떤 모델로 저장/해제할지는 자유입니다. 정책을 바꾸고 싶다면 이유와 새 요구사항을 명시하고 해당 계약 테스트도 함께 조정하세요.

### 오류 계약

| MissionException.Code | HTTP | 예시 |
|---|---|---|
| INVALID_REQUEST | 400 | key 누락/공백, amount≤0, 신규 결제 금액 불일치 |
| NOT_FOUND | 404 | 주문 없음 |
| CONFLICT | 409 | 같은 키의 다른 요청, 결제된 주문의 새 키 |
| GATEWAY_FAILED | 502 | 승인 전 명확한 외부 실패 |

서비스 직접 호출도 위 예외 계약을 지킵니다. Controller 검증만으로 서비스 검증을 대체하지 않습니다.
메시지 문구는 자유이며 JSON 오류는 code/message를 포함합니다.

## 핵심 TODO

`PaymentService.pay(Long orderId, String idempotencyKey, PaymentRequest request)`만 의도적으로 미구현입니다.
필요한 엔티티/Repository 메서드/DB 제약/트랜잭션 경계를 직접 추가하세요.
DTO, 컨트롤러, 도메인 기본 필드, 오류 처리, Gateway 인터페이스와 데모, 테스트용 Fake는 제공됩니다.

## 테스트 시나리오

`PaymentTest`는 실제 Spring 서비스와 H2 DB를 사용합니다. 각 테스트는 새 주문·새 키를 커밋하며
테스트 자체에 `@Transactional`을 붙이지 않습니다. 서비스 호출 후 JDBC로 커밋 결과를 확인합니다.
추가한 멱등 테이블의 삭제 순서에 종속되지 않도록 이전 테스트 주문을 삭제하지 않으며 모든 DB 검증은 해당 주문으로 범위를 제한합니다.
Fake Gateway는 동시 호출에 안전한 호출 기록만 제공하고 **멱등 처리 자체는 하지 않습니다**.

| 시나리오 | 검증 |
|---|---|
| fixture | 주문 커밋, 초기 PENDING, 결제 없음 |
| 정상 결제 | 응답과 DB ID, 금액, 상태, 승인 시각/번호, Gateway 인자와 1회 호출 |
| 순차 3회 | 응답 동일, 전체 결제 행 스냅샷 유지, Gateway 1회 |
| 같은 키 다른 금액 | CONFLICT, 기존 결제 및 승인 유지 |
| 다른 키 동일 주문 | CONFLICT, 새 결제/추가 승인 없음 |
| 같은 키 다른 주문 | CONFLICT, 다른 주문 PENDING/결제 없음 |
| 동시 10 thread | 모든 응답 성공·동일 결과, DB 1건, 실제 승인 1건, Gateway 1회 |
| 첫 외부 실패 후 재시도 | 실패 상태 보존, 다음 성공, 세 번째는 재사용 |
| 입력/주문 검증 | 없는 주문, 0/음수/불일치 금액, blank key, 외부 호출 없음 |
| HTTP | 정상/재시도 200, 충돌 409, 키 누락 및 잘못된 금액 400 |

동시 테스트는 10개 worker의 준비를 기다린 뒤 한 번에 출발시킵니다. Gateway에 짧은 지연을 넣어 겹침을 유도하고,
Future 예외를 모두 수집해 하나라도 실패하면 테스트가 실패합니다. 응답을 임의로 필터링해서 성공 개수만 세지 않습니다.
테스트는 총 30초 결과 대기 제한과 worker 종료 절차를 갖습니다.

## 고민 포인트

- 조회 후 없으면 저장하는 방식에서 두 요청이 동시에 조회하면 무엇이 발생하나요?
- Payment 중복 저장만 막아도 외부 approve 1회를 보장할 수 있나요?
- 키를 언제 확보하고 언제 결과를 확정하나요? 다른 서버 인스턴스는 무엇을 관찰하나요?
- DB 예외 뒤 같은 트랜잭션에서 계속 조회/저장하면 가능한가요?
- 원래 키의 결과를 재사용하기 전에 다른 요청인지 어떤 데이터로 판단하나요?
- 외부 호출 중 DB 연결과 락을 오래 유지할 때 비용과 실패 모드는 무엇인가요?
- 외부 승인은 성공했는데 DB 커밋이 실패했다면 재시도는 안전한가요?

### 실서비스 한계와 선택 심화

이 Gateway 시그니처에는 외부 시스템용 멱등 키가 없습니다. 로컬 DB 롤백은 외부 승인을 되돌리지 못합니다.
테스트의 명확한 승인 전 실패와 달리 네트워크 타임아웃은 승인 여부가 불명확할 수 있습니다.
프로세스가 외부 승인 직후 죽는 경우까지 위 인터페이스만으로 보편적인 exactly-once를 증명할 수는 없습니다.
결과 조회/조정, 외부 멱등 키, 복구 정책 중 무엇이 필요한지 직접 검토하세요. 특정 해법을 스타터에 강제하지 않습니다.

H2 단일 앱 10-thread 테스트는 기본 경쟁 조건을 검증하지만 다중 프로세스 안전성을 증명하지 않습니다.
완성 후 실제 대상 DB와 앱 두 인스턴스를 사용한 테스트, 서로 다른 키로 같은 주문 동시 승인, 승인 후 DB 실패 주입을 확장해보세요.

## 완료 기준

전체 테스트 통과, 불필요한 Gateway 호출 없음, 멱등 모델과 DB 동시성 제어의 근거를 설명할 수 있어야 합니다.
프로세스 내부 락 없이 다중 인스턴스에서 어떻게 동작하는지와 외부 승인/DB 저장 사이의 실패 한계를 기록하세요.
`build/reports/tests/test/index.html`에서 테스트 결과를 확인합니다.
초기 `UnsupportedOperationException` 유래 실패는 정상이며 `@Disabled`나 빈 테스트로 통과시키지 않습니다.
