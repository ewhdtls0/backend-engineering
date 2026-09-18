package study.activityfeed.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "members")
public class Member {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    protected Member() {}

    public Member(String name) { this.name = name; }

    public Long getId() { return id; }
    public String getName() { return name; }
}
