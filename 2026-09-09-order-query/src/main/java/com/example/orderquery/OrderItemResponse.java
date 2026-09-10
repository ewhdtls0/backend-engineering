package com.example.orderquery;

import java.math.BigDecimal;
public record OrderItemResponse(Long productId, String productName, int quantity, BigDecimal unitPrice) {}
