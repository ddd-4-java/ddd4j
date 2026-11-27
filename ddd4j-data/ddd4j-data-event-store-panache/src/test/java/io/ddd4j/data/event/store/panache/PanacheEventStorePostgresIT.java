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
package io.ddd4j.data.event.store.panache;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.ddd.event.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PostgreSQL 容器轨：验证 Panache EventStore 的共享 TEXT schema 与事件往返。
 */
@Testcontainers(disabledWithoutDocker = true)
class PanacheEventStorePostgresIT {

    /**
     * 为当前集成测试提供独立 PostgreSQL 服务的容器。
     */
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            // 临时 schema/事件往返测试；跨容器重启的持久性由独立恢复场景验证。
            .withTmpFs(Collections.singletonMap("/var/lib/postgresql/data", "rw"));
    /**
     * 测试事件流中使用的聚合类型名称。
     */
    private static final String ORDER_TYPE = "Order";
    /**
     * 用于创建 JPA 实体管理器的工厂。
     */
    private EntityManagerFactory entityManagerFactory;
    /**
     * 执行 JPA 查询和持久化操作的实体管理器。
     */
    private EntityManager entityManager;
    /**
     * 读取及追加领域事件的事件存储。
     */
    private EventStore eventStore;

    /**
     * 测试执行前执行 <code>POSTGRES.getJdbcUrl()</code>、<code>POSTGRES.getUsername()</code>、<code>POSTGRES.getPassword()</code>、<code>configuration.buildSessionFactory()</code> 完成资源准备。
     */
    @BeforeEach
    void setUp() {
        Configuration configuration = new Configuration()
                .setProperty("hibernate.connection.driver_class", "org.postgresql.Driver")
                .setProperty("hibernate.connection.url", POSTGRES.getJdbcUrl())
                .setProperty("hibernate.connection.username", POSTGRES.getUsername())
                .setProperty("hibernate.connection.password", POSTGRES.getPassword())
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .addAnnotatedClass(PanacheStoredEventEntity.class);
        entityManagerFactory = configuration.buildSessionFactory();
        entityManager = entityManagerFactory.createEntityManager();
        eventStore = new PanacheEventStore(entityManager);
    }

    /**
     * 测试结束后执行 <code>entityManager.isOpen()</code>、<code>entityManager.close()</code>、<code>entityManagerFactory.isOpen()</code>、<code>entityManagerFactory.close()</code> 完成资源回收或状态恢复。
     */
    @AfterEach
    void tearDown() {
        if (entityManager != null && entityManager.isOpen()) {
            entityManager.close();
        }
        if (entityManagerFactory != null && entityManagerFactory.isOpen()) {
            entityManagerFactory.close();
        }
    }

    /**
     * 验证<code>dataType</code> 等于 <code>"text"</code>，<code>eventStore.read(ORDER_TYPE, orderId)</code> 的大小为 <code>1</code>，<code>eventStore.read(ORDER_TYPE, orderId).get(0).payload()</code> 的类型为 <code>OrderCreatedEvent.class</code>。
     */
    @Test
    void payloadColumnShouldUseTextAndRoundTripTypedEvent() {
        Object dataType = entityManager.createNativeQuery("""
                select data_type
                from information_schema.columns
                where table_schema = current_schema()
                  and table_name = 'ddd4j_event_store'
                  and column_name = 'payload'
                """).getSingleResult();
        assertThat(dataType).isEqualTo("text");

        TestAggregateRootId orderId = new TestAggregateRootId("order-pg-1");
        eventStore.append(ORDER_TYPE, orderId, List.of(new OrderCreatedEvent(orderId)), 0L);

        assertThat(eventStore.read(ORDER_TYPE, orderId)).hasSize(1);
        assertThat(eventStore.read(ORDER_TYPE, orderId).get(0).payload())
                .isInstanceOf(OrderCreatedEvent.class);
    }

    /**
     * 测试专用聚合根标识，供仓储、事件序列化或命令路由构造独立的聚合实例。
     */
    private final static class TestAggregateRootId implements AggregateRootId {

        /**
         * Java 序列化使用的版本标识。
         */
        private static final long serialVersionUID = 0L;
        /**
         * 测试聚合注册和反序列化所用的实体类型标识。
         */
        private static final EntityType TYPE = new StringEntityType("Order");
        /**
         * 组成测试标识的原始值。
         */
        private final String value;

        /**
         * 设置 <code>value</code>，用于构造 <code>TestAggregateRootId</code> 测试实例。
         *
         * @param value 组成测试标识的原始值
         */
        @JsonCreator()
        private TestAggregateRootId(@JsonProperty("value") String value) {
            this.value = value;
        }

        /**
         * 返回当前值或标识公开的类型。
         *
         * @return 实际返回<code>TYPE</code>，供调用方取得当前值
         */
        @Override
        public EntityType getType() {
            return TYPE;
        }

        /**
         * 返回聚合或实体标识的业务字符串。
         *
         * @return 实际返回<code>value</code>，供调用方取得当前值
         */
        @Override
        public String asString() {
            return value;
        }

        /**
         * 返回带类型前缀的标识字符串。
         *
         * <p>测试夹具会调用 <code>TYPE.asString()</code>。</p>
         *
         * @return 实际返回<code>TYPE.asString() + ":" + value</code>
         */
        @Override
        public String asTypedString() {
            return TYPE.asString() + ":" + value;
        }

        /**
         * 读取<code>value</code> 字段。
         *
         * @return <code>value</code> 字段
         */
        @JsonProperty("value")
        public String value() {
            return value;
        }

        /**
         * 根据<code>value</code>比较对象的值是否相等。
         *
         * <p>只比较运行时类型相同的对象。</p>
         *
         * @param obj 用来比较或转换的输入对象
         * @return 对象满足此实现的相等条件时为真
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                return false;
            }
            TestAggregateRootId other = (TestAggregateRootId) obj;
            return Objects.equals(this.value, other.value);
        }

        /**
         * 计算与对象相等条件一致的哈希值。
         *
         * @return 由参与相等比较的状态计算出的哈希值
         */
        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(value);
            return result;
        }

        /**
         * 生成用于调试和日志的对象描述，包含<code>value</code>。
         *
         * @return 表示当前对象状态的字符串
         */
        @Override
        public String toString() {
            return "TestAggregateRootId[value=" + value + "]";
        }
    }

    /**
     * 表示订单创建的测试领域事件，供断言验证事件处理链。
     */
    static final class OrderCreatedEvent extends DomainEvent<TestAggregateRootId> {

        /**
         * 使用 <code>super()</code> 初始化 <code>OrderCreatedEvent</code> 测试夹具。
         */
        OrderCreatedEvent() {
            super();
        }

        /**
         * 使用 <code>super()</code> 初始化 <code>OrderCreatedEvent</code> 测试夹具。
         *
         * @param orderId 测试中要定位的订单标识
         */
        OrderCreatedEvent(TestAggregateRootId orderId) {
            super(new EntityIdPath(orderId));
        }
    }
}
