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

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于显式处理器集合的默认查询路由实现（形态对齐 {@code DefaultCommandBus}）。
 *
 * <p>对应规格：{@code complete-cqrs-es-base-capabilities/query-bus}。
 *
 * <h3>构造即快照</h3>
 * <p>构造时对处理器集合一次性快照（逐个 {@code putIfAbsent}，同类型冲突即抛
 * {@link IllegalStateException}）；构造后对传入集合的变更不回灌到已构造的总线。
 *
 * <h3>只读契约</h3>
 * <p>未注册查询类型的 ask 被拒绝（{@code IllegalStateException}），不存在任何隐式写路径。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 1.0.x
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class DefaultQueryBus implements QueryBus {

    /** \u6309\u67e5\u8be2\u8fd0\u884c\u65f6\u7c7b\u578b\u7d22\u5f15\u7684\u5904\u7406\u5668\u3002 */
    private final Map<Class<?>, QueryHandler<?, ?>> handlers = new ConcurrentHashMap<>();

    /**
     * 使用显式处理器集合构造查询总线。
     *
     * @param queryHandlers 查询处理器集合
     * @throws IllegalStateException 同一查询类型存在多个处理器
     */
    public DefaultQueryBus(Collection<? extends QueryHandler<?, ?>> queryHandlers) {
        Objects.requireNonNull(queryHandlers, "queryHandlers must not be null").forEach(this::register);
    }

    /**
     * 将给定查询交给查询总线处理。
     *
     * <p>此实现调用 <code>handlers.get(actual.getClass())</code>。</p>
     *
     * @param <R> 此操作处理的值或结果的 Java 类型
     * @param query 交给查询处理器或仓储的查询条件
     * @return 实际返回<code>(R)handler.handle(actual)</code>
     */
    @Override
    public <R> R ask(Object query) {
        Object actual = Objects.requireNonNull(query, "query must not be null");
        QueryHandler handler = handlers.get(actual.getClass());
        if (Objects.isNull(handler)) {
            throw new IllegalStateException("No handler found for query: " + actual.getClass().getName());
        }
        return (R) handler.handle(actual);
    }

    /**
     * \u6839\u636e\u58f0\u660e\u7684\u67e5\u8be2\u7c7b\u578b\u6ce8\u518c\u5904\u7406\u5668\uff0c\u91cd\u590d\u7c7b\u578b\u4f1a\u5728\u88c5\u914d\u65f6\u5931\u8d25\u3002
     * @param handler \u5f85\u7ed1\u5b9a\u7684\u67e5\u8be2\u5904\u7406\u5668
     */
    private void register(QueryHandler<?, ?> handler) {
        QueryHandler<?, ?> actual = Objects.requireNonNull(handler, "handler must not be null");
        Object previous = handlers.putIfAbsent(actual.queryType(), actual);
        if (Objects.nonNull(previous)) {
            throw new IllegalStateException("Multiple handlers found for query: " + actual.queryType().getName());
        }
    }
}
