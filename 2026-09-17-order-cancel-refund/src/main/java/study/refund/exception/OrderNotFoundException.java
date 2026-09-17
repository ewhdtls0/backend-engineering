package study.refund.exception;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(Long id) { super("Order not found: " + id); }
}
