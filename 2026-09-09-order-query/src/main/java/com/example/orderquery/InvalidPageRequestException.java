package com.example.orderquery;

public class InvalidPageRequestException extends RuntimeException {
    public InvalidPageRequestException() { super("page >= 0, 1 <= size <= 100 이어야 합니다."); }
}
