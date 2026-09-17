package study.refund.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import study.refund.domain.*;
import study.refund.repository.*;
import study.refund.service.CancelOrderService;

@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationSupport.GatewayTestConfiguration.class)
public abstract class IntegrationSupport {
    @Autowired protected OrderRepository orders;
    @Autowired protected PaymentRepository payments;
    @Autowired protected CancelOrderService service;
    @Autowired protected FakePaymentGateway gateway;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected PlatformTransactionManager transactionManager;

    @TestConfiguration(proxyBeanMethods = false)
    public static class GatewayTestConfiguration {
        @Bean @Primary
        FakePaymentGateway testPaymentGateway() { return new FakePaymentGateway(); }
    }

    @BeforeEach
    void isolate() {
        CompletionWriteFailure.reset();
        gateway.reset();
        jdbc.execute("DROP TRIGGER IF EXISTS fail_payment_completion");
        jdbc.execute("DROP TRIGGER IF EXISTS fail_order_completion");
        // FK 순서로 삭제한다. 테스트 전체를 트랜잭션으로 감싸지 않는다.
        payments.deleteAllInBatch();
        orders.deleteAllInBatch();
        jdbc.execute("CREATE TRIGGER fail_payment_completion BEFORE UPDATE ON payments FOR EACH ROW CALL 'study.refund.support.CompletionWriteFailure'");
        jdbc.execute("CREATE TRIGGER fail_order_completion BEFORE UPDATE ON orders FOR EACH ROW CALL 'study.refund.support.CompletionWriteFailure'");
    }

    @AfterEach
    void resetFaults() {
        CompletionWriteFailure.reset();
        gateway.reset();
    }

    protected Fixture fixture(OrderStatus orderStatus, PaymentStatus paymentStatus) {
        return new TransactionTemplate(transactionManager).execute(tx -> {
            Order order = orders.saveAndFlush(new Order(7L, 25_000L, orderStatus));
            Payment payment = payments.saveAndFlush(new Payment(order, "pay-" + order.getId(), 25_000L, paymentStatus));
            return new Fixture(order.getId(), payment.getId(), payment.getPaymentKey(), payment.getAmount());
        });
    }

    protected OrderStatus orderStatus(Fixture f) {
        return orders.findById(f.orderId()).orElseThrow().getStatus();
    }
    protected PaymentStatus paymentStatus(Fixture f) {
        return payments.findById(f.paymentId()).orElseThrow().getStatus();
    }
    protected FakePaymentGateway.Call expectedCall(Fixture f) {
        return new FakePaymentGateway.Call(f.paymentKey(), f.amount());
    }
    protected record Fixture(Long orderId, Long paymentId, String paymentKey, long amount) {}
}
