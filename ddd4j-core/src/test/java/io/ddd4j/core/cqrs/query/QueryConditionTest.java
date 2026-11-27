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

import io.ddd4j.core.ddd.model.AggregateRoot;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link Query} ORM 无关条件模型测试。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class QueryConditionTest {

    /**
     * 在测试框架中执行 <code>query.getWhereConditions()</code>，验证该场景。
     */
    @Test
    void nullOperatorsShouldBeRecordedWithoutValue() {
        OrderQuery query = new OrderQuery()
                .isNull(Order::getDeletedAt)
                .isNotNull(Order::getCreatedAt);

        assertThat(query.getWhereConditions())
                .extracting(LambdaCondition::operator)
                .containsExactly("IS_NULL", "IS_NOT_NULL");
    }

    /**
     * 验证<code>query.getWhereConditions()</code>为空。
     */
    @Test
    void valueOperatorShouldIgnoreNullValue() {
        OrderQuery query = new OrderQuery().eq(Order::getStatus, null);

        assertThat(query.getWhereConditions()).isEmpty();
    }

    /**
     * 在测试框架中执行 <code>query.getWhereConditions()</code>、<code>org.assertj.core.groups.Tuple.tuple()</code>，验证该场景。
     */
    @Test
    void richQueryMethodShouldComposeDomainConditions() {
        OrderQuery query = new OrderQuery().paid().createdAfter(LocalDateTime.of(2026, 1, 1, 0, 0));

        assertThat(query.getWhereConditions())
                .extracting(LambdaCondition::property, LambdaCondition::operator, LambdaCondition::value)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("status", "=", "PAID"),
                        org.assertj.core.groups.Tuple.tuple("createdAt", ">=", LocalDateTime.of(2026, 1, 1, 0, 0))
                );
    }

    /**
     * 验证<code>condition.propertyRef().space()</code> 等于 <code>PropertySpace.PERSISTENCE</code>，<code>condition.property()</code> 等于 <code>"createdAt"</code>，<code>query.getSize()</code> 等于 <code>20</code>。
     */
    @Test
    void persistenceScopeShouldKeepPoPropertySeparateFromDomainModel() {
        OrderQuery query = new OrderQuery();

        query.withPO(OrderPO.class)
                .eq(OrderPO::getOrderStatus, "PAID")
                .isNull(OrderPO::getDeletedAt)
                .orderByDesc(OrderPO::getCreatedAt)
                .current(2)
                .size(20);

        assertThat(query.getWhereConditions())
                .extracting(condition -> condition.propertyRef().space(),
                        LambdaCondition::property,
                        condition -> condition.propertyRef().ownerType())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(PropertySpace.PERSISTENCE, "orderStatus", OrderPO.class),
                        org.assertj.core.groups.Tuple.tuple(PropertySpace.PERSISTENCE, "deletedAt", OrderPO.class));
        assertThat(query.getOrderByConditions()).singleElement().satisfies(condition -> {
            assertThat(condition.propertyRef().space()).isEqualTo(PropertySpace.PERSISTENCE);
            assertThat(condition.property()).isEqualTo("createdAt");
        });
        assertThat(query.getCurrent()).isEqualTo(2);
        assertThat(query.getSize()).isEqualTo(20);
    }

    /**
     * 订单测试查询对象，供构造仓储检索条件。
     */
    static final class OrderQuery extends Query<Order> {

        /**
         * 通过 <code>eq()</code> 取得当前测试需要的结果。
         *
         * @return 当前测试使用的结果，取自 <code>eq(Order::getStatus, "PAID")</code>
         */
        OrderQuery paid() {
            return eq(Order::getStatus, "PAID");
        }

        /**
         * 通过 <code>ge()</code> 取得当前测试需要的结果。
         *
         * @param time 传给 <code>ge()</code> 的 <code>LocalDateTime</code> 输入
         * @return 当前测试使用的结果，取自 <code>ge(Order::getCreatedAt, time)</code>
         */
        OrderQuery createdAfter(LocalDateTime time) {
            return ge(Order::getCreatedAt, time);
        }
    }

    /**
     * 供测试验证事件应用和持久化的聚合样本。
     */
    static final class Order extends AggregateRoot<String> {

        /**
         * 标识当前测试对象的主键或事件标识。
         */
        private final String id;
        /**
         * 测试替身返回、模拟或捕获的状态。
         */
        private final String status;
        /**
         * 记录创建的时间。
         */
        private final LocalDateTime createdAt;
        /**
         * 记录被逻辑删除的时间。
         */
        private final LocalDateTime deletedAt;

        /**
         * 用给定值初始化 <code>Order</code> 实例。
         *
         * @param id        <code>id</code> 字段
         * @param status    <code>status</code> 字段
         * @param createdAt 记录创建的时间
         * @param deletedAt 记录被逻辑删除的时间
         */
        Order(String id, String status, LocalDateTime createdAt, LocalDateTime deletedAt) {
            this.id = id;
            this.status = status;
            this.createdAt = createdAt;
            this.deletedAt = deletedAt;
        }

        /**
         * 读取<code>id</code> 字段。
         *
         * @return <code>id</code> 字段
         */
        @Override
        public String id() {
            return id;
        }

        /**
         * 读取<code>status</code> 字段。
         *
         * @return <code>status</code> 字段
         */
        String getStatus() {
            return status;
        }

        /**
         * 读取记录创建的时间。
         *
         * @return 记录创建的时间
         */
        LocalDateTime getCreatedAt() {
            return createdAt;
        }

        /**
         * 读取记录被逻辑删除的时间。
         *
         * @return 记录被逻辑删除的时间
         */
        LocalDateTime getDeletedAt() {
            return deletedAt;
        }
    }

    /**
     * 持久化层使用的订单测试数据模型。
     */
    static final class OrderPO {

        /**
         * 商品或订单合同要求检查的订单状态。
         */
        private String orderStatus;
        /**
         * 记录创建的时间。
         */
        private LocalDateTime createdAt;
        /**
         * 记录被逻辑删除的时间。
         */
        private LocalDateTime deletedAt;

        /**
         * 读取<code>orderStatus</code> 字段。
         *
         * @return <code>orderStatus</code> 字段
         */
        String getOrderStatus() {
            return orderStatus;
        }

        /**
         * 读取记录创建的时间。
         *
         * @return 记录创建的时间
         */
        LocalDateTime getCreatedAt() {
            return createdAt;
        }

        /**
         * 读取记录被逻辑删除的时间。
         *
         * @return 记录被逻辑删除的时间
         */
        LocalDateTime getDeletedAt() {
            return deletedAt;
        }
    }
}
