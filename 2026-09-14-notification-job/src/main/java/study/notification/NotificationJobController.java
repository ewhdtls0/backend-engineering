package study.notification;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/notification-jobs")
public class NotificationJobController {
    private final NotificationJobService service;
    public NotificationJobController(NotificationJobService service) { this.service = service; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public CreateJobResponse create(@Valid @RequestBody CreateJobRequest request) { return service.createJob(request); }
    @PostMapping("/{jobId}/execute") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void execute(@PathVariable Long jobId) { service.execute(jobId); }
    @GetMapping("/{jobId}")
    public JobResponse get(@PathVariable Long jobId) { return service.getJob(jobId); }
}
