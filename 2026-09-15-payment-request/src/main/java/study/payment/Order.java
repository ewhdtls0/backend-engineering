package study.payment;

import jakarta.persistence.*;
@Entity @Table(name = "purchase_orders")
public class Order {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private long totalAmount;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private OrderStatus status;
    protected Order() {}
    public Order(long totalAmount) { this.totalAmount = totalAmount; status = OrderStatus.PENDING; }
    public Long getId() { return id; }
    public long getTotalAmount() { return totalAmount; }
    public OrderStatus getStatus() { return status; }
    public void markPaid() { status = OrderStatus.PAID; }
}
