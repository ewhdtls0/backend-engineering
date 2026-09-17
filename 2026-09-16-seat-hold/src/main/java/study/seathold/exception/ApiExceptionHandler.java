package study.seathold.exception;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice
public class ApiExceptionHandler {
    public record ApiError(String code, String message) {}
    @ExceptionHandler(SeatHoldException.class)
    ResponseEntity<ApiError> handle(SeatHoldException ex) {
        return ResponseEntity.status(ex.getStatus())
            .body(new ApiError(ex.getStatus().name(), ex.getMessage()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST", "memberId must be positive"));
    }
}

