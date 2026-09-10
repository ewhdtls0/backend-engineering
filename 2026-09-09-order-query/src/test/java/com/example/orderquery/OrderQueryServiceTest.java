package com.example.orderquery;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.example.orderquery.SqlCaptureInspector"
})
@Transactional
class OrderQueryServiceTest {
    @Autowired EntityManager em;
    @Autowired EntityManagerFactory emf;
    @Autowired OrderQueryService service;

    @Test
    void returnsProductNamesQuantitiesAndTotalPrice() {
        Member member = OrderFixtures.memberWithOrders(em, 1, 2);
        var result = service.findOrders(member.getId(), PageRequest.of(0, 20));

        assertThat(result.content()).hasSize(1);
        var order = result.content().getFirst();
        assertThat(order.items()).extracting(OrderItemResponse::productName)
                .containsExactly("상품-0-0", "상품-0-1");
        assertThat(order.items()).extracting(OrderItemResponse::quantity)
                .containsExactly(1, 2);
        assertThat(order.items()).extracting(OrderItemResponse::unitPrice)
                .allSatisfy(price -> assertThat(price).isEqualByComparingTo("1000.00"));
        assertThat(order.totalPrice()).isEqualByComparingTo("3000.00");
    }

    @Test
    void paginatesTwentyFiveOrdersWithoutDuplicates() {
        Member member = OrderFixtures.memberWithOrders(em, 25, 1);
        SqlCaptureInspector.clear();
        var first = service.findOrders(member.getId(), PageRequest.of(0, 20));
        var second = service.findOrders(member.getId(), PageRequest.of(1, 20));

        assertThat(first.content()).hasSize(20);
        assertThat(second.content()).hasSize(5);
        assertThat(first.page()).isZero();
        assertThat(second.page()).isEqualTo(1);
        assertThat(first.size()).isEqualTo(20);
        assertThat(first.totalElements()).isEqualTo(25);
        assertThat(second.totalElements()).isEqualTo(25);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(second.totalPages()).isEqualTo(2);
        var firstIds = first.content().stream().map(OrderResponse::orderId).toList();
        var secondIds = second.content().stream().map(OrderResponse::orderId).toList();
        assertThat(new HashSet<>(firstIds)).doesNotContainAnyElementsOf(secondIds);
        assertThat(SqlCaptureInspector.orderSelects())
                .as("컬렉션 조회 때문에 메모리에서 페이지를 자르지 않고 DB SQL에 페이지 제한이 있어야 한다")
                .isNotEmpty()
                .allSatisfy(sql -> assertThat(sql).containsPattern("(?i)(offset|fetch first|limit)") );
    }

    @Test
    void sortsOrdersNewestFirst() {
        Member member = OrderFixtures.memberWithOrders(em, 3, 1);
        var result = service.findOrders(member.getId(), PageRequest.of(0, 20));

        assertThat(result.content()).extracting(OrderResponse::orderedAt)
                .containsExactly(
                        LocalDateTime.of(2026, 9, 9, 12, 2),
                        LocalDateTime.of(2026, 9, 9, 12, 1),
                        LocalDateTime.of(2026, 9, 9, 12, 0));
    }

    @Test
    void usesOrderIdDescendingWhenOrderedTimesAreEqual() {
        Member member = new Member("동일 시각 회원");
        Product product = new Product("동일 시각 상품", new java.math.BigDecimal("1000.00"));
        em.persist(member);
        em.persist(product);
        LocalDateTime sameTime = LocalDateTime.of(2026, 9, 9, 12, 0);
        Order first = new Order(member, sameTime);
        first.addItem(product, 1, new java.math.BigDecimal("1000.00"));
        Order second = new Order(member, sameTime);
        second.addItem(product, 1, new java.math.BigDecimal("1000.00"));
        em.persist(first);
        em.persist(second);
        em.flush();
        em.clear();

        var result = service.findOrders(member.getId(), PageRequest.of(0, 20));

        assertThat(result.content()).extracting(OrderResponse::orderId)
                .containsExactly(second.getId(), first.getId());
    }

    @Test
    void returnsOnlyTheRequestedMembersOrders() {
        Member requested = OrderFixtures.memberWithOrders(em, 2, 1);
        Member other = OrderFixtures.memberWithOrders(em, 3, 1);
        var result = service.findOrders(requested.getId(), PageRequest.of(0, 20));

        assertThat(result.content()).hasSize(2);
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.content()).flatExtracting(OrderResponse::items)
                .extracting(OrderItemResponse::productName)
                .containsExactlyInAnyOrder("상품-0-0", "상품-1-0");
        assertThat(requested.getId()).isNotEqualTo(other.getId());
    }

    @Test
    void returnsEmptyPageForMemberWithoutOrders() {
        Member member = new Member("주문 없는 회원");
        em.persist(member);
        em.flush();
        em.clear();
        var result = service.findOrders(member.getId(), PageRequest.of(0, 20));

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
    }

    @Test
    void returnsEmptyContentBeyondLastPageWhileKeepingTotals() {
        Member member = OrderFixtures.memberWithOrders(em, 3, 1);
        var result = service.findOrders(member.getId(), PageRequest.of(2, 2));

        assertThat(result.content()).isEmpty();
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.totalPages()).isEqualTo(2);
    }

    @Test
    void rejectsMissingMember() {
        assertThatThrownBy(() -> service.findOrders(-1L, PageRequest.of(0, 20)))
                .isInstanceOf(MemberNotFoundException.class)
                .hasMessageContaining("-1");
    }

    @Test
    void loadsTwentyOrdersAndOneHundredItemsWithBoundedSqlCount() {
        Member member = OrderFixtures.memberWithOrders(em, 20, 5);
        var statistics = emf.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        var result = service.findOrders(member.getId(), PageRequest.of(0, 20));

        assertThat(result.content()).hasSize(20);
        assertThat(result.content()).flatExtracting(OrderResponse::items).hasSize(100);
        assertThat(result.content()).flatExtracting(OrderResponse::items)
                .extracting(OrderItemResponse::productName).doesNotContainNull();
        assertThat(result.content()).extracting(OrderResponse::totalPrice)
                .allSatisfy(total -> assertThat(total).isEqualByComparingTo("15000.00"));
        long sqlCount = statistics.getPrepareStatementCount();
        System.out.println("20 orders x 5 items 조회 SQL 수: " + sqlCount);
        assertThat(sqlCount).as("주문 수에 비례하는 N+1 조회가 발생하면 안 된다")
                .isLessThanOrEqualTo(6);
    }
}
