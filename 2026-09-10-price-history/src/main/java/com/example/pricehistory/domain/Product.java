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

    public Long getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void changePrice(BigDecimal price, LocalDateTime now) {
        this.price = price;
        this.updatedAt = now;
    }
}
