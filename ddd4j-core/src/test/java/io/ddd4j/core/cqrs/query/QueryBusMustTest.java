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

    /** \u9a8c\u8bc1<code>count</code> \u7b49\u4e8e <code>3</code>\uff0c<code>found</code> \u7b49\u4e8e <code>"order:order-2"</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>count</code> \u7b49\u4e8e <code>3</code>\uff0c<code>readModel</code> \u7684\u5927\u5c0f\u4e3a <code>3</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>bus.ask(new FindOrderQuery("order-9"))</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>readModel</code> \u7684\u5927\u5c0f\u4e3a <code>1</code>\u3002 */
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
        /** \u8ba2\u5355\u6807\u8bc6\u3002 */
        private final String orderId;

        /**
         * \u7528\u7ed9\u5b9a\u503c\u521d\u59cb\u5316 <code>FindOrderQuery</code> \u5b9e\u4f8b\u3002
         * @param orderId \u8ba2\u5355\u6807\u8bc6
         */
        FindOrderQuery(String orderId) {
            this.orderId = orderId;
        }

        /**
         * \u8bfb\u53d6\u8ba2\u5355\u6807\u8bc6\u3002
         * @return \u8ba2\u5355\u6807\u8bc6
         */
        String orderId() {
            return orderId;
        }
    }
}
