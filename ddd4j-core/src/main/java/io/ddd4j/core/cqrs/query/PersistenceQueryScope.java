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
package io.ddd4j.core.cqrs.query;

import io.ddd4j.core.api.Page;
import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.core.util.SFunction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 显式持久化对象查询作用域。
 *
 * <p>该作用域只负责构造 ORM 无关的查询 AST，不持有 ORM Wrapper，也不把 PO
 * 元数据写入领域模型。Repository 执行时必须再次校验 PO 类型。
 *
 * <p>1.0.x（JDK8）实现说明：3.0.x 版本依赖 ddd4j-kit 的 StrPool/CollKit，
 * 此处以私有常量与纯 JDK8 判空替代（操作符取值与 3.0.x StrPool 完全一致）。
 *
 * @param <M> 聚合根类型
 * @param <P> 持久化对象类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
public final class PersistenceQueryScope<M extends AggregateRoot<?>, P> {

    // ==================== 查询操作符（与 3.0.x kit StrPool 取值一致） ====================
    private static final String EQUALS = "=";
    private static final String NOT_EQUALS = "<>";
    private static final String LIKE = "LIKE";
    private static final String LIKE_LEFT = "LIKE_LEFT";
    private static final String LIKE_RIGHT = "LIKE_RIGHT";
    private static final String NOT_LIKE = "NOT_LIKE";
    private static final String GT = ">";
    private static final String GE = ">=";
    private static final String LT = "<";
    private static final String LE = "<=";
    private static final String IN = "IN";
    private static final String NOT_IN = "NOT_IN";
    private static final String IS_NULL = "IS_NULL";
    private static final String IS_NOT_NULL = "IS_NOT_NULL";
    private static final String ASC = "ASC";
    private static final String DESC = "DESC";

    private final Query<M> query;
    private final Class<P> persistenceType;

    PersistenceQueryScope(Query<M> query, Class<P> persistenceType) {
        this.query = Objects.requireNonNull(query, "query must not be null");
        this.persistenceType = Objects.requireNonNull(persistenceType, "persistenceType must not be null");
    }

    public PersistenceQueryScope<M, P> eq(SFunction<P, ?> property, Object value) {
        return condition(true, property, EQUALS, value);
    }

    public PersistenceQueryScope<M, P> eq(boolean condition, SFunction<P, ?> property, Object value) {
        return condition(condition, property, EQUALS, value);
    }

    public PersistenceQueryScope<M, P> ne(SFunction<P, ?> property, Object value) {
        return condition(true, property, NOT_EQUALS, value);
    }

    public PersistenceQueryScope<M, P> like(SFunction<P, ?> property, Object value) {
        return condition(true, property, LIKE, value);
    }

    public PersistenceQueryScope<M, P> likeLeft(SFunction<P, ?> property, Object value) {
        return condition(true, property, LIKE_LEFT, value);
    }

    public PersistenceQueryScope<M, P> likeRight(SFunction<P, ?> property, Object value) {
        return condition(true, property, LIKE_RIGHT, value);
    }

    public PersistenceQueryScope<M, P> notLike(SFunction<P, ?> property, Object value) {
        return condition(true, property, NOT_LIKE, value);
    }

    public PersistenceQueryScope<M, P> gt(SFunction<P, ?> property, Object value) {
        return condition(true, property, GT, value);
    }

    public PersistenceQueryScope<M, P> ge(SFunction<P, ?> property, Object value) {
        return condition(true, property, GE, value);
    }

    public PersistenceQueryScope<M, P> lt(SFunction<P, ?> property, Object value) {
        return condition(true, property, LT, value);
    }

    public PersistenceQueryScope<M, P> le(SFunction<P, ?> property, Object value) {
        return condition(true, property, LE, value);
    }

    public PersistenceQueryScope<M, P> between(SFunction<P, ?> property, Object start, Object end) {
        condition(Objects.nonNull(start), property, GE, start);
        return condition(Objects.nonNull(end), property, LE, end);
    }

    public PersistenceQueryScope<M, P> in(SFunction<P, ?> property, Collection<?> values) {
        return condition(isNotEmpty(values), property, IN,
                Objects.isNull(values) ? null : new ArrayList<Object>(values));
    }

    public PersistenceQueryScope<M, P> notIn(SFunction<P, ?> property, Collection<?> values) {
        return condition(isNotEmpty(values), property, NOT_IN,
                Objects.isNull(values) ? null : new ArrayList<Object>(values));
    }

    public PersistenceQueryScope<M, P> isNull(SFunction<P, ?> property) {
        return condition(true, property, IS_NULL, null);
    }

    public PersistenceQueryScope<M, P> isNotNull(SFunction<P, ?> property) {
        return condition(true, property, IS_NOT_NULL, null);
    }

    public PersistenceQueryScope<M, P> orderByAsc(SFunction<P, ?> property) {
        query.addOrderBy(reference(property), ASC);
        return this;
    }

    public PersistenceQueryScope<M, P> orderByDesc(SFunction<P, ?> property) {
        query.addOrderBy(reference(property), DESC);
        return this;
    }

    public PersistenceQueryScope<M, P> current(long current) {
        query.current(current);
        return this;
    }

    public PersistenceQueryScope<M, P> size(long size) {
        query.size(size);
        return this;
    }

    public PersistenceQueryScope<M, P> ignoreTenantId() {
        query.ignoreTenantId();
        return this;
    }

    public Query<M> domain() {
        return query;
    }

    // 说明：3.0.x 版本另有 list()/page()/one()/oneOpt()/count() 执行方法，依赖 3.0.x Query 的
    // Repository 执行绑定（674 行完整实现）。1.0.x Query 为纯条件模型（无执行绑定），
    // 故本契约仅移植查询构造 DSL；执行请通过 domain() 取得 Query 后由业务仓储完成。

    private static boolean isNotEmpty(Collection<?> collection) {
        return Objects.nonNull(collection) && !collection.isEmpty();
    }

    private PersistenceQueryScope<M, P> condition(boolean condition, SFunction<P, ?> property,
                                                   String operator, Object value) {
        query.addCondition(condition, reference(property), operator, value);
        return this;
    }

    private PropertyRef reference(SFunction<P, ?> property) {
        return PropertyRef.persistence(persistenceType, property);
    }
}
