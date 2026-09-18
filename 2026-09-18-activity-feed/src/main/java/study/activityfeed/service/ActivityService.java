package study.activityfeed.service;

import study.activityfeed.dto.ActivityPageResponse;

public interface ActivityService {
    ActivityPageResponse getActivities(Long memberId, int page, int size);
}
