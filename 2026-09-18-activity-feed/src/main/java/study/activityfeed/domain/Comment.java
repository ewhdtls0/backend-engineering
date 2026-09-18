package study.activityfeed.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "comments")
public class Comment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Comment() {}

    public Comment(Member member, String content, LocalDateTime createdAt) {
        this.member = member;
        this.content = content;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Member getMember() { return member; }
    public String getContent() { return content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
