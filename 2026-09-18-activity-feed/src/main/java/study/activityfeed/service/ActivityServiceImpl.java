package study.activityfeed.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import study.activityfeed.dto.ActivityPageResponse;
import study.activityfeed.dto.ActivityResponse;
import study.activityfeed.exception.MemberNotFoundException;
import study.activityfeed.repository.MemberRepository;

import java.util.List;

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

        PageRequest pageRequest = PageRequest.of(page, size);

        if (pageRequest.getOffset() > Integer.MAX_VALUE) {
            return new ActivityPageResponse(
                    memberId,
                    page,
                    size,
                    List.of()
            );
        }

        List<ActivityResponse> activities = memberRepository.findAllActivitiesById(memberId, pageRequest)
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
