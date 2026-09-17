package study.refund;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import study.refund.domain.*;
import study.refund.dto.CancelOrderResponse;
import study.refund.exception.*;
import study.refund.support.*;
import static org.assertj.core.api.Assertions.*;

@DisplayName("주문 취소 및 환불 인수 테스트")
class CancelOrderAcceptanceTest extends IntegrationSupport {
    @Test
    @DisplayName("결제 완료 주문을 취소하면 외부 환불을 한 번 실행하고 주문과 결제를 완료 상태로 변경한다")
    void paidOrderIsCanceledAndRefundedExactlyOnce() {
        Fixture f = fixture(OrderStatus.PAID, PaymentStatus.PAID);
        CancelOrderResponse result = service.cancel(f.orderId());
        assertThat(result).isEqualTo(new CancelOrderResponse(f.orderId(), OrderStatus.CANCELED,
                CancelOrderResponse.RefundStatus.COMPLETED));
        assertThat(gateway.attempts()).containsExactly(expectedCall(f));
        assertThat(gateway.successes()).containsExactly(expectedCall(f));
        assertCompleted(f);
    }

    @Test
    @DisplayName("외부 환불이 거절되면 완료 처리하지 않고 재처리 가능한 DB 상태를 남긴다")
    void gatewayRejectionLeavesDurableRecoveryEvidence() {
        Fixture f = fixture(OrderStatus.PAID, PaymentStatus.PAID);
        gateway.rejectRequests(true);
        cancelWithoutRequiringAnErrorPolicy(f);
        assertThat(gateway.attempts()).isNotEmpty().allMatch(expectedCall(f)::equals);
        assertThat(gateway.successes()).isEmpty();
        assertRetryableFailure(f);
    }

    @Test
    @DisplayName("같은 주문을 순차로 두 번 취소해도 외부 환불은 한 번만 실행한다")
    void twoSequentialRequestsDoNotRefundTwice() {
        Fixture f = fixture(OrderStatus.PAID, PaymentStatus.PAID);
        CancelOrderResponse first = service.cancel(f.orderId());
        CancelOrderResponse second = service.cancel(f.orderId());
        assertThat(second).isEqualTo(first);
        assertThat(second.refundStatus()).isEqualTo(CancelOrderResponse.RefundStatus.COMPLETED);
        assertThat(gateway.attempts()).containsExactly(expectedCall(f));
        assertThat(gateway.successes()).containsExactly(expectedCall(f));
        assertCompleted(f);
    }

    @Test
    @DisplayName("이미 취소와 환불이 완료된 주문은 외부 호출 없이 같은 성공 결과를 반환한다")
    void previouslyCanceledOrderDoesNotCallGateway() {
        Fixture f = fixture(OrderStatus.CANCELED, PaymentStatus.REFUNDED);
        assertThat(service.cancel(f.orderId())).isEqualTo(new CancelOrderResponse(f.orderId(),
                OrderStatus.CANCELED, CancelOrderResponse.RefundStatus.COMPLETED));
        assertThat(gateway.attempts()).isEmpty();
        assertCompleted(f);
    }

    @Test
    @DisplayName("존재하지 않는 주문은 예외를 던지고 외부 환불을 호출하지 않는다")
    void missingOrderFailsWithoutCallingGateway() {
        assertThatThrownBy(() -> service.cancel(Long.MAX_VALUE)).isInstanceOf(OrderNotFoundException.class);
        assertThat(gateway.attempts()).isEmpty();
        assertThat(orders.count()).isZero();
        assertThat(payments.count()).isZero();
    }

    @Test
    @DisplayName("실패한 결제를 재처리해 완료하고 완료된 결제의 재처리는 거절하거나 무시한다")
    void knownFailedPaymentCanBeRetriedAndFinalized() {
        // 외부 효과가 없었다고 확인된 실패를 저장한 상태. timeout/결과 불명과 구분한다.
        Fixture f = fixture(OrderStatus.CANCELING, PaymentStatus.REFUND_FAILED);
        retry(f.paymentId());
        assertThat(gateway.attempts()).containsExactly(expectedCall(f));
        assertThat(gateway.successes()).containsExactly(expectedCall(f));
        assertCompleted(f);
        retryWithoutRequiringAnErrorPolicy(f.paymentId());
        assertThat(gateway.attempts()).containsExactly(expectedCall(f));
        assertCompleted(f);
    }

    @Test
    @DisplayName("외부 환불 거절 후 결제사가 복구되면 재처리를 통해 최종 완료할 수 있다")
    void rejectedCancelCanBeRecoveredAfterGatewayBecomesAvailable() {
        Fixture f = fixture(OrderStatus.PAID, PaymentStatus.PAID);
        gateway.rejectRequests(true);
        cancelWithoutRequiringAnErrorPolicy(f);
        assertThat(gateway.attempts()).isNotEmpty().allMatch(expectedCall(f)::equals);
        assertThat(gateway.successes()).isEmpty();
        assertRetryableFailure(f);
        int failedAttempts = gateway.attempts().size();
        gateway.rejectRequests(false);
        retry(f.paymentId());
        assertThat(gateway.attempts()).hasSize(failedAttempts + 1).allMatch(expectedCall(f)::equals);
        assertThat(gateway.successes()).containsExactly(expectedCall(f));
        assertCompleted(f);
    }

    private void retry(Long paymentId) {
        // 다른 재처리 진입점을 설계하면 이 어댑터를 연결한다. 결과 assertion은 유지한다.
        service.processRefund(paymentId);
    }

    private void retryWithoutRequiringAnErrorPolicy(Long paymentId) {
        try {
            retry(paymentId);
        } catch (UnsupportedOperationException todo) {
            throw todo;
        } catch (RuntimeException alreadyCompleted) {
            // 완료된 재처리를 예외로 거절하는 정책도 허용한다.
        }
    }

    private void cancelWithoutRequiringAnErrorPolicy(Fixture f) {
        CancelOrderResponse result;
        try {
            result = service.cancel(f.orderId());
        } catch (UnsupportedOperationException todo) {
            throw todo; // starter TODO를 성공적인 실패 처리로 오인하지 않는다.
        } catch (RuntimeException failure) {
            return; // 반환/예외 정책은 자유. 호출 기록과 실제 DB 상태는 호출자가 반드시 검증.
        }
        assertThat(result).isNotNull();
        assertThat(result.orderId()).isEqualTo(f.orderId());
        assertThat(result.refundStatus()).isNotEqualTo(CancelOrderResponse.RefundStatus.COMPLETED);
        assertThat(result.status()).isNotEqualTo(OrderStatus.CANCELED);
    }

    private void assertCompleted(Fixture f) {
        assertThat(orderStatus(f)).isEqualTo(OrderStatus.CANCELED);
        assertThat(paymentStatus(f)).isEqualTo(PaymentStatus.REFUNDED);
    }

    private void assertRetryableFailure(Fixture f) {
        assertThat(orderStatus(f)).isNotEqualTo(OrderStatus.CANCELED);
        assertThat(paymentStatus(f)).isEqualTo(PaymentStatus.REFUND_FAILED);
    }

    private void assertOutcomeUnknown(Fixture f) {
        assertThat(orderStatus(f)).isEqualTo(OrderStatus.CANCELING);
        assertThat(paymentStatus(f)).isEqualTo(PaymentStatus.REFUND_PENDING);
    }
}
