package study.cartsync.exception;

public class MissionException extends RuntimeException {
    public enum Code { INVALID_REQUEST, CART_NOT_FOUND, PRODUCT_NOT_FOUND }
    private final Code code;
    public MissionException(Code code, String message) { super(message); this.code = code; }
    public Code getCode() { return code; }
}
