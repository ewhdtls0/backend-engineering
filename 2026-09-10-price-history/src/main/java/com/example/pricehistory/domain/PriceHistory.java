package com.example.pricehistory.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
public class PriceHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal previousPrice;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal changedPrice;
    @Column(nullable = false, length = 500)
    private String reason;
    @Column(nullable = false)
    private LocalDateTime changedAt;

    protected PriceHistory() {}

    public PriceHistory(Product product, BigDecimal previousPrice, BigDecimal changedPrice, String reason, LocalDateTime changedAt) {
        this.product = product;
        this.previousPrice = previousPrice;
        this.changedPrice = changedPrice;
        this.reason = reason;
        this.changedAt = changedAt;
    }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public BigDecimal getPreviousPrice() { return previousPrice; }
    public BigDecimal getChangedPrice() { return changedPrice; }
    public String getReason() { return reason; }
    public LocalDateTime getChangedAt() { return changedAt; }
}
