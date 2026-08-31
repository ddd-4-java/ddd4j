package io.ddd4j.core.cqrs.query;

import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.core.util.SFunction;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** ORM 无关的 Lambda 查询条件模型。 */
@SuppressWarnings("unchecked")
public abstract class Query<M extends AggregateRoot<?>> implements Serializable {
    private static final long serialVersionUID = 1L;
    private long current = 1L;
    private long size = 10L;
    private boolean ignoreTenantId;
    private final List<LambdaCondition> conditions = new ArrayList<LambdaCondition>();
    private final List<LambdaCondition> orderByConditions = new ArrayList<LambdaCondition>();
    private final List<LambdaCondition> setOperations = new ArrayList<LambdaCondition>();
    public <Q extends Query<M>> Q eq(SFunction<M, ?> column, Object value) {
        return eq(true, column, value);
    }

    public <Q extends Query<M>> Q eq(boolean condition, SFunction<M, ?> column, Object value) {
        return addCondition(condition, column, "=", value);
    }

    public <Q extends Query<M>> Q ne(SFunction<M, ?> column, Object value) {
        return ne(true, column, value);
    }

    public <Q extends Query<M>> Q ne(boolean condition, SFunction<M, ?> column, Object value) {
        return addCondition(condition, column, "<>", value);
    }

    public <Q extends Query<M>> Q like(SFunction<M, ?> column, Object value) {
        return like(true, column, value);
    }

    public <Q extends Query<M>> Q like(boolean condition, SFunction<M, ?> column, Object value) {
        return addCondition(condition, column, "LIKE", value);
    }

    public <Q extends Query<M>> Q likeLeft(SFunction<M, ?> column, Object value) {
        return likeLeft(true, column, value);
    }

    public <Q extends Query<M>> Q likeLeft(boolean condition, SFunction<M, ?> column, Object value) {
        return addCondition(condition, column, "LIKE_LEFT", value);
    }

    public <Q extends Query<M>> Q likeRight(SFunction<M, ?> column, Object value) {
        return likeRight(true, column, value);
    }

    public <Q extends Query<M>> Q likeRight(boolean condition, SFunction<M, ?> column, Object value) {
        return addCondition(condition, column, "LIKE_RIGHT", value);
    }

    public <Q extends Query<M>> Q notLike(SFunction<M, ?> column, Object value) {
        return notLike(true, column, value);
    }

    public <Q extends Query<M>> Q notLike(boolean condition, SFunction<M, ?> column, Object value) {
        return addCondition(condition, column, "NOT_LIKE", value);
    }

    public <Q extends Query<M>> Q gt(SFunction<M, ?> column, Object value) {
        return gt(true, column, value);
    }

    public <Q extends Query<M>> Q gt(boolean condition, SFunction<M, ?> column, Object value) {
        return addCondition(condition, column, ">", value);
    }

    public <Q extends Query<M>> Q ge(SFunction<M, ?> column, Object value) {
        return ge(true, column, value);
    }

    public <Q extends Query<M>> Q ge(boolean condition, SFunction<M, ?> column, Object value) {
        return addCondition(condition, column, ">=", value);
    }

    public <Q extends Query<M>> Q lt(SFunction<M, ?> column, Object value) {
        return lt(true, column, value);
    }

    public <Q extends Query<M>> Q lt(boolean condition, SFunction<M, ?> column, Object value) {
        return addCondition(condition, column, "<", value);
    }

    public <Q extends Query<M>> Q le(SFunction<M, ?> column, Object value) {
        return le(true, column, value);
    }

    public <Q extends Query<M>> Q le(boolean condition, SFunction<M, ?> column, Object value) {
        return addCondition(condition, column, "<=", value);
    }

    public <Q extends Query<M>> Q between(SFunction<M, ?> column, Object start, Object end) {
        return between(true, column, start, end);
    }

    public <Q extends Query<M>> Q between(boolean condition, SFunction<M, ?> column, Object start, Object end) {
        if (condition && Objects.nonNull(start)) {
            ge(column, start);
        }
        if (condition && Objects.nonNull(end)) {
            le(column, end);
        }
        return (Q) this;
    }

    public <Q extends Query<M>> Q in(SFunction<M, ?> column, Collection<?> values) {
        return in(true, column, values);
    }

    public <Q extends Query<M>> Q in(boolean condition, SFunction<M, ?> column, Collection<?> values) {
        return addCollectionCondition(condition, column, "IN", values);
    }

    public <Q extends Query<M>> Q notIn(SFunction<M, ?> column, Collection<?> values) {
        return notIn(true, column, values);
    }

    public <Q extends Query<M>> Q notIn(boolean condition, SFunction<M, ?> column, Collection<?> values) {
        return addCollectionCondition(condition, column, "NOT_IN", values);
    }

    public <Q extends Query<M>> Q isNull(SFunction<M, ?> column) {
        return isNull(true, column);
    }

    public <Q extends Query<M>> Q isNull(boolean condition, SFunction<M, ?> column) {
        return addCondition(condition, column, "IS_NULL", null);
    }

    public <Q extends Query<M>> Q isNotNull(SFunction<M, ?> column) {
        return isNotNull(true, column);
    }

    public <Q extends Query<M>> Q isNotNull(boolean condition, SFunction<M, ?> column) {
        return addCondition(condition, column, "IS_NOT_NULL", null);
    }

    public <Q extends Query<M>> Q orderByAsc(SFunction<M, ?> column) {
        return orderByAsc(true, column);
    }

    public <Q extends Query<M>> Q orderByAsc(boolean condition, SFunction<M, ?> column) {
        if (condition) {
            orderByConditions.add(LambdaCondition.asc(PropertyRef.domain(column)));
        }
        return (Q) this;
    }

    public <Q extends Query<M>> Q orderByDesc(SFunction<M, ?> column) {
        return orderByDesc(true, column);
    }

    public <Q extends Query<M>> Q orderByDesc(boolean condition, SFunction<M, ?> column) {
        if (condition) {
            orderByConditions.add(LambdaCondition.desc(PropertyRef.domain(column)));
        }
        return (Q) this;
    }

    public <Q extends Query<M>> Q set(SFunction<M, ?> column, Object value) {
        return set(true, column, value);
    }

    public <Q extends Query<M>> Q set(boolean condition, SFunction<M, ?> column, Object value) {
        if (condition) {
            setOperations.add(new LambdaCondition(PropertyRef.domain(column), "=", value));
        }
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

    public <Q extends Query<M>> Q size(boolean condition, long size) {
        if (condition) {
            return size(size);
        }
        return (Q) this;
    }

    public <Q extends Query<M>> Q ignoreTenantId() {
        this.ignoreTenantId = true;
        return (Q) this;
    }

    public <Q extends Query<M>> Q ignorePage() {
        return size(-1L);
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

    public List<LambdaCondition> getSetOperations() {
        return Collections.unmodifiableList(setOperations);
    }

    private <Q extends Query<M>> Q addCondition(boolean condition, SFunction<M, ?> column, String operator, Object value) {
        if (condition) {
            conditions.add(new LambdaCondition(PropertyRef.domain(column), operator, value));
        }
        return (Q) this;
    }

    private <Q extends Query<M>> Q addCollectionCondition(boolean condition, SFunction<M, ?> column,
                                                            String operator, Collection<?> values) {
        if (condition && Objects.nonNull(values) && !values.isEmpty()) {
            conditions.add(new LambdaCondition(PropertyRef.domain(column), operator,
                    new ArrayList<Object>(values)));
        }
        return (Q) this;
    }
}
