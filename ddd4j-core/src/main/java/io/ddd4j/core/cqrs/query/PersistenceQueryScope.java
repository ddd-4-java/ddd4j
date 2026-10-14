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
import java.util.Optional;
import java.util.Objects;
import java.util.List;
import java.util.Collection;
import java.util.ArrayList;
import io.ddd4j.kit.lang.SFunction;

import io.ddd4j.core.api.Page;
import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.kit.lang.CollKit;
import io.ddd4j.kit.text.StrPool;

import java.util.*;

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

    /**
     * 添加持久化对象属性的等于条件。
     *
     * @param property 持久化对象属性引用
     * @param value 比较值
     * @return 当前持久化查询作用域，供链式调用
     */
    public PersistenceQueryScope<M, P> eq(SFunction<P, ?> property, Object value) {
        return condition(true, property, StrPool.EQUALS, value);
    }

    /**
     * 按条件添加持久化对象属性的等于条件。
     *
     * @param condition 为 true 时才添加条件
     * @param property 持久化对象属性引用
     * @param value 比较值
     * @return 当前持久化查询作用域，供链式调用
     */
    public PersistenceQueryScope<M, P> eq(boolean condition, SFunction<P, ?> property, Object value) {
        return condition(condition, property, StrPool.EQUALS, value);
    }

    /**
     * 添加持久化对象属性的不等于条件。
     *
     * @param property 持久化对象属性引用
     * @param value 比较值
     * @return 当前持久化查询作用域，供链式调用
     */
    public PersistenceQueryScope<M, P> ne(SFunction<P, ?> property, Object value) {
        return condition(true, property, StrPool.NOT_EQUALS, value);
    }

    /**
     * 添加持久化对象属性的模糊匹配条件。
     *
     * @param property 持久化对象属性引用
     * @param value 比较值
     * @return 当前持久化查询作用域，供链式调用
     */
    public PersistenceQueryScope<M, P> like(SFunction<P, ?> property, Object value) {
        return condition(true, property, StrPool.LIKE, value);
    }

    /**
     * 添加持久化对象属性的左侧模糊匹配条件。
     *
     * @param property 持久化对象属性引用
     * @param value 比较值
     * @return 当前持久化查询作用域，供链式调用
     */
    public PersistenceQueryScope<M, P> likeLeft(SFunction<P, ?> property, Object value) {
        return condition(true, property, StrPool.LIKE_LEFT, value);
    }

    /**
     * 添加持久化对象属性的右侧模糊匹配条件。
     *
     * @param property 持久化对象属性引用
     * @param value 比较值
     * @return 当前持久化查询作用域，供链式调用
     */
    public PersistenceQueryScope<M, P> likeRight(SFunction<P, ?> property, Object value) {
        return condition(true, property, StrPool.LIKE_RIGHT, value);
    }

    /**
     * 添加持久化对象属性的非模糊匹配条件。
     *
     * @param property 持久化对象属性引用
     * @param value 比较值
     * @return 当前持久化查询作用域，供链式调用
     */
    public PersistenceQueryScope<M, P> notLike(SFunction<P, ?> property, Object value) {
        return condition(true, property, StrPool.NOT_LIKE, value);
    }

    /**
     * 添加持久化对象属性的大于条件。
     *
     * @param property 持久化对象属性引用
     * @param value 比较值
     * @return 当前持久化查询作用域，供链式调用
     */
    public PersistenceQueryScope<M, P> gt(SFunction<P, ?> property, Object value) {
        return condition(true, property, StrPool.GT, value);
    }

    /**
     * 添加持久化对象属性的大于或等于条件。
     *
     * @param property 持久化对象属性引用
     * @param value 比较值
     * @return 当前持久化查询作用域，供链式调用
     */
    public PersistenceQueryScope<M, P> ge(SFunction<P, ?> property, Object value) {
        return condition(true, property, StrPool.GE, value);
    }

    /**
     * 添加持久化对象属性的小于条件。
     *
     * @param property 持久化对象属性引用
     * @param value 比较值
     * @return 当前持久化查询作用域，供链式调用
     */
    public PersistenceQueryScope<M, P> lt(SFunction<P, ?> property, Object value) {
        return condition(true, property, StrPool.LT, value);
    }

    /**
     * 添加持久化对象属性的小于或等于条件。
     *
     * @param property 持久化对象属性引用
     * @param value 比较值
     * @return 当前持久化查询作用域，供链式调用
     */
    public PersistenceQueryScope<M, P> le(SFunction<P, ?> property, Object value) {
        return condition(true, property, StrPool.LE, value);
    }

    /**
     * 添加持久化对象属性的范围条件。
     *
     * @param property 持久化对象属性引用
     * @param start 范围下界，null 时忽略
     * @param end 范围上界，null 时忽略
     * @return 当前持久化查询作用域，供链式调用
     */
    public PersistenceQueryScope<M, P> between(SFunction<P, ?> property, Object start, Object end) {
        condition(Objects.nonNull(start), property, StrPool.GE, start);
        return condition(Objects.nonNull(end), property, StrPool.LE, end);
    }

    /**
     * 添加持久化对象属性的集合包含条件。
     *
     * @param property 持久化对象属性引用
     * @param values 候选值集合；为空时忽略条件
     * @return 当前持久化查询作用域，供链式调用
     */
    public PersistenceQueryScope<M, P> in(SFunction<P, ?> property, Collection<?> values) {
        return condition(CollKit.isNotEmpty(values), property, StrPool.IN,
                Objects.isNull(values) ? null : new ArrayList<>(values));
    }

    /**
     * 添加持久化对象属性的集合排除条件。
     *
     * @param property 持久化对象属性引用
     * @param values 候选值集合；为空时忽略条件
     * @return 当前持久化查询作用域，供链式调用
     */
    public PersistenceQueryScope<M, P> notIn(SFunction<P, ?> property, Collection<?> values) {
        return condition(CollKit.isNotEmpty(values), property, StrPool.NOT_IN,
                Objects.isNull(values) ? null : new ArrayList<>(values));
    }

    /**
     * 添加持久化对象属性的为空条件。
     *
     * @param property 持久化对象属性引用
     * @return 当前持久化查询作用域，供链式调用
     */
    public PersistenceQueryScope<M, P> isNull(SFunction<P, ?> property) {
        return condition(true, property, StrPool.IS_NULL, null);
    }

    /**
     * 添加持久化对象属性的非空条件。
     *
     * @param property 持久化对象属性引用
     * @return 当前持久化查询作用域，供链式调用
     */
    public PersistenceQueryScope<M, P> isNotNull(SFunction<P, ?> property) {
        return condition(true, property, StrPool.IS_NOT_NULL, null);
    }

    /**
     * 添加持久化对象属性的升序排序条件。
     *
     * @param property 持久化对象属性引用
     * @return 当前持久化查询作用域，供链式调用
     */
    public PersistenceQueryScope<M, P> orderByAsc(SFunction<P, ?> property) {
        query.addOrderBy(reference(property), StrPool.ASC);
        return this;
    }

    /**
     * 添加持久化对象属性的降序排序条件。
     *
     * @param property 持久化对象属性引用
     * @return 当前持久化查询作用域，供链式调用
     */
    public PersistenceQueryScope<M, P> orderByDesc(SFunction<P, ?> property) {
        query.addOrderBy(reference(property), StrPool.DESC);
        return this;
    }

    /**
     * 设置当前页码。
     *
     * @param current 当前页码，从 1 开始
     * @return 当前持久化查询作用域，供链式调用
     */
    public PersistenceQueryScope<M, P> current(long current) {
        query.current(current);
        return this;
    }

    /**
     * 设置每页条数。
     *
     * @param size 每页条数，-1 表示不分页
     * @return 当前持久化查询作用域，供链式调用
     */
    public PersistenceQueryScope<M, P> size(long size) {
        query.size(size);
        return this;
    }

    /**
     * 忽略租户隔离，仅用于获授权的跨租户管理查询。
     *
     * @return 当前持久化查询作用域，供链式调用
     */
    public PersistenceQueryScope<M, P> ignoreTenantId() {
        query.ignoreTenantId();
        return this;
    }

    /**
     * 退出持久化对象属性作用域，返回原领域查询。
     *
     * @return 原领域查询
     */
    public Query<M> domain() {
        return query;
    }

    /**
     * 执行领域模型列表查询。
     *
     * @return 匹配的领域模型列表
     */
    public List<M> list() {
        return query.list();
    }

    /**
     * 执行领域模型分页查询。
     *
     * @return 分页查询结果
     */
    public Page<M> page() {
        return query.page();
    }

    /**
     * 查询第一条匹配的领域模型。
     *
     * @return 第一条领域模型，未匹配时为 null
     */
    public M one() {
        return query.one();
    }

    /**
     * 以 Optional 返回第一条匹配的领域模型。
     *
     * @return 第一条领域模型的 Optional
     */
    public Optional<M> oneOpt() {
        return query.oneOpt();
    }

    /**
     * 统计匹配的领域模型数。
     *
     * @return 匹配记录数
     */
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
