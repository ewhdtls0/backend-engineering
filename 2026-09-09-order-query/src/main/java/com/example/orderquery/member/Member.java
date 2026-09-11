package com.example.orderquery.member;

import jakarta.persistence.*;
@Entity
@Table(name = "members")
public class Member {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    protected Member() {}
    public Member(String name) { this.name = name; }
    public Long getId() { return id; }
    public String getName() { return name; }
}
