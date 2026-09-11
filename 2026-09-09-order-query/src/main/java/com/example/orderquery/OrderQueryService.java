package com.example.orderquery;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        // 회원 확인
        memberRepository.findById(memberId)
            .orElseThrow(() -> new MemberNotFoundException(memberId));

        Page<OrderResponse> responsePage = orderRepository
                .findAllByMemberId(memberId, pageable)
                .map(OrderResponse::from);

        return new OrderPageResponse(
                responsePage.getContent(),
                responsePage.getNumber(),
                responsePage.getSize(),
                responsePage.getTotalElements(),
                responsePage.getTotalPages());
    }
}
