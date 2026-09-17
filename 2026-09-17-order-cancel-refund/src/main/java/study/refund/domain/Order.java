package study.refund.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "orders")
public class Order {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long memberId;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private OrderStatus status;
    @Column(nullable = false)
    private long totalAmount;

    protected Order() {}

    public Order(Long memberId, long totalAmount, OrderStatus status) {
        this.memberId = java.util.Objects.requireNonNull(memberId);
        if (totalAmount <= 0) throw new IllegalArgumentException("totalAmount must be positive");
        this.totalAmount = totalAmount;
        this.status = java.util.Objects.requireNonNull(status);
    }

    public Long getId() { return id; }
    public Long getMemberId() { return memberId; }
    public OrderStatus getStatus() { return status; }
    public long getTotalAmount() { return totalAmount; }

    // 상태 전이 허용 규칙은 과제에서 설계한다.
    public void changeStatus(OrderStatus status) {
        this.status = java.util.Objects.requireNonNull(status);
    }
}
