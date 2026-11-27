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

    /** \u9a8c\u8bc1<code>repository.property(query.getWhereConditions().get(0))</code> \u7b49\u4e8e <code>"orderStatus"</code>\u3002 */
    @Test
    void domainPropertyShouldUseRepositoryMapping() {
        TestRepository repository = new TestRepository(mock(EntityManager.class));
        OrderQuery query = new OrderQuery().eq(Order::status, "PAID");

        assertThat(repository.property(query.getWhereConditions().get(0))).isEqualTo("orderStatus");
    }

    /** \u9a8c\u8bc1<code>repository.property(query.getWhereConditions().get(0))</code> \u7b49\u4e8e <code>"orderStatus"</code>\u3002 */
    @Test
    void persistenceScopeShouldUseJpaPropertyDirectly() {
        TestRepository repository = new TestRepository(mock(EntityManager.class));
        OrderQuery query = new OrderQuery();
        query.withPO(OrderPO.class).eq(OrderPO::getOrderStatus, "PAID");

        assertThat(repository.property(query.getWhereConditions().get(0))).isEqualTo("orderStatus");
    }

    /** \u9a8c\u8bc1<code>repository.property(condition)</code> \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
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

    /** \u5b9e\u73b0 <code>JpaAggregateRepository</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>property()</code>\u3001<code>persistenceProperty()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    static final class TestRepository extends JpaAggregateRepository<Order, OrderPO, String> {

        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>TestRepository</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param entityManager \u7528\u4e8e\u521d\u59cb\u5316 <code>TestRepository</code> \u7684 <code>EntityManager</code> \u503c
         */
        TestRepository(EntityManager entityManager) {
            super(entityManager, Order.class, OrderPO.class);
        }

        /**
         * \u901a\u8fc7 <code>persistenceProperty()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @param condition \u51b3\u5b9a\u672c\u6b21\u6d4b\u8bd5\u64cd\u4f5c\u6216\u65ad\u8a00\u662f\u5426\u6210\u7acb\u7684\u6761\u4ef6
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>persistenceProperty(condition)</code>
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

    /** \u8ba2\u5355\u6d4b\u8bd5\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u6784\u9020\u4ed3\u50a8\u68c0\u7d22\u6761\u4ef6\u3002 */
    static final class OrderQuery extends Query<Order> {
    }

    /** \u4f9b\u6d4b\u8bd5\u9a8c\u8bc1\u4e8b\u4ef6\u5e94\u7528\u548c\u6301\u4e45\u5316\u7684\u805a\u5408\u6837\u672c\u3002 */
    static final class Order extends AggregateRoot<String> {

        /** \u6807\u8bc6\u5f53\u524d\u6d4b\u8bd5\u5bf9\u8c61\u7684\u4e3b\u952e\u6216\u4e8b\u4ef6\u6807\u8bc6\u3002 */
        private String id;
        /** \u6d4b\u8bd5\u66ff\u8eab\u8fd4\u56de\u3001\u6a21\u62df\u6216\u6355\u83b7\u7684\u72b6\u6001\u3002 */
        private String status;

        /**
         * \u8bfb\u53d6<code>id</code> \u5b57\u6bb5\u3002
         * @return <code>id</code> \u5b57\u6bb5
         */
        @Override
        public String id() {
            return id;
        }

        /**
         * \u8bfb\u53d6<code>status</code> \u5b57\u6bb5\u3002
         * @return <code>status</code> \u5b57\u6bb5
         */
        String status() {
            return status;
        }
    }

    /** \u6301\u4e45\u5316\u5c42\u4f7f\u7528\u7684\u8ba2\u5355\u6d4b\u8bd5\u6570\u636e\u6a21\u578b\u3002 */
    static final class OrderPO {

        /** \u5546\u54c1\u6216\u8ba2\u5355\u5408\u540c\u8981\u6c42\u68c0\u67e5\u7684\u8ba2\u5355\u72b6\u6001\u3002 */
        private String orderStatus;

        /**
         * \u8bfb\u53d6<code>orderStatus</code> \u5b57\u6bb5\u3002
         * @return <code>orderStatus</code> \u5b57\u6bb5
         */
        String getOrderStatus() {
            return orderStatus;
        }
    }

    /** \u5c06 <code>orderStatus</code> \u7b49\u72b6\u6001\u4e00\u8d77\u4f20\u5165\u5f53\u524d\u6d4b\u8bd5\uff0c\u4f9b\u8c03\u7528\u6216\u7ed3\u679c\u65ad\u8a00\u4f7f\u7528\u3002 */
    static final class OtherOrderPO {

        /** \u5546\u54c1\u6216\u8ba2\u5355\u5408\u540c\u8981\u6c42\u68c0\u67e5\u7684\u8ba2\u5355\u72b6\u6001\u3002 */
        private String orderStatus;

        /**
         * \u8bfb\u53d6<code>orderStatus</code> \u5b57\u6bb5\u3002
         * @return <code>orderStatus</code> \u5b57\u6bb5
         */
        String getOrderStatus() {
            return orderStatus;
        }
    }
}
