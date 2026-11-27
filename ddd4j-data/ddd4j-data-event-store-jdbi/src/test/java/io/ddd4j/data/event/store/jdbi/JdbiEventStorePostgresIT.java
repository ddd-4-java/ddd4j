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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PostgreSQL 容器轨：验证 JDBI EventStore 的真实 DDL、持久化与读回。
 *
 * <p>Docker 不可用时自动跳过（{@code disabledWithoutDocker = true}）。
 *
 * @since 1.0.x
 */
@Testcontainers(disabledWithoutDocker = true)
class JdbiEventStorePostgresIT {

    /** 保存 <code>PG</code> 对应的 <code>PostgreSQLContainer&lt;?&gt;</code> 数据。 */
    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    /** \u6d4b\u8bd5\u4e8b\u4ef6\u6d41\u4e2d\u4f7f\u7528\u7684\u805a\u5408\u7c7b\u578b\u540d\u79f0\u3002 */
    private static final String ORDER_TYPE = "Order";
    /** \u8d1f\u8d23\u83b7\u53d6 JDBC \u8fde\u63a5\u5e76\u6267\u884c SQL \u64cd\u4f5c\u7684\u8bbf\u95ee\u5165\u53e3\u3002 */
    private Jdbi jdbi;
    /** \u8bfb\u53d6\u53ca\u8ffd\u52a0\u9886\u57df\u4e8b\u4ef6\u7684\u4e8b\u4ef6\u5b58\u50a8\u3002 */
    private EventStore eventStore;

    /** \u6d4b\u8bd5\u524d\u4f9d\u6b21\u521d\u59cb\u5316<code>jdbi</code>\u3001\u521d\u59cb\u5316<code>eventStore</code>\u3002 */
    @BeforeEach
    void setUp() {
        jdbi = Jdbi.create(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        eventStore = new JdbiEventStore(jdbi);
    }

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>jdbi.useHandle()</code>\u3001<code>handle.execute()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void tearDown() {
        jdbi.useHandle(handle -> handle.execute("DROP TABLE IF EXISTS " + EventStoreConstants.TABLE_NAME));
    }

    /** \u9a8c\u8bc1<code>events</code> \u7684\u5927\u5c0f\u4e3a <code>2</code>\uff0c<code>events.get(0).aggregateType()</code> \u7b49\u4e8e <code>ORDER_TYPE</code>\uff0c<code>events.get(1).payload()</code> \u7684\u7c7b\u578b\u4e3a <code>OrderCreatedEvent.class</code>\u3002 */
    @Test
    void appendAndReadShouldRoundTripTypedEventOnPostgres() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-pg-1");
        eventStore.append(ORDER_TYPE, orderId, Arrays.<DomainEvent<?>>asList(
                new OrderCreatedEvent("created"), new OrderCreatedEvent("renamed")), 0);

        List<StoredEvent> events = eventStore.read(ORDER_TYPE, orderId);
        assertEquals(2, events.size());
        assertEquals(1L, events.get(0).version());
        assertEquals(2L, events.get(1).version());
        assertInstanceOf(OrderCreatedEvent.class, events.get(0).payload());
        assertEquals("created", ((OrderCreatedEvent) events.get(0).payload()).getFact());
        assertEquals(orderId.asString(), events.get(0).aggregateId().asString());
    }

    /** \u9a8c\u8bc1<code>eventStore.append(ORDER_TYPE, orderId, List.of(new OrderCreatedEvent(orderId)), 0)</code> \u629b\u51fa <code>AggregateVersionConflictException.class</code>\u3002 */
    @Test
    void appendWithStaleVersionShouldRejectOnPostgres() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-pg-2");
        eventStore.append(ORDER_TYPE, orderId,
                Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("first")), 0);

        assertThrows(AggregateVersionConflictException.class,
                () -> eventStore.append(ORDER_TYPE, orderId,
                        Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("stale")), 0));
    }

    /**
     * \u9a8c\u8bc1 PostgreSQL \u4e8b\u4ef6\u5b58\u50a8\u5728\u53d8\u66f4 JVM \u65f6\u533a\u540e\u4ecd\u80fd\u5f80\u8fd4\u540c\u4e00\u4e2a UTC \u65f6\u95f4\u70b9\u3002
     * @throws Exception \u51c6\u5907\u6d4b\u8bd5\u6570\u636e\u5e93\u3001\u542f\u52a8\u6846\u67b6\u6216\u6267\u884c\u4e1a\u52a1\u5408\u540c\u5931\u8d25
     */
    @Test
    void timestampShouldRoundTripAcrossTimezonesOnPostgres() throws Exception {
        JdbiTimestampTimezoneContractTest.assertUtcRoundTrip(jdbi);
    }

    /** \u9a8c\u8bc1<code>names</code> \u5305\u542b <code>"uk_position"</code>\u3002 */
    @Test
    void positionConstraintUsesSharedNameOnPostgres() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-pg-position-schema");
        eventStore.append(ORDER_TYPE, orderId,
                Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("seed")), 0);

        List<String> names = jdbi.withHandle(handle -> handle.createQuery(
                        "select constraint_name from information_schema.table_constraints "
                                + "where lower(table_name) = lower(:tableName) "
                                + "and constraint_type = 'UNIQUE'")
                .bind("tableName", EventStoreConstants.TABLE_NAME)
                .mapTo(String.class)
                .list());
        assertTrue(names.contains("uk_position"));
    }

    /**
     * 验证<code>rs.next()</code>为真，<code>rs.getString("data_type")</code> 等于 <code>"text"</code>。
     *
     * @throws Exception 执行数据库操作时失败
     */
    @Test
    void payloadColumnShouldUsePortableTextType() throws Exception {
        // 先 append 一次触发懒建表
        TestAggregateRootId orderId = new TestAggregateRootId("order-pg-schema");
        eventStore.append(ORDER_TYPE, orderId,
                Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("seed")), 0);

        try (Connection conn = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "select data_type from information_schema.columns"
                             + " where lower(table_name) = lower('" + EventStoreConstants.TABLE_NAME + "')"
                             + " and lower(column_name) = lower('" + EventStoreConstants.COLUMN_PAYLOAD + "')")) {
            assertTrue(rs.next(), "payload column must exist");
            assertEquals("text", rs.getString("data_type"));
        }
    }

    /** \u6d4b\u8bd5\u4e13\u7528\u805a\u5408\u6839\u6807\u8bc6\uff0c\u4f9b\u4ed3\u50a8\u3001\u4e8b\u4ef6\u5e8f\u5217\u5316\u6216\u547d\u4ee4\u8def\u7531\u6784\u9020\u72ec\u7acb\u7684\u805a\u5408\u5b9e\u4f8b\u3002 */
    private static final class TestAggregateRootId implements AggregateRootId {

        /** \u6d4b\u8bd5\u805a\u5408\u6ce8\u518c\u548c\u53cd\u5e8f\u5217\u5316\u6240\u7528\u7684\u5b9e\u4f53\u7c7b\u578b\u6807\u8bc6\u3002 */
        private static final EntityType TYPE = new StringEntityType("Order");

        /** \u7528\u4e8e\u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c\u3002 */
        private final String value;

        /**
         * \u4ee5\u7ed9\u5b9a\u72b6\u6001\u6784\u9020 <code>TestAggregateRootId</code> \u6d4b\u8bd5\u5bf9\u8c61\u3002
         * @param value \u7528\u4e8e\u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c
         */
        private TestAggregateRootId(String value) {
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
    }

    /**
     * 业务事件样例：无参构造 + JavaBean 属性（Jackson payload 序列化约定）。
     */
    public static final class OrderCreatedEvent extends DomainEvent<TestAggregateRootId> {

        /** 保存 <code>fact</code> 对应的 <code>String</code> 数据。 */
        private String fact;

        /** \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>OrderCreatedEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002 */
        public OrderCreatedEvent() {
            super();
        }

        /**
         * 初始化 <code>OrderCreatedEvent</code> 使用的 <code>fact</code>。
         *
         * @param fact 传入此操作的 <code>String</code> 类型 <code>fact</code>
         */
        private OrderCreatedEvent(String fact) {
            super(new EntityIdPath(new TestAggregateRootId("order-1")));
            this.fact = fact;
        }

        /**
         * 读取<code>fact</code> 的当前值。
         *
         * @return 返回表达式 <code>fact</code>
         */
        public String getFact() {
            return fact;
        }

        /**
         * 执行 <code>set Fact</code> 对应的处理。
         *
         * @param fact 传入此操作的 <code>String</code> 类型 <code>fact</code>
         */
        public void setFact(String fact) {
            this.fact = fact;
        }
    }
}
