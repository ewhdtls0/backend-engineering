package study.activityfeed.dto;

import java.time.LocalDateTime;

public record ActivityResponse(ActivityType type, Long activityId, String content,
                               LocalDateTime createdAt) {}
