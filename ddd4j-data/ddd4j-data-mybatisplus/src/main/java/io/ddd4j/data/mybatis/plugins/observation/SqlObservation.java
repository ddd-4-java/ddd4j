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

import java.util.Objects;

/**
 * MyBatis-Plus SQL 执行观测数据。
 *
 * <p>{@code statementId} — MappedStatement 标识
 * <p>{@code sql} — 已执行的 SQL
 * <p>{@code elapsedNanos} — 耗时，单位为纳秒
 * <p>{@code error} — 执行异常，可为空
 */
public final class SqlObservation {

    /** \u6267\u884c SQL \u5bf9\u5e94\u7684 MyBatis \u6620\u5c04\u8bed\u53e5\u6807\u8bc6\u3002 */
    private final String statementId;
    /** \u672c\u6b21 SQL \u6267\u884c\u7684\u8bed\u53e5\u6587\u672c\u3002 */
    private final String sql;
    /** SQL \u6267\u884c\u8017\u65f6\uff0c\u5355\u4f4d\u4e3a\u7eb3\u79d2\u3002 */
    private final long elapsedNanos;
    /** SQL \u6267\u884c\u5931\u8d25\u65f6\u8bb0\u5f55\u7684\u5f02\u5e38\u3002 */
    private final Throwable error;

    /**
     * MyBatis-Plus SQL 执行观测数据。
     *
     * @param statementId  MappedStatement 标识
     * @param sql          已执行的 SQL
     * @param elapsedNanos 耗时，单位为纳秒
     * @param error        执行异常，可为空
     */
    @JsonCreator
    public SqlObservation(@JsonProperty("statementId") String statementId,
                          @JsonProperty("sql") String sql,
                          @JsonProperty("elapsedNanos") long elapsedNanos,
                          @JsonProperty("error") Throwable error) {
        this.statementId = statementId;
        this.sql = sql;
        this.elapsedNanos = elapsedNanos;
        this.error = error;
    }

    /**
     * @return SQL 执行耗时，单位为毫秒
     */
    public long elapsedMillis() {
        return elapsedNanos / 1_000_000L;
    }

    /**
     * @return MyBatis 语句标识
     */
    @JsonProperty("statementId")
    public String statementId() {
        return statementId;
    }

    /**
     * @return 已执行的 SQL
     */
    @JsonProperty("sql")
    public String sql() {
        return sql;
    }

    /**
     * @return SQL 执行耗时，单位为纳秒
     */
    @JsonProperty("elapsedNanos")
    public long elapsedNanos() {
        return elapsedNanos;
    }

    /**
     * @return 执行异常；成功时为空
     */
    @JsonProperty("error")
    public Throwable error() {
        return error;
    }

    /**
     * @return MyBatis 语句标识
     */
    public String getStatementId() {
        return statementId;
    }

    /**
     * @return 已执行的 SQL
     */
    public String getSql() {
        return sql;
    }

    /**
     * @return SQL 执行耗时，单位为纳秒
     */
    public long getElapsedNanos() {
        return elapsedNanos;
    }

    /**
     * @return 执行异常；成功时为空
     */
    public Throwable getError() {
        return error;
    }

    /**
     * 根据<code>statementId</code>、<code>sql</code>、<code>error</code>、<code>elapsedNanos</code>比较对象的值是否相等。
     *
     * @param o 用来比较的另一个对象，允许为空
     * @return 对象满足此实现的相等条件时为真
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
        return Objects.equals(statementId, that.statementId)
                && Objects.equals(sql, that.sql)
                && elapsedNanos == that.elapsedNanos
                && Objects.equals(error, that.error);
    }

    /**
     * 计算与对象相等条件一致的哈希值。
     *
     * @return 由参与相等比较的状态计算出的哈希值
     */
    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Objects.hashCode(statementId);
        result = 31 * result + Objects.hashCode(sql);
        result = 31 * result + Long.hashCode(elapsedNanos);
        result = 31 * result + Objects.hashCode(error);
        return result;
    }

    /**
     * 生成用于调试和日志的对象描述，包含<code>statementId</code>、<code>sql</code>、<code>elapsedNanos</code>、<code>error</code>。
     *
     * @return 表示当前对象状态的字符串
     */
    @Override
    public String toString() {
        return "SqlObservation[statementId=" + statementId + ", sql=" + sql + ", elapsedNanos=" + elapsedNanos + ", error=" + error + "]";
    }
}
