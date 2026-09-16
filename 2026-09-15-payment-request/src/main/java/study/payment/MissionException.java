package study.payment;

public class MissionException extends RuntimeException {
    public enum Code { INVALID_REQUEST, NOT_FOUND, CONFLICT, GATEWAY_FAILED }
    private final Code code;
    public MissionException(Code code, String message) { super(message); this.code = code; }
    public Code getCode() { return code; }
}
