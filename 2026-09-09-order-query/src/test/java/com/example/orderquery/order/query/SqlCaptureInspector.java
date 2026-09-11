package com.example.orderquery.order.query;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class SqlCaptureInspector implements StatementInspector {
    private static final List<String> SQL = new CopyOnWriteArrayList<>();

    @Override
    public String inspect(String sql) {
        SQL.add(sql);
        return sql;
    }

    static void clear() {
        SQL.clear();
    }

    static List<String> orderSelects() {
        return SQL.stream()
                .filter(sql -> sql.toLowerCase().startsWith("select"))
                .filter(sql -> sql.toLowerCase().contains(" from orders "))
                .filter(sql -> !sql.toLowerCase().contains("count("))
                .toList();
    }
}
