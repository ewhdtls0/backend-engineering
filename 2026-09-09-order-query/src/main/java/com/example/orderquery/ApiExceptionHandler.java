package com.example.orderquery;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice
public class ApiExceptionHandler {
    public record ErrorResponse(String code, String message) {}
    @ExceptionHandler(MemberNotFoundException.class)
    public ResponseEntity<ErrorResponse> memberNotFound(MemberNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("MEMBER_NOT_FOUND", ex.getMessage()));
    }
    @ExceptionHandler(InvalidPageRequestException.class)
    public ResponseEntity<ErrorResponse> invalidPage(InvalidPageRequestException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_PAGE", ex.getMessage()));
    }
}
