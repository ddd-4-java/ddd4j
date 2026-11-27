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
import org.testcontainers.DockerClientFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * EventStoreDB 容器轨（进程级单例夹具）：在既有 {@link EsdbEventStoreIT} 契约之外，
 * 补充乐观并发冲突拒绝与因果元数据（correlationId/causationId）回读验证。
 *
 * <p>Docker 不可用时自动跳过（{@code assumeTrue}）。
 *
 * @since 1.0.x
 */
class EsdbEventStoreContainerIT {

    /** 保存 <code>"Order"</code>，供 <code>EsdbEventStoreContainerIT</code> 的处理流程复用。 */
    private static final String ORDER_TYPE = "Order";

    /** 保存 <code>client</code> 对应的 <code>EventStoreDBClient</code> 数据。 */
    private static EventStoreDBClient client;
    /** 保存 <code>store</code> 对应的 <code>EventStore</code> 数据。 */
    private EventStore store;

    /**
     * 初始化测试资源，调用 <code>DockerClientFactory.instance</code>、<code>ESDB.getHost</code>、<code>ESDB.getMappedPort</code>。
     *
     * @throws Exception 等待并发操作时失败
     */
    @BeforeAll
    static void createClient() throws Exception {
        // 进程级单例夹具：Docker 不可用则整组跳过
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is not available");
        // 容器级就绪探测：/health/live 2xx 即就绪（ESDB 单节点内存库秒级可达）
        String host = EsdbContainerSupport.ESDB.getHost();
        Integer port = EsdbContainerSupport.ESDB.getMappedPort(2113);
        long deadline = System.currentTimeMillis() + 120_000L;
        int lastStatus = -1;
        Exception lastError = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpURLConnection connection = (HttpURLConnection)
                        new URL("http://" + host + ":" + port + "/health/live").openConnection();
                connection.setConnectTimeout(2_000);
                connection.setReadTimeout(2_000);
                lastStatus = connection.getResponseCode();
                lastError = null;
                if (lastStatus >= 200 && lastStatus < 300) {
                    break;
                }
            } catch (IOException probeFailure) {
                lastError = probeFailure;
                lastStatus = -1;
            }
            Thread.sleep(1_000L);
        }
        if (lastStatus < 200 || lastStatus >= 300) {
            throw new IllegalStateException("ESDB /health/live not ready: status=" + lastStatus
                    + ", error=" + lastError);
        }
        System.out.println("ESDB_SINGLETON_IT_HEALTH=status:" + lastStatus);
        client = EventStoreDBClient.create(EventStoreDBConnectionString.parseOrThrow(
                "esdb://" + host + ":" + port + "?tls=false&maxDiscoverAttempts=3"));
        System.out.println("ESDB_SINGLETON_IT_CONTAINER=started image="
                + EsdbContainerSupport.ESDB.getDockerImageName() + " port:" + port);
    }

    /** 释放测试资源，调用 <code>client.shutdown</code>。 */
    @AfterAll
    static void closeClient() {
        if (client != null) {
            client.shutdown();
        }
    }

    /** 初始化测试资源，调用 <code>System.nanoTime</code>。 */
    @BeforeEach
    void setUp() {
        // 每用例独立流前缀，避免单例容器内跨用例串流
        store = new EsdbEventStore(client, "it-singleton-" + System.nanoTime() + "-");
    }

    /** 验证<code>store.append(ORDER_TYPE, id, Collections.singletonList(new TestEvent(id)), 0)</code> 抛出 <code>AggregateVersionConflictException.class</code>，<code>conflict.expectedVersion()</code> 等于 <code>0L</code>，<code>conflict.actualVersion()</code> 等于 <code>1L</code>。 */
    @Test
    void appendWithStaleVersionShouldRejectWithoutPartialWrites() {
        TestId id = new TestId("order-conflict");
        store.append(ORDER_TYPE, id, Collections.singletonList(new TestEvent(id)), 0);

        assertThatThrownBy(() -> store.append(ORDER_TYPE, id,
                Collections.singletonList(new TestEvent(id)), 0))
                .isInstanceOf(AggregateVersionConflictException.class)
                .satisfies(error -> {
                    AggregateVersionConflictException conflict = (AggregateVersionConflictException) error;
                    assertThat(conflict.expectedVersion()).isEqualTo(0L);
                    assertThat(conflict.actualVersion()).isEqualTo(1L);
                });
        assertThat(store.read(ORDER_TYPE, id)).extracting(StoredEvent::version).containsExactly(1L);
        System.out.println("ESDB_SINGLETON_IT_CONFLICT=expected:0,actual:1");
    }

    /** 验证<code>events</code> 的大小为 <code>2</code>，<code>events.get(0).correlationId()</code>为空，<code>events.get(0).causationId()</code>为空。 */
    @Test
    void causalityMetadataShouldRoundTripThroughBrokerStorage() {
        TestId id = new TestId("order-causality");
        TestEvent root = new TestEvent(id);
        // 以 root 为因果源构造派生事件：correlationId=root.eventId，causationId=root.eventId
        TestEvent derived = new TestEvent(id, root);

        store.append(ORDER_TYPE, id, Arrays.<DomainEvent<?>>asList(root, derived), 0);

        List<StoredEvent> events = store.read(ORDER_TYPE, id);
        assertThat(events).hasSize(2);
        assertThat(events.get(0).correlationId()).isNull();
        assertThat(events.get(0).causationId()).isNull();
        assertThat(events.get(1).correlationId()).isEqualTo(root.getEventId());
        assertThat(events.get(1).causationId()).isEqualTo(root.getEventId());
        System.out.println("ESDB_SINGLETON_IT_CAUSALITY=correlation:" + events.get(1).correlationId()
                + ",causation:" + events.get(1).causationId());
    }

    /** 为事件序列化回归提供固定类型的测试聚合标识。 */
    static final class TestId implements AggregateRootId {
        /** 保存 <code>TYPE</code> 对应的 <code>EntityType</code> 数据。 */
        private static final EntityType TYPE = new StringEntityType("Order");
        /** 保存 <code>value</code> 对应的 <code>String</code> 数据。 */
        private final String value;

        /**
         * 用给定值构造领域事件序列化测试使用的聚合标识。
         *
         * @param value 由当前成员保存或用于计算的值
         */
        TestId(String value) {
            this.value = value;
        }

        /**
         * 返回测试聚合标识的类型前缀。
         *
         * @return 返回表达式 <code>TYPE</code>
         */
        @Override
        public EntityType getType() {
            return TYPE;
        }

        /**
         * 返回不含类型前缀的聚合标识字符串。
         *
         * @return 返回表达式 <code>value</code>
         */
        @Override
        public String asString() {
            return value;
        }

        /**
         * 返回包含类型前缀的聚合标识字符串。
         *
         * @return 返回表达式 <code>TYPE.asString() + ":" + value</code>
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

        /**
         * 按 <code>hash Code</code> 的契约计算并返回结果。
         *
         * @return 返回表达式 <code>java.util.Objects.hashCode(value)</code>
         */
        @Override
        public int hashCode() {
            return java.util.Objects.hashCode(value);
        }

        /**
         * 按 <code>to String</code> 的契约计算并返回结果。
         *
         * @return 返回表达式 <code>"TestId{" + value + "}"</code>
         */
        @Override
        public String toString() {
            return "TestId{" + value + "}";
        }
    }

    /** 为测试场景提供 <code>DomainEvent</code> 的实现。 */
    static final class TestEvent extends DomainEvent<TestId> {
        /** 初始化 <code>TestEvent</code> 并委托其默认构造路径。 */
        TestEvent() {
            super();
        }

        /**
         * 初始化 <code>TestEvent</code> 并委托其默认构造路径。
         *
         * @param id 传入此操作的 <code>TestId</code> 类型 <code>id</code>
         */
        TestEvent(TestId id) {
            super(new EntityIdPath(id));
        }

        /**
         * 初始化 <code>TestEvent</code> 并委托其默认构造路径。
         *
         * @param id 传入此操作的 <code>TestId</code> 类型 <code>id</code>
         * @param respondTo 传入此操作的 <code>DomainEvent&lt;TestId&gt;</code> 类型 <code>respondTo</code>
         */
        TestEvent(TestId id, DomainEvent<TestId> respondTo) {
            super(new EntityIdPath(id), respondTo);
        }
    }
}
