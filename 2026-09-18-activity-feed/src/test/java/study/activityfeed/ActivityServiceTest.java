package study.activityfeed;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import study.activityfeed.domain.Member;
import study.activityfeed.dto.*;
import study.activityfeed.exception.MemberNotFoundException;
import study.activityfeed.service.ActivityService;
import study.activityfeed.support.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class ActivityServiceTest extends BusinessDatabaseTest {
    @Autowired ActivityService service;

    private ActivityPageResponse page(Long id, int page, int size) {
        readyForRequest();
        ActivityPageResponse result = service.getActivities(id, page, size);
        assertThat(result.memberId()).isEqualTo(id);
        assertThat(result.page()).isEqualTo(page);
        assertThat(result.size()).isEqualTo(size);
        assertThat(result.activities()).doesNotContainNull().hasSizeLessThanOrEqualTo(size);
        return result;
    }

    @Test void mixedActivitiesAreNewestFirstWithCorrectContentAndMemberIsolation() {
        Member target = member("target");
        Member other = member("other");
        List<ActivityResponse> expected = List.of(post(target, 3), comment(target, 9),
                post(target, 1), comment(target, 6));
        post(other, 100);
        comment(other, 101);

        assertThat(page(target.getId(), 0, 20).activities()).containsExactlyElementsOf(newest(expected));
    }

    @Test void fortyFiveActivitiesProduceExactTwentyTwentyFivePages() {
        Member target = member("45 activities");
        List<ActivityResponse> expected = fortyFive(target);
        List<ActivityResponse> actual = new ArrayList<>();

        for (int p = 0; p < 3; p++) {
            List<ActivityResponse> slice = page(target.getId(), p, 20).activities();
            assertThat(slice).containsExactlyElementsOf(expected.subList(p * 20, Math.min(45, (p + 1) * 20)));
            actual.addAll(slice);
        }
        assertThat(actual).hasSize(45).containsExactlyElementsOf(expected);
        assertThat(actual.stream().map(DatabaseTest::key)).doesNotHaveDuplicates();
        assertThat(page(target.getId(), 3, 20).activities()).isEmpty();
    }

    @ParameterizedTest @EnumSource(ActivityType.class)
    void memberMayHaveOnlyOneActivityType(ActivityType type) {
        Member target = member("one type");
        List<ActivityResponse> expected = new ArrayList<>();
        for (int i = 0; i < 23; i++) expected.add(type == ActivityType.POST ? post(target, i) : comment(target, i));
        List<ActivityResponse> sorted = newest(expected);

        assertThat(page(target.getId(), 0, 20).activities()).containsExactlyElementsOf(sorted.subList(0, 20));
        assertThat(page(target.getId(), 1, 20).activities()).containsExactlyElementsOf(sorted.subList(20, 23));
    }

    @Test void equalTimestampsHaveStableOrderAcrossRepeatedReadsAndPageSizes() {
        Member target = member("ties");
        List<ActivityResponse> fixture = new ArrayList<>();
        for (int i = 0; i < 23; i++) fixture.add(post(target, 0));
        for (int i = 0; i < 22; i++) fixture.add(comment(target, 0));
        List<ActivityResponse> baseline = page(target.getId(), 0, 100).activities();
        assertThat(baseline).hasSize(45).containsExactlyInAnyOrderElementsOf(fixture);

        for (int repeat = 0; repeat < 3; repeat++) {
            assertThat(page(target.getId(), 0, 100).activities()).containsExactlyElementsOf(baseline);
            List<ActivityResponse> paged = new ArrayList<>();
            for (int p = 0; p < 7; p++) paged.addAll(page(target.getId(), p, 7).activities());
            assertThat(paged).containsExactlyElementsOf(baseline);
            assertThat(paged.stream().map(DatabaseTest::key)).doesNotHaveDuplicates();
            assertThat(page(target.getId(), 7, 7).activities()).isEmpty();
        }
    }

    @Test void existingMemberWithoutActivitiesHasEmptyPage() {
        Member target = member("empty");
        assertThat(page(target.getId(), 0, 20).activities()).isEmpty();
    }

    @Test void veryLargeValidPageDoesNotOverflowIntoEarlierResults() {
        Member target = member("large page");
        post(target, 1);
        assertThat(page(target.getId(), Integer.MAX_VALUE, 100).activities()).isEmpty();
    }

    @Test void missingMemberThrowsWithoutReadingActivityTables() {
        Member other = member("other");
        post(other, 1);
        comment(other, 2);
        readyForRequest();
        try (JdbcReadProbe.Sample sample = JdbcReadProbe.begin()) {
            assertThatThrownBy(() -> service.getActivities(Long.MAX_VALUE, 0, 20))
                    .isInstanceOf(MemberNotFoundException.class);
            assertThat(sample.sql()).noneMatch(sql -> sql.toLowerCase(Locale.ROOT)
                    .matches("(?s).*\\b(posts|comments)\\b.*"));
            sample.assertAtMost(1);
        }
    }
}
