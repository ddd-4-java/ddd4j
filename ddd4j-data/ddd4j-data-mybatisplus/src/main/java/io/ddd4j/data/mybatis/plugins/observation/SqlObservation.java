package io.ddd4j.data.mybatis.plugins.observation;

/**
 * MyBatis-Plus SQL 执行观测数据。
 *
 * 2026-09-05：record 降级为 JDK8 兼容 class。
 */
public final class SqlObservation {

    private final String statementId;
    private final String sql;
    private final long elapsedNanos;
    private final Throwable error;

    public SqlObservation(String statementId, String sql, long elapsedNanos, Throwable error) {
        this.statementId = statementId;
        this.sql = sql;
        this.elapsedNanos = elapsedNanos;
        this.error = error;
    }

    public long elapsedMillis() {
        return elapsedNanos / 1_000_000L;
    }

    public String statementId() { return statementId; }
    public String sql() { return sql; }
    public long elapsedNanos() { return elapsedNanos; }
    public Throwable error() { return error; }
}
