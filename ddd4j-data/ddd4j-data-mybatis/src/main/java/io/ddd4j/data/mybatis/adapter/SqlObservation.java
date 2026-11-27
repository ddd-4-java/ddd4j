package io.ddd4j.data.mybatis.adapter;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

import java.util.List;

/**
 * 原生 MyBatis SQL 执行观测数据。
 */

public final class SqlObservation {

    /** Java 序列化版本标识。 */
    private static final long serialVersionUID = 0L;

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
     * 按 <code>elapsed Millis</code> 的契约计算并返回结果。
     *
     * @return 返回表达式 <code>elapsedNanos / 1_000_000L</code>
     */
    public long elapsedMillis() {
        return elapsedNanos / 1_000_000L;
    }

    /**
     * 初始化 <code>SqlObservation</code> 使用的 <code>statementId</code>、<code>sql</code>、<code>sortedParams</code>、<code>elapsedNanos</code>、<code>error</code>。
     *
     * @param statementId MappedStatement \u6807\u8bc6
     * @param sql \u5df2\u6267\u884c\u7684 SQL
     * @param sortedParams \u5df2\u6392\u5e8f\u7684\u53c2\u6570\u5feb\u7167
     * @param elapsedNanos \u8017\u65f6\uff0c\u5355\u4f4d\u4e3a\u7eb3\u79d2
     * @param error \u6267\u884c\u5f02\u5e38\uff0c\u53ef\u4e3a\u7a7a
     */

    @JsonCreator()
    public SqlObservation(@JsonProperty("statementId") String statementId, @JsonProperty("sql") String sql, @JsonProperty("sortedParams") List<String> sortedParams, @JsonProperty("elapsedNanos") long elapsedNanos, @JsonProperty("error") Throwable error) {
        this.statementId = statementId;
        this.sql = sql;
        this.sortedParams = sortedParams;
        this.elapsedNanos = elapsedNanos;
        this.error = error;
    }

    /**
     * 读取<code>statementId</code> 的当前值。
     *
     * @return 返回表达式 <code>statementId</code>
     */
    @JsonProperty("statementId")
    public String statementId() {
        return statementId;
    }

    /**
     * 读取<code>sql</code> 的当前值。
     *
     * @return 返回表达式 <code>sql</code>
     */
    @JsonProperty("sql")
    public String sql() {
        return sql;
    }

    /**
     * \u8fd4\u56de\u5df2\u6392\u5e8f\u7684\u53c2\u6570\u5feb\u7167\u3002
     * @return \u5df2\u6392\u5e8f\u7684\u53c2\u6570\u5feb\u7167
     */
    @JsonProperty("sortedParams")
    public List<String> sortedParams() {
        return sortedParams;
    }

    /**
     * 读取<code>elapsedNanos</code> 的当前值。
     *
     * @return 返回表达式 <code>elapsedNanos</code>
     */
    @JsonProperty("elapsedNanos")
    public long elapsedNanos() {
        return elapsedNanos;
    }

    /**
     * 读取按字段描述的输入校验错误集合。
     *
     * @return 返回表达式 <code>error</code>
     */
    @JsonProperty("error")
    public Throwable error() {
        return error;
    }

    /**
     * 执行 <code>equals</code>，调用 <code>obj.getClass</code>。
     *
     * @param obj 传入此操作的 <code>Object</code> 类型 <code>obj</code>
     * @return 按条件返回 <code>true</code>、<code>false</code>
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (Objects.isNull(obj) || getClass() != obj.getClass()) {
            return false;
        }
        SqlObservation other = (SqlObservation) obj;
        return Objects.equals(this.statementId, other.statementId) && Objects.equals(this.sql, other.sql) && Objects.equals(this.sortedParams, other.sortedParams) && this.elapsedNanos == other.elapsedNanos && Objects.equals(this.error, other.error);
    }

    /**
     * 读取<code>result</code> 的当前值。
     *
     * @return 返回表达式 <code>result</code>
     */
    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Objects.hashCode(statementId);
        result = 31 * result + Objects.hashCode(sql);
        result = 31 * result + Objects.hashCode(sortedParams);
        result = 31 * result + Long.hashCode(elapsedNanos);
        result = 31 * result + Objects.hashCode(error);
        return result;
    }

    /**
     * 按 <code>to String</code> 的契约计算并返回结果。
     *
     * @return 当前操作得到的 <code>String</code> 结果
     */
    @Override
    public String toString() {
        return "SqlObservation[statementId=" + statementId + ", sql=" + sql + ", sortedParams=" + sortedParams + ", elapsedNanos=" + elapsedNanos + ", error=" + error + "]";
    }
}
