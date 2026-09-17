package study.refund.support;

import java.sql.*;
import org.h2.api.Trigger;

/** 테스트 DB에만 설치. repository.save, dirty checking, 직접 SQL 모두에서 작동한다. */
public class CompletionWriteFailure implements Trigger {
    private static boolean armed;
    private static int hits;
    private int statusIndex;

    public static void arm() { armed = true; }
    public static int hits() { return hits; }
    public static void reset() { armed = false; hits = 0; }

    @Override
    public void init(Connection conn, String schema, String name, String table,
                     boolean before, int type) throws SQLException {
        try (Statement statement = conn.createStatement();
             ResultSet rows = statement.executeQuery("SELECT * FROM " + schema + "." + table + " WHERE 1=0")) {
            ResultSetMetaData meta = rows.getMetaData();
            statusIndex = -1;
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                if ("STATUS".equalsIgnoreCase(meta.getColumnName(i))) statusIndex = i - 1;
            }
            if (statusIndex < 0) throw new SQLException("No STATUS column in " + table);
        }
    }

    @Override
    public void fire(Connection conn, Object[] oldRow, Object[] newRow) throws SQLException {
        String status = String.valueOf(newRow[statusIndex]);
        if (armed && ("REFUNDED".equals(status) || "CANCELED".equals(status))) {
            hits++;
            throw new SQLException("Injected: completion write unavailable after external success", "45000");
        }
    }
}
