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

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

/**
 * MyBatis-Plus SQL 执行观测数据。
 */

public final class SqlObservation {

    private static final long serialVersionUID = 0L;

    private final String statementId;

    private final String sql;

    private final long elapsedNanos;

    private final Throwable error;

    public long elapsedMillis() {
        return elapsedNanos / 1_000_000L;
    }

    public long getElapsedNanos() {
        return elapsedNanos;
    }

    public Throwable getError() {
        return error;
    }

    public String getSql() {
        return sql;
    }

    public String getStatementId() {
        return statementId;
    }

    /**
 * @param statementId MappedStatement 标识
 * @param sql 已执行的 SQL
 * @param elapsedNanos 耗时，单位为纳秒
 * @param error 执行异常，可为空
 */

    @JsonCreator()
    public SqlObservation(@JsonProperty("statementId") String statementId, @JsonProperty("sql") String sql, @JsonProperty("elapsedNanos") long elapsedNanos, @JsonProperty("error") Throwable error) {
        this.statementId = statementId;
        this.sql = sql;
        this.elapsedNanos = elapsedNanos;
        this.error = error;
    }

    @JsonProperty("statementId")
    public String statementId() {
        return statementId;
    }

    @JsonProperty("sql")
    public String sql() {
        return sql;
    }

    @JsonProperty("elapsedNanos")
    public long elapsedNanos() {
        return elapsedNanos;
    }

    @JsonProperty("error")
    public Throwable error() {
        return error;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (Objects.isNull(obj) || getClass() != obj.getClass()) {
            return false;
        }
        SqlObservation other = (SqlObservation) obj;
        return Objects.equals(this.statementId, other.statementId) && Objects.equals(this.sql, other.sql) && this.elapsedNanos == other.elapsedNanos && Objects.equals(this.error, other.error);
    }

    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Objects.hashCode(statementId);
        result = 31 * result + Objects.hashCode(sql);
        result = 31 * result + Long.hashCode(elapsedNanos);
        result = 31 * result + Objects.hashCode(error);
        return result;
    }

    @Override
    public String toString() {
        return "SqlObservation[statementId=" + statementId + ", sql=" + sql + ", elapsedNanos=" + elapsedNanos + ", error=" + error + "]";
    }
}
