package com.example.orderquery;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class OrderQueryService {
    private final MemberRepository memberRepository;
    private final OrderRepository orderRepository;
    public OrderQueryService(MemberRepository memberRepository, OrderRepository orderRepository) {
        this.memberRepository = memberRepository;
        this.orderRepository = orderRepository;
    }
    public OrderPageResponse findOrders(Long memberId, Pageable pageable) {
        // TODO: 회원 확인, 최신순 페이지 조회, 연관 데이터 조회, DTO 변환, totalPrice 계산.
        // TODO: 트랜잭션 경계 및 SQL 증가 양상을 직접 설계하고 검증하세요.

        // 회원 확인
        memberRepository.findById(memberId)
            .orElseThrow(() -> new MemberNotFoundException(memberId));

        Page<Order> allOrdersByMemberId = orderRepository.findAllByMemberId(memberId, pageable);

        List<OrderResponse> orderResponses = allOrdersByMemberId.stream()
                .map(OrderResponse::from)
                .toList();

        long totalElements = allOrdersByMemberId.getTotalElements();
        int totalPages = allOrdersByMemberId.getTotalPages();

        return new OrderPageResponse(
                orderResponses,
                pageable.getPageNumber(),
                pageable.getPageSize(),
                totalElements,
                totalPages);
    }
}
