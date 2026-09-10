package com.example.orderquery;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {
    // TODO: 직접 선택한 조회 전략에 필요한 메서드만 추가하세요.
    @Query(value = """
        select o from Order o join fetch o.member where o.member.id = :memberId order by o.orderedAt desc, o.id desc 
    """,
    countQuery = """
        select count(o) from Order o where o.member.id = :memberId
    """)
    Page<Order> findAllByMemberId(@Param("memberId") Long memberId, Pageable pageable);
}
