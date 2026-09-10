package com.example.orderquery;

import org.springframework.data.jpa.repository.JpaRepository;
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    // TODO: 직접 선택한 조회 전략에 필요한 메서드만 추가하세요.
}
