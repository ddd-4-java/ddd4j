package io.ddd4j.data.eventstore.jpa;

import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.cqrs.eventstore.EventStoreConstants;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PostgreSQL 容器轨：验证 JPA EventStore 的真实 DDL、持久化与读回。
 *
 * <p>Docker 不可用时自动跳过（{@code disabledWithoutDocker = true}）。
 *
 * @since 1.0.x
 */
@Testcontainers(disabledWithoutDocker = true)
class JpaEventStorePostgresIT {

    private static final String ORDER_TYPE = "Order";

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    private static EntityManagerFactory entityManagerFactory;
    private EntityManager entityManager;
    private EventStore eventStore;

    @BeforeAll
    static void createEntityManagerFactory() throws Exception {
        // 用原生 JDBC 创建 DDL（TEXT payload，与 2.0/3.0 统一 schema 对齐）
try (Connection conn = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()))
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE " + EventStoreConstants.TABLE_NAME + " ("
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
        cfg.setProperty("hibernate.connection.driver_class", "org.postgresql.Driver");
        cfg.setProperty("hibernate.connection.url", PG.getJdbcUrl());
        cfg.setProperty("hibernate.connection.username", PG.getUsername());
        cfg.setProperty("hibernate.connection.password", PG.getPassword());
        cfg.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQL10Dialect");
        cfg.setProperty("hibernate.show_sql", "false");
        cfg.addAnnotatedClass(StoredEventEntity.class);
        entityManagerFactory = cfg.buildSessionFactory();
    }

    @AfterAll
    static void closeEntityManagerFactory() {
        if (entityManagerFactory != null && entityManagerFactory.isOpen()) {
            entityManagerFactory.close();
        }
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
try (Connection conn = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()))
             Statement stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE TABLE " + EventStoreConstants.TABLE_NAME);
        }
    }

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

    @Test
    void appendWithStaleVersionShouldRejectOnPostgres() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-pg-2");
        eventStore.append(ORDER_TYPE, orderId,
                Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("first")), 0);

        assertThrows(AggregateVersionConflictException.class,
                () -> eventStore.append(ORDER_TYPE, orderId,
                        Collections.<DomainEvent<?>>singletonList(new OrderCreatedEvent("stale")), 0));
    }

    @Test
    void payloadColumnShouldUsePortableTextType() throws Exception {
        // PG 将列名折叠为小写；information_schema 中 table_schema = current_schema()
try (Connection conn = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()))
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "select data_type from information_schema.columns"
                             + " where lower(table_name) = lower('" + EventStoreConstants.TABLE_NAME + "')"
                             + " and lower(column_name) = lower('" + EventStoreConstants.COLUMN_PAYLOAD + "')")) {
            assertTrue(rs.next(), "payload column must exist");
            assertEquals("text", rs.getString("data_type"));
        }
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
