package study.notification;

import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name = "notification_jobs")
public class NotificationJob {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private JobStatus status;
    @Column(nullable = false, length = 2000) private String message;
    @Column(nullable = false) private int totalCount;
    @Column(nullable = false) private Instant createdAt;
    protected NotificationJob() {}
    public NotificationJob(String message, int totalCount) {
        this.message = message; this.totalCount = totalCount;
        this.status = JobStatus.PENDING; this.createdAt = Instant.now();
    }
    public Long getId() { return id; }
    public JobStatus getStatus() { return status; }
    public String getMessage() { return message; }
    public int getTotalCount() { return totalCount; }
    public Instant getCreatedAt() { return createdAt; }
    public void changeStatus(JobStatus status) { this.status = status; }
    public void changeTotalCount(int totalCount) { this.totalCount = totalCount; }
}
