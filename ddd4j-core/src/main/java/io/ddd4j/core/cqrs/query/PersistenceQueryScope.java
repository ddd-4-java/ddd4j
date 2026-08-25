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
import io.ddd4j.kit.lang.CollKit;
import io.ddd4j.kit.text.StrPool;

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
 * @param <M> 聚合根类型
 * @param <P> 持久化对象类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
public final class PersistenceQueryScope<M extends AggregateRoot<?>, P> {

    private final Query<M> query;
    private final Class<P> persistenceType;

    PersistenceQueryScope(Query<M> query, Class<P> persistenceType) {
        this.query = Objects.requireNonNull(query, "query must not be null");
        this.persistenceType = Objects.requireNonNull(persistenceType, "persistenceType must not be null");
    }

    public PersistenceQueryScope<M, P> eq(SFunction<P, ?> property, Object value) {
        return condition(true, property, StrPool.EQUALS, value);
    }

    public PersistenceQueryScope<M, P> eq(boolean condition, SFunction<P, ?> property, Object value) {
        return condition(condition, property, StrPool.EQUALS, value);
    }

    public PersistenceQueryScope<M, P> ne(SFunction<P, ?> property, Object value) {
        return condition(true, property, StrPool.NOT_EQUALS, value);
    }

    public PersistenceQueryScope<M, P> like(SFunction<P, ?> property, Object value) {
        return condition(true, property, StrPool.LIKE, value);
    }

    public PersistenceQueryScope<M, P> likeLeft(SFunction<P, ?> property, Object value) {
        return condition(true, property, StrPool.LIKE_LEFT, value);
    }

    public PersistenceQueryScope<M, P> likeRight(SFunction<P, ?> property, Object value) {
        return condition(true, property, StrPool.LIKE_RIGHT, value);
    }

    public PersistenceQueryScope<M, P> notLike(SFunction<P, ?> property, Object value) {
        return condition(true, property, StrPool.NOT_LIKE, value);
    }

    public PersistenceQueryScope<M, P> gt(SFunction<P, ?> property, Object value) {
        return condition(true, property, StrPool.GT, value);
    }

    public PersistenceQueryScope<M, P> ge(SFunction<P, ?> property, Object value) {
        return condition(true, property, StrPool.GE, value);
    }

    public PersistenceQueryScope<M, P> lt(SFunction<P, ?> property, Object value) {
        return condition(true, property, StrPool.LT, value);
    }

    public PersistenceQueryScope<M, P> le(SFunction<P, ?> property, Object value) {
        return condition(true, property, StrPool.LE, value);
    }

    public PersistenceQueryScope<M, P> between(SFunction<P, ?> property, Object start, Object end) {
        condition(Objects.nonNull(start), property, StrPool.GE, start);
        return condition(Objects.nonNull(end), property, StrPool.LE, end);
    }

    public PersistenceQueryScope<M, P> in(SFunction<P, ?> property, Collection<?> values) {
        return condition(CollKit.isNotEmpty(values), property, StrPool.IN,
                Objects.isNull(values) ? null : new ArrayList<>(values));
    }

    public PersistenceQueryScope<M, P> notIn(SFunction<P, ?> property, Collection<?> values) {
        return condition(CollKit.isNotEmpty(values), property, StrPool.NOT_IN,
                Objects.isNull(values) ? null : new ArrayList<>(values));
    }

    public PersistenceQueryScope<M, P> isNull(SFunction<P, ?> property) {
        return condition(true, property, StrPool.IS_NULL, null);
    }

    public PersistenceQueryScope<M, P> isNotNull(SFunction<P, ?> property) {
        return condition(true, property, StrPool.IS_NOT_NULL, null);
    }

    public PersistenceQueryScope<M, P> orderByAsc(SFunction<P, ?> property) {
        query.addOrderBy(reference(property), StrPool.ASC);
        return this;
    }

    public PersistenceQueryScope<M, P> orderByDesc(SFunction<P, ?> property) {
        query.addOrderBy(reference(property), StrPool.DESC);
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

    public List<M> list() {
        return query.list();
    }

    public Page<M> page() {
        return query.page();
    }

    public M one() {
        return query.one();
    }

    public Optional<M> oneOpt() {
        return query.oneOpt();
    }

    public long count() {
        return query.count();
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
