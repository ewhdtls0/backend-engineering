package study.refund.exception;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(OrderNotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(OrderNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex);
    }
    @ExceptionHandler(OrderNotCancelableException.class)
    ResponseEntity<ProblemDetail> conflict(OrderNotCancelableException ex) {
        return problem(HttpStatus.CONFLICT, ex);
    }
    @ExceptionHandler(RefundProcessingException.class)
    ResponseEntity<ProblemDetail> unavailable(RefundProcessingException ex) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, ex);
    }
    private ResponseEntity<ProblemDetail> problem(HttpStatus status, RuntimeException ex) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, ex.getMessage()));
    }
}
