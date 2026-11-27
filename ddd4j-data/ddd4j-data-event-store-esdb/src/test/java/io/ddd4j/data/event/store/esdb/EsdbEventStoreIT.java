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
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * EventStoreDB 的强类型 Testcontainers 验证。
 */
@Testcontainers(disabledWithoutDocker = true)
class EsdbEventStoreIT {
    /**
     * 为当前集成测试提供隔离服务的容器。
     */
    @Container
    static final GenericContainer<?> ESDB = new GenericContainer<>(DockerImageName.parse("eventstore/eventstore:24.10.0-bookworm-slim"))
            .withExposedPorts(2113)
            // MEM_DB 测试不验证跨容器持久性；隔离临时锁文件与测试数据。
            .withTmpFs(Map.of("/tmp", "rw,mode=1777",
                    "/var/lib/eventstore", "rw,uid=1000,gid=1000,mode=0700"))
            .withEnv("EVENTSTORE_CLUSTER_SIZE", "1")
            .withEnv("EVENTSTORE_RUN_PROJECTIONS", "All")
            .withEnv("EVENTSTORE_START_STANDARD_PROJECTIONS", "true")
            .withEnv("EVENTSTORE_INSECURE", "true")
            .withEnv("EVENTSTORE_MEM_DB", "true")
            .waitingFor(Wait.forHttp("/health/live").forPort(2113)
                    .forStatusCodeMatching(status -> status >= 200 && status < 300))
            .withStartupTimeout(Duration.ofMinutes(2));
    /**
     * 测试事件流中使用的聚合类型名称。
     */
    private static final String ORDER_TYPE = "Order";
    /**
     * 发送与接收测试请求的客户端。
     */
    private static EventStoreDBClient client;
    /**
     * 保存测试数据的内存存储。
     */
    private EventStore store;

    /**
     * 测试前依次初始化<code>client</code>。
     */
    @BeforeAll
    static void createClient() {
        client = EventStoreDBClient.create(EventStoreDBConnectionString.parseOrThrow(
                "esdb://" + ESDB.getHost() + ":" + ESDB.getMappedPort(2113)
                        + "?tls=false&maxDiscoverAttempts=3"));
    }

    /**
     * 测试后依次关闭<code>client</code>。
     */
    @AfterAll
    static void closeClient() {
        if (client != null) client.shutdown();
    }

    /**
     * 测试前依次初始化<code>store</code>。
     */
    @BeforeEach
    void setUp() {
        store = new EsdbEventStore(client, "it-" + System.nanoTime() + "-");
    }

    /**
     * 验证<code>event.aggregateType()</code> 等于 <code>ORDER_TYPE</code>。
     */
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
     * 验证<code>store.append(ORDER_TYPE, id, List.of(new TestEvent(id)), 0)</code> 抛出 <code>AggregateVersionConflictException.class</code>，<code>conflict.expectedVersion()</code> 等于 <code>0L</code>，<code>conflict.actualVersion()</code> 等于 <code>1L</code>。
     */
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

    /**
     * 验证<code>events</code> 的大小为 <code>2</code>，<code>events.get(0).correlationId()</code>为空，<code>events.get(1).causationId()</code> 等于 <code>root.getEventId()</code>。
     */
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

        /**
         * Java 序列化使用的版本标识。
         */
        private static final long serialVersionUID = 0L;
        /**
         * 测试聚合注册和反序列化所用的实体类型标识。
         */
        private static final EntityType TYPE = new StringEntityType("Order");
        /**
         * 用于组成测试标识的原始值。
         */
        private final String value;

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
         * 获取值。
         *
         * @return 方法执行结果。
         */
        @Override
        public EntityType getType() {
            return TYPE;
        }

        /**
         * asString() 方法。
         *
         * @return 结果字符串。
         */
        @Override
        public String asString() {
            return value;
        }

        /**
         * asTypedString() 方法。
         *
         * @return 结果字符串。
         */
        @Override
        public String asTypedString() {
            return TYPE.asString() + ":" + value;
        }

        /**
         * 读取<code>value</code> 字段。
         *
         * @return <code>value</code> 字段
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
     *
     * @author ddd4j
     * @since 3.0.x
     */
    static final class TestEvent extends DomainEvent<TestId> {
        /**
         * 使用 <code>super()</code> 初始化 <code>TestEvent</code> 测试夹具。
         */
        TestEvent() {
            super();
        }

        /**
         * 使用 <code>super()</code> 初始化 <code>TestEvent</code> 测试夹具。
         *
         * @param id 当前要查找的测试记录标识
         */
        TestEvent(TestId id) {
            super(new EntityIdPath(id));
        }

        /**
         * 使用 <code>super()</code> 初始化 <code>TestEvent</code> 测试夹具。
         *
         * @param id        当前要查找的测试记录标识
         * @param respondTo 用于初始化 <code>TestEvent</code> 的 <code>DomainEvent&lt;TestId&gt;</code> 值
         */
        TestEvent(TestId id, DomainEvent<TestId> respondTo) {
            super(new EntityIdPath(id), respondTo);
        }
    }
}
