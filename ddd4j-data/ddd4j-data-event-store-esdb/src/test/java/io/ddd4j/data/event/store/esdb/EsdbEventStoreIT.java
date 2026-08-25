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
import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link EsdbEventStore} 集成测试——Testcontainers EventStoreDB 全量契约验证。
 *
 * <p>覆盖场景：
 * <ol>
 *   <li>append 后 read 单流按版本顺序返回</li>
 *   <li>乐观锁：expectedVersion 不匹配抛 {@link IllegalStateException} 且不落库</li>
 *   <li>readAll 按 commitPosition 升序 + limit 分页</li>
 *   <li>事件 payload 往返（写入→读出 event 内容一致，类型还原）</li>
 *   <li>多聚合 append 交错时全局 position 单调</li>
 * </ol>
 *
 * <p>镜像选择：{@code eventstore/eventstore:24.10.0-bookworm-slim}（2024 年 LTS 版本，
 * 基于 Debian Bookworm，体积小且稳定）。配置 {@code INSECURE=true} 禁用 TLS
 * 以简化测试环境（单节点、无证书），{@code EVENTSTORE_ENABLE_ATOM_PUB_OVER_HTTP=true}
 * 启用 AtomPub HTTP 端点（便于调试，不影响 gRPC 核心功能）。
 *
 * <p>需要本地 Docker 可用；无 Docker 环境时 Testcontainers 会自动跳过。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
@Testcontainers(disabledWithoutDocker = true)
class EsdbEventStoreIT {

    /**
     * EventStoreDB 容器：
     * <ul>
     *   <li>镜像：eventstore/eventstore:24.10.0-bookworm-slim（2024 LTS）</li>
     *   <li>INSECURE=true：单节点禁用 TLS，简化测试连接</li>
     *   <li>EVENTSTORE_ENABLE_ATOM_PUB_OVER_HTTP=true：启用 HTTP API（调试用）</li>
     *   <li>EVENTSTORE_MEM_DB=true：内存模式，不持久化到磁盘（测试隔离）</li>
     *   <li>端口：2113（gRPC + HTTP 共用）</li>
     * </ul>
     */
    @Container
    private static final GenericContainer<?> ESDB = new GenericContainer<>(
            DockerImageName.parse("eventstore/eventstore:24.10.0-bookworm-slim"))
            .withExposedPorts(2113)
            .withEnv("INSECURE", "true")
            .withEnv("EVENTSTORE_ENABLE_ATOM_PUB_OVER_HTTP", "true")
            .withEnv("EVENTSTORE_MEM_DB", "true")
            .withEnv("EVENTSTORE_START_STANDARD_PROJECTIONS", "true");

    private static EventStoreDBClient client;
    private EventStore eventStore;

    @BeforeAll
    static void createClient() throws Exception {
        String connectionString = "esdb://localhost:" + ESDB.getMappedPort(2113) + "?tls=false&maxDiscoverAttempts=10";
        client = EventStoreDBClient.create(
                EventStoreDBConnectionString.parseOrThrow(connectionString));
    }

    @AfterAll
    static void closeClient() {
        if (client != null) {
            client.shutdown();
        }
    }

    @BeforeEach
    void setUp() {
        // 每个测试使用唯一前缀，确保流隔离
        eventStore = new EsdbEventStore(client, "test-" + System.nanoTime() + "-");
    }

    @Test
    @DisplayName("append 后 read 单流按版本顺序返回")
    void appendThenReadShouldReturnEventsInVersionOrder() {
        String aggregateId = "order-001";
        OrderCreatedEvent event1 = new OrderCreatedEvent("order-001", "customer-001");
        OrderItemAddedEvent event2 = new OrderItemAddedEvent("order-001", "item-001", 2);
        OrderItemAddedEvent event3 = new OrderItemAddedEvent("order-001", "item-002", 1);

        eventStore.append(aggregateId, List.of(event1, event2, event3), 0);

        List<StoredEvent> events = eventStore.read(aggregateId);
        assertThat(events).hasSize(3);
        assertThat(events.get(0).version()).isEqualTo(0);
        assertThat(events.get(1).version()).isEqualTo(1);
        assertThat(events.get(2).version()).isEqualTo(2);
        assertThat(events.get(0).aggregateId()).contains("order-001");
    }

    @Test
    @DisplayName("乐观锁：expectedVersion 不匹配抛 IllegalStateException 且不落库")
    void appendWithWrongExpectedVersionShouldThrowAndNotPersist() {
        String aggregateId = "order-002";
        OrderCreatedEvent event1 = new OrderCreatedEvent("order-002", "customer-002");
        eventStore.append(aggregateId, List.of(event1), 0);

        OrderItemAddedEvent event2 = new OrderItemAddedEvent("order-002", "item-003", 5);

        assertThatThrownBy(() -> eventStore.append(aggregateId, List.of(event2), 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Version conflict");

        // 验证未多落库
        List<StoredEvent> events = eventStore.read(aggregateId);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).version()).isEqualTo(0);
    }

    @Test
    @DisplayName("readAll 按 commitPosition 升序 + limit 分页")
    void readAllShouldReturnEventsInPositionOrderWithLimit() {
        String aggregateId1 = "order-010";
        String aggregateId2 = "order-020";
        OrderCreatedEvent event1 = new OrderCreatedEvent("order-010", "customer-010");
        OrderCreatedEvent event2 = new OrderCreatedEvent("order-020", "customer-020");
        OrderItemAddedEvent event3 = new OrderItemAddedEvent("order-010", "item-010", 1);

        eventStore.append(aggregateId1, List.of(event1), 0);
        eventStore.append(aggregateId2, List.of(event2), 0);
        eventStore.append(aggregateId1, List.of(event3), 1);

        List<StoredEvent> allEvents = eventStore.readAll(0, 10);
        assertThat(allEvents).hasSize(3);
        // position（commitPosition）应该单调递增
        assertThat(allEvents.get(0).position()).isLessThan(allEvents.get(1).position());
        assertThat(allEvents.get(1).position()).isLessThan(allEvents.get(2).position());

        // 测试 limit
        List<StoredEvent> limitedEvents = eventStore.readAll(0, 2);
        assertThat(limitedEvents).hasSize(2);
    }

    @Test
    @DisplayName("事件 payload 往返：写入→读出 event 内容一致，类型还原")
    void eventPayloadShouldSurviveRoundTrip() {
        String aggregateId = "order-030";
        OrderCreatedEvent originalEvent = new OrderCreatedEvent("order-030", "customer-030");

        eventStore.append(aggregateId, List.of(originalEvent), 0);

        List<StoredEvent> events = eventStore.read(aggregateId);
        assertThat(events).hasSize(1);

        Object restoredEvent = events.get(0).event();
        assertThat(restoredEvent).isInstanceOf(OrderCreatedEvent.class);

        OrderCreatedEvent typedEvent = (OrderCreatedEvent) restoredEvent;
        assertThat(typedEvent.orderId()).isEqualTo("order-030");
        assertThat(typedEvent.customerId()).isEqualTo("customer-030");
    }

    @Test
    @DisplayName("多聚合 append 交错时全局 position 严格单调")
    void multipleAggregatesShouldHaveMonotonicallyIncreasingPositions() {
        String aggregateId1 = "order-040";
        String aggregateId2 = "order-050";
        String aggregateId3 = "order-060";

        OrderCreatedEvent event1 = new OrderCreatedEvent("order-040", "customer-040");
        OrderCreatedEvent event2 = new OrderCreatedEvent("order-050", "customer-050");
        OrderCreatedEvent event3 = new OrderCreatedEvent("order-060", "customer-060");

        eventStore.append(aggregateId1, List.of(event1), 0);
        eventStore.append(aggregateId2, List.of(event2), 0);
        eventStore.append(aggregateId3, List.of(event3), 0);

        List<StoredEvent> allEvents = eventStore.readAll(0, 100);
        assertThat(allEvents).hasSize(3);

        long previousPosition = -1;
        for (StoredEvent event : allEvents) {
            assertThat(event.position()).isGreaterThan(previousPosition);
            previousPosition = event.position();
        }
    }

    @Test
    @DisplayName("read 不存在的流返回空列表")
    void readNonExistentStreamReturnsEmpty() {
        List<StoredEvent> events = eventStore.read("non-existent-stream-" + System.nanoTime());
        assertThat(events).isEmpty();
    }

    // =================== 测试事件类 ===================

    record OrderCreatedEvent(String orderId, String customerId) {
    }

    record OrderItemAddedEvent(String orderId, String itemId, int quantity) {
    }
}
