package study.cartsync.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    public record ErrorResponse(String code, String message) {}
    @ExceptionHandler(MissionException.class)
    public ResponseEntity<ErrorResponse> mission(MissionException ex) {
        int status = ex.getCode() == MissionException.Code.CART_NOT_FOUND ? 404 : 400;
        return ResponseEntity.status(status).body(new ErrorResponse(ex.getCode().name(), ex.getMessage()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> invalid(Exception ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_REQUEST", "요청 형식을 확인하세요."));
    }
}
