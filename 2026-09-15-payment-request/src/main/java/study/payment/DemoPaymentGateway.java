package study.payment;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
/** 실제 결제망 연결 없이 매 호출 새 승인 결과를 만드는 로컬 실습용 Gateway. */
@Component
public class DemoPaymentGateway implements PaymentGateway {
    public PaymentApproval approve(Long orderId, long amount) {
        return new PaymentApproval("demo-" + UUID.randomUUID(), Instant.now());
    }
}
