package study.refund.service;

public interface PaymentGateway {
    void refund(String paymentKey, long amount);
}
