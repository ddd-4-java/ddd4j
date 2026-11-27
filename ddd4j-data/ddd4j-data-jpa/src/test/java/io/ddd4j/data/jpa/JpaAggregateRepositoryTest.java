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
package io.ddd4j.data.jpa;

import io.ddd4j.core.cqrs.query.LambdaCondition;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * {@link JpaAggregateRepository} 属性空间翻译契约测试。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class JpaAggregateRepositoryTest {

    /**
     * 验证<code>repository.property(query.getWhereConditions().get(0))</code> 等于 <code>"orderStatus"</code>。
     */
    @Test
    void domainPropertyShouldUseRepositoryMapping() {
        TestRepository repository = new TestRepository(mock(EntityManager.class));
        OrderQuery query = new OrderQuery().eq(Order::status, "PAID");

        assertThat(repository.property(query.getWhereConditions().get(0))).isEqualTo("orderStatus");
    }

    /**
     * 验证<code>repository.property(query.getWhereConditions().get(0))</code> 等于 <code>"orderStatus"</code>。
     */
    @Test
    void persistenceScopeShouldUseJpaPropertyDirectly() {
        TestRepository repository = new TestRepository(mock(EntityManager.class));
        OrderQuery query = new OrderQuery();
        query.withPO(OrderPO.class).eq(OrderPO::getOrderStatus, "PAID");

        assertThat(repository.property(query.getWhereConditions().get(0))).isEqualTo("orderStatus");
    }

    /**
     * 验证<code>repository.property(condition)</code> 抛出 <code>IllegalArgumentException.class</code>。
     */
    @Test
    void persistenceScopeShouldRejectAnotherEntityType() {
        TestRepository repository = new TestRepository(mock(EntityManager.class));
        OrderQuery query = new OrderQuery();
        query.withPO(OtherOrderPO.class).eq(OtherOrderPO::getOrderStatus, "PAID");
        LambdaCondition condition = query.getWhereConditions().get(0);

        assertThatThrownBy(() -> repository.property(condition))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PERSISTENCE", OtherOrderPO.class.getName(), OrderPO.class.getName());
    }

    /**
     * 实现 <code>JpaAggregateRepository</code>，供测试调用 <code>property()</code>、<code>persistenceProperty()</code> 并观察结果。
     */
    static final class TestRepository extends JpaAggregateRepository<Order, OrderPO, String> {

        /**
         * 使用 <code>super()</code> 初始化 <code>TestRepository</code> 测试夹具。
         *
         * @param entityManager 用于初始化 <code>TestRepository</code> 的 <code>EntityManager</code> 值
         */
        TestRepository(EntityManager entityManager) {
            super(entityManager, Order.class, OrderPO.class);
        }

        /**
         * 通过 <code>persistenceProperty()</code> 取得当前测试需要的结果。
         *
         * @param condition 决定本次测试操作或断言是否成立的条件
         * @return 当前测试使用的结果，取自 <code>persistenceProperty(condition)</code>
         */
        String property(LambdaCondition condition) {
            return persistenceProperty(condition);
        }

        /**
         * 将领域属性名映射为 JPA 持久化属性名。
         *
         * <p>测试夹具会调用 <code>"status".equals(domainProperty)</code>、<code>super.persistenceProperty(domainProperty)</code>。</p>
         *
         * @param domainProperty 领域属性名
         * @return 根据执行路径返回<code>"orderStatus"</code>、<code>super.persistenceProperty(domainProperty)</code>
         */
        @Override
        protected String persistenceProperty(String domainProperty) {
            if ("status".equals(domainProperty)) {
                return "orderStatus";
            }
            return super.persistenceProperty(domainProperty);
        }
    }

    /**
     * 订单测试查询对象，供构造仓储检索条件。
     */
    static final class OrderQuery extends Query<Order> {
    }

    /**
     * 供测试验证事件应用和持久化的聚合样本。
     */
    static final class Order extends AggregateRoot<String> {

        /**
         * 标识当前测试对象的主键或事件标识。
         */
        private String id;
        /**
         * 测试替身返回、模拟或捕获的状态。
         */
        private String status;

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
        String status() {
            return status;
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
         * 读取<code>orderStatus</code> 字段。
         *
         * @return <code>orderStatus</code> 字段
         */
        String getOrderStatus() {
            return orderStatus;
        }
    }

    /**
     * 将 <code>orderStatus</code> 等状态一起传入当前测试，供调用或结果断言使用。
     */
    static final class OtherOrderPO {

        /**
         * 商品或订单合同要求检查的订单状态。
         */
        private String orderStatus;

        /**
         * 读取<code>orderStatus</code> 字段。
         *
         * @return <code>orderStatus</code> 字段
         */
        String getOrderStatus() {
            return orderStatus;
        }
    }
}
