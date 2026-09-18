package study.activityfeed;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import study.activityfeed.domain.Member;
import study.activityfeed.dto.*;
import study.activityfeed.service.ActivityService;
import study.activityfeed.support.*;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ActivityVolumeTest extends BusinessDatabaseTest {
    @Autowired ActivityService service;

    @ParameterizedTest @ValueSource(ints = {0, 37})
    void largeMemberReturnsExactPageWithoutBulkMaterialization(int page) {
        Member target = member("large member");
        Member other = member("unrelated member");
        post(other, 999999);
        comment(other, 999998);
        cold();
        insert(target.getId(), "posts", "title", 10_000, 5);
        insert(target.getId(), "comments", "content", 40_000, 1);
        assertThat(jdbc.queryForObject("select count(*) from posts where member_id = ?", Long.class, target.getId()))
                .isEqualTo(10_000L);
        assertThat(jdbc.queryForObject("select count(*) from comments where member_id = ?", Long.class, target.getId()))
                .isEqualTo(40_000L);

        // Independent, fixture-specific oracle. This is NOT an implementation of the feed query.
        // At both tested pages every expected activity belongs to the newest post-only time interval.
        List<ActivityResponse> expected = new ArrayList<>();
        for (int position = page * 20; position < page * 20 + 20; position++) {
            int i = 9999 - position;
            Long id = jdbc.queryForObject("select id from posts where member_id = ? and title = ?",
                    Long.class, target.getId(), "posts-" + i);
            expected.add(new ActivityResponse(ActivityType.POST, id, "posts-" + i, BASE.plusSeconds(i * 5L)));
        }
        readyForRequest();
        Statistics stats = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        stats.clear();

        try (JdbcReadProbe.Sample sample = JdbcReadProbe.begin()) {
            ActivityPageResponse response = service.getActivities(target.getId(), page, 20);
            assertThat(response.memberId()).isEqualTo(target.getId());
            assertThat(response.page()).isEqualTo(page);
            assertThat(response.size()).isEqualTo(20);
            assertThat(response.activities()).containsExactlyElementsOf(expected);
            // Generous regression ceiling, not an optimal-query prescription.
            // Counts consumed result rows even when projected directly to DTOs or fetched through JDBC.
            sample.assertAtMost(5_000);
            assertThat(sample.sql()).as("Cold service call must perform a database read").isNotEmpty();
            assertThat(sample.sql()).noneMatch(sql -> sql.toLowerCase(java.util.Locale.ROOT).contains("count("));
            assertThat(sample.rows()).as("member row plus requested activity rows").isEqualTo(21);
            assertThat(stats.getEntityLoadCount()).as("Hibernate entities materialized during request")
                    .isLessThanOrEqualTo(5_000);
            System.out.printf("page=%d JDBC rows=%d entities=%d SQL=%s%n", page, sample.rows(),
                    stats.getEntityLoadCount(), sample.sql());
        }
    }

    private void insert(Long memberId, String table, String textColumn, int count, int step) {
        List<Integer> indexes = java.util.stream.IntStream.range(0, count).boxed().toList();
        jdbc.batchUpdate("insert into " + table + " (member_id, " + textColumn + ", created_at) values (?, ?, ?)",
                indexes, 500, (statement, i) -> {
                    statement.setLong(1, memberId);
                    statement.setString(2, table + "-" + i);
                    statement.setTimestamp(3, Timestamp.valueOf(BASE.plusSeconds((long) i * step)));
                });
    }
}
