package study.notification;

import io.micrometer.common.util.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class NotificationJobService {
    private final NotificationJobRepository jobs;
    private final NotificationTargetRepository targets;
    private final NotificationSender sender;
    public NotificationJobService(NotificationJobRepository jobs, NotificationTargetRepository targets,
                                  NotificationSender sender) {
        this.jobs = jobs; this.targets = targets; this.sender = sender;
    }

    @Transactional
    public CreateJobResponse createJob(CreateJobRequest request) {
        // 유효성 검사
        if (request == null || request.memberIds().isEmpty() || StringUtils.isBlank(request.message())) {
            throw new MissionException(MissionException.Code.INVALID_REQUEST, "잘못된 요청입니다.");
        }

        Set<Long> memberIdSet = new HashSet<>();

        boolean isDuplicated = request.memberIds()
                .stream()
                .anyMatch(memberId -> !memberIdSet.add(memberId));

        if (isDuplicated) {
            throw new MissionException(MissionException.Code.INVALID_REQUEST, "중복된 정보가 있습니다.");
        }

        NotificationJob notificationJob = new NotificationJob(request.message(), request.memberIds().size());

        jobs.save(notificationJob);

        List<NotificationTarget> notificationTargets = request.memberIds()
                .stream()
                .map(memberId -> new NotificationTarget(notificationJob.getId(), memberId))
                .toList();

        targets.saveAll(notificationTargets);

        return new CreateJobResponse(
                notificationJob.getId(),
                JobStatus.PENDING
        );
    }

    @Transactional
    public void execute(Long jobId) {

        NotificationJob notificationJob = jobs.findById(jobId)
                .orElseThrow(() -> new MissionException(MissionException.Code.NOT_FOUND, "해당하는 Job이 없습니다."));

        notificationJob.changeStatus(JobStatus.PROCESSING);

        List<NotificationTarget> allTargets = targets.findByJobIdOrderByIdAsc(jobId);

        AtomicInteger successCount = new AtomicInteger();

        allTargets.forEach(target -> {
            try {
                sender.send(target.getMemberId(), notificationJob.getMessage());
                target.recordSuccess();
                successCount.incrementAndGet();
            } catch (Exception e) {
                target.recordFailure("알림 전송 실패");
            }
        });

        if (successCount.get() == notificationJob.getTotalCount()) {
            notificationJob.changeStatus(JobStatus.COMPLETED);
        } else if (successCount.get() == 0) {
            notificationJob.changeStatus(JobStatus.FAILED);
        } else {
            notificationJob.changeStatus(JobStatus.COMPLETED_WITH_FAILURES);
        }
    }
    @Transactional(readOnly = true)
    public JobResponse getJob(Long jobId) {
        NotificationJob job = jobs.findById(jobId).orElseThrow(() ->
            new MissionException(MissionException.Code.NOT_FOUND, "작업이 존재하지 않습니다."));
        var results = targets.findByJobIdOrderByIdAsc(jobId).stream().map(t ->
            new JobResponse.TargetResult(t.getMemberId(), t.getStatus(), t.getFailureReason())).toList();
        return new JobResponse(job.getId(), job.getStatus(), job.getTotalCount(),
            results.stream().filter(t -> t.status() == TargetStatus.SUCCESS).count(),
            results.stream().filter(t -> t.status() == TargetStatus.FAILED).count(), results);
    }
}
