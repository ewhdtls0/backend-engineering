package study.payment;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
@RestController
public class PaymentController {
    private final PaymentService service;
    public PaymentController(PaymentService service) { this.service = service; }
    @PostMapping("/api/orders/{orderId}/payments")
    public PaymentResponse pay(@PathVariable Long orderId, @RequestHeader("Idempotency-Key") String key,
                               @Valid @RequestBody PaymentRequest request) {
        if (key.isBlank()) throw new MissionException(MissionException.Code.INVALID_REQUEST, "키는 비어 있을 수 없습니다.");
        return service.pay(orderId, key, request);
    }
}
