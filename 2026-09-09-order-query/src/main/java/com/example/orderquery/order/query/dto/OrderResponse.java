package com.example.orderquery.order.query.dto;

import com.example.orderquery.order.Order;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(Long orderId, LocalDateTime orderedAt, List<OrderItemResponse> items, BigDecimal totalPrice) {

    public static OrderResponse from(Order entity) {
        return new OrderResponse(
                entity.getId(),
                entity.getOrderedAt(),
                entity.getItems()
                        .stream()
                        .map(item -> new OrderItemResponse(item.getProduct().getId(), item.getProduct().getName(), item.getQuantity(), item.getUnitPrice()))
                        .toList(),
                entity.getItems()
                        .stream()
                        .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
    }
}
