package study.payment;

import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final OrderIdempotencyKeyRepository keys;
    private final IdempotencyKeyClaimService keyClaims;

    public PaymentService(OrderRepository orders, PaymentRepository payments, PaymentGateway gateway,
                          OrderIdempotencyKeyRepository keys, IdempotencyKeyClaimService keyClaims) {
        this.orders = orders;
        this.payments = payments;
        this.gateway = gateway;
        this.keys = keys;
        this.keyClaims = keyClaims;
    }

    @Transactional
    public PaymentResponse pay(Long orderId, String idempotencyKey, PaymentRequest request) {
        // 기존 키의 충돌은 신규 주문·금액 검증보다 우선한다.
        if (idempotencyKey == null || idempotencyKey.isBlank() || request == null) {
            throw new MissionException(MissionException.Code.INVALID_REQUEST, "잘못된 요청입니다.");
        }
        var existing = keys.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) return replayOrReject(existing.get(), orderId, request.amount());

        if (orderId == null || orderId <= 0 || request.amount() <= 0) {
            throw new MissionException(MissionException.Code.INVALID_REQUEST, "잘못된 요청입니다.");
        }

        // 같은 주문 요청은 락으로 직렬화되므로, 대기 뒤 키를 다시 확인한다.
        Order order = orders.findById(orderId)
                .orElseThrow(() -> new MissionException(MissionException.Code.NOT_FOUND, "해당하는 주문이 없습니다."));
        existing = keys.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) return replayOrReject(existing.get(), orderId, request.amount());

        if (order.getTotalAmount() != request.amount()) {
            throw new MissionException(MissionException.Code.INVALID_REQUEST, "요청 금액이 잘못 되었습니다.");
        }

        if (order.getStatus() == OrderStatus.PAID) {
            throw new MissionException(MissionException.Code.CONFLICT, "이미 결제된 요청입니다.");
        }

        try {
            // 키를 Gateway 호출 전에 별도 트랜잭션에서 선점한다.
            keyClaims.claim(idempotencyKey, orderId, request.amount());
        } catch (DataIntegrityViolationException e) {
            return keys.findByIdempotencyKey(idempotencyKey)
                    .map(history -> replayOrReject(history, orderId, request.amount()))
                    .orElseThrow(() -> e);
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

            order.markPaid();

            return new PaymentResponse(
                    payment.getId(),
                    orderId,
                    request.amount(),
                    PaymentStatus.APPROVED
            );
        } catch (GatewayException e) {
            keyClaims.releaseAfterDefiniteGatewayFailure(idempotencyKey);
            throw new MissionException(MissionException.Code.GATEWAY_FAILED, "요청에 실패했습니다.");
        }




    }

    private PaymentResponse replayOrReject(OrderIdempotencyKey history, Long orderId, long amount) {
        if (!Objects.equals(history.getOrderId(), orderId) || history.getAmount() != amount) {
            throw new MissionException(MissionException.Code.CONFLICT, "같은 멱등 키에 다른 요청을 사용할 수 없습니다.");
        }
        // 키 선점 직후에는 최초 요청이 아직 Payment를 커밋하지 않았을 수 있다.
        // 같은 주문 락을 한 번 통과하면 최초 요청의 커밋 결과를 안전하게 읽을 수 있다.
        orders.findById(orderId)
                .orElseThrow(() -> new MissionException(MissionException.Code.NOT_FOUND, "해당하는 주문이 없습니다."));
        var payment = payments.findByOrderId(orderId)
                .orElseThrow(() -> new MissionException(MissionException.Code.NOT_FOUND, "결제 정보가 없습니다."));
        return new PaymentResponse(payment.getId(), orderId, amount, payment.getStatus());
    }
}
