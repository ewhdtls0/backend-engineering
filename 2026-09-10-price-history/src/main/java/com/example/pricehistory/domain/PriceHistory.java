package com.example.pricehistory.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
public class PriceHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // 스타터는 단순 ID 참조를 사용합니다. 필요하면 Product 연관관계로 변경하세요.
    @Column(nullable = false)
    private Long productId;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal previousPrice;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal changedPrice;
    @Column(nullable = false, length = 500)
    private String reason;
    @Column(nullable = false)
    private LocalDateTime changedAt;

    protected PriceHistory() {}
    // TODO: 생성자/팩토리와 이력 생성 책임을 직접 결정하세요.
    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public BigDecimal getPreviousPrice() { return previousPrice; }
    public BigDecimal getChangedPrice() { return changedPrice; }
    public String getReason() { return reason; }
    public LocalDateTime getChangedAt() { return changedAt; }
}
