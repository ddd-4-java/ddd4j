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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * 原生 MyBatis SQL 执行观测数据。
 *
 * <p>{@code statementId} — MappedStatement 标识
 * <p>{@code sql} — 已执行的 SQL
 * <p>{@code sortedParams} — 已排序的参数快照
 * <p>{@code elapsedNanos} — 耗时，单位为纳秒
 * <p>{@code error} — 执行异常，可为空
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
     * @param statementId  MappedStatement 标识
     * @param sql          已执行的 SQL
     * @param sortedParams 已排序的参数快照
     * @param elapsedNanos 耗时，单位为纳秒
     * @param error        执行异常，可为空
     */

    @JsonCreator
    public SqlObservation(@JsonProperty("statementId") String statementId,
                          @JsonProperty("sql") String sql,
                          @JsonProperty("sortedParams") List<String> sortedParams,
                          @JsonProperty("elapsedNanos") long elapsedNanos,
                          @JsonProperty("error") Throwable error) {
        this.statementId = statementId;
        this.sql = sql;
        this.sortedParams = sortedParams;
        this.elapsedNanos = elapsedNanos;
        this.error = error;
    }

    /**
     * 返回 SQL 执行耗时。
     *
     * @return 执行耗时，单位为毫秒
     */
    public long elapsedMillis() {
        return elapsedNanos / 1_000_000L;
    }

    /**
     * 返回 MyBatis 语句标识。
     *
     * @return MyBatis 语句标识
     */
    @JsonProperty("statementId")
    public String statementId() {
        return statementId;
    }

    /**
     * 返回已执行的 SQL。
     *
     * @return 已执行的 SQL
     */
    @JsonProperty("sql")
    public String sql() {
        return sql;
    }

    /**
     * 返回已排序的参数快照。
     *
     * @return 已排序的参数快照
     */
    @JsonProperty("sortedParams")
    public List<String> sortedParams() {
        return sortedParams;
    }

    /**
     * 返回执行耗时。
     *
     * @return 执行耗时，单位为纳秒
     */
    @JsonProperty("elapsedNanos")
    public long elapsedNanos() {
        return elapsedNanos;
    }

    /**
     * 返回执行异常。
     *
     * @return 执行异常；成功时为空
     */
    @JsonProperty("error")
    public Throwable error() {
        return error;
    }

    /**
     * 返回 MyBatis 语句标识，供 Bean 调用方使用。
     *
     * @return MyBatis 语句标识
     */
    public String getStatementId() {
        return statementId;
    }

    /**
     * 返回已执行的 SQL，供 Bean 调用方使用。
     *
     * @return 已执行的 SQL
     */
    public String getSql() {
        return sql;
    }

    /**
     * 返回已排序的参数快照，供 Bean 调用方使用。
     *
     * @return 已排序的参数快照
     */
    public List<String> getSortedParams() {
        return sortedParams;
    }

    /**
     * 返回执行耗时，供 Bean 调用方使用。
     *
     * @return 执行耗时，单位为纳秒
     */
    public long getElapsedNanos() {
        return elapsedNanos;
    }

    /**
     * 返回执行异常，供 Bean 调用方使用。
     *
     * @return 执行异常；成功时为空
     */
    public Throwable getError() {
        return error;
    }

    /**
     * 按全部观测字段判断值相等，保证跨版本去重语义一致。
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SqlObservation)) {
            return false;
        }
        SqlObservation that = (SqlObservation) o;
        return elapsedNanos == that.elapsedNanos
                && Objects.equals(statementId, that.statementId)
                && Objects.equals(sql, that.sql)
                && Objects.equals(sortedParams, that.sortedParams)
                && Objects.equals(error, that.error);
    }

    /**
     * 按 record 组件顺序计算哈希，引用字段允许为空。
     */
    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Objects.hashCode(statementId);
        result = 31 * result + Objects.hashCode(sql);
        result = 31 * result + Objects.hashCode(sortedParams);
        result = 31 * result + Long.hashCode(elapsedNanos);
        return 31 * result + Objects.hashCode(error);
    }

    /**
     * 返回与其他版本 record 相同的观测文本。
     */
    @Override
    public String toString() {
        return "SqlObservation[statementId=" + statementId + ", sql=" + sql
                + ", sortedParams=" + sortedParams + ", elapsedNanos=" + elapsedNanos
                + ", error=" + error + ']';
    }
}
