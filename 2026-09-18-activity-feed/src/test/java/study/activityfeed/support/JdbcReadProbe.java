package study.activityfeed.support;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.lang.reflect.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Test-only observation at the JDBC boundary, independent of repository/query style. */
public final class JdbcReadProbe {
    private static final ThreadLocal<Sample> CURRENT = new ThreadLocal<>();

    private JdbcReadProbe() {}

    public static Sample begin() {
        if (CURRENT.get() != null) throw new IllegalStateException("Probe already active");
        Sample sample = new Sample();
        CURRENT.set(sample);
        return sample;
    }

    public static final class Sample implements AutoCloseable {
        private long rows;
        private final List<String> sql = new ArrayList<>();

        public long rows() { return rows; }
        public List<String> sql() { return List.copyOf(sql); }

        public void assertAtMost(long limit) {
            if (rows > limit) throw new AssertionError("JDBC_ROWS_LIMIT: consumed " + rows
                    + " rows; limit=" + limit + "; SQL=" + sql);
        }

        @Override public void close() { CURRENT.remove(); }
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException exception) { throw exception.getCause(); }
    }

    private static Object proxy(Class<?> api, InvocationHandler handler) {
        return Proxy.newProxyInstance(JdbcReadProbe.class.getClassLoader(), new Class<?>[]{api}, handler);
    }

    private static ResultSet resultSet(ResultSet target) {
        return (ResultSet) proxy(ResultSet.class, (p, method, args) -> {
            Object value = invoke(target, method, args);
            Sample sample = CURRENT.get();
            if (sample != null && method.getName().equals("next") && Boolean.TRUE.equals(value)) {
                sample.rows++;
            }
            return value;
        });
    }

    private static Statement statement(Statement target, String preparedSql) {
        Class<?> api = target instanceof CallableStatement ? CallableStatement.class
                : target instanceof PreparedStatement ? PreparedStatement.class : Statement.class;
        return (Statement) proxy(api, (p, method, args) -> {
            String name = method.getName();
            Sample sample = CURRENT.get();
            if (sample != null && (name.equals("execute") || name.equals("executeQuery"))) {
                String sql = args != null && args.length > 0 && args[0] instanceof String
                        ? (String) args[0] : preparedSql;
                sample.sql.add(sql == null ? "<unknown>" : sql);
            }
            Object value = invoke(target, method, args);
            return value instanceof ResultSet rs ? resultSet(rs) : value;
        });
    }

    private static Connection connection(Connection target) {
        return (Connection) proxy(Connection.class, (p, method, args) -> {
            Object value = invoke(target, method, args);
            if (value instanceof Statement statement) {
                String sql = args != null && args.length > 0 && args[0] instanceof String
                        ? (String) args[0] : null;
                return statement(statement, sql);
            }
            return value;
        });
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Configuration {
        @Bean
        static BeanPostProcessor observedDataSource() {
            return new BeanPostProcessor() {
                @Override public Object postProcessAfterInitialization(Object bean, String name) {
                    if (!(bean instanceof DataSource source)) return bean;
                    return new DelegatingDataSource(source) {
                        @Override public Connection getConnection() throws SQLException {
                            return connection(source.getConnection());
                        }
                        @Override public Connection getConnection(String username, String password)
                                throws SQLException {
                            return connection(source.getConnection(username, password));
                        }
                    };
                }
            };
        }
    }
}
