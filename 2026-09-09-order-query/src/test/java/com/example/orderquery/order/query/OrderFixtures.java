package com.example.orderquery.order.query;

import com.example.orderquery.member.Member;
import com.example.orderquery.order.Order;
import com.example.orderquery.product.Product;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public final class OrderFixtures {
    private OrderFixtures() {}
    public static Member memberWithOrders(EntityManager em, int orders, int itemsPerOrder) {
        Member member = new Member("학습 회원");
        em.persist(member);
        for (int i = 0; i < orders; i++) {
            Order order = new Order(member, LocalDateTime.of(2026, 9, 9, 12, 0).plusMinutes(i));
            for (int j = 0; j < itemsPerOrder; j++) {
                // 상품 공유로 SQL 문제가 가려지지 않도록 각 항목은 서로 다른 상품을 참조합니다.
                Product product = new Product("상품-" + i + "-" + j, new BigDecimal("1200.00"));
                em.persist(product);
                order.addItem(product, j + 1, new BigDecimal("1000.00"));
            }
            em.persist(order);
        }
        em.flush();
        em.clear();
        return member;
    }
}
