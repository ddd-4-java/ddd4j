package io.ddd4j.core.cqrs.query;

import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.core.util.SFunction;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** ORM 无关的 Lambda 查询条件模型。 */
@SuppressWarnings("unchecked")
public abstract class Query<M extends AggregateRoot<?>> implements Serializable {
    private static final long serialVersionUID = 1L;
    private long current = 1L;
    private long size = 10L;
    private boolean ignoreTenantId;
    private final List<LambdaCondition> conditions = new ArrayList<LambdaCondition>();
    private final List<LambdaCondition> orderByConditions = new ArrayList<LambdaCondition>();
    public <Q extends Query<M>> Q eq(SFunction<M, ?> column, Object value) {
        return addCondition(column, "=", value);
    }

    public <Q extends Query<M>> Q ne(SFunction<M, ?> column, Object value) {
        return addCondition(column, "<>", value);
    }

    public <Q extends Query<M>> Q like(SFunction<M, ?> column, Object value) {
        return addCondition(column, "LIKE", value);
    }

    public <Q extends Query<M>> Q gt(SFunction<M, ?> column, Object value) {
        return addCondition(column, ">", value);
    }

    public <Q extends Query<M>> Q ge(SFunction<M, ?> column, Object value) {
        return addCondition(column, ">=", value);
    }

    public <Q extends Query<M>> Q lt(SFunction<M, ?> column, Object value) {
        return addCondition(column, "<", value);
    }

    public <Q extends Query<M>> Q le(SFunction<M, ?> column, Object value) {
        return addCondition(column, "<=", value);
    }

    public <Q extends Query<M>> Q orderByAsc(SFunction<M, ?> column) {
        orderByConditions.add(LambdaCondition.asc(PropertyRef.domain(column)));
        return (Q) this;
    }

    public <Q extends Query<M>> Q orderByDesc(SFunction<M, ?> column) {
        orderByConditions.add(LambdaCondition.desc(PropertyRef.domain(column)));
        return (Q) this;
    }

    public <Q extends Query<M>> Q current(long current) {
        if (current < 1) {
            throw new IllegalArgumentException("current must be positive");
        }
        this.current = current;
        return (Q) this;
    }

    public <Q extends Query<M>> Q size(long size) {
        if (size == 0 || size < -1) {
            throw new IllegalArgumentException("size must be positive or -1");
        }
        this.size = size;
        return (Q) this;
    }

    public <Q extends Query<M>> Q ignoreTenantId() {
        this.ignoreTenantId = true;
        return (Q) this;
    }

    public long getCurrent() {
        return current;
    }

    public long getSize() {
        return size;
    }

    public boolean isIgnoreTenantId() {
        return ignoreTenantId;
    }

    public List<LambdaCondition> getConditions() {
        return Collections.unmodifiableList(conditions);
    }

    public List<LambdaCondition> getOrderByConditions() {
        return Collections.unmodifiableList(orderByConditions);
    }

    private <Q extends Query<M>> Q addCondition(SFunction<M, ?> column, String operator, Object value) {
        conditions.add(new LambdaCondition(PropertyRef.domain(column), operator, value));
        return (Q) this;
    }
}
