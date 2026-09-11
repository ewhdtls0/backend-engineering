package com.example.pricehistory.exception;

import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class ApiExceptionHandler {
    public record ErrorResponse(String code, String message) {}
    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(ProductNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("PRODUCT_NOT_FOUND", ex.getMessage()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, MethodArgumentTypeMismatchException.class,
                       HttpMessageNotReadableException.class, IllegalArgumentException.class, InvalidDateRangeException.class})
    public ResponseEntity<ErrorResponse> badRequest(Exception ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_REQUEST", "요청 값과 날짜 형식을 확인하세요."));
    }
}
