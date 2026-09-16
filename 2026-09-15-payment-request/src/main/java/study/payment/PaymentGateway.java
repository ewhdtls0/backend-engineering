package study.payment;

public interface PaymentGateway {
    PaymentApproval approve(Long orderId, long amount);
}
