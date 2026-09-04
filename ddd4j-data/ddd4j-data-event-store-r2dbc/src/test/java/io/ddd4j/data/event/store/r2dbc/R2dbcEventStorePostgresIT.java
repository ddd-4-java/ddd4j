package io.ddd4j.data.event.store.r2dbc;

import java.util.Collections;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EntityIdRegistry;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.StringEntityType;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.Connection;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/** PostgreSQL 容器轨：验证 R2DBC EventStore 自身 DDL 与事件往返。 */
@Testcontainers(disabledWithoutDocker = true)
class R2dbcEventStorePostgresIT {

    private static final String ORDER_TYPE = "Order";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private PostgresqlConnectionFactory connectionFactory;
    private R2dbcEventStore eventStore;

    @BeforeEach
    void setUp() {
        connectionFactory = new PostgresqlConnectionFactory(PostgresqlConnectionConfiguration.builder()
                .host(POSTGRES.getHost())
                .port(POSTGRES.getMappedPort(5432))
                .database(POSTGRES.getDatabaseName())
                .username(POSTGRES.getUsername())
                .password(POSTGRES.getPassword())
                .build());
        EntityIdRegistry.register("Order", TestAggregateRootId::new);
        eventStore = new R2dbcEventStore(connectionFactory);
    }

    @AfterEach
    void tearDown() {
        EntityIdRegistry.unregister("Order");
        Connection connection = Mono.from(connectionFactory.create()).block();
        if (connection != null) {
            Mono.from(connection.createStatement("DROP TABLE IF EXISTS DDD4J_EVENT_STORE").execute()).block();
            Mono.from(connection.close()).block();
        }
    }

    @Test
    void appendAndReadShouldRoundTripTypedEventOnPostgres() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-pg-1");
        OrderCreatedEvent event = new OrderCreatedEvent(orderId);

        eventStore.append(ORDER_TYPE, orderId, Collections.singletonList(event), 0L);

        assertThat(eventStore.read(ORDER_TYPE, orderId)).hasSize(1);
        assertThat(eventStore.read(ORDER_TYPE, orderId).get(0).aggregateId().asString())
                .isEqualTo(orderId.asString());
        assertThat(eventStore.read(ORDER_TYPE, orderId).get(0).payload())
                .isInstanceOf(OrderCreatedEvent.class);
    }

    private record TestAggregateRootId(String value) implements AggregateRootId {

        private static final EntityType TYPE = new StringEntityType("Order");

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

    static final class OrderCreatedEvent extends DomainEvent<TestAggregateRootId> {

        OrderCreatedEvent() {
            super();
        }

        OrderCreatedEvent(TestAggregateRootId orderId) {
            super(new EntityIdPath(orderId));
        }
    }
}
