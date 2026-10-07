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

import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.core.constant.EventStoreConstants;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.StringEntityType;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    private static final String ORDER_TYPE = "Order";

    @BeforeAll
    static void ensureContainerStarted() {
        // 进程级单例夹具：Docker 不可用则整组跳过
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is not available");
        assertTrue(MysqlEventStoreContainerSupport.MYSQL.isRunning());
        System.out.println("MYSQL_JDBI_IT_CONTAINER=started image="
                + MysqlEventStoreContainerSupport.MYSQL.getDockerImageName());
    }

    @AfterAll
    static void noteSingletonLifecycle() {
        // 进程级单例容器不在此关闭：同一 JVM 内可能还有其它 IT 复用（JVM 退出时由 Ryuk 回收）
    }

    private Jdbi jdbi;
    private EventStore eventStore;

    @BeforeEach
    void setUp() {
        jdbi = Jdbi.create(MysqlEventStoreContainerSupport.MYSQL.getJdbcUrl(),
                MysqlEventStoreContainerSupport.MYSQL.getUsername(),
                MysqlEventStoreContainerSupport.MYSQL.getPassword());
        eventStore = new JdbiEventStore(jdbi);
    }

    @AfterEach
    void tearDown() {
        jdbi.useHandle(handle -> handle.execute("DROP TABLE IF EXISTS " + EventStoreConstants.TABLE_NAME));
    }

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

    private static final class TestAggregateRootId implements AggregateRootId {

        private static final EntityType TYPE = new StringEntityType("Order");

        private final String value;

        private TestAggregateRootId(String value) {
            this.value = value;
        }

        @Override
        public EntityType getType() {
            return TYPE;
        }

        @Override
        public String asString() {
            return value;
        }

        @Override
        public String asTypedString() {
            return TYPE.asString() + ":" + value;
        }
    }

    /** 业务事件样例：无参构造 + JavaBean 属性（Jackson payload 序列化约定）。 */
    public static final class OrderCreatedEvent extends DomainEvent<TestAggregateRootId> {

        private String fact;

        public OrderCreatedEvent() {
            super();
        }

        private OrderCreatedEvent(String fact) {
            super(new EntityIdPath(new TestAggregateRootId("order-1")));
            this.fact = fact;
        }

        public String getFact() {
            return fact;
        }

        public void setFact(String fact) {
            this.fact = fact;
        }
    }
}
