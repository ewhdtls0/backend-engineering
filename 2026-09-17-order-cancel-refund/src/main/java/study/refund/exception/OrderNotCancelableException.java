package study.refund.exception;

public class OrderNotCancelableException extends RuntimeException {
    public OrderNotCancelableException(Long id) { super("Order cannot be canceled: " + id); }
}
