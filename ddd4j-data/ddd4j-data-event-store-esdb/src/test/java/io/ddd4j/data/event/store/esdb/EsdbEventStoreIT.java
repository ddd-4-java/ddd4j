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

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

import com.eventstore.dbclient.EventStoreDBClient;
import com.eventstore.dbclient.EventStoreDBConnectionString;
import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.StringEntityType;
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
    /** \u6d4b\u8bd5\u4e8b\u4ef6\u6d41\u4e2d\u4f7f\u7528\u7684\u805a\u5408\u7c7b\u578b\u540d\u79f0\u3002 */
    private static final String ORDER_TYPE = "Order";
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
        store.append(ORDER_TYPE, id, List.of(new TestEvent(id), new TestEvent(id), new TestEvent(id)), 0);
        List<StoredEvent> all = store.read(ORDER_TYPE, id);
        assertThat(all).extracting(StoredEvent::version).containsExactly(1L, 2L, 3L);
        assertThat(store.read(ORDER_TYPE, id, 2, 3)).extracting(StoredEvent::version).containsExactly(2L, 3L);
        assertThat(store.readAll(0, 10)).allSatisfy(event -> assertThat(event.aggregateType()).isEqualTo(ORDER_TYPE));
    }

    /**
     * 验证聚合重建和区间查询可以越过第一批事件。
     */
    @Test
    void readsPastTheFirstBatch() {
        TestId id = new TestId("long-stream");
        for (int start = 0; start < 4098; start += 256) {
            int count = Math.min(256, 4098 - start);
            store.append(ORDER_TYPE, id, java.util.stream.IntStream.range(0, count)
                    .mapToObj(index -> new TestEvent(id)).toList(), start);
        }
        assertThat(store.read(ORDER_TYPE, id)).hasSize(4098);
        assertThat(store.readAll(0L, 4098)).extracting(StoredEvent::version)
                .containsExactlyElementsOf(java.util.stream.LongStream.rangeClosed(1L, 4098L).boxed().toList());
        assertThat(store.read(ORDER_TYPE, id, 4095, 4098)).extracting(StoredEvent::version)
                .containsExactly(4095L, 4096L, 4097L, 4098L);
        var tail = store.read(ORDER_TYPE, id, 4097, 4098);
        long lastPosition = tail.get(1).position();
        assertThat(store.readAll(tail.get(0).position() + 1L, 1)).extracting(StoredEvent::version)
                .containsExactly(4098L);
        assertThat(store.readAll(lastPosition, 1)).extracting(StoredEvent::version)
                .containsExactly(4098L);
        var otherPrefix = new EsdbEventStore(client, "after-long-stream-");
        TestId after = new TestId("after");
        otherPrefix.append(ORDER_TYPE, after, List.of(new TestEvent(after)), 0L);
        assertThat(otherPrefix.readAll(0L, 1)).hasSize(1);
    }

    /** \u9a8c\u8bc1<code>store.append(ORDER_TYPE, id, List.of(new TestEvent(id)), 0)</code> \u629b\u51fa <code>AggregateVersionConflictException.class</code>\uff0c<code>conflict.expectedVersion()</code> \u7b49\u4e8e <code>0L</code>\uff0c<code>conflict.actualVersion()</code> \u7b49\u4e8e <code>1L</code>\u3002 */
    @Test
    void staleVersionRejectsWithoutPartialWrites() {
        TestId id = new TestId("order-conflict");
        store.append(ORDER_TYPE, id, List.of(new TestEvent(id)), 0);

        assertThatThrownBy(() -> store.append(ORDER_TYPE, id, List.of(new TestEvent(id)), 0))
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

        store.append(ORDER_TYPE, id, List.of(root, derived), 0);

        List<StoredEvent> events = store.read(ORDER_TYPE, id);
        assertThat(events).hasSize(2);
        assertThat(events.get(0).correlationId()).isNull();
        assertThat(events.get(0).causationId()).isNull();
        assertThat(events.get(1).correlationId()).isEqualTo(root.getEventId());
        assertThat(events.get(1).causationId()).isEqualTo(root.getEventId());
    }

    /**
     * 测试用聚合根标识，封装订单标识字符串并关联固定实体类型。
     */
    final static class TestId implements AggregateRootId {

        /** Java \u5e8f\u5217\u5316\u4f7f\u7528\u7684\u7248\u672c\u6807\u8bc6\u3002 */
        private static final long serialVersionUID = 0L;

        /** \u7528\u4e8e\u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c\u3002 */
        private final String value;

        /** \u6d4b\u8bd5\u805a\u5408\u6ce8\u518c\u548c\u53cd\u5e8f\u5217\u5316\u6240\u7528\u7684\u5b9e\u4f53\u7c7b\u578b\u6807\u8bc6\u3002 */
        private static final EntityType TYPE = new StringEntityType("Order");

        /**
         * 获取值。
         * @return 方法执行结果。
         */
        @Override
        public EntityType getType() {
            return TYPE;
        }

        /**
         * asString() 方法。
         * @return 结果字符串。
         */
        @Override
        public String asString() {
            return value;
        }

        /**
         * asTypedString() 方法。
         * @return 结果字符串。
         */
        @Override
        public String asTypedString() {
            return TYPE.asString() + ":" + value;
        }

        /**
         * 创建聚合根标识实例。
         *
         * @param value 聚合根标识的字符串值
         */
        @JsonCreator()
        TestId(@JsonProperty("value") String value) {
            this.value = value;
        }

        /**
         * \u8bfb\u53d6<code>value</code> \u5b57\u6bb5\u3002
         * @return <code>value</code> \u5b57\u6bb5
         */
        @JsonProperty("value")
        public String value() {
            return value;
        }

        /**
         * 根据<code>value</code>比较对象的值是否相等。
         *
         * <p>只比较运行时类型相同的对象。</p>
         *
         * @param obj 用来比较或转换的输入对象
         * @return 对象满足此实现的相等条件时为真
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                return false;
            }
            TestId other = (TestId) obj;
            return Objects.equals(this.value, other.value);
        }

        /**
         * 计算与对象相等条件一致的哈希值。
         *
         * @return 由参与相等比较的状态计算出的哈希值
         */
        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(value);
            return result;
        }

        /**
         * 生成用于调试和日志的对象描述，包含<code>value</code>。
         *
         * @return 表示当前对象状态的字符串
         */
        @Override
        public String toString() {
            return "TestId[value=" + value + "]";
        }
    }

    /**
     * 类型说明。
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
