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
import org.junit.jupiter.api.*;
import org.testcontainers.DockerClientFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * MySQL 容器轨（进程级单例夹具）：验证 JDBI EventStore 在 MySQL 方言下的
 * 懒建表 DDL、持久化、读回、全局 position 顺序与乐观锁拒绝。
 *
 * <p>Docker 不可用时自动跳过（{@code assumeTrue}）。
 *
 * @since 1.0.x
 */
class JdbiEventStoreMysqlIT {

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
     * 测试执行前执行 <code>assumeTrue()</code>、<code>DockerClientFactory.instance()</code>、<code>MysqlEventStoreContainerSupport.MYSQL.isRunning()</code>、<code>System.out.println()</code> 完成资源准备。
     */
    @BeforeAll
    static void ensureContainerStarted() {
        // 进程级单例夹具：Docker 不可用则整组跳过
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is not available");
        assertTrue(MysqlEventStoreContainerSupport.MYSQL.isRunning());
        System.out.println("MYSQL_JDBI_IT_CONTAINER=started image="
                + MysqlEventStoreContainerSupport.MYSQL.getDockerImageName());
    }

    /**
     * 保留进程内共享的 MySQL 容器供其他集成测试复用。
     */
    @AfterAll
    static void noteSingletonLifecycle() {
        // 进程级单例容器不在此关闭：同一 JVM 内可能还有其它 IT 复用（JVM 退出时由 Ryuk 回收）
    }

    /**
     * 测试前依次初始化<code>jdbi</code>、初始化<code>eventStore</code>。
     */
    @BeforeEach
    void setUp() {
        jdbi = Jdbi.create(MysqlEventStoreContainerSupport.MYSQL.getJdbcUrl(),
                MysqlEventStoreContainerSupport.MYSQL.getUsername(),
                MysqlEventStoreContainerSupport.MYSQL.getPassword());
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
     * 验证 MySQL 事件存储在变更 JVM 时区后仍能往返同一个 UTC 时间点。
     *
     * @throws Exception 准备测试数据库、启动框架或执行业务合同失败
     */
    @Test
    void timestampShouldRoundTripAcrossTimezonesOnMysql() throws Exception {
        JdbiTimestampTimezoneContractTest.assertUtcRoundTrip(jdbi);
    }

    /**
     * 验证<code>events.size()</code> 等于 <code>2</code>，<code>events.get(0).version()</code> 等于 <code>1L</code>，<code>events.get(0).aggregateId().asString()</code> 等于 <code>orderId.asString()</code>。
     */
    @Test
    void appendAndReadShouldRoundTripTypedEventOnMysql() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-mysql-1");
        eventStore.append(ORDER_TYPE, orderId, Arrays.<DomainEvent<?>>asList(
                new OrderCreatedEvent("created"), new OrderCreatedEvent("renamed")), 0);

        List<StoredEvent> events = eventStore.read(ORDER_TYPE, orderId);
        assertEquals(2, events.size());
        assertEquals(1L, events.get(0).version());
        assertEquals(2L, events.get(1).version());
        assertInstanceOf(OrderCreatedEvent.class, events.get(0).payload());
        assertEquals("created", ((OrderCreatedEvent) events.get(0).payload()).getFact());
        assertEquals(orderId.asString(), events.get(0).aggregateId().asString());
        System.out.println("MYSQL_JDBI_IT_ROUNDTRIP=events:" + events.size());
    }

    /**
     * 验证被测调用 抛出 <code>AggregateVersionConflictException.class</code>，<code>conflict.expectedVersion()</code> 等于 <code>0L</code>，<code>eventStore.read(ORDER_TYPE, orderId).size()</code> 等于 <code>1</code>。
     */
    @Test
    void appendWithStaleVersionShouldRejectOnMysql() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-mysql-2");
        eventStore.append(ORDER_TYPE, orderId,
                Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("first")), 0);

        AggregateVersionConflictException conflict = assertThrows(AggregateVersionConflictException.class,
                () -> eventStore.append(ORDER_TYPE, orderId,
                        Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("stale")), 0));
        assertEquals(0L, conflict.expectedVersion());
        assertEquals(1L, conflict.actualVersion());
        assertEquals(1, eventStore.read(ORDER_TYPE, orderId).size());
        System.out.println("MYSQL_JDBI_IT_CONFLICT=expected:0,actual:1");
    }

    /**
     * 验证<code>events.size()</code> 等于 <code>2</code>，<code>events.get(0).position()</code> 等于 <code>1L</code>，<code>events.get(1).position()</code> 等于 <code>2L</code>。
     */
    @Test
    void readAllShouldPreserveGlobalPositionOrderAcrossAggregatesOnMysql() {
        eventStore.append(ORDER_TYPE, new TestAggregateRootId("order-mysql-a"),
                Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("a")), 0);
        eventStore.append(ORDER_TYPE, new TestAggregateRootId("order-mysql-b"),
                Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("b")), 0);

        List<StoredEvent> events = eventStore.readAll(1, 10);
        assertEquals(2, events.size());
        assertEquals(1L, events.get(0).position());
        assertEquals(2L, events.get(1).position());
        System.out.println("MYSQL_JDBI_IT_GLOBAL_ORDER=positions:1,2");
    }

    /**
     * 验证<code>rs.next()</code>为真，<code>rs.getString("data_type")</code> 等于 <code>"text"</code>。
     *
     * @throws Exception 执行数据库操作时失败
     */
    @Test
    void payloadColumnShouldUsePortableTextTypeOnMysql() throws Exception {
        // 懒建表：先 append 一次
        eventStore.append(ORDER_TYPE, new TestAggregateRootId("order-mysql-schema"),
                Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("seed")), 0);

        try (Connection conn = DriverManager.getConnection(MysqlEventStoreContainerSupport.MYSQL.getJdbcUrl(),
                MysqlEventStoreContainerSupport.MYSQL.getUsername(), MysqlEventStoreContainerSupport.MYSQL.getPassword());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "select data_type from information_schema.columns"
                             + " where lower(table_name) = lower('" + EventStoreConstants.TABLE_NAME + "')"
                             + " and lower(column_name) = lower('" + EventStoreConstants.COLUMN_PAYLOAD + "')")) {
            assertTrue(rs.next(), "payload column must exist");
            assertEquals("text", rs.getString("data_type"));
        }
        System.out.println("MYSQL_JDBI_IT_SCHEMA=payload:text");
    }

    /**
     * 测试专用聚合根标识，供仓储、事件序列化或命令路由构造独立的聚合实例。
     */
    private static final class TestAggregateRootId implements AggregateRootId {

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

        /**
         * 本用例写入或读取的事件事实。
         */
        private String fact;

        /**
         * 使用 <code>super()</code> 初始化 <code>OrderCreatedEvent</code> 测试夹具。
         */
        public OrderCreatedEvent() {
            super();
        }

        /**
         * 以给定状态构造 <code>OrderCreatedEvent</code> 测试对象。
         *
         * @param fact 本用例写入或读取的事件事实
         */
        private OrderCreatedEvent(String fact) {
            super(new EntityIdPath(new TestAggregateRootId("order-1")));
            this.fact = fact;
        }

        /**
         * 读取<code>fact</code> 字段。
         *
         * @return <code>fact</code> 字段
         */
        public String getFact() {
            return fact;
        }

        /**
         * 设置<code>fact</code> 字段。
         *
         * @param fact 要写入的值
         */
        public void setFact(String fact) {
            this.fact = fact;
        }
    }
}
