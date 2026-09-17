package study.refund.service;

/** 외부 결제사가 환불을 수행하지 않았다고 확정해서 알려 준 경우에만 사용한다. */
public class RefundRejectedException extends RuntimeException {
    public RefundRejectedException(String message) {
        super(message);
    }
}
