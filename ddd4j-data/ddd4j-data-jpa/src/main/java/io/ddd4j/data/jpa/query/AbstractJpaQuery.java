package io.ddd4j.data.jpa.query;

import io.ddd4j.core.cqrs.query.Query;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * JPA 轨道的充血 Query 基类（标准 JPA Criteria API）。
 *
 * <p>继承 ddd4j-core 的 {@link Query}，保留字段后缀自动映射的充血查询能力，
 * 同时支持手动 JPA Criteria 条件构建（链式 API），方便业务方在复杂查询场景使用。
 *
 * <h3>使用方式</h3>
 *
 * <p><b>方式一：字段后缀自动映射</b>
 * <pre>{@code
 * public class AgentQuery extends AbstractJpaQuery<Agent> {
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
 *      .ge("createTime", startTime);
 * List<Agent> list = query.list();
 * }</pre>
 *
 * @param <T> 聚合根类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
public abstract class AbstractJpaQuery<T> extends Query<T> {

    private static final long serialVersionUID = 1L;

    /**
     * 手动构建的条件列表
     */
    private transient List<JpaCondition> jpaConditions;

    /**
     * 手动设置的 ORDER BY
     */
    private transient String manualOrderBy;

    // ========================= 条件构建入口 =========================

    public <Q extends AbstractJpaQuery<T>> Q eq(String column, Object value) {
        addCondition(column, "=", value);
        return (Q) this;
    }

    public <Q extends AbstractJpaQuery<T>> Q ne(String column, Object value) {
        addCondition(column, "<>", value);
        return (Q) this;
    }

    public <Q extends AbstractJpaQuery<T>> Q like(String column, Object value) {
        addCondition(column, "LIKE", value);
        return (Q) this;
    }

    public <Q extends AbstractJpaQuery<T>> Q likeLeft(String column, Object value) {
        addCondition(column, "LIKE_LEFT", value);
        return (Q) this;
    }

    public <Q extends AbstractJpaQuery<T>> Q likeRight(String column, Object value) {
        addCondition(column, "LIKE_RIGHT", value);
        return (Q) this;
    }

    public <Q extends AbstractJpaQuery<T>> Q gt(String column, Object value) {
        addCondition(column, ">", value);
        return (Q) this;
    }

    public <Q extends AbstractJpaQuery<T>> Q ge(String column, Object value) {
        addCondition(column, ">=", value);
        return (Q) this;
    }

    public <Q extends AbstractJpaQuery<T>> Q lt(String column, Object value) {
        addCondition(column, "<", value);
        return (Q) this;
    }

    public <Q extends AbstractJpaQuery<T>> Q le(String column, Object value) {
        addCondition(column, "<=", value);
        return (Q) this;
    }

    public <Q extends AbstractJpaQuery<T>> Q in(String column, Collection<?> values) {
        if (values != null && !values.isEmpty()) {
            addCondition(column, "IN", values);
        }
        return (Q) this;
    }

    public <Q extends AbstractJpaQuery<T>> Q notIn(String column, Collection<?> values) {
        if (values != null && !values.isEmpty()) {
            addCondition(column, "NOT IN", values);
        }
        return (Q) this;
    }

    public <Q extends AbstractJpaQuery<T>> Q isNull(String column) {
        addCondition(column, "IS NULL", null);
        return (Q) this;
    }

    public <Q extends AbstractJpaQuery<T>> Q isNotNull(String column) {
        addCondition(column, "IS NOT NULL", null);
        return (Q) this;
    }

    public <Q extends AbstractJpaQuery<T>> Q orderBy(String column) {
        this.manualOrderBy = column;
        return (Q) this;
    }

    // ========================= 内部条件构建 =========================

    private void addCondition(String column, String operator, Object value) {
        if (jpaConditions == null) {
            jpaConditions = new ArrayList<>();
        }
        jpaConditions.add(new JpaCondition(column, operator, value));
    }

    public boolean hasManualConditions() {
        return jpaConditions != null && !jpaConditions.isEmpty();
    }

    public List<JpaCondition> getJpaConditions() {
        return jpaConditions != null ? jpaConditions : Collections.emptyList();
    }

    public String getManualOrderBy() {
        return manualOrderBy;
    }

    /**
     * JPA 条件记录。
     */
    public record JpaCondition(String column, String operator, Object value) implements Serializable {
    }
}
