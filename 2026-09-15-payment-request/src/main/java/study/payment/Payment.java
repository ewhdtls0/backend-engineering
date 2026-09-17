package study.payment;

import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name = "payments")
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true) private Long orderId;
    @Column(nullable = false) private long amount;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private PaymentStatus status;
    @Column(nullable = false) private Instant approvedAt;
    @Column(nullable = false) private String approvalReference;
    protected Payment() {}
    public Payment(Long orderId, long amount, String approvalReference, Instant approvedAt) {
        this.orderId = orderId; this.amount = amount; this.approvalReference = approvalReference;
        this.approvedAt = approvedAt; this.status = PaymentStatus.APPROVED;
    }
    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public long getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
    public Instant getApprovedAt() { return approvedAt; }
    public String getApprovalReference() { return approvalReference; }
}
