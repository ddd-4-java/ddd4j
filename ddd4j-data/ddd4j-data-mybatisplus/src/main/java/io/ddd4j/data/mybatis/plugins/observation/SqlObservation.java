package io.ddd4j.data.mybatis.plugins.observation;

/**
 * MyBatis-Plus SQL 执行观测数据。
 *
 * @param statementId MappedStatement 标识
 * @param sql 已执行的 SQL
 * @param elapsedNanos 耗时，单位为纳秒
 * @param error 执行异常，可为空
 */
public record SqlObservation(String statementId, String sql, long elapsedNanos, Throwable error) {

    public long elapsedMillis() {
        return elapsedNanos / 1_000_000L;
    }
}
