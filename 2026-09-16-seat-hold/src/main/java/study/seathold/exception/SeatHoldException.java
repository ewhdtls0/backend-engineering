package study.seathold.exception;
import org.springframework.http.HttpStatus;
public class SeatHoldException extends RuntimeException {
    private final HttpStatus status;
    public SeatHoldException(HttpStatus status, String message) {
        super(message); this.status = status;
    }
    public HttpStatus getStatus() { return status; }
}

