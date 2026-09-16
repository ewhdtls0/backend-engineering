package study.payment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

@Service
public class PaymentService {
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final OrderIdempotencyKeyRepository keys;

    public PaymentService(OrderRepository orders, PaymentRepository payments, PaymentGateway gateway, OrderIdempotencyKeyRepository keys) {
        this.orders = orders;
        this.payments = payments;
        this.gateway = gateway;
        this.keys = keys;
    }

    @Transactional
    public PaymentResponse pay(Long orderId, String idempotencyKey, PaymentRequest request) {
        // 유효성 검사
        if (idempotencyKey == null || idempotencyKey.isBlank() || orderId == null || orderId < 0 || request == null || request.amount() < 0) {
            throw new MissionException(MissionException.Code.INVALID_REQUEST, "잘못된 요청입니다.");
        }

        Order order = orders.findById(orderId)
                .orElseThrow(() -> new MissionException(MissionException.Code.NOT_FOUND, "해당하는 주문이 없습니다."));

        Optional<OrderIdempotencyKey> existing = keys.findByIdempotencyKey(idempotencyKey);

        if (existing.isPresent()) {
            OrderIdempotencyKey history = existing.get();

            if (!Objects.equals(history.getOrderId(), orderId)
                    || history.getAmount() != request.amount()) {
                throw new MissionException(
                        MissionException.Code.CONFLICT,
                        "같은 멱등 키에 다른 요청을 사용할 수 없습니다."
                );
            }

            var payment = payments.findByOrderId(orderId)
                    .orElseThrow(() -> new MissionException(
                            MissionException.Code.NOT_FOUND,
                            "결제 정보가 없습니다."
                    ));

            return new PaymentResponse(
                    payment.getId(),
                    orderId,
                    request.amount(),
                    payment.getStatus()
            );
        }

        if (order.getTotalAmount() != request.amount()) {
            throw new MissionException(MissionException.Code.INVALID_REQUEST, "요청 금액이 잘못 되었습니다.");
        }

        if (order.getStatus().equals(OrderStatus.PAID)) {
            throw new MissionException(MissionException.Code.CONFLICT, "이미 결제된 요청입니다.");
        }

        try {
            PaymentApproval approve = gateway.approve(orderId, request.amount());
            Payment payment = new Payment(
                    orderId,
                    request.amount(),
                    approve.reference(),
                    approve.approvedAt()
            );

            payments.save(payment);

            // 키 이력 저장
            keys.save(new OrderIdempotencyKey(
                    idempotencyKey,
                    orderId,
                    request.amount()
            ));

            order.markPaid();

            return new PaymentResponse(
                    payment.getId(),
                    orderId,
                    request.amount(),
                    PaymentStatus.APPROVED
            );
        } catch (Exception e) {
            throw new MissionException(MissionException.Code.GATEWAY_FAILED, "요청에 실패했습니다.");
        }




    }
}
