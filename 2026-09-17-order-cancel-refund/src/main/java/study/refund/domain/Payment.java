package study.refund.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "payments")
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;
    @Column(nullable = false, unique = true)
    private String paymentKey;
    @Column(nullable = false)
    private long amount;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private PaymentStatus status;

    protected Payment() {}

    public Payment(Order order, String paymentKey, long amount, PaymentStatus status) {
        this.order = java.util.Objects.requireNonNull(order);
        if (paymentKey == null || paymentKey.isBlank()) throw new IllegalArgumentException("paymentKey required");
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
        this.paymentKey = paymentKey;
        this.amount = amount;
        this.status = java.util.Objects.requireNonNull(status);
    }

    public Long getId() { return id; }
    public Order getOrder() { return order; }
    public String getPaymentKey() { return paymentKey; }
    public long getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }

    // 이 setter가 비즈니스 전이 검증을 대신하지 않는다.
    public void changeStatus(PaymentStatus status) {
        this.status = java.util.Objects.requireNonNull(status);
    }

    public void startRefund() {
        if (status != PaymentStatus.PAID && status != PaymentStatus.REFUND_FAILED) {
            throw new IllegalStateException("PAID 또는 REFUND_FAILED 결제만 환불을 시작할 수 있습니다.");
        }
        status = PaymentStatus.REFUND_PENDING;
    }

    public void completeRefund() {
        if (status != PaymentStatus.REFUND_PENDING) throw new IllegalStateException("REFUND_PENDING 결제만 완료할 수 있습니다.");
        status = PaymentStatus.REFUNDED;
    }

    public void rejectRefund() {
        if (status != PaymentStatus.REFUND_PENDING) throw new IllegalStateException("REFUND_PENDING 결제만 실패로 기록할 수 있습니다.");
        status = PaymentStatus.REFUND_FAILED;
    }
}
