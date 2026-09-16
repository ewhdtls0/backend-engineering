package study.payment;

public record PaymentResponse(Long paymentId, Long orderId, long amount, PaymentStatus status) {}
