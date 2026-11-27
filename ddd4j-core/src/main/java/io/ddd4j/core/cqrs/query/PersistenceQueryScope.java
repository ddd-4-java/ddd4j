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

    /** \u63a5\u6536\u6301\u4e45\u5316\u5b57\u6bb5\u67e5\u8be2\u6761\u4ef6\u7684\u539f\u59cb\u9886\u57df\u67e5\u8be2\u3002 */
    private final Query<M> query;
    /** \u5141\u8bb8\u672c\u67e5\u8be2\u4f5c\u7528\u57df\u5f15\u7528\u7684\u6301\u4e45\u5316\u5bf9\u8c61\u7c7b\u578b\u3002 */
    private final Class<P> persistenceType;

    /**
     * \u4e3a\u5df2\u6709\u9886\u57df\u67e5\u8be2\u6253\u5f00\u663e\u5f0f\u7684\u6301\u4e45\u5316\u5bf9\u8c61\u5b57\u6bb5\u4f5c\u7528\u57df\u3002
     * @param query \u8981\u63a5\u6536\u6761\u4ef6\u7684\u9886\u57df\u67e5\u8be2
     * @param persistenceType \u6301\u4e45\u5316\u5bf9\u8c61\u7c7b\u578b
     */
    PersistenceQueryScope(Query<M> query, Class<P> persistenceType) {
        this.query = Objects.requireNonNull(query, "query must not be null");
        this.persistenceType = Objects.requireNonNull(persistenceType, "persistenceType must not be null");
    }

    /**
     * \u6dfb\u52a0\u6301\u4e45\u5316\u5bf9\u8c61\u5c5e\u6027\u7684\u7b49\u4e8e\u6761\u4ef6\u3002
     * @param property \u6301\u4e45\u5316\u5bf9\u8c61\u5c5e\u6027\u5f15\u7528
     * @param value \u6bd4\u8f83\u503c
     * @return \u5f53\u524d\u6301\u4e45\u5316\u67e5\u8be2\u4f5c\u7528\u57df\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public PersistenceQueryScope<M, P> eq(SFunction<P, ?> property, Object value) {
        return condition(true, property, StrPool.EQUALS, value);
    }

    /**
     * \u6309\u6761\u4ef6\u6dfb\u52a0\u6301\u4e45\u5316\u5bf9\u8c61\u5c5e\u6027\u7684\u7b49\u4e8e\u6761\u4ef6\u3002
     * @param condition \u4e3a true \u65f6\u624d\u6dfb\u52a0\u6761\u4ef6
     * @param property \u6301\u4e45\u5316\u5bf9\u8c61\u5c5e\u6027\u5f15\u7528
     * @param value \u6bd4\u8f83\u503c
     * @return \u5f53\u524d\u6301\u4e45\u5316\u67e5\u8be2\u4f5c\u7528\u57df\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public PersistenceQueryScope<M, P> eq(boolean condition, SFunction<P, ?> property, Object value) {
        return condition(condition, property, StrPool.EQUALS, value);
    }

    /**
     * \u6dfb\u52a0\u6301\u4e45\u5316\u5bf9\u8c61\u5c5e\u6027\u7684\u4e0d\u7b49\u4e8e\u6761\u4ef6\u3002
     * @param property \u6301\u4e45\u5316\u5bf9\u8c61\u5c5e\u6027\u5f15\u7528
     * @param value \u6bd4\u8f83\u503c
     * @return \u5f53\u524d\u6301\u4e45\u5316\u67e5\u8be2\u4f5c\u7528\u57df\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public PersistenceQueryScope<M, P> ne(SFunction<P, ?> property, Object value) {
        return condition(true, property, StrPool.NOT_EQUALS, value);
    }

    /**
     * \u6dfb\u52a0\u6301\u4e45\u5316\u5bf9\u8c61\u5c5e\u6027\u7684\u6a21\u7cca\u5339\u914d\u6761\u4ef6\u3002
     * @param property \u6301\u4e45\u5316\u5bf9\u8c61\u5c5e\u6027\u5f15\u7528
     * @param value \u6bd4\u8f83\u503c
     * @return \u5f53\u524d\u6301\u4e45\u5316\u67e5\u8be2\u4f5c\u7528\u57df\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public PersistenceQueryScope<M, P> like(SFunction<P, ?> property, Object value) {
        return condition(true, property, StrPool.LIKE, value);
    }

    /**
     * \u6dfb\u52a0\u6301\u4e45\u5316\u5bf9\u8c61\u5c5e\u6027\u7684\u5de6\u4fa7\u6a21\u7cca\u5339\u914d\u6761\u4ef6\u3002
     * @param property \u6301\u4e45\u5316\u5bf9\u8c61\u5c5e\u6027\u5f15\u7528
     * @param value \u6bd4\u8f83\u503c
     * @return \u5f53\u524d\u6301\u4e45\u5316\u67e5\u8be2\u4f5c\u7528\u57df\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public PersistenceQueryScope<M, P> likeLeft(SFunction<P, ?> property, Object value) {
        return condition(true, property, StrPool.LIKE_LEFT, value);
    }

    /**
     * \u6dfb\u52a0\u6301\u4e45\u5316\u5bf9\u8c61\u5c5e\u6027\u7684\u53f3\u4fa7\u6a21\u7cca\u5339\u914d\u6761\u4ef6\u3002
     * @param property \u6301\u4e45\u5316\u5bf9\u8c61\u5c5e\u6027\u5f15\u7528
     * @param value \u6bd4\u8f83\u503c
     * @return \u5f53\u524d\u6301\u4e45\u5316\u67e5\u8be2\u4f5c\u7528\u57df\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public PersistenceQueryScope<M, P> likeRight(SFunction<P, ?> property, Object value) {
        return condition(true, property, StrPool.LIKE_RIGHT, value);
    }

    /**
     * \u6dfb\u52a0\u6301\u4e45\u5316\u5bf9\u8c61\u5c5e\u6027\u7684\u975e\u6a21\u7cca\u5339\u914d\u6761\u4ef6\u3002
     * @param property \u6301\u4e45\u5316\u5bf9\u8c61\u5c5e\u6027\u5f15\u7528
     * @param value \u6bd4\u8f83\u503c
     * @return \u5f53\u524d\u6301\u4e45\u5316\u67e5\u8be2\u4f5c\u7528\u57df\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public PersistenceQueryScope<M, P> notLike(SFunction<P, ?> property, Object value) {
        return condition(true, property, StrPool.NOT_LIKE, value);
    }

    /**
     * \u6dfb\u52a0\u6301\u4e45\u5316\u5bf9\u8c61\u5c5e\u6027\u7684\u5927\u4e8e\u6761\u4ef6\u3002
     * @param property \u6301\u4e45\u5316\u5bf9\u8c61\u5c5e\u6027\u5f15\u7528
     * @param value \u6bd4\u8f83\u503c
     * @return \u5f53\u524d\u6301\u4e45\u5316\u67e5\u8be2\u4f5c\u7528\u57df\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public PersistenceQueryScope<M, P> gt(SFunction<P, ?> property, Object value) {
        return condition(true, property, StrPool.GT, value);
    }

    /**
     * \u6dfb\u52a0\u6301\u4e45\u5316\u5bf9\u8c61\u5c5e\u6027\u7684\u5927\u4e8e\u6216\u7b49\u4e8e\u6761\u4ef6\u3002
     * @param property \u6301\u4e45\u5316\u5bf9\u8c61\u5c5e\u6027\u5f15\u7528
     * @param value \u6bd4\u8f83\u503c
     * @return \u5f53\u524d\u6301\u4e45\u5316\u67e5\u8be2\u4f5c\u7528\u57df\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public PersistenceQueryScope<M, P> ge(SFunction<P, ?> property, Object value) {
        return condition(true, property, StrPool.GE, value);
    }

    /**
     * \u6dfb\u52a0\u6301\u4e45\u5316\u5bf9\u8c61\u5c5e\u6027\u7684\u5c0f\u4e8e\u6761\u4ef6\u3002
     * @param property \u6301\u4e45\u5316\u5bf9\u8c61\u5c5e\u6027\u5f15\u7528
     * @param value \u6bd4\u8f83\u503c
     * @return \u5f53\u524d\u6301\u4e45\u5316\u67e5\u8be2\u4f5c\u7528\u57df\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public PersistenceQueryScope<M, P> lt(SFunction<P, ?> property, Object value) {
        return condition(true, property, StrPool.LT, value);
    }

    /**
     * \u6dfb\u52a0\u6301\u4e45\u5316\u5bf9\u8c61\u5c5e\u6027\u7684\u5c0f\u4e8e\u6216\u7b49\u4e8e\u6761\u4ef6\u3002
     * @param property \u6301\u4e45\u5316\u5bf9\u8c61\u5c5e\u6027\u5f15\u7528
     * @param value \u6bd4\u8f83\u503c
     * @return \u5f53\u524d\u6301\u4e45\u5316\u67e5\u8be2\u4f5c\u7528\u57df\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public PersistenceQueryScope<M, P> le(SFunction<P, ?> property, Object value) {
        return condition(true, property, StrPool.LE, value);
    }

    /**
     * \u6dfb\u52a0\u6301\u4e45\u5316\u5bf9\u8c61\u5c5e\u6027\u7684\u8303\u56f4\u6761\u4ef6\u3002
     * @param property \u6301\u4e45\u5316\u5bf9\u8c61\u5c5e\u6027\u5f15\u7528
     * @param start \u8303\u56f4\u4e0b\u754c\uff0cnull \u65f6\u5ffd\u7565
     * @param end \u8303\u56f4\u4e0a\u754c\uff0cnull \u65f6\u5ffd\u7565
     * @return \u5f53\u524d\u6301\u4e45\u5316\u67e5\u8be2\u4f5c\u7528\u57df\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public PersistenceQueryScope<M, P> between(SFunction<P, ?> property, Object start, Object end) {
        condition(Objects.nonNull(start), property, StrPool.GE, start);
        return condition(Objects.nonNull(end), property, StrPool.LE, end);
    }

    /**
     * \u6dfb\u52a0\u6301\u4e45\u5316\u5bf9\u8c61\u5c5e\u6027\u7684\u96c6\u5408\u5305\u542b\u6761\u4ef6\u3002
     * @param property \u6301\u4e45\u5316\u5bf9\u8c61\u5c5e\u6027\u5f15\u7528
     * @param values \u5019\u9009\u503c\u96c6\u5408\uff1b\u4e3a\u7a7a\u65f6\u5ffd\u7565\u6761\u4ef6
     * @return \u5f53\u524d\u6301\u4e45\u5316\u67e5\u8be2\u4f5c\u7528\u57df\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public PersistenceQueryScope<M, P> in(SFunction<P, ?> property, Collection<?> values) {
        return condition(CollKit.isNotEmpty(values), property, StrPool.IN,
                Objects.isNull(values) ? null : new ArrayList<>(values));
    }

    /**
     * \u6dfb\u52a0\u6301\u4e45\u5316\u5bf9\u8c61\u5c5e\u6027\u7684\u96c6\u5408\u6392\u9664\u6761\u4ef6\u3002
     * @param property \u6301\u4e45\u5316\u5bf9\u8c61\u5c5e\u6027\u5f15\u7528
     * @param values \u5019\u9009\u503c\u96c6\u5408\uff1b\u4e3a\u7a7a\u65f6\u5ffd\u7565\u6761\u4ef6
     * @return \u5f53\u524d\u6301\u4e45\u5316\u67e5\u8be2\u4f5c\u7528\u57df\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public PersistenceQueryScope<M, P> notIn(SFunction<P, ?> property, Collection<?> values) {
        return condition(CollKit.isNotEmpty(values), property, StrPool.NOT_IN,
                Objects.isNull(values) ? null : new ArrayList<>(values));
    }

    /**
     * \u6dfb\u52a0\u6301\u4e45\u5316\u5bf9\u8c61\u5c5e\u6027\u7684\u4e3a\u7a7a\u6761\u4ef6\u3002
     * @param property \u6301\u4e45\u5316\u5bf9\u8c61\u5c5e\u6027\u5f15\u7528
     * @return \u5f53\u524d\u6301\u4e45\u5316\u67e5\u8be2\u4f5c\u7528\u57df\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public PersistenceQueryScope<M, P> isNull(SFunction<P, ?> property) {
        return condition(true, property, StrPool.IS_NULL, null);
    }

    /**
     * \u6dfb\u52a0\u6301\u4e45\u5316\u5bf9\u8c61\u5c5e\u6027\u7684\u975e\u7a7a\u6761\u4ef6\u3002
     * @param property \u6301\u4e45\u5316\u5bf9\u8c61\u5c5e\u6027\u5f15\u7528
     * @return \u5f53\u524d\u6301\u4e45\u5316\u67e5\u8be2\u4f5c\u7528\u57df\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public PersistenceQueryScope<M, P> isNotNull(SFunction<P, ?> property) {
        return condition(true, property, StrPool.IS_NOT_NULL, null);
    }

    /**
     * \u6dfb\u52a0\u6301\u4e45\u5316\u5bf9\u8c61\u5c5e\u6027\u7684\u5347\u5e8f\u6392\u5e8f\u6761\u4ef6\u3002
     * @param property \u6301\u4e45\u5316\u5bf9\u8c61\u5c5e\u6027\u5f15\u7528
     * @return \u5f53\u524d\u6301\u4e45\u5316\u67e5\u8be2\u4f5c\u7528\u57df\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public PersistenceQueryScope<M, P> orderByAsc(SFunction<P, ?> property) {
        query.addOrderBy(reference(property), StrPool.ASC);
        return this;
    }

    /**
     * \u6dfb\u52a0\u6301\u4e45\u5316\u5bf9\u8c61\u5c5e\u6027\u7684\u964d\u5e8f\u6392\u5e8f\u6761\u4ef6\u3002
     * @param property \u6301\u4e45\u5316\u5bf9\u8c61\u5c5e\u6027\u5f15\u7528
     * @return \u5f53\u524d\u6301\u4e45\u5316\u67e5\u8be2\u4f5c\u7528\u57df\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public PersistenceQueryScope<M, P> orderByDesc(SFunction<P, ?> property) {
        query.addOrderBy(reference(property), StrPool.DESC);
        return this;
    }

    /**
     * \u8bbe\u7f6e\u5f53\u524d\u9875\u7801\u3002
     * @param current \u5f53\u524d\u9875\u7801\uff0c\u4ece 1 \u5f00\u59cb
     * @return \u5f53\u524d\u6301\u4e45\u5316\u67e5\u8be2\u4f5c\u7528\u57df\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public PersistenceQueryScope<M, P> current(long current) {
        query.current(current);
        return this;
    }

    /**
     * \u8bbe\u7f6e\u6bcf\u9875\u6761\u6570\u3002
     * @param size \u6bcf\u9875\u6761\u6570\uff0c-1 \u8868\u793a\u4e0d\u5206\u9875
     * @return \u5f53\u524d\u6301\u4e45\u5316\u67e5\u8be2\u4f5c\u7528\u57df\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public PersistenceQueryScope<M, P> size(long size) {
        query.size(size);
        return this;
    }

    /**
     * \u5ffd\u7565\u79df\u6237\u9694\u79bb\uff0c\u4ec5\u7528\u4e8e\u83b7\u6388\u6743\u7684\u8de8\u79df\u6237\u7ba1\u7406\u67e5\u8be2\u3002
     * @return \u5f53\u524d\u6301\u4e45\u5316\u67e5\u8be2\u4f5c\u7528\u57df\uff0c\u4f9b\u94fe\u5f0f\u8c03\u7528
     */
    public PersistenceQueryScope<M, P> ignoreTenantId() {
        query.ignoreTenantId();
        return this;
    }

    /**
     * \u9000\u51fa\u6301\u4e45\u5316\u5bf9\u8c61\u5c5e\u6027\u4f5c\u7528\u57df\uff0c\u8fd4\u56de\u539f\u9886\u57df\u67e5\u8be2\u3002
     * @return \u539f\u9886\u57df\u67e5\u8be2
     */
    public Query<M> domain() {
        return query;
    }

    /**
     * \u6267\u884c\u9886\u57df\u6a21\u578b\u5217\u8868\u67e5\u8be2\u3002
     * @return \u5339\u914d\u7684\u9886\u57df\u6a21\u578b\u5217\u8868
     */
    public List<M> list() {
        return query.list();
    }

    /**
     * \u6267\u884c\u9886\u57df\u6a21\u578b\u5206\u9875\u67e5\u8be2\u3002
     * @return \u5206\u9875\u67e5\u8be2\u7ed3\u679c
     */
    public Page<M> page() {
        return query.page();
    }

    /**
     * one() 方法。
     * @return 方法执行结果。
     */
    public M one() {
        return query.one();
    }

    /**
     * \u4ee5 Optional \u8fd4\u56de\u7b2c\u4e00\u6761\u5339\u914d\u7684\u9886\u57df\u6a21\u578b\u3002
     * @return \u7b2c\u4e00\u6761\u9886\u57df\u6a21\u578b\u7684 Optional
     */
    public Optional<M> oneOpt() {
        return query.oneOpt();
    }

    /**
     * count() 方法。
     * @return 结果数值。
     */
    public long count() {
        return query.count();
    }

    /**
     * \u5c06\u672c\u4f5c\u7528\u57df\u4e2d\u7684\u6301\u4e45\u5316\u5c5e\u6027\u8f6c\u6362\u6210\u67e5\u8be2\u7b5b\u9009\u6761\u4ef6\u3002
     * @param condition \u662f\u5426\u8ffd\u52a0\u8be5\u6761\u4ef6
     * @param property \u6301\u4e45\u5316\u5c5e\u6027\u7684\u65b9\u6cd5\u5f15\u7528
     * @param operator \u6761\u4ef6\u8fd0\u7b97\u7b26
     * @param value \u6bd4\u8f83\u503c\u6216\u67e5\u8be2\u64cd\u4f5c\u6570
     * @return \u5f53\u524d\u6301\u4e45\u5316\u67e5\u8be2\u4f5c\u7528\u57df
     */
    private PersistenceQueryScope<M, P> condition(boolean condition, SFunction<P, ?> property,
                                                  String operator, Object value) {
        query.addCondition(condition, reference(property), operator, value);
        return this;
    }

    /**
     * \u6309\u672c\u4f5c\u7528\u57df\u7ed1\u5b9a\u7684\u6301\u4e45\u5316\u7c7b\u578b\u89e3\u6790\u5b57\u6bb5\u5f15\u7528\u3002
     * @param property \u6301\u4e45\u5316\u5c5e\u6027\u8bbf\u95ee\u5668\u7684\u65b9\u6cd5\u5f15\u7528
     * @return \u6807\u8bc6\u6301\u4e45\u5316\u5b57\u6bb5\u7684\u5c5e\u6027\u5f15\u7528
     */
    private PropertyRef reference(SFunction<P, ?> property) {
        return PropertyRef.persistence(persistenceType, property);
    }
}
