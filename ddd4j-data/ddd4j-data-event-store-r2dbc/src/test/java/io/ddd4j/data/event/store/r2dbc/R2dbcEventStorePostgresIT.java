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

import io.ddd4j.core.constant.EventStoreConstants;
import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.core.ddd.event.*;
import io.r2dbc.spi.*;
import org.junit.jupiter.api.*;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.r2dbc.spi.ConnectionFactoryOptions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * PostgreSQL 容器轨（进程级单例夹具）：验证 R2DBC EventStore 在真实 PostgreSQL
 * 方言下的懒建表 DDL、持久化、读回、全局 position 顺序与乐观锁拒绝。
 *
 * <p>H2 内存轨（{@link R2dbcEventStoreTest}）验证契约逻辑，本容器轨验证 PostgreSQL 方言差异。
 * Docker 不可用时自动跳过（{@code assumeTrue}）。
 *
 * @since 1.0.x
 */
class R2dbcEventStorePostgresIT {

    private static final String ORDER_TYPE = "Order";
    private ConnectionFactory connectionFactory;
    private EventStore eventStore;

    @BeforeAll
    static void ensureContainerStarted() {
        // 进程级单例夹具：Docker 不可用则整组跳过
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is not available");
        assertThat(PostgresEventStoreContainerSupport.POSTGRES.isRunning()).isTrue();
        System.out.println("R2DBC_PG_IT_CONTAINER=started image="
                + PostgresEventStoreContainerSupport.POSTGRES.getDockerImageName());
    }

    @AfterAll
    static void noteSingletonLifecycle() {
        // 进程级单例容器不在此关闭：同一 JVM 内可能还有其它 IT 复用（JVM 退出时由 Ryuk 回收）
    }

    @BeforeEach
    void setUp() {
        PostgreSQLContainer<?> postgres = PostgresEventStoreContainerSupport.POSTGRES;
        connectionFactory = ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(DRIVER, "postgresql")
                .option(HOST, postgres.getHost())
                .option(PORT, postgres.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT))
                .option(DATABASE, postgres.getDatabaseName())
                .option(USER, postgres.getUsername())
                .option(PASSWORD, postgres.getPassword())
                .option(Option.valueOf("schema"), "public")
                .build());
        EntityIdRegistry.register(TestAggregateRootId.TYPE_NAME, TestAggregateRootId::new);
        eventStore = new R2dbcEventStore(connectionFactory);
    }

    @AfterEach
    void tearDown() {
        EntityIdRegistry.unregister(TestAggregateRootId.TYPE_NAME);
        Connection connection = Mono.from(connectionFactory.create()).block();
        if (connection != null) {
            Mono.from(connection.createStatement("DROP TABLE IF EXISTS " + EventStoreConstants.TABLE_NAME).execute()).block();
            Mono.from(connection.close()).block();
        }
    }

    @Test
    void appendAndReadShouldRoundTripTypedMetadataOnPostgres() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-pg-1");
        OrderCreatedEvent event = new OrderCreatedEvent(orderId);

        eventStore.append(ORDER_TYPE, orderId, Collections.singletonList(event), 0);

        List<StoredEvent> events = eventStore.read(ORDER_TYPE, orderId);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).aggregateType()).isEqualTo(ORDER_TYPE);
        assertThat(events.get(0).aggregateId()).isEqualTo(orderId);
        assertThat(events.get(0).payload()).isInstanceOf(OrderCreatedEvent.class);
        assertThat(events.get(0).eventId()).isEqualTo(event.getEventId());
        System.out.println("R2DBC_PG_IT_ROUNDTRIP=events:" + events.size());
    }

    @Test
    void versionRangeAndAggregateTypeIsolationShouldFollowCoreContractOnPostgres() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-pg-shared");
        eventStore.append(ORDER_TYPE, orderId, Arrays.<DomainEvent<?>>asList(
                new OrderCreatedEvent(orderId), new OrderCreatedEvent(orderId), new OrderCreatedEvent(orderId)), 0);
        eventStore.append("Invoice", orderId, Collections.singletonList(new OrderCreatedEvent(orderId)), 0);

        assertThat(eventStore.read(ORDER_TYPE, orderId)).extracting(StoredEvent::version)
                .containsExactly(1L, 2L, 3L);
        assertThat(eventStore.read(ORDER_TYPE, orderId, 1, 2))
                .extracting(StoredEvent::version).containsExactly(1L, 2L);
        assertThat(eventStore.read("Invoice", orderId)).hasSize(1);
        System.out.println("R2DBC_PG_IT_RANGE_AND_ISOLATION=ok");
    }

    @Test
    void readAllShouldPreserveGlobalPositionOrderOnPostgres() {
        eventStore.append(ORDER_TYPE, new TestAggregateRootId("order-pg-a"),
                Collections.singletonList(new OrderCreatedEvent(new TestAggregateRootId("order-pg-a"))), 0);
        eventStore.append(ORDER_TYPE, new TestAggregateRootId("order-pg-b"),
                Collections.singletonList(new OrderCreatedEvent(new TestAggregateRootId("order-pg-b"))), 0);

        List<StoredEvent> events = eventStore.readAll(1, 10);
        assertThat(events).extracting(StoredEvent::position).containsExactly(1L, 2L);
        System.out.println("R2DBC_PG_IT_GLOBAL_ORDER=positions:1,2");
    }

    @Test
    void appendWithStaleVersionShouldRejectOnPostgres() {
        TestAggregateRootId orderId = new TestAggregateRootId("order-pg-conflict");
        eventStore.append(ORDER_TYPE, orderId, Collections.singletonList(new OrderCreatedEvent(orderId)), 0);

        assertThatThrownBy(() -> eventStore.append(ORDER_TYPE, orderId,
                Collections.singletonList(new OrderCreatedEvent(orderId)), 0))
                .isInstanceOf(AggregateVersionConflictException.class)
                .satisfies(error -> {
                    AggregateVersionConflictException conflict = (AggregateVersionConflictException) error;
                    assertThat(conflict.expectedVersion()).isEqualTo(0L);
                    assertThat(conflict.actualVersion()).isEqualTo(1L);
                });
        assertThat(eventStore.read(ORDER_TYPE, orderId)).hasSize(1);
        System.out.println("R2DBC_PG_IT_CONFLICT=expected:0,actual:1");
    }

    /**
     * 测试聚合根标识（进程内注册 EntityIdRegistry 供 payload 反序列化）。
     */
    static final class TestAggregateRootId implements AggregateRootId {
        private static final String TYPE_NAME = "R2dbcPgOrder";
        private static final EntityType TYPE = new StringEntityType(TYPE_NAME);
        private final String value;

        TestAggregateRootId(String value) {
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
            return TYPE_NAME + ":" + value;
        }

        @Override
        public boolean equals(Object object) {
            if (!(object instanceof TestAggregateRootId)) {
                return false;
            }
            TestAggregateRootId other = (TestAggregateRootId) object;
            return value.equals(other.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }

    /**
     * 业务事件样例：无参构造 + Jackson payload 序列化约定。
     */
    static final class OrderCreatedEvent extends DomainEvent<TestAggregateRootId> {
        OrderCreatedEvent() {
            super();
        }

        OrderCreatedEvent(TestAggregateRootId orderId) {
            super(new EntityIdPath(orderId));
        }
    }
}
