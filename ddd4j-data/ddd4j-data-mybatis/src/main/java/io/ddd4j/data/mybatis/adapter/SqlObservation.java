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
public record SqlObservation(String statementId, String sql, List<String> sortedParams, long elapsedNanos, Throwable error) {

    public long elapsedMillis() {
        return elapsedNanos / 1_000_000L;
    }
}
