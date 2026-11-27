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

package io.ddd4j.data.mybatis.plugins.observation;

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

    /** \u6267\u884c SQL \u5bf9\u5e94\u7684 MyBatis \u6620\u5c04\u8bed\u53e5\u6807\u8bc6\u3002 */
    private final String statementId;
    /** \u672c\u6b21 SQL \u6267\u884c\u7684\u8bed\u53e5\u6587\u672c\u3002 */
    private final String sql;
    /** \u6309\u539f\u53c2\u6570\u4f4d\u7f6e\u6392\u5e8f\u7684\u89c2\u6d4b\u53c2\u6570\u3002 */
    private final List<String> sortedParams;
    /** SQL \u6267\u884c\u8017\u65f6\uff0c\u5355\u4f4d\u4e3a\u7eb3\u79d2\u3002 */
    private final long elapsedNanos;
    /** SQL \u6267\u884c\u5931\u8d25\u65f6\u8bb0\u5f55\u7684\u5f02\u5e38\u3002 */
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
     * \u6309\u5168\u90e8\u89c2\u6d4b\u5b57\u6bb5\u5224\u65ad\u503c\u76f8\u7b49\uff0c\u4fdd\u8bc1\u8de8\u7248\u672c\u53bb\u91cd\u8bed\u4e49\u4e00\u81f4\u3002
     *
     * @param o \u5f85\u6bd4\u8f83\u7684\u5bf9\u8c61
     * @return \u53e6\u4e00\u89c2\u6d4b\u5177\u6709\u76f8\u540c\u5b57\u6bb5\u503c\u65f6\u8fd4\u56de true
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
     * \u6309 record \u7ec4\u4ef6\u987a\u5e8f\u8ba1\u7b97\u54c8\u5e0c\uff0c\u5f15\u7528\u5b57\u6bb5\u5141\u8bb8\u4e3a\u7a7a\u3002
     *
     * @return \u6839\u636e\u89c2\u6d4b\u5b57\u6bb5\u8ba1\u7b97\u7684\u6563\u5217\u503c
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
     * \u8fd4\u56de\u4e0e\u5176\u4ed6\u7248\u672c record \u76f8\u540c\u7684\u89c2\u6d4b\u6587\u672c\u3002
     *
     * @return \u7528\u4e8e\u8bca\u65ad\u7684 SQL \u89c2\u6d4b\u5185\u5bb9
     */
    @Override
    public String toString() {
        return "SqlObservation[statementId=" + statementId + ", sql=" + sql
                + ", sortedParams=" + sortedParams + ", elapsedNanos=" + elapsedNanos
                + ", error=" + error + ']';
    }
}
