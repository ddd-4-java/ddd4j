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
package io.ddd4j.data.event.store.esdb;

import com.eventstore.dbclient.EventStoreDBClient;
import com.eventstore.dbclient.EventStoreDBConnectionString;
import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.core.ddd.event.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * EventStoreDB 的强类型 Testcontainers 验证。
 */
@Testcontainers(disabledWithoutDocker = true)
class EsdbEventStoreIT {
    /** \u4e3a\u5f53\u524d\u96c6\u6210\u6d4b\u8bd5\u63d0\u4f9b\u9694\u79bb\u670d\u52a1\u7684\u5bb9\u5668\u3002 */
    @Container
    static final GenericContainer<?> ESDB = new GenericContainer<>(DockerImageName.parse("eventstore/eventstore:24.10.0-bookworm-slim"))
            .withExposedPorts(2113)
            .withEnv("EVENTSTORE_CLUSTER_SIZE", "1")
            .withEnv("EVENTSTORE_RUN_PROJECTIONS", "All")
            .withEnv("EVENTSTORE_START_STANDARD_PROJECTIONS", "true")
            .withEnv("EVENTSTORE_INSECURE", "true")
            .withEnv("EVENTSTORE_MEM_DB", "true")
            .waitingFor(Wait.forHttp("/health/live").forPort(2113)
                    .forStatusCodeMatching(status -> status >= 200 && status < 300))
            .withStartupTimeout(Duration.ofMinutes(2));
    /** \u6d4b\u8bd5\u4e8b\u4ef6\u6d41\u4e2d\u4f7f\u7528\u7684\u805a\u5408\u7c7b\u578b\u540d\u79f0\u3002 */
    private static final String ORDER_TYPE = "Order";
    /** \u53d1\u9001\u4e0e\u63a5\u6536\u6d4b\u8bd5\u8bf7\u6c42\u7684\u5ba2\u6237\u7aef\u3002 */
    private static EventStoreDBClient client;
    /** \u4fdd\u5b58\u6d4b\u8bd5\u6570\u636e\u7684\u5185\u5b58\u5b58\u50a8\u3002 */
    private EventStore store;

    /** \u6d4b\u8bd5\u524d\u4f9d\u6b21\u521d\u59cb\u5316<code>client</code>\u3002 */
    @BeforeAll
    static void createClient() {
        client = EventStoreDBClient.create(EventStoreDBConnectionString.parseOrThrow(
                "esdb://" + ESDB.getHost() + ":" + ESDB.getMappedPort(2113)
                        + "?tls=false&maxDiscoverAttempts=3"));
    }

    /** \u6d4b\u8bd5\u540e\u4f9d\u6b21\u5173\u95ed<code>client</code>\u3002 */
    @AfterAll
    static void closeClient() {
        if (client != null) client.shutdown();
    }

    /** \u6d4b\u8bd5\u524d\u4f9d\u6b21\u521d\u59cb\u5316<code>store</code>\u3002 */
    @BeforeEach
    void setUp() {
        store = new EsdbEventStore(client, "it-" + System.nanoTime() + "-");
    }

    /** \u9a8c\u8bc1<code>event.aggregateType()</code> \u7b49\u4e8e <code>ORDER_TYPE</code>\u3002 */
    @Test
    void appendReadRangeAndGlobalReadFollowStrongContract() {
        TestId id = new TestId("order-1");
        store.append(ORDER_TYPE, id, java.util.Arrays.asList(new TestEvent(id), new TestEvent(id), new TestEvent(id)), 0);
        List<StoredEvent> all = store.read(ORDER_TYPE, id);
        assertThat(all).extracting(StoredEvent::version).containsExactly(1L, 2L, 3L);
        assertThat(store.read(ORDER_TYPE, id, 2, 3)).extracting(StoredEvent::version).containsExactly(2L, 3L);
        assertThat(store.readAll(0, 10)).allSatisfy(event -> assertThat(event.aggregateType()).isEqualTo(ORDER_TYPE));
    }

    /** \u9a8c\u8bc1<code>store.append(ORDER_TYPE, id, List.of(new TestEvent(id)), 0)</code> \u629b\u51fa <code>AggregateVersionConflictException.class</code>\uff0c<code>conflict.expectedVersion()</code> \u7b49\u4e8e <code>0L</code>\uff0c<code>conflict.actualVersion()</code> \u7b49\u4e8e <code>1L</code>\u3002 */
    @Test
    void staleVersionRejectsWithoutPartialWrites() {
        TestId id = new TestId("order-conflict");
        store.append(ORDER_TYPE, id, java.util.Arrays.<DomainEvent<?>>asList(new TestEvent(id)), 0);

        assertThatThrownBy(() -> store.append(ORDER_TYPE, id,
                java.util.Arrays.<DomainEvent<?>>asList(new TestEvent(id)), 0))
                .isInstanceOf(AggregateVersionConflictException.class)
                .satisfies(error -> {
                    AggregateVersionConflictException conflict = (AggregateVersionConflictException) error;
                    assertThat(conflict.expectedVersion()).isEqualTo(0L);
                    assertThat(conflict.actualVersion()).isEqualTo(1L);
                });
        assertThat(store.read(ORDER_TYPE, id)).extracting(StoredEvent::version).containsExactly(1L);
    }

    /** \u9a8c\u8bc1<code>events</code> \u7684\u5927\u5c0f\u4e3a <code>2</code>\uff0c<code>events.get(0).correlationId()</code>\u4e3a\u7a7a\uff0c<code>events.get(1).causationId()</code> \u7b49\u4e8e <code>root.getEventId()</code>\u3002 */
    @Test
    void causalityMetadataRoundTripsThroughEventStoreDb() {
        TestId id = new TestId("order-causality");
        TestEvent root = new TestEvent(id);
        TestEvent derived = new TestEvent(id, root);

        store.append(ORDER_TYPE, id, java.util.Arrays.<DomainEvent<?>>asList(root, derived), 0);

        List<StoredEvent> events = store.read(ORDER_TYPE, id);
        assertThat(events).hasSize(2);
        assertThat(events.get(0).correlationId()).isNull();
        assertThat(events.get(0).causationId()).isNull();
        assertThat(events.get(1).correlationId()).isEqualTo(root.getEventId());
        assertThat(events.get(1).causationId()).isEqualTo(root.getEventId());
    }

    /** \u6d4b\u8bd5\u7528\u805a\u5408\u6839\u6807\u8bc6\uff0c\u5c01\u88c5\u8ba2\u5355\u6807\u8bc6\u5b57\u7b26\u4e32\u5e76\u5173\u8054\u56fa\u5b9a\u5b9e\u4f53\u7c7b\u578b\u3002 */
    static final class TestId implements AggregateRootId {
        /** \u6d4b\u8bd5\u805a\u5408\u6ce8\u518c\u548c\u53cd\u5e8f\u5217\u5316\u6240\u7528\u7684\u5b9e\u4f53\u7c7b\u578b\u6807\u8bc6\u3002 */
        private static final EntityType TYPE = new StringEntityType("Order");
        /** \u7528\u4e8e\u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c\u3002 */
        private final String value;

        /**
         * \u521b\u5efa\u805a\u5408\u6839\u6807\u8bc6\u5b9e\u4f8b\u3002
         * @param value \u805a\u5408\u6839\u6807\u8bc6\u7684\u5b57\u7b26\u4e32\u503c
         */
        TestId(String value) {
            this.value = value;
        }

        /**
         * \u83b7\u53d6\u503c\u3002
         * @return \u65b9\u6cd5\u6267\u884c\u7ed3\u679c\u3002
         */
        @Override
        public EntityType getType() {
            return TYPE;
        }

        /**
         * asString() \u65b9\u6cd5\u3002
         * @return \u7ed3\u679c\u5b57\u7b26\u4e32\u3002
         */
        @Override
        public String asString() {
            return value;
        }

        /**
         * asTypedString() \u65b9\u6cd5\u3002
         * @return \u7ed3\u679c\u5b57\u7b26\u4e32\u3002
         */
        @Override
        public String asTypedString() {
            return TYPE.asString() + ":" + value;
        }

        /**
         * 按 <code>equals</code> 的契约计算并返回结果。
         *
         * @param o 传入此操作的 <code>Object</code> 类型 <code>o</code>
         * @return 返回表达式 <code>this == o || (o instanceof TestId &amp;&amp; java.util.Objects.equals(value, ((TestId) o).value))</code>
         */
        @Override
        public boolean equals(Object o) {
            return this == o || (o instanceof TestId && java.util.Objects.equals(value, ((TestId) o).value));
        }

        /** {@inheritDoc} */
        @Override
        public int hashCode() {
            return java.util.Objects.hashCode(value);
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return "TestId{" + value + "}";
        }
    }

    /**
     * \u7c7b\u578b\u8bf4\u660e\u3002
     * @author ddd4j
     * @since 3.0.x
     */
    static final class TestEvent extends DomainEvent<TestId> {
        /** \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>TestEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002 */
        TestEvent() {
            super();
        }

        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>TestEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param id \u5f53\u524d\u8981\u67e5\u627e\u7684\u6d4b\u8bd5\u8bb0\u5f55\u6807\u8bc6
         */
        TestEvent(TestId id) {
            super(new EntityIdPath(id));
        }

        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>TestEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param id \u5f53\u524d\u8981\u67e5\u627e\u7684\u6d4b\u8bd5\u8bb0\u5f55\u6807\u8bc6
         * @param respondTo \u7528\u4e8e\u521d\u59cb\u5316 <code>TestEvent</code> \u7684 <code>DomainEvent&lt;TestId&gt;</code> \u503c
         */
        TestEvent(TestId id, DomainEvent<TestId> respondTo) {
            super(new EntityIdPath(id), respondTo);
        }
    }
}
