package study.seathold.exception;
import org.springframework.dao.PessimisticLockingFailureException;
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

    // 락 획득 실패
    @ExceptionHandler(PessimisticLockingFailureException.class)
    ResponseEntity<ApiError> lockConflict(
            PessimisticLockingFailureException ex
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(
                        "SEAT_LOCK_CONFLICT",
                        "좌석 처리 중 충돌이 발생했습니다. 잠시 후 다시 시도해주세요."
                ));
    }
}

