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
package io.ddd4j.data.event.store.jdbi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.ddd4j.core.constant.EventStoreConstants;
import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.core.ddd.event.*;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * PostgreSQL 容器轨：验证 JDBI EventStore 的真实 DDL、持久化与读取。
 */
@Testcontainers(disabledWithoutDocker = true)
class JdbiEventStorePostgresIT {

    /**
     * 为当前集成测试提供独立 PostgreSQL 服务的容器。
     */
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withTmpFs(Map.of("/var/lib/postgresql/data", "rw"));
    /**
     * 测试事件流中使用的聚合类型名称。
     */
    private static final String ORDER_TYPE = "Order";
    /**
     * 负责获取 JDBC 连接并执行 SQL 操作的访问入口。
     */
    private Jdbi jdbi;
    /**
     * 读取及追加领域事件的事件存储。
     */
    private EventStore eventStore;

    /**
     * 测试前依次初始化<code>jdbi</code>、初始化<code>eventStore</code>。
     */
    @BeforeEach
    void setUp() {
        jdbi = Jdbi.create(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        eventStore = new JdbiEventStore(jdbi);
    }

    /**
     * 测试结束后执行 <code>jdbi.useHandle()</code>、<code>handle.execute()</code> 完成资源回收或状态恢复。
     */
    @AfterEach
    void tearDown() {
        jdbi.useHandle(handle -> handle.execute("DROP TABLE IF EXISTS " + EventStoreConstants.TABLE_NAME));
    }

    /**
     * 验证<code>events</code> 的大小为 <code>2</code>，<code>events.get(0).aggregateType()</code> 等于 <code>ORDER_TYPE</code>，<code>events.get(1).payload()</code> 的类型为 <code>OrderCreatedEvent.class</code>。
     */
    @Test
    void appendAndReadShouldRoundTripTypedEventOnPostgres() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-pg-1");
        eventStore.append(ORDER_TYPE, orderId,
                List.of(new OrderCreatedEvent(orderId), new OrderCreatedEvent(orderId)), 0L);

        List<StoredEvent> events = eventStore.read(ORDER_TYPE, orderId);
        assertThat(events).hasSize(2);
        assertThat(events).extracting(StoredEvent::version).containsExactly(1L, 2L);
        assertThat(events.get(0).aggregateType()).isEqualTo(ORDER_TYPE);
        assertThat(events.get(0).aggregateId().asString()).isEqualTo(orderId.asString());
        assertThat(events.get(0).payload()).isInstanceOf(OrderCreatedEvent.class);
        assertThat(events.get(1).aggregateId().asString()).isEqualTo(orderId.asString());
        assertThat(events.get(1).payload()).isInstanceOf(OrderCreatedEvent.class);
        assertThat(eventStore.readAll(0L, 2)).extracting(StoredEvent::position).hasSize(2);
    }

    /**
     * 验证<code>eventStore.append(ORDER_TYPE, orderId, List.of(new OrderCreatedEvent(orderId)), 0)</code> 抛出 <code>AggregateVersionConflictException.class</code>。
     */
    @Test
    void appendWithStaleVersionShouldRejectOnPostgres() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-pg-2");
        eventStore.append(ORDER_TYPE, orderId, List.of(new OrderCreatedEvent(orderId)), 0);

        assertThrows(AggregateVersionConflictException.class,
                () -> eventStore.append(ORDER_TYPE, orderId,
                        List.of(new OrderCreatedEvent(orderId)), 0));
    }

    /**
     * 验证 PostgreSQL 事件存储在变更 JVM 时区后仍能往返同一个 UTC 时间点。
     *
     * @throws Exception 准备测试数据库、启动框架或执行业务合同失败
     */
    @Test
    void timestampShouldRoundTripAcrossTimezonesOnPostgres() throws Exception {
        JdbiTimestampTimezoneContractTest.assertUtcRoundTrip(jdbi);
    }

    /**
     * 验证<code>names</code> 包含 <code>"uk_position"</code>。
     */
    @Test
    void positionConstraintUsesSharedNameOnPostgres() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-pg-position-schema");
        eventStore.append(ORDER_TYPE, orderId, List.of(new OrderCreatedEvent(orderId)), 0);

        List<String> names = jdbi.withHandle(handle -> handle.createQuery(
                        "select constraint_name from information_schema.table_constraints "
                                + "where lower(table_name) = lower(:tableName) "
                                + "and constraint_type = 'UNIQUE'")
                .bind("tableName", EventStoreConstants.TABLE_NAME)
                .mapTo(String.class)
                .list());
        assertThat(names).contains("uk_position");
    }

    /**
     * 验证<code>dataType</code> 等于 <code>"text"</code>。
     */
    @Test
    void payloadColumnShouldUsePortableTextType() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-pg-schema");
        eventStore.append(ORDER_TYPE, orderId, List.of(new OrderCreatedEvent(orderId)), 0);

        String dataType = jdbi.withHandle(handle -> handle.createQuery(
                        "select data_type from information_schema.columns "
                                + "where lower(table_name) = lower(:tableName) "
                                + "and lower(column_name) = lower(:columnName)")
                .bind("tableName", EventStoreConstants.TABLE_NAME)
                .bind("columnName", EventStoreConstants.COLUMN_PAYLOAD)
                .mapTo(String.class)
                .one());
        assertThat(dataType).isEqualTo("text");
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
         * 用于组成测试标识的原始值。
         */
        private final String value;

        /**
         * 以给定状态构造 <code>TestAggregateRootId</code> 测试对象。
         *
         * @param value 用于组成测试标识的原始值
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
