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
package io.ddd4j.data.event.store.r2dbc;

import io.ddd4j.core.ddd.event.AggregateRootId;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

import io.ddd4j.core.ddd.event.*;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.Connection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PostgreSQL 容器轨：验证 R2DBC EventStore 自身 DDL 与事件往返。
 */
@Testcontainers(disabledWithoutDocker = true)
class R2dbcEventStorePostgresIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
    private static final String ORDER_TYPE = "Order";
    private PostgresqlConnectionFactory connectionFactory;
    private R2dbcEventStore eventStore;

    private final static class TestAggregateRootId implements AggregateRootId {

        private static final long serialVersionUID = 0L;

        private final String value;

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

        @JsonCreator()
        private TestAggregateRootId(@JsonProperty("value") String value) {
            this.value = value;
        }

        @JsonProperty("value")
        public String value() {
            return value;
        }

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

        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(value);
            return result;
        }

        @Override
        public String toString() {
            return "TestAggregateRootId[value=" + value + "]";
        }
    }

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

        eventStore.append(ORDER_TYPE, orderId, List.of(event), 0L);

        assertThat(eventStore.read(ORDER_TYPE, orderId)).hasSize(1);
        assertThat(eventStore.read(ORDER_TYPE, orderId).get(0).aggregateId().asString())
                .isEqualTo(orderId.asString());
        assertThat(eventStore.read(ORDER_TYPE, orderId).get(0).payload())
                .isInstanceOf(OrderCreatedEvent.class);
    } static final class OrderCreatedEvent extends DomainEvent<TestAggregateRootId> {

        OrderCreatedEvent() {
            super();
        }

        OrderCreatedEvent(TestAggregateRootId orderId) {
            super(new EntityIdPath(orderId));
        }
    }
}
