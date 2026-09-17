package study.refund.controller;

import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;
import study.refund.dto.CancelOrderResponse;
import study.refund.service.CancelOrderService;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final CancelOrderService service;
    public OrderController(CancelOrderService service) { this.service = service; }

    @PostMapping("/{orderId}/cancel")
    public CancelOrderResponse cancel(@PathVariable @Positive Long orderId) {
        return service.cancel(orderId);
    }
}
