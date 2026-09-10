package com.example.orderquery;

import jakarta.persistence.*;
import java.math.BigDecimal;
@Entity
@Table(name = "products")
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;
    protected Product() {}
    public Product(String name, BigDecimal price) { this.name = name; this.price = price; }
    public Long getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }
}
