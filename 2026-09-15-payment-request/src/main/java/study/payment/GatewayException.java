package study.payment;

/** 승인 전에 확실히 거절된 경우. 타임아웃으로 결과가 불명확한 상황과 구분합니다. */
public class GatewayException extends RuntimeException {
    public GatewayException(String message) { super(message); }
}
