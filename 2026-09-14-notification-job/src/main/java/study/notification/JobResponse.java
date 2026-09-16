package study.notification;

import java.util.List;
public record JobResponse(Long jobId, JobStatus status, int totalCount, long successCount,
                          long failureCount, List<TargetResult> targets) {
    public record TargetResult(Long memberId, TargetStatus status, String failureReason) {}
}
