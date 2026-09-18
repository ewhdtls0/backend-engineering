package study.activityfeed;

import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import study.activityfeed.domain.*;
import study.activityfeed.dto.*;
import study.activityfeed.support.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/** These tests must pass before the exercise is implemented. */
class StarterInfrastructureTest extends DatabaseTest {
    @Test void contextMappingsFixturesAndLazyRelationsWork() {
        Member target = member("fixture");
        ActivityResponse expectedPost = post(target, 1);
        ActivityResponse expectedComment = comment(target, 2);
        cold();

        Post post = em.find(Post.class, expectedPost.activityId());
        Comment comment = em.find(Comment.class, expectedComment.activityId());
        assertThat(post.getTitle()).isEqualTo(expectedPost.content());
        assertThat(post.getCreatedAt()).isEqualTo(expectedPost.createdAt());
        assertThat(comment.getContent()).isEqualTo(expectedComment.content());
        assertThat(comment.getCreatedAt()).isEqualTo(expectedComment.createdAt());
        assertThat(Hibernate.isInitialized(post.getMember())).isFalse();
        assertThat(Hibernate.isInitialized(comment.getMember())).isFalse();
        assertThat(post.getMember().getId()).isEqualTo(target.getId());
        assertThat(comment.getMember().getId()).isEqualTo(target.getId());
        assertThat(newest(List.of(expectedPost, expectedComment)))
                .containsExactly(expectedComment, expectedPost);
    }

    @Test void fortyFiveFixtureAndPageOracleAreValid() {
        List<ActivityResponse> fixture = fortyFive(member("oracle"));
        cold();
        assertThat(fixture).hasSize(45);
        assertThat(fixture.stream().map(DatabaseTest::key)).doesNotHaveDuplicates();
        assertThat(fixture).containsExactlyElementsOf(newest(fixture));
        assertThat(fixture.subList(0, 20)).hasSize(20);
        assertThat(fixture.subList(20, 40)).hasSize(20);
        assertThat(fixture.subList(40, 45)).hasSize(5);
        assertThat(jdbc.queryForObject("select count(*) from posts", Long.class)).isEqualTo(25);
        assertThat(jdbc.queryForObject("select count(*) from comments", Long.class)).isEqualTo(20);
    }

    @Test void probeCountsProjectionRowsAndRejectsExcessiveConsumption() {
        Member target = member("probe");
        for (int i = 0; i < 12; i++) post(target, i);
        cold();

        try (JdbcReadProbe.Sample sample = JdbcReadProbe.begin()) {
            List<String> rows = jdbc.queryForList("select title from posts where member_id = ?", String.class, target.getId());
            assertThat(rows).hasSize(12);
            assertThat(sample.rows()).isEqualTo(12);
            assertThat(sample.sql()).hasSize(1);
            assertThatThrownBy(() -> sample.assertAtMost(10)).isInstanceOf(AssertionError.class)
                    .hasMessageContaining("JDBC_ROWS_LIMIT");
        }
        try (JdbcReadProbe.Sample fresh = JdbcReadProbe.begin()) {
            assertThat(fresh.rows()).isZero();
            assertThat(fresh.sql()).isEmpty();
        }
    }

    @Test void probeAlsoCountsPlainStatementExecuteResultSet() throws Exception {
        Member target = member("statement probe");
        comment(target, 1);
        comment(target, 2);
        cold();
        try (Connection connection = jdbc.getDataSource().getConnection();
             Statement statement = connection.createStatement();
             JdbcReadProbe.Sample sample = JdbcReadProbe.begin()) {
            // Use a constant result set because this separate connection cannot see uncommitted fixtures.
            assertThat(statement.execute("select X from system_range(1, 3)")).isTrue();
            try (ResultSet rows = statement.getResultSet()) {
                int count = 0;
                while (rows.next()) count++;
                assertThat(count).isEqualTo(3);
            }
            assertThat(sample.rows()).isEqualTo(3);
            assertThat(sample.sql()).hasSize(1);
        }
    }

    @Test void probeAndHibernateStatisticsObserveEntityLoading() {
        Member target = member("hibernate probe");
        for (int i = 0; i < 12; i++) post(target, i);
        cold();
        Statistics stats = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        try (JdbcReadProbe.Sample sample = JdbcReadProbe.begin()) {
            assertThat(em.createQuery("select p from Post p where p.member.id = :id", Post.class)
                    .setParameter("id", target.getId()).getResultList()).hasSize(12);
            assertThat(sample.rows()).isEqualTo(12);
            assertThat(stats.getEntityLoadCount()).isEqualTo(12);
            assertThatThrownBy(() -> sample.assertAtMost(10)).isInstanceOf(AssertionError.class);
        }
    }

    @Test void pageResponseCopiesItsInput() {
        var mutable = new java.util.ArrayList<ActivityResponse>();
        ActivityPageResponse response = new ActivityPageResponse(1L, 0, 20, mutable);
        mutable.add(new ActivityResponse(ActivityType.POST, 1L, "later", BASE));
        assertThat(response.activities()).isEmpty();
        assertThatThrownBy(() -> response.activities().add(mutable.getFirst()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void activityTablesHaveMemberTimeAndIdCompositeIndexes() throws Exception {
        assertThat(indexColumns("POSTS", "IDX_POSTS_MEMBER_CREATED_ID"))
                .containsExactly("MEMBER_ID", "CREATED_AT", "ID");
        assertThat(indexColumns("COMMENTS", "IDX_COMMENTS_MEMBER_CREATED_ID"))
                .containsExactly("MEMBER_ID", "CREATED_AT", "ID");

        String postPlan = jdbc.queryForObject("""
                explain select id from posts
                where member_id = ?
                order by created_at desc, id desc
                fetch first 20 rows only
                """, String.class, Long.MAX_VALUE);
        String commentPlan = jdbc.queryForObject("""
                explain select id from comments
                where member_id = ?
                order by created_at desc, id desc
                fetch first 20 rows only
                """, String.class, Long.MAX_VALUE);
        assertThat(postPlan).containsIgnoringCase("IDX_POSTS_MEMBER_CREATED_ID");
        assertThat(commentPlan).containsIgnoringCase("IDX_COMMENTS_MEMBER_CREATED_ID");
    }

    private List<String> indexColumns(String table, String index) throws Exception {
        List<String> columns = new ArrayList<>();
        try (Connection connection = jdbc.getDataSource().getConnection();
             ResultSet rows = connection.getMetaData().getIndexInfo(null, null, table, false, false)) {
            while (rows.next()) {
                if (index.equalsIgnoreCase(rows.getString("INDEX_NAME"))) {
                    columns.add(rows.getString("COLUMN_NAME"));
                }
            }
        }
        return columns;
    }
}
