package com.example.orderquery;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.*;
@Entity
@Table(name = "orders")
public class Order {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;
    @Column(nullable = false)
    private LocalDateTime orderedAt;
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();
    protected Order() {}
    public Order(Member member, LocalDateTime orderedAt) { this.member = member; this.orderedAt = orderedAt; }
    public OrderItem addItem(Product product, int quantity, BigDecimal unitPrice) {
        OrderItem item = new OrderItem(this, product, quantity, unitPrice);
        items.add(item);
        return item;
    }
    public Long getId() { return id; }
    public Member getMember() { return member; }
    public LocalDateTime getOrderedAt() { return orderedAt; }
    public List<OrderItem> getItems() { return Collections.unmodifiableList(items); }
}
