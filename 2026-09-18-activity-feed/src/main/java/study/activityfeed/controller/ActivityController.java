package study.activityfeed.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;
import study.activityfeed.dto.ActivityPageResponse;
import study.activityfeed.service.ActivityService;

@RestController
@RequestMapping("/api/members")
public class ActivityController {
    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping("/{memberId}/activities")
    public ActivityPageResponse getActivities(
            @PathVariable @Positive Long memberId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return activityService.getActivities(memberId, page, size);
    }
}
