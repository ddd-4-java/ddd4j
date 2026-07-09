package io.ddd4j.data.mybatis.query;

import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 原生 MyBatis 轨道的充血 Query 基类（零 MyBatis-Plus 依赖）。
 *
 * <p>继承 ddd4j-core 的 {@link Query}，保留字段后缀自动映射的充血查询能力
 * （如 {@code nameLike} → 自动 LIKE、{@code statusNot} → 自动 NE），
 * 同时支持手动 SQL 条件构建（链式 API），方便业务方在复杂查询场景使用。
 *
 * <h3>使用方式</h3>
 *
 * <p><b>方式一：字段后缀自动映射（cloud-agents 风格，零改动迁移）</b>
 * <pre>{@code
 * public class AgentQuery extends AbstractMybatisQuery<Agent> {
 *     private String nameLike;    // 自动 LIKE
 *     private String agentType;   // 自动 EQ
 *     private Integer status;     // 自动 EQ
 * }
 * List<Agent> list = new AgentQuery().list();
 * }</pre>
 *
 * <p><b>方式二：手动条件构建</b>
 * <pre>{@code
 * AgentQuery query = new AgentQuery();
 * query.eq("status", 1)
 *      .like("name", "测试")
 *      .ge("createTime", startTime)
 *      .orderBy("createTime_DESC");
 * List<Agent> list = query.list();
 * }</pre>
 *
 * @param <T> 聚合根类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
public abstract class AbstractMybatisQuery<T> extends Query<T> {

    private static final long serialVersionUID = 1L;

    /** 手动构建的 SQL 条件列表 */
    private transient List<SqlCondition> sqlConditions;

    /** 手动设置的 ORDER BY */
    private transient String manualOrderBy;

    // ========================= 条件构建入口 =========================

    public <Q extends AbstractMybatisQuery<T>> Q eq(String column, Object value) {
        addCondition(column, "=", value);
        return (Q) this;
    }

    public <Q extends AbstractMybatisQuery<T>> Q ne(String column, Object value) {
        addCondition(column, "<>", value);
        return (Q) this;
    }

    public <Q extends AbstractMybatisQuery<T>> Q like(String column, Object value) {
        addCondition(column, "LIKE", value);
        return (Q) this;
    }

    public <Q extends AbstractMybatisQuery<T>> Q likeLeft(String column, Object value) {
        addCondition(column, "LIKE_LEFT", value);
        return (Q) this;
    }

    public <Q extends AbstractMybatisQuery<T>> Q likeRight(String column, Object value) {
        addCondition(column, "LIKE_RIGHT", value);
        return (Q) this;
    }

    public <Q extends AbstractMybatisQuery<T>> Q gt(String column, Object value) {
        addCondition(column, ">", value);
        return (Q) this;
    }

    public <Q extends AbstractMybatisQuery<T>> Q ge(String column, Object value) {
        addCondition(column, ">=", value);
        return (Q) this;
    }

    public <Q extends AbstractMybatisQuery<T>> Q lt(String column, Object value) {
        addCondition(column, "<", value);
        return (Q) this;
    }

    public <Q extends AbstractMybatisQuery<T>> Q le(String column, Object value) {
        addCondition(column, "<=", value);
        return (Q) this;
    }

    public <Q extends AbstractMybatisQuery<T>> Q in(String column, Collection<?> values) {
        if (values != null && !values.isEmpty()) {
            addCondition(column, "IN", values);
        }
        return (Q) this;
    }

    public <Q extends AbstractMybatisQuery<T>> Q notIn(String column, Collection<?> values) {
        if (values != null && !values.isEmpty()) {
            addCondition(column, "NOT IN", values);
        }
        return (Q) this;
    }

    public <Q extends AbstractMybatisQuery<T>> Q isNull(String column) {
        addCondition(column, "IS NULL", null);
        return (Q) this;
    }

    public <Q extends AbstractMybatisQuery<T>> Q isNotNull(String column) {
        addCondition(column, "IS NOT NULL", null);
        return (Q) this;
    }

    public <Q extends AbstractMybatisQuery<T>> Q orderBy(String column) {
        this.manualOrderBy = column;
        return (Q) this;
    }

    // ========================= 内部条件构建 =========================

    private void addCondition(String column, String operator, Object value) {
        if (sqlConditions == null) {
            sqlConditions = new ArrayList<>();
        }
        sqlConditions.add(new SqlCondition(column, operator, value));
    }

    /**
     * 是否有手动构建的条件。
     */
    public boolean hasManualConditions() {
        return sqlConditions != null && !sqlConditions.isEmpty();
    }

    /**
     * 获取手动构建的条件列表。
     */
    public List<SqlCondition> getSqlConditions() {
        return sqlConditions != null ? sqlConditions : Collections.emptyList();
    }

    /**
     * 获取手动设置的 ORDER BY。
     */
    public String getManualOrderBy() {
        return manualOrderBy;
    }

    /**
     * SQL 条件记录。
     */
    public record SqlCondition(String column, String operator, Object value) implements Serializable {
    }
}
