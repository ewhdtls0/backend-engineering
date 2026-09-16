package study.payment;

import jakarta.validation.constraints.Positive;
public record PaymentRequest(@Positive long amount) {}
