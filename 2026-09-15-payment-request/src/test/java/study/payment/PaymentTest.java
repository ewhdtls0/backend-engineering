package study.payment;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("결제 승인 서비스 통합 테스트")
class PaymentTest {
    @Autowired PaymentService service;
    @Autowired OrderRepository orders;
    @Autowired PaymentRepository payments;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired FakeGateway gateway;
    Long orderId;
    String key;
    static final PaymentRequest REQUEST = new PaymentRequest(35000);
    static final Instant APPROVED_AT = Instant.parse("2026-09-15T00:00:00Z");
    @TestConfiguration static class Config {
        @Bean @Primary FakeGateway fakeGateway() { return new FakeGateway(); }
    }
    static class FakeGateway implements PaymentGateway {
        record Call(Long orderId, long amount) {}
        final List<Call> calls = new CopyOnWriteArrayList<>();
        final List<String> approvals = new CopyOnWriteArrayList<>();
        final AtomicBoolean rejectNext = new AtomicBoolean();
        volatile long delayMillis;
        public PaymentApproval approve(Long orderId, long amount) {
            calls.add(new Call(orderId, amount));
            if (rejectNext.getAndSet(false)) throw new GatewayException("definite rejection before approval");
            try { if (delayMillis > 0) Thread.sleep(delayMillis); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            // Fake에는 멱등 처리/락이 없습니다. 호출마다 독립된 외부 승인이 발생합니다.
            String reference = UUID.randomUUID().toString();
            approvals.add(reference);
            return new PaymentApproval(reference, APPROVED_AT);
        }
    }
    @BeforeEach void fixture() {
        gateway.calls.clear(); gateway.approvals.clear(); gateway.rejectNext.set(false); gateway.delayMillis = 0;
        key = UUID.randomUUID().toString();
        // 매번 새 주문/키. 학습자가 추가한 멱등 테이블의 삭제 순서에 테스트가 의존하지 않습니다.
        orderId = new TransactionTemplate(manager).execute(status -> orders.saveAndFlush(new Order(35000)).getId());
        assertThat(jdbc.queryForObject("select status from purchase_orders where id=?", String.class, orderId)).isEqualTo("PENDING");
    }
    List<Map<String,Object>> rows() { return jdbc.queryForList("select * from payments where order_id=? order by id", orderId); }
    void error(Runnable action, MissionException.Code code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(MissionException.class,
            e -> assertThat(e.getCode()).isEqualTo(code));
    }
    void approvedOnce(PaymentResponse result) {
        assertThat(result.paymentId()).isNotNull();
        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.amount()).isEqualTo(35000);
        assertThat(result.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(rows()).hasSize(1);
        var stored = payments.findByOrderId(orderId).orElseThrow();
        assertThat(stored.getId()).isEqualTo(result.paymentId());
        assertThat(stored.getAmount()).isEqualTo(35000);
        assertThat(stored.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(stored.getApprovedAt()).isEqualTo(APPROVED_AT);
        assertThat(gateway.approvals).containsExactly(stored.getApprovalReference());
        assertThat(jdbc.queryForObject("select status from purchase_orders where id=?", String.class, orderId)).isEqualTo("PAID");
        assertThat(gateway.calls).allSatisfy(c -> {
            assertThat(c.orderId()).isEqualTo(orderId); assertThat(c.amount()).isEqualTo(35000);
        });
    }
    @Test
    @DisplayName("준비된 주문은 커밋된 PENDING 상태이며 결제가 없다")
    void fixtureHasCommittedPendingOrder() {
        assertThat(orders.findById(orderId).orElseThrow().getTotalAmount()).isEqualTo(35000);
        assertThat(rows()).isEmpty(); assertThat(gateway.calls).isEmpty();
    }
    @Test
    @DisplayName("최초 결제는 한 번 승인하고 Payment를 저장한 뒤 주문을 PAID로 바꾼다")
    void normalPayment() {
        approvedOnce(service.pay(orderId,key,REQUEST));
        assertThat(gateway.calls).hasSize(1);
    }
    @Test
    @DisplayName("같은 키와 같은 요청을 세 번 보내면 최초 승인 결과를 재사용한다")
    void sameKeyThreeTimesReturnsSameBusinessResult() {
        var first = service.pay(orderId,key,REQUEST);
        var before = rows();
        assertThat(service.pay(orderId,key,REQUEST)).isEqualTo(first);
        assertThat(service.pay(orderId,key,REQUEST)).isEqualTo(first);
        approvedOnce(first);
        assertThat(rows()).isEqualTo(before); assertThat(gateway.calls).hasSize(1);
    }
    @Test
    @DisplayName("같은 키에 다른 금액을 보내면 기존 결제를 보존하고 CONFLICT를 반환한다")
    void sameKeyDifferentAmountIsConflictAndPreservesApproval() {
        var first = service.pay(orderId,key,REQUEST); var before = rows();
        error(() -> service.pay(orderId,key,new PaymentRequest(40000)), MissionException.Code.CONFLICT);
        approvedOnce(first); assertThat(rows()).isEqualTo(before); assertThat(gateway.calls).hasSize(1);
    }
    @Test
    @DisplayName("이미 결제된 주문에 다른 키를 보내면 추가 승인을 하지 않고 CONFLICT를 반환한다")
    void differentKeyOnPaidOrderIsConflict() {
        var first = service.pay(orderId,key,REQUEST); var before = rows();
        error(() -> service.pay(orderId,"other-"+key,REQUEST), MissionException.Code.CONFLICT);
        approvedOnce(first); assertThat(rows()).isEqualTo(before); assertThat(gateway.calls).hasSize(1);
    }
    @Test
    @DisplayName("같은 키를 다른 주문에 사용하면 다른 주문을 변경하지 않고 CONFLICT를 반환한다")
    void sameKeyDifferentOrderIsConflict() {
        var first = service.pay(orderId,key,REQUEST);
        Long other = new TransactionTemplate(manager).execute(s -> orders.saveAndFlush(new Order(35000)).getId());
        error(() -> service.pay(other,key,REQUEST), MissionException.Code.CONFLICT);
        approvedOnce(first); assertThat(gateway.calls).hasSize(1);
        assertThat(payments.findByOrderId(other)).isEmpty();
        assertThat(orders.findById(other).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING);
    }
    @Test
    @DisplayName("동일 요청 10개가 동시에 와도 Payment와 Gateway 승인은 각각 한 번만 발생한다")
    void tenConcurrentRequestsProduceOnePaymentAndOneGatewayApproval() throws Exception {
        gateway.delayMillis = 150;
        var pool = Executors.newFixedThreadPool(10);
        var ready = new CountDownLatch(10); var start = new CountDownLatch(1);
        List<Future<PaymentResponse>> futures = new ArrayList<>();
        List<PaymentResponse> results = new ArrayList<>();
        List<Throwable> errors = new ArrayList<>();
        try {
            for (int i=0;i<10;i++) futures.add(pool.submit(() -> {
                ready.countDown();
                if (!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("start gate timeout");
                return service.pay(orderId,key,REQUEST);
            }));
            assertThat(ready.await(10,TimeUnit.SECONDS)).as("all workers ready").isTrue();
            start.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            // 모든 worker 결과와 예외를 회수해 호출 실패를 숨기지 않습니다.
            for (var f : futures) {
                try { results.add(f.get(Math.max(1,deadline-System.nanoTime()),TimeUnit.NANOSECONDS)); }
                catch (ExecutionException e) { errors.add(e.getCause()); }
                catch (TimeoutException e) { errors.add(e); }
            }
            assertThat(errors).as("all ten callers must succeed: %s",errors).isEmpty();
            assertThat(results).hasSize(10);
            assertThat(results).extracting(PaymentResponse::paymentId).doesNotContainNull().containsOnly(results.getFirst().paymentId());
            assertThat(results).allSatisfy(r -> assertThat(r).isEqualTo(results.getFirst()));
            approvedOnce(results.getFirst()); assertThat(gateway.calls).hasSize(1);
        } finally {
            start.countDown(); futures.forEach(f -> f.cancel(true)); pool.shutdownNow();
            assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).as("workers terminated").isTrue();
        }
    }
    @Test
    @DisplayName("승인 전 Gateway 실패는 결제를 남기지 않고 같은 키 재시도를 허용한다")
    void definiteGatewayFailureAllowsRetryWithSameKey() {
        gateway.rejectNext.set(true);
        error(() -> service.pay(orderId,key,REQUEST), MissionException.Code.GATEWAY_FAILED);
        assertThat(rows()).isEmpty(); assertThat(gateway.calls).hasSize(1); assertThat(gateway.approvals).isEmpty();
        assertThat(orders.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING);
        var result = service.pay(orderId,key,REQUEST);
        approvedOnce(result); assertThat(gateway.calls).hasSize(2);
        assertThat(service.pay(orderId,key,REQUEST)).isEqualTo(result);
        assertThat(gateway.calls).hasSize(2);
    }
    @Test
    @DisplayName("없는 주문은 Gateway 호출 전에 NOT_FOUND로 거부한다")
    void nonexistentOrderDoesNotCallGateway() {
        error(() -> service.pay(Long.MAX_VALUE,key,REQUEST), MissionException.Code.NOT_FOUND);
        assertThat(gateway.calls).isEmpty(); assertThat(rows()).isEmpty();
    }
    @ParameterizedTest
    @ValueSource(longs = {0,-1,40000})
    @DisplayName("0·음수·주문 금액과 다른 금액은 Gateway 호출 전에 거부한다")
    void invalidAmountDoesNotCallGateway(long amount) {
        error(() -> service.pay(orderId,key,new PaymentRequest(amount)), MissionException.Code.INVALID_REQUEST);
        assertThat(gateway.calls).isEmpty(); assertThat(rows()).isEmpty();
        assertThat(orders.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING);
    }
    @Test
    @DisplayName("공백 멱등 키는 서비스에서 INVALID_REQUEST로 거부한다")
    void blankKeyRejectedByService() {
        error(() -> service.pay(orderId," ",REQUEST), MissionException.Code.INVALID_REQUEST);
        assertThat(gateway.calls).isEmpty(); assertThat(rows()).isEmpty();
    }
    @Test
    @DisplayName("HTTP 최초 요청과 같은 키 재요청은 모두 200과 같은 승인 결과를 반환한다")
    void httpApprovalAndReplay() throws Exception {
        String body = mvc.perform(post("/api/orders/{id}/payments",orderId).header("Idempotency-Key",key)
            .contentType("application/json").content("{\"amount\":35000}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.paymentId").isNumber())
            .andExpect(jsonPath("$.orderId").value(orderId)).andExpect(jsonPath("$.amount").value(35000))
            .andExpect(jsonPath("$.status").value("APPROVED")).andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/orders/{id}/payments",orderId).header("Idempotency-Key",key)
            .contentType("application/json").content("{\"amount\":35000}"))
            .andExpect(status().isOk()).andExpect(content().json(body));
        assertThat(rows()).hasSize(1); assertThat(gateway.calls).hasSize(1);
    }
    @Test
    @DisplayName("HTTP에서 같은 키와 다른 금액을 보내면 409를 반환한다")
    void httpChangedAmountReturns409() throws Exception {
        service.pay(orderId,key,REQUEST); var before = rows();
        mvc.perform(post("/api/orders/{id}/payments",orderId).header("Idempotency-Key",key)
            .contentType("application/json").content("{\"amount\":40000}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"));
        assertThat(rows()).isEqualTo(before); assertThat(gateway.calls).hasSize(1);
    }
    @Test
    @DisplayName("HTTP 요청에 멱등 키가 없으면 400을 반환한다")
    void httpRequiresKey() throws Exception {
        mvc.perform(post("/api/orders/{id}/payments",orderId).contentType("application/json").content("{\"amount\":35000}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(rows()).isEmpty(); assertThat(gateway.calls).isEmpty();
    }
    @Test
    @DisplayName("HTTP 요청의 공백 멱등 키는 400을 반환한다")
    void httpRejectsBlankKey() throws Exception {
        mvc.perform(post("/api/orders/{id}/payments",orderId).header("Idempotency-Key"," ")
            .contentType("application/json").content("{\"amount\":35000}"))
            .andExpect(status().isBadRequest());
        assertThat(rows()).isEmpty(); assertThat(gateway.calls).isEmpty();
    }
    @ParameterizedTest
    @ValueSource(strings = {"{}","{\"amount\":0}","{\"amount\":-1}"})
    @DisplayName("HTTP 요청의 누락·0·음수 금액은 400을 반환한다")
    void httpRejectsInvalidAmount(String body) throws Exception {
        mvc.perform(post("/api/orders/{id}/payments",orderId).header("Idempotency-Key",key)
            .contentType("application/json").content(body)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(rows()).isEmpty(); assertThat(gateway.calls).isEmpty();
    }
}
