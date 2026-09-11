package com.example.orderquery.common.error;

public class MemberNotFoundException extends RuntimeException {
    public MemberNotFoundException(Long memberId) { super("Member not found: " + memberId); }
}
