package study.notification;

import jakarta.persistence.*;
@Entity @Table(name = "notification_targets")
public class NotificationTarget {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long jobId;
    @Column(nullable = false) private Long memberId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private TargetStatus status;
    @Column(length = 2000) private String failureReason;
    protected NotificationTarget() {}
    public NotificationTarget(Long jobId, Long memberId) {
        this.jobId = jobId; this.memberId = memberId; this.status = TargetStatus.PENDING;
    }
    public Long getId() { return id; }
    public Long getJobId() { return jobId; }
    public Long getMemberId() { return memberId; }
    public TargetStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public void recordSuccess() { status = TargetStatus.SUCCESS; failureReason = null; }
    public void recordFailure(String reason) { status = TargetStatus.FAILED; failureReason = reason; }
}
