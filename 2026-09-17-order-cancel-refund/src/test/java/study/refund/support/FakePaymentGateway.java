package study.refund.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import study.refund.service.PaymentGateway;
import study.refund.service.RefundRejectedException;

/** 실패 호출도 attempts에 남고 실제 성공만 successes에 남는 테스트 대역이다. */
public class FakePaymentGateway implements PaymentGateway {
    public record Call(String paymentKey, long amount) {}
    private final List<Call> attempts = new CopyOnWriteArrayList<>();
    private final List<Call> successes = new CopyOnWriteArrayList<>();
    private volatile boolean fail;
    private volatile Runnable beforeResult = () -> {};
    private volatile Runnable afterSuccess = () -> {};

    @Override
    public void refund(String paymentKey, long amount) {
        Call call = new Call(paymentKey, amount);
        attempts.add(call);
        beforeResult.run();
        if (fail) throw new RefundRejectedException("Injected: gateway rejected before refund");
        successes.add(call);
        afterSuccess.run();
    }

    public List<Call> attempts() { return List.copyOf(attempts); }
    public List<Call> successes() { return List.copyOf(successes); }
    public void rejectRequests(boolean fail) { this.fail = fail; }
    public void beforeResult(Runnable action) { this.beforeResult = action; }
    public void afterSuccess(Runnable action) { this.afterSuccess = action; }
    public void reset() {
        attempts.clear();
        successes.clear();
        fail = false;
        beforeResult = () -> {};
        afterSuccess = () -> {};
    }
}
