package study.refund;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.support.TransactionTemplate;
import study.refund.domain.*;
import study.refund.support.*;
import static org.assertj.core.api.Assertions.*;

/** TODO 서비스 없이 fixture/context/fake/SQL 장애장치 자체를 검증한다. */
@DisplayName("Starter 테스트 기반 검증")
class StarterInfrastructureTest extends IntegrationSupport {
    @Test
    @DisplayName("커밋된 fixture를 새로운 Repository 트랜잭션에서 조회할 수 있다")
    void committedFixtureCanBeReadFromFreshRepositoryTransactions() {
        assertThat(orders.count()).isZero();
        assertThat(payments.count()).isZero();
        Fixture f = fixture(OrderStatus.PAID, PaymentStatus.PAID);
        assertThat(f.orderId()).isPositive();
        assertThat(f.paymentId()).isPositive();
        assertThat(orderStatus(f)).isEqualTo(OrderStatus.PAID);
        assertThat(paymentStatus(f)).isEqualTo(PaymentStatus.PAID);
        assertThat(payments.findByOrderId(f.orderId()).orElseThrow().getAmount()).isEqualTo(25_000L);
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            assertThat(payments.findById(f.paymentId()).orElseThrow().getOrder().getMemberId()).isEqualTo(7L);
        });
    }

    @Test
    @DisplayName("Fake gateway는 시도와 성공을 구분해 기록하고 초기화할 수 있다")
    void fakeRecordsAttemptsAndSuccessesSeparatelyAndCanReset() {
        gateway.rejectRequests(true);
        assertThatThrownBy(() -> gateway.refund("key", 100)).isInstanceOf(study.refund.service.RefundRejectedException.class);
        assertThat(gateway.attempts()).containsExactly(new FakePaymentGateway.Call("key", 100));
        assertThat(gateway.successes()).isEmpty();
        gateway.rejectRequests(false);
        gateway.refund("key", 100);
        assertThat(gateway.attempts()).hasSize(2);
        assertThat(gateway.successes()).containsExactly(new FakePaymentGateway.Call("key", 100));
        gateway.reset();
        assertThat(gateway.attempts()).isEmpty();
        assertThat(gateway.successes()).isEmpty();
    }

    @Test
    @DisplayName("완료 상태 저장 장애를 JPA flush에서 발생시키고 트랜잭션을 rollback한다")
    void injectedFailureOccursOnJpaFlushAndRollsBackCompletion() {
        Fixture f = fixture(OrderStatus.CANCELING, PaymentStatus.REFUND_PENDING);
        gateway.afterSuccess(CompletionWriteFailure::arm);
        gateway.refund(f.paymentKey(), f.amount());
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            Payment payment = payments.findById(f.paymentId()).orElseThrow();
            payment.changeStatus(PaymentStatus.REFUNDED);
            payments.flush();
        })).isInstanceOf(DataAccessException.class);
        assertThat(CompletionWriteFailure.hits()).isEqualTo(1);
        assertThat(paymentStatus(f)).isEqualTo(PaymentStatus.REFUND_PENDING);
        assertThat(gateway.successes()).containsExactly(expectedCall(f));
        CompletionWriteFailure.reset();
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            payments.findById(f.paymentId()).orElseThrow().changeStatus(PaymentStatus.REFUNDED);
        });
        assertThat(paymentStatus(f)).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    @DisplayName("완료 상태 저장 장애는 직접 실행한 Order UPDATE SQL에도 적용된다")
    void injectedFailureAlsoWorksForDirectOrderSql() {
        Fixture f = fixture(OrderStatus.CANCELING, PaymentStatus.REFUND_PENDING);
        CompletionWriteFailure.arm();
        assertThatThrownBy(() -> jdbc.update("UPDATE orders SET status='CANCELED' WHERE id=?", f.orderId()))
                .isInstanceOf(DataAccessException.class);
        assertThat(CompletionWriteFailure.hits()).isEqualTo(1);
        assertThat(orderStatus(f)).isEqualTo(OrderStatus.CANCELING);
    }
}
