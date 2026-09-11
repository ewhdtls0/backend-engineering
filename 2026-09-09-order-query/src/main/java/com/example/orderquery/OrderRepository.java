package com.example.orderquery;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {
    @Query(value = """
        select o from Order o where o.member.id = :memberId order by o.orderedAt desc, o.id desc
    """,
    countQuery = """
        select count(o) from Order o where o.member.id = :memberId
    """)
    Page<Order> findAllByMemberId(@Param("memberId") Long memberId, Pageable pageable);

    Long member(Member member);
}
