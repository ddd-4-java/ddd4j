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
package io.ddd4j.data.event.store.jpa;

import io.ddd4j.core.constant.EventStoreConstants;
import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.core.ddd.event.*;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.*;
import org.testcontainers.DockerClientFactory;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
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
 * MySQL 容器轨（进程级单例夹具）：验证 JPA EventStore 在 MySQL 方言下的
 * 真实 DDL、持久化、读回与乐观锁拒绝。
 *
 * <p>Docker 不可用时自动跳过（{@code assumeTrue} + {@code disabledWithoutDocker}）。
 *
 * @since 1.0.x
 */
class JpaEventStoreMysqlIT {

    private static final String ORDER_TYPE = "Order";

    private static EntityManagerFactory entityManagerFactory;
    private EntityManager entityManager;
    private EventStore eventStore;

    @BeforeAll
    static void startContainerAndCreateEntityManagerFactory() throws Exception {
        // 进程级单例夹具：Docker 不可用则整组跳过
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is not available");
        MysqlEventStoreContainerSupport.MYSQL.isRunning();
        // 用原生 JDBC 创建 DDL（TEXT payload，与 2.0/3.0 统一 schema 对齐）
        try (Connection conn = DriverManager.getConnection(MysqlEventStoreContainerSupport.MYSQL.getJdbcUrl(),
                MysqlEventStoreContainerSupport.MYSQL.getUsername(), MysqlEventStoreContainerSupport.MYSQL.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS " + EventStoreConstants.TABLE_NAME + " ("
                    + EventStoreConstants.COLUMN_AGGREGATE_ID + " VARCHAR(255) NOT NULL, "
                    + EventStoreConstants.COLUMN_AGGREGATE_TYPE + " VARCHAR(255) NOT NULL, "
                    + EventStoreConstants.COLUMN_VERSION + " BIGINT NOT NULL, "
                    + EventStoreConstants.COLUMN_POSITION + " BIGINT NOT NULL, "
                    + EventStoreConstants.COLUMN_EVENT_TYPE + " VARCHAR(512) NOT NULL, "
                    + EventStoreConstants.COLUMN_EVENT_ID + " VARCHAR(64), "
                    + EventStoreConstants.COLUMN_CORRELATION_ID + " VARCHAR(64), "
                    + EventStoreConstants.COLUMN_CAUSATION_ID + " VARCHAR(64), "
                    + EventStoreConstants.COLUMN_PAYLOAD + " TEXT NOT NULL, "
                    + EventStoreConstants.COLUMN_TIMESTAMP + " TIMESTAMP NOT NULL, "
                    + "PRIMARY KEY (" + EventStoreConstants.COLUMN_AGGREGATE_TYPE + ", "
                    + EventStoreConstants.COLUMN_AGGREGATE_ID + ", " + EventStoreConstants.COLUMN_VERSION + "), "
                    + "CONSTRAINT uk_" + EventStoreConstants.TABLE_NAME + "_position UNIQUE ("
                    + EventStoreConstants.COLUMN_POSITION + ")"
                    + ")");
        }
        Configuration cfg = new Configuration();
        cfg.setProperty("hibernate.connection.driver_class", "com.mysql.cj.jdbc.Driver");
        cfg.setProperty("hibernate.connection.url", MysqlEventStoreContainerSupport.MYSQL.getJdbcUrl());
        cfg.setProperty("hibernate.connection.username", MysqlEventStoreContainerSupport.MYSQL.getUsername());
        cfg.setProperty("hibernate.connection.password", MysqlEventStoreContainerSupport.MYSQL.getPassword());
        cfg.setProperty("hibernate.connection.isolation", String.valueOf(Connection.TRANSACTION_READ_COMMITTED));
        cfg.setProperty("hibernate.dialect", "org.hibernate.dialect.MySQL8Dialect");
        cfg.setProperty("hibernate.show_sql", "false");
        cfg.addAnnotatedClass(StoredEventEntity.class);
        entityManagerFactory = cfg.buildSessionFactory();
        System.out.println("MYSQL_IT_CONTAINER=started image=" + MysqlEventStoreContainerSupport.MYSQL.getDockerImageName());
    }

    @AfterAll
    static void closeEntityManagerFactory() {
        if (entityManagerFactory != null && entityManagerFactory.isOpen()) {
            entityManagerFactory.close();
        }
        // 进程级单例容器不在此关闭：同一 JVM 内可能还有其它 IT 复用（JVM 退出时由 Ryuk 回收）
    }

    @BeforeEach
    void setUp() {
        entityManager = entityManagerFactory.createEntityManager();
        eventStore = new JpaEventStore(entityManager);
    }

    @AfterEach
    void tearDown() throws Exception {
        EntityTransaction tx = entityManager.getTransaction();
        if (tx.isActive()) {
            tx.rollback();
        }
        entityManager.close();
        try (Connection conn = DriverManager.getConnection(MysqlEventStoreContainerSupport.MYSQL.getJdbcUrl(),
                MysqlEventStoreContainerSupport.MYSQL.getUsername(), MysqlEventStoreContainerSupport.MYSQL.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE TABLE " + EventStoreConstants.TABLE_NAME);
        }
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
        System.out.println("MYSQL_IT_ROUNDTRIP=events:" + events.size());
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
        entityManager.clear();
        assertEquals(1, eventStore.read(ORDER_TYPE, orderId).size());
        System.out.println("MYSQL_IT_CONFLICT=expected:0,actual:1");
    }

    @Test
    void payloadColumnShouldUsePortableTextTypeOnMysql() throws Exception {
        // 先 append 一次确保表已建
        TestAggregateRootId orderId = new TestAggregateRootId("order-mysql-schema");
        eventStore.append(ORDER_TYPE, orderId,
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
        System.out.println("MYSQL_IT_SCHEMA=payload:text");
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

    /**
     * 业务事件样例：无参构造 + JavaBean 属性（Jackson payload 序列化约定）。
     */
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
