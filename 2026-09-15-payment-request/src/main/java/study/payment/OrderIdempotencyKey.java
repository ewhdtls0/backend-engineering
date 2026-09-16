package study.payment;

import jakarta.persistence.*;

@Entity
@Table(name = "purchase_orders_idempotency_key")
public class OrderIdempotencyKey {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private long amount;

    public OrderIdempotencyKey(String idempotencyKey, Long orderId, long amount) {
        this.idempotencyKey = idempotencyKey;
        this.orderId = orderId;
        this.amount = amount;
    }

    protected OrderIdempotencyKey() {

    }

    public Long getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Long getOrderId() {
        return orderId;
    }

    public long getAmount() {
        return amount;
    }
}
