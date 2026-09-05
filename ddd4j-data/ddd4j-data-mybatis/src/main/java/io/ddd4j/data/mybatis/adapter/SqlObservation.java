/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

/**
 * 原生 MyBatis SQL 执行观测数据。
 *
 * @param statementId MappedStatement 标识
 * @param sql 已执行的 SQL
 * @param sortedParams 已排序的参数快照
 * @param elapsedNanos 耗时，单位为纳秒
 * @param error 执行异常，可为空
 */

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

    public String getStatementId() {
        return statementId;
    }

    public String getSql() {
        return sql;
    }

    public List<String> getSortedParams() {
        return sortedParams;
    }

    public long getElapsedNanos() {
        return elapsedNanos;
    }

    public Throwable getError() {
        return error;
    }
}
