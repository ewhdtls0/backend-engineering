package study.refund.service;

import org.springframework.stereotype.Service;
import study.refund.dto.CancelOrderResponse;
import study.refund.exception.RefundProcessingException;

@Service
public class CancelOrderService {
    private final RefundStateService refundState;
    private final PaymentGateway gateway;

    public CancelOrderService(RefundStateService refundState, PaymentGateway gateway) {
        this.refundState = refundState;
        this.gateway = gateway;
    }

    public CancelOrderResponse cancel(Long orderId) {
        RefundStateService.Preparation preparation = refundState.prepareCancellation(orderId);
        if (!preparation.requiresGatewayCall()) return preparation.response();
        return executeRefund(preparation.attempt(), true);
    }

    public void processRefund(Long paymentId) {
        executeRefund(refundState.prepareRetry(paymentId), false);
    }

    private CancelOrderResponse executeRefund(RefundStateService.RefundAttempt attempt, boolean returnFailure) {
        try {
            // 준비 트랜잭션은 이미 끝났다. 느린 외부 호출 동안 DB 연결과 잠금을 점유하지 않는다.
            gateway.refund(attempt.paymentKey(), attempt.amount());
        } catch (RefundRejectedException rejected) {
            // 외부 효과가 없다고 확정된 거절만 안전하게 다시 시도할 수 있는 실패로 기록한다.
            refundState.markRejected(attempt.paymentId());
            return returnFailure ? refundState.failedResponse(attempt.orderId()) : null;
        } catch (RuntimeException outcomeUnknown) {
            // timeout처럼 성공 여부를 모르면 PENDING을 보존한다. 자동 재호출은 중복 환불 위험이 있다.
            throw new RefundProcessingException("환불 결과를 확인할 수 없습니다. 결제사 조회 후 복구해야 합니다.", outcomeUnknown);
        }

        try {
            // 외부 성공 뒤 완료 저장은 별도 트랜잭션이다. 실패해도 앞서 커밋한 PENDING은 남는다.
            return refundState.complete(attempt.paymentId());
        } catch (RuntimeException completionFailure) {
            throw new RefundProcessingException("외부 환불은 성공했지만 완료 상태 저장에 실패했습니다.", completionFailure);
        }
    }
}
