package study.activityfeed.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Commit fixtures, then exercise the real application transaction boundary. */
public abstract class BusinessDatabaseTest extends DatabaseTest {
    @Autowired private PlatformTransactionManager transactionManager;

    @BeforeEach
    void databaseStartsEmpty() {
        assertThat(jdbc.queryForObject("select count(*) from comments", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from posts", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from members", Long.class)).isZero();
    }

    protected void readyForRequest() {
        if (TestTransaction.isActive()) {
            cold();
            TestTransaction.flagForCommit();
            TestTransaction.end();
        }
    }

    @AfterEach
    void cleanCommittedFixturesEvenWhenBusinessAssertionsFail() {
        if (TestTransaction.isActive()) {
            TestTransaction.flagForRollback();
            TestTransaction.end();
        }
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.update("delete from comments");
            jdbc.update("delete from posts");
            jdbc.update("delete from members");
        });
    }
}
