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
package io.ddd4j.data.event.store.jdbi;

import io.ddd4j.core.constant.EventStoreConstants;
import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link JdbiEventStore} 集成测试——H2 内存数据库全量契约验证。
 *
 * <p>覆盖场景：
 * <ol>
 *   <li>append 后 read 单流按版本顺序返回</li>
 *   <li>乐观锁：expectedVersion 不匹配抛 {@link IllegalStateException} 且不落库</li>
 *   <li>readAll 按 position 升序 + limit 分页</li>
 *   <li>事件 payload 往返（写入->读出 event 内容一致，类型还原）</li>
 *   <li>多聚合 append 交错时全局 position 单调</li>
 * </ol>
 *
 * <p>使用 H2 内存数据库，通过 Jdbi 连接，不依赖 Spring。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
@DisplayName("JdbiEventStore")
class JdbiEventStoreTest {

    private Jdbi jdbi;
    private EventStore eventStore;

    @BeforeEach
    void setUp() {
        // 每个测试使用独立的 H2 内存数据库
        String dbName = "eventstore_test_" + System.nanoTime();
        jdbi = Jdbi.create("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1");
        eventStore = new JdbiEventStore(jdbi);
    }

    @AfterEach
    void tearDown() {
        try {
            jdbi.useHandle(handle ->
                    handle.execute("DROP TABLE IF EXISTS " + EventStoreConstants.TABLE_NAME));
        } catch (Exception e) {
            // 忽略清理失败
        }
    }

    @Test
    @DisplayName("append 后 read 应按版本升序返回事件")
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
        assertThat(events.get(0).aggregateId()).isEqualTo("order-001");
    }

    @Test
    @DisplayName("append 版本冲突应抛 IllegalStateException 且不落库")
    void appendWithWrongExpectedVersionShouldThrowAndNotPersist() {
        String aggregateId = "order-002";
        OrderCreatedEvent event1 = new OrderCreatedEvent("order-002", "customer-002");
        eventStore.append(aggregateId, List.of(event1), 0);

        OrderItemAddedEvent event2 = new OrderItemAddedEvent("order-002", "item-003", 5);

        assertThatThrownBy(() -> eventStore.append(aggregateId, List.of(event2), 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Version conflict");

        List<StoredEvent> events = eventStore.read(aggregateId);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).version()).isEqualTo(0);
    }

    @Test
    @DisplayName("readAll 应按 position 升序返回事件并支持 limit 分页")
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
        assertThat(allEvents.get(0).position()).isLessThan(allEvents.get(1).position());
        assertThat(allEvents.get(1).position()).isLessThan(allEvents.get(2).position());

        List<StoredEvent> limitedEvents = eventStore.readAll(0, 2);
        assertThat(limitedEvents).hasSize(2);

        long secondPosition = allEvents.get(1).position();
        List<StoredEvent> fromSecond = eventStore.readAll(secondPosition, 10);
        assertThat(fromSecond).hasSize(2);
        assertThat(fromSecond.get(0).position()).isEqualTo(secondPosition);
    }

    @Test
    @DisplayName("event payload 应完成往返序列化")
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
    @DisplayName("多聚合交错写入时全局 position 应单调递增")
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
    @DisplayName("read 不存在的聚合应返回空列表")
    void readNonExistentAggregateShouldReturnEmptyList() {
        List<StoredEvent> events = eventStore.read("non-existent");
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("append 空事件列表应为无操作")
    void appendEmptyEventsShouldBeNoOp() {
        String aggregateId = "order-empty";
        eventStore.append(aggregateId, List.of(), 0);

        List<StoredEvent> events = eventStore.read(aggregateId);
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("append 多次追加应递增版本")
    void appendMultipleAppendsShouldIncrementVersion() {
        String aggregateId = "order-070";
        OrderCreatedEvent event1 = new OrderCreatedEvent("order-070", "customer-070");
        OrderItemAddedEvent event2 = new OrderItemAddedEvent("order-070", "item-070", 1);
        OrderItemAddedEvent event3 = new OrderItemAddedEvent("order-070", "item-071", 2);

        eventStore.append(aggregateId, List.of(event1), 0);
        eventStore.append(aggregateId, List.of(event2), 1);
        eventStore.append(aggregateId, List.of(event3), 2);

        List<StoredEvent> events = eventStore.read(aggregateId);
        assertThat(events).hasSize(3);
        assertThat(events.get(0).version()).isEqualTo(0);
        assertThat(events.get(1).version()).isEqualTo(1);
        assertThat(events.get(2).version()).isEqualTo(2);
    }

    @Test
    @DisplayName("StoredEvent 应包含正确的 timestamp")
    void appendShouldRecordTimestamp() {
        String aggregateId = "order-080";
        OrderCreatedEvent event = new OrderCreatedEvent("order-080", "customer-080");

        eventStore.append(aggregateId, List.of(event), 0);

        List<StoredEvent> events = eventStore.read(aggregateId);
        assertThat(events.get(0).timestamp()).isNotNull();
    }

    @Test
    @DisplayName("不同聚合的事件应独立存储")
    void readDifferentAggregatesShouldBeIndependent() {
        OrderCreatedEvent a1 = new OrderCreatedEvent("agg-1", "c1");
        OrderCreatedEvent b1 = new OrderCreatedEvent("agg-2", "c2");
        OrderItemAddedEvent a2 = new OrderItemAddedEvent("agg-1", "item-1", 1);

        eventStore.append("agg-1", List.of(a1), 0);
        eventStore.append("agg-2", List.of(b1), 0);
        eventStore.append("agg-1", List.of(a2), 1);

        assertThat(eventStore.read("agg-1")).hasSize(2);
        assertThat(eventStore.read("agg-2")).hasSize(1);

        Object restoredA1 = eventStore.read("agg-1").get(0).event();
        assertThat(restoredA1).isInstanceOf(OrderCreatedEvent.class);
        assertThat(((OrderCreatedEvent) restoredA1).orderId()).isEqualTo("agg-1");

        Object restoredA2 = eventStore.read("agg-1").get(1).event();
        assertThat(restoredA2).isInstanceOf(OrderItemAddedEvent.class);
        assertThat(((OrderItemAddedEvent) restoredA2).itemId()).isEqualTo("item-1");

        Object restoredB1 = eventStore.read("agg-2").get(0).event();
        assertThat(restoredB1).isInstanceOf(OrderCreatedEvent.class);
        assertThat(((OrderCreatedEvent) restoredB1).orderId()).isEqualTo("agg-2");
    }

    // =================== 测试事件类 ===================

    /**
     * 订单创建事件（测试用 POJO）。
     */
    record OrderCreatedEvent(String orderId, String customerId) {
    }

    /**
     * 订单项添加事件（测试用 POJO）。
     */
    record OrderItemAddedEvent(String orderId, String itemId, int quantity) {
    }
}
