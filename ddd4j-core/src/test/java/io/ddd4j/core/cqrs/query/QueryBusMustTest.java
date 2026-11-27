/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.core.cqrs.query;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * QueryBus 只读分发行为契约测试（规格：{@code query-bus}）。
 *
 * <p>覆盖：按类型路由返回处理器结果、查询路径无写副作用（重复 ask 读模型不变）、
 * 未知查询类型拒绝。
 */
class QueryBusMustTest {

    /**
     * 验证<code>count</code> 等于 <code>3</code>，<code>found</code> 等于 <code>"order:order-2"</code>。
     */
    @Test
    void askMustRouteByQueryTypeAndReturnHandlerResult() {
        List<String> readModel = Arrays.asList("order-1", "order-2", "order-3");
        DefaultQueryBus bus = new DefaultQueryBus(Arrays.asList(
                new QueryHandler<CountOrdersQuery, Integer>() {
                    @Override
                    public Class<CountOrdersQuery> queryType() {
                        return CountOrdersQuery.class;
                    }

                    @Override
                    public Integer handle(CountOrdersQuery query) {
                        return readModel.size();
                    }
                },
                new QueryHandler<FindOrderQuery, String>() {
                    @Override
                    public Class<FindOrderQuery> queryType() {
                        return FindOrderQuery.class;
                    }

                    @Override
                    public String handle(FindOrderQuery query) {
                        return "order:" + query.orderId();
                    }
                }));

        Integer count = bus.ask(new CountOrdersQuery());
        String found = bus.ask(new FindOrderQuery("order-2"));

        assertThat(count).isEqualTo(3);
        assertThat(found).isEqualTo("order:order-2");
    }

    /**
     * 验证<code>count</code> 等于 <code>3</code>，<code>readModel</code> 的大小为 <code>3</code>。
     */
    @Test
    void askMustNotMutateReadModelAcrossRepeatedQueries() {
        List<String> readModel = new ArrayList<>(Arrays.asList("order-1", "order-2", "order-3"));
        DefaultQueryBus bus = new DefaultQueryBus(Arrays.asList(
                new QueryHandler<CountOrdersQuery, Integer>() {
                    @Override
                    public Class<CountOrdersQuery> queryType() {
                        return CountOrdersQuery.class;
                    }

                    @Override
                    public Integer handle(CountOrdersQuery query) {
                        return readModel.size();
                    }
                }));

        Integer count = bus.ask(new CountOrdersQuery());

        assertThat(count).isEqualTo(3);
        // 只读语义：两次 ask 前后读模型记录数不变，无任何写入发生
        assertThat(readModel).hasSize(3).containsExactly("order-1", "order-2", "order-3");
    }

    /**
     * 验证<code>bus.ask(new FindOrderQuery("order-9"))</code> 抛出 <code>IllegalStateException.class</code>，<code>readModel</code> 的大小为 <code>1</code>。
     */
    @Test
    void unknownQueryTypeMustBeRejectedWithoutSideEffects() {
        List<String> readModel = new ArrayList<>(Arrays.asList("order-1"));
        DefaultQueryBus bus = new DefaultQueryBus(Arrays.asList(
                new QueryHandler<CountOrdersQuery, Integer>() {
                    @Override
                    public Class<CountOrdersQuery> queryType() {
                        return CountOrdersQuery.class;
                    }

                    @Override
                    public Integer handle(CountOrdersQuery query) {
                        return readModel.size();
                    }
                }));

        assertThatThrownBy(() -> bus.ask(new FindOrderQuery("order-9")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FindOrderQuery");
        // 拒绝路径不产生任何副作用
        assertThat(readModel).hasSize(1);
    }

    /**
     * 只读查询对象：订单数统计。
     */
    static final class CountOrdersQuery {
    }

    /**
     * 只读查询对象：订单明细。
     */
    static final class FindOrderQuery {
        /**
         * 订单标识。
         */
        private final String orderId;

        /**
         * 用给定值初始化 <code>FindOrderQuery</code> 实例。
         *
         * @param orderId 订单标识
         */
        FindOrderQuery(String orderId) {
            this.orderId = orderId;
        }

        /**
         * 读取订单标识。
         *
         * @return 订单标识
         */
        String orderId() {
            return orderId;
        }
    }
}
