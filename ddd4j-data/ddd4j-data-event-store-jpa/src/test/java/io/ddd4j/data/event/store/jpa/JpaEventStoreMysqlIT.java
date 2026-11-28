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
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.StringEntityType;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
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
 * MySQL 容器轨（进程级单例夹具）：验证 JPA EventStore 在 MySQL 方言下的
 * 真实 DDL、持久化、读回与乐观锁拒绝。
 *
 * <p>Docker 不可用时自动跳过（{@code assumeTrue} + {@code disabledWithoutDocker}）。
 *
 * @since 1.0.x
 */
class JpaEventStoreMysqlIT {

    /** \u6d4b\u8bd5\u4e8b\u4ef6\u6d41\u4e2d\u4f7f\u7528\u7684\u805a\u5408\u7c7b\u578b\u540d\u79f0\u3002 */
    private static final String ORDER_TYPE = "Order";

    /** \u7528\u4e8e\u521b\u5efa JPA \u5b9e\u4f53\u7ba1\u7406\u5668\u7684\u5de5\u5382\u3002 */
    private static EntityManagerFactory entityManagerFactory;
    /** \u6267\u884c JPA \u67e5\u8be2\u548c\u6301\u4e45\u5316\u64cd\u4f5c\u7684\u5b9e\u4f53\u7ba1\u7406\u5668\u3002 */
    private EntityManager entityManager;
    /** \u8bfb\u53d6\u53ca\u8ffd\u52a0\u9886\u57df\u4e8b\u4ef6\u7684\u4e8b\u4ef6\u5b58\u50a8\u3002 */
    private EventStore eventStore;

    /**
     * \u6d4b\u8bd5\u524d\u4f9d\u6b21\u521d\u59cb\u5316<code>entityManagerFactory</code>\u3002
     * @throws Exception \u6267\u884c\u6570\u636e\u5e93\u64cd\u4f5c\u65f6\u5931\u8d25
     */
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
        cfg.setProperty("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
        cfg.setProperty("hibernate.show_sql", "false");
        cfg.addAnnotatedClass(StoredEventEntity.class);
        entityManagerFactory = cfg.buildSessionFactory();
        System.out.println("MYSQL_IT_CONTAINER=started image=" + MysqlEventStoreContainerSupport.MYSQL.getDockerImageName());
    }

    /** \u6d4b\u8bd5\u540e\u4f9d\u6b21\u5173\u95ed<code>entityManagerFactory</code>\u3002 */
    @AfterAll
    static void closeEntityManagerFactory() {
        if (entityManagerFactory != null && entityManagerFactory.isOpen()) {
            entityManagerFactory.close();
        }
        // 进程级单例容器不在此关闭：同一 JVM 内可能还有其它 IT 复用（JVM 退出时由 Ryuk 回收）
    }

    /** \u6d4b\u8bd5\u524d\u4f9d\u6b21\u521d\u59cb\u5316<code>entityManager</code>\u3001\u521d\u59cb\u5316<code>eventStore</code>\u3002 */
    @BeforeEach
    void setUp() {
        entityManager = entityManagerFactory.createEntityManager();
        eventStore = new JpaEventStore(entityManager);
    }

    /**
     * \u6d4b\u8bd5\u540e\u4f9d\u6b21\u5173\u95ed<code>entityManager</code>\u3002
     * @throws Exception \u6267\u884c\u6570\u636e\u5e93\u64cd\u4f5c\u65f6\u5931\u8d25
     */
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

    /** \u9a8c\u8bc1<code>events.size()</code> \u7b49\u4e8e <code>2</code>\uff0c<code>events.get(0).version()</code> \u7b49\u4e8e <code>1L</code>\uff0c<code>events.get(0).aggregateId().asString()</code> \u7b49\u4e8e <code>orderId.asString()</code>\u3002 */
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

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>AggregateVersionConflictException.class</code>\uff0c<code>conflict.expectedVersion()</code> \u7b49\u4e8e <code>0L</code>\uff0c<code>eventStore.read(ORDER_TYPE, orderId).size()</code> \u7b49\u4e8e <code>1</code>\u3002 */
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

    /**
     * \u9a8c\u8bc1<code>rs.next()</code>\u4e3a\u771f\uff0c<code>rs.getString("data_type")</code> \u7b49\u4e8e <code>"text"</code>\u3002
     * @throws Exception \u6267\u884c\u6570\u636e\u5e93\u64cd\u4f5c\u65f6\u5931\u8d25
     */
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

        /** \u672c\u7528\u4f8b\u5199\u5165\u6216\u8bfb\u53d6\u7684\u4e8b\u4ef6\u4e8b\u5b9e\u3002 */
        private String fact;

        /** \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>OrderCreatedEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002 */
        public OrderCreatedEvent() {
            super();
        }

        /**
         * \u4ee5\u7ed9\u5b9a\u72b6\u6001\u6784\u9020 <code>OrderCreatedEvent</code> \u6d4b\u8bd5\u5bf9\u8c61\u3002
         * @param fact \u672c\u7528\u4f8b\u5199\u5165\u6216\u8bfb\u53d6\u7684\u4e8b\u4ef6\u4e8b\u5b9e
         */
        private OrderCreatedEvent(String fact) {
            super(new EntityIdPath(new TestAggregateRootId("order-1")));
            this.fact = fact;
        }

        /**
         * \u8bfb\u53d6<code>fact</code> \u5b57\u6bb5\u3002
         * @return <code>fact</code> \u5b57\u6bb5
         */
        public String getFact() {
            return fact;
        }

        /**
         * \u8bbe\u7f6e<code>fact</code> \u5b57\u6bb5\u3002
         * @param fact \u8981\u5199\u5165\u7684\u503c
         */
        public void setFact(String fact) {
            this.fact = fact;
        }
    }
}
