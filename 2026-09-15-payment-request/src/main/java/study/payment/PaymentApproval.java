package study.payment;

import java.time.Instant;
public record PaymentApproval(String reference, Instant approvedAt) {}
