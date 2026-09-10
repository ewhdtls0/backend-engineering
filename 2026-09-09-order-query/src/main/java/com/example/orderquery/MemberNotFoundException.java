package com.example.orderquery;

public class MemberNotFoundException extends RuntimeException {
    public MemberNotFoundException(Long memberId) { super("Member not found: " + memberId); }
}
