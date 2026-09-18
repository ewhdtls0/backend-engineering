package study.activityfeed.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import study.activityfeed.dto.ActivityPageResponse;
import study.activityfeed.dto.ActivityResponse;
import study.activityfeed.exception.MemberNotFoundException;
import study.activityfeed.repository.MemberRepository;

@Service
public class ActivityServiceImpl implements ActivityService {
    private final MemberRepository memberRepository;

    public ActivityServiceImpl(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public ActivityPageResponse getActivities(Long memberId, int page, int size) {
        if (memberId == null || memberId <= 0 || page < 0 || size <= 0) {
            throw new IllegalArgumentException("잘못된 요청입니다.");
        }

        memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberNotFoundException(memberId));

        long offset = (long) page * size;
        var activities = memberRepository.findAllActivitiesById(memberId, offset, size)
                .stream()
                .map(activity -> new ActivityResponse(
                        activity.getType(),
                        activity.getId(),
                        activity.getContent(),
                        activity.getCreatedAt()
                )).toList();

        return new ActivityPageResponse(
                memberId,
                page,
                size,
                activities
        );
    }
}
