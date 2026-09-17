package study.refund.dto;

import study.refund.domain.OrderStatus;

public record CancelOrderResponse(Long orderId, OrderStatus status, RefundStatus refundStatus) {
    public enum RefundStatus { COMPLETED, PENDING, FAILED }
}
