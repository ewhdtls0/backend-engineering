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
    // TODO: 필요한 예외 계약을 추가하세요. 미구현 예외를 성공 응답으로 숨기지 마세요.
}
