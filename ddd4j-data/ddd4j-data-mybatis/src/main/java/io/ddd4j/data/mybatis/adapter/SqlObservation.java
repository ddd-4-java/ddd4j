package io.ddd4j.data.mybatis.adapter;

import java.util.List;

/**
 * 原生 MyBatis SQL 执行观测数据。
 *
 * @param statementId MappedStatement 标识
 * @param sql 已执行的 SQL
 * @param sortedParams 已排序的参数快照
 * @param elapsedNanos 耗时，单位为纳秒
 * @param error 执行异常，可为空
 */
public final class SqlObservation {

    private final String statementId;
    private final String sql;
    private final List<String> sortedParams;
    private final long elapsedNanos;
    private final Throwable error;

    public SqlObservation(String statementId, String sql, List<String> sortedParams, long elapsedNanos, Throwable error) {
        this.statementId = statementId;
        this.sql = sql;
        this.sortedParams = sortedParams;
        this.elapsedNanos = elapsedNanos;
        this.error = error;
    }

    public long elapsedMillis() {
        return elapsedNanos / 1_000_000L;
    }

    public String statementId() { return statementId; }
    public String sql() { return sql; }
    public List<String> sortedParams() { return sortedParams; }
    public long elapsedNanos() { return elapsedNanos; }
    public Throwable error() { return error; }
}
