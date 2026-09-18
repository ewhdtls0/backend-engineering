package study.activityfeed.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "posts")
public class Post {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Post() {}

    public Post(Member member, String title, LocalDateTime createdAt) {
        this.member = member;
        this.title = title;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Member getMember() { return member; }
    public String getTitle() { return title; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
