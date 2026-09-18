package study.activityfeed.dto;

import java.util.List;

public record ActivityPageResponse(Long memberId, int page, int size,
                                   List<ActivityResponse> activities) {
    public ActivityPageResponse {
        activities = List.copyOf(activities);
    }
}
