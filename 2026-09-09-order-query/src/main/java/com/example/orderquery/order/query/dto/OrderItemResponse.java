package com.example.orderquery.order.query.dto;

import java.math.BigDecimal;
public record OrderItemResponse(Long productId, String productName, int quantity, BigDecimal unitPrice) {}
