package study.activityfeed.domain;

import study.activityfeed.dto.ActivityType;

import java.time.LocalDateTime;

public interface ActivityProjection {
    Long getId();
    ActivityType getType();
    String getContent();
    LocalDateTime getCreatedAt();
}
