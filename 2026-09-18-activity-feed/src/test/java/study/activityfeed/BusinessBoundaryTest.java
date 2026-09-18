package study.activityfeed;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import study.activityfeed.domain.Member;
import study.activityfeed.support.BusinessDatabaseTest;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessBoundaryTest extends BusinessDatabaseTest {
    @Test void committedFixturesAreVisibleThroughIndependentConnectionWithoutTestTransaction() throws Exception {
        Member target = member("committed fixture");
        post(target, 1);
        comment(target, 2);
        readyForRequest();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        try (Connection connection = jdbc.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select count(*) from posts where member_id = ?")) {
            statement.setLong(1, target.getId());
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong(1)).isEqualTo(1);
                assertThat(rows.next()).isFalse();
            }
        }
        assertThat(jdbc.queryForObject("select count(*) from comments where member_id = ?",
                Long.class, target.getId())).isEqualTo(1);
    }
}
