package study.payment;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
@RestControllerAdvice
public class ApiExceptionHandler {
    public record ErrorResponse(String code, String message) {}
    @ExceptionHandler(MissionException.class)
    ResponseEntity<ErrorResponse> domain(MissionException e) {
        int status = switch (e.getCode()) {
            case INVALID_REQUEST -> 400; case NOT_FOUND -> 404;
            case CONFLICT -> 409; case GATEWAY_FAILED -> 502;
        };
        return ResponseEntity.status(status).body(new ErrorResponse(e.getCode().name(), e.getMessage()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, MissingRequestHeaderException.class,
        HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorResponse> invalid(Exception e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_REQUEST", "요청 형식과 필수 값을 확인하세요."));
    }
}
