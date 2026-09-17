package study.seathold;
import java.sql.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.h2.api.Trigger;
/** Test-only database fault. Works for dirty checking, save and direct SQL updates. */
public class FailUpdateTrigger implements Trigger {
    static final AtomicInteger fired = new AtomicInteger();
    @Override public void fire(Connection connection, Object[] oldRow, Object[] newRow) throws SQLException {
        fired.incrementAndGet();
        throw new SQLException("TEST_CONFIRM_WRITE_FAILURE", "45000");
    }
}

