package study.refund.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import study.refund.domain.Order;
import study.refund.domain.OrderStatus;
import study.refund.domain.Payment;
import study.refund.domain.PaymentStatus;
import study.refund.dto.CancelOrderResponse;
import study.refund.exception.OrderNotCancelableException;
import study.refund.exception.OrderNotFoundException;
import study.refund.repository.OrderRepository;
import study.refund.repository.PaymentRepository;

@Service
public class CancelOrderService {
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final EntityManager em;

    public CancelOrderService(OrderRepository orders, PaymentRepository payments, PaymentGateway gateway, EntityManager em) {
        this.orders = orders;
        this.payments = payments;
        this.gateway = gateway;
        this.em = em;
    }

    @Transactional
    public CancelOrderResponse cancel(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("잘못된 요청입니다.");
        }

        Order order = orders.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        Payment payment = payments.findByOrderId(orderId)
                .orElseThrow(() -> new EntityNotFoundException("진행된 결제가 없습니다."));

        // 취소 됐거나 취소 진행 중
        if (order.getStatus().equals(OrderStatus.CANCELED) || order.getStatus().equals(OrderStatus.CANCELING)) {
            return new CancelOrderResponse(
                    orderId,
                    order.getStatus(),
                    CancelOrderResponse.RefundStatus.COMPLETED
            );
        }

        try {
            // 결제 시도
            order.changeStatus(OrderStatus.CANCELING);
            payment.changeStatus(PaymentStatus.REFUND_PENDING);

            em.flush();

            gateway.refund(payment.getPaymentKey(), payment.getAmount());

            order.changeStatus(OrderStatus.CANCELED);
            payment.changeStatus(PaymentStatus.REFUNDED);
        } catch (Exception e) {
            payment.changeStatus(PaymentStatus.REFUND_FAILED);
            return new CancelOrderResponse(orderId, order.getStatus(), CancelOrderResponse.RefundStatus.FAILED);
        }

        return new CancelOrderResponse(
                orderId,
                order.getStatus(),
                CancelOrderResponse.RefundStatus.COMPLETED
        );
    }

    @Transactional
    public void processRefund(Long paymentId) {
        if (paymentId == null || paymentId <= 0) {
            throw new IllegalArgumentException("잘못된 요청입니다.");
        }

        Payment payment = payments.findByIdWithOrder(paymentId)
                .orElseThrow(() -> new EntityNotFoundException("진행된 결제가 없습니다."));

        if (payment.getStatus().equals(PaymentStatus.REFUNDED) || payment.getOrder().getStatus().equals(OrderStatus.CANCELED)) {
            throw new OrderNotCancelableException(payment.getOrder().getId());
        }
        try {
            payment.changeStatus(PaymentStatus.REFUND_PENDING);
            em.flush();
            gateway.refund(payment.getPaymentKey(), payment.getAmount());

            payment.getOrder().changeStatus(OrderStatus.CANCELED);
            payment.changeStatus(PaymentStatus.REFUNDED);
        } catch (Exception e) {
            payment.changeStatus(PaymentStatus.REFUND_FAILED);
        }
    }
}
