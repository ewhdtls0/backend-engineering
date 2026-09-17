package study.refund.service;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import study.refund.domain.*;
import study.refund.dto.CancelOrderResponse;
import study.refund.exception.OrderNotCancelableException;
import study.refund.exception.OrderNotFoundException;
import study.refund.repository.OrderRepository;
import study.refund.repository.PaymentRepository;

@Service
public class RefundStateService {
    private final OrderRepository orders;
    private final PaymentRepository payments;

    public RefundStateService(OrderRepository orders, PaymentRepository payments) {
        this.orders = orders;
        this.payments = payments;
    }

    @Transactional
    public Preparation prepareCancellation(Long orderId) {
        requirePositive(orderId);
        // 같은 주문의 준비 단계만 직렬화한다. 잠금은 외부 API 호출 전에 커밋과 함께 해제된다.
        Order order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        Payment payment = payments.findByOrderId(orderId)
                .orElseThrow(() -> new EntityNotFoundException("진행된 결제가 없습니다."));

        if (order.getStatus() == OrderStatus.CANCELED && payment.getStatus() == PaymentStatus.REFUNDED) {
            return Preparation.respond(response(order, CancelOrderResponse.RefundStatus.COMPLETED));
        }
        if (order.getStatus() == OrderStatus.CANCELING && payment.getStatus() == PaymentStatus.REFUND_PENDING) {
            return Preparation.respond(response(order, CancelOrderResponse.RefundStatus.PENDING));
        }
        if (order.getStatus() != OrderStatus.PAID || payment.getStatus() != PaymentStatus.PAID) {
            throw new OrderNotCancelableException(orderId);
        }

        order.startCancellation();
        payment.startRefund();
        return Preparation.call(new RefundAttempt(payment.getId(), order.getId(), payment.getPaymentKey(), payment.getAmount()));
    }

    @Transactional
    public RefundAttempt prepareRetry(Long paymentId) {
        requirePositive(paymentId);
        Payment payment = payments.findByIdWithOrderForUpdate(paymentId)
                .orElseThrow(() -> new EntityNotFoundException("진행된 결제가 없습니다."));
        Order order = payment.getOrder();
        // PENDING은 성공 여부가 불명확하므로 결제사 확인 없이 재호출하지 않는다.
        if (order.getStatus() != OrderStatus.CANCELING || payment.getStatus() != PaymentStatus.REFUND_FAILED) {
            throw new OrderNotCancelableException(order.getId());
        }
        payment.startRefund();
        return new RefundAttempt(payment.getId(), order.getId(), payment.getPaymentKey(), payment.getAmount());
    }

    @Transactional
    public CancelOrderResponse complete(Long paymentId) {
        Payment payment = payment(paymentId);
        payment.getOrder().completeCancellation();
        payment.completeRefund();
        return response(payment.getOrder(), CancelOrderResponse.RefundStatus.COMPLETED);
    }

    @Transactional
    public void markRejected(Long paymentId) {
        payment(paymentId).rejectRefund();
    }

    @Transactional(readOnly = true)
    public CancelOrderResponse failedResponse(Long orderId) {
        Order order = orders.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        return response(order, CancelOrderResponse.RefundStatus.FAILED);
    }

    private Payment payment(Long paymentId) {
        return payments.findByIdWithOrder(paymentId)
                .orElseThrow(() -> new EntityNotFoundException("진행된 결제가 없습니다."));
    }

    private CancelOrderResponse response(Order order, CancelOrderResponse.RefundStatus refundStatus) {
        return new CancelOrderResponse(order.getId(), order.getStatus(), refundStatus);
    }

    private void requirePositive(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("잘못된 요청입니다.");
    }

    public record RefundAttempt(Long paymentId, Long orderId, String paymentKey, long amount) {}

    public record Preparation(RefundAttempt attempt, CancelOrderResponse response) {
        static Preparation call(RefundAttempt attempt) { return new Preparation(attempt, null); }
        static Preparation respond(CancelOrderResponse response) { return new Preparation(null, response); }
        boolean requiresGatewayCall() { return attempt != null; }
    }
}
