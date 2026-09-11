package com.example.orderquery.order.query;

import com.example.orderquery.common.error.InvalidPageRequestException;
import com.example.orderquery.order.query.dto.OrderPageResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.PageRequest;
@RestController
@RequestMapping("/api/members/{memberId}/orders")
public class OrderController {
    private final OrderQueryService service;
    public OrderController(OrderQueryService service) { this.service = service; }
    @GetMapping
    public OrderPageResponse findOrders(@PathVariable Long memberId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidPageRequestException();
        }
        return service.findOrders(memberId, PageRequest.of(page, size));
    }
}
