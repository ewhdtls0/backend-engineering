package com.example.pricehistory.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Product() {}
    public Product(String name, BigDecimal price, LocalDateTime updatedAt) {
        this.name = name; this.price = price; this.updatedAt = updatedAt;
    }
    // TODO: 가격 변경 인터페이스와 책임을 직접 설계하세요.
    public Long getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
