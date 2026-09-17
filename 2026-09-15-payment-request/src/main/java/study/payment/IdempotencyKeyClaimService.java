package study.payment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyKeyClaimService {
    private final OrderIdempotencyKeyRepository keys;

    public IdempotencyKeyClaimService(OrderIdempotencyKeyRepository keys) {
        this.keys = keys;
    }

    /** 외부 승인 전에 키를 DB에 선점해 다른 인스턴스의 중복 승인을 막는다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void claim(String key, Long orderId, long amount) {
        keys.saveAndFlush(new OrderIdempotencyKey(key, orderId, amount));
    }

    /** 승인 전에 확실히 실패한 경우에만 키를 해제해 같은 키 재시도를 허용한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseAfterDefiniteGatewayFailure(String key) {
        keys.deleteByIdempotencyKey(key);
    }
}
