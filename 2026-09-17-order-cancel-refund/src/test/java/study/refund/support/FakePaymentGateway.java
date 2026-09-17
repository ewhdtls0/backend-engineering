package study.refund.support;

import java.util.ArrayList;
import java.util.List;
import study.refund.service.PaymentGateway;

/** 순차 테스트용. 실패 호출도 attempts에 남고 실제 성공만 successes에 남는다. */
public class FakePaymentGateway implements PaymentGateway {
    public record Call(String paymentKey, long amount) {}
    private final List<Call> attempts = new ArrayList<>();
    private final List<Call> successes = new ArrayList<>();
    private boolean fail;
    private Runnable afterSuccess = () -> {};

    @Override
    public void refund(String paymentKey, long amount) {
        Call call = new Call(paymentKey, amount);
        attempts.add(call);
        if (fail) throw new GatewayRejectedException();
        successes.add(call);
        afterSuccess.run();
    }

    public List<Call> attempts() { return List.copyOf(attempts); }
    public List<Call> successes() { return List.copyOf(successes); }
    public void rejectRequests(boolean fail) { this.fail = fail; }
    public void afterSuccess(Runnable action) { this.afterSuccess = action; }
    public void reset() {
        attempts.clear();
        successes.clear();
        fail = false;
        afterSuccess = () -> {};
    }

    /** 이 fake의 거절은 외부에 환불 효과가 없었던 것으로 확정된 실패다. */
    public static class GatewayRejectedException extends RuntimeException {
        public GatewayRejectedException() { super("Injected: gateway rejected before refund"); }
    }
}
