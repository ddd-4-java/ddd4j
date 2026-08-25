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
package io.ddd4j.core.cqrs.eventstore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link EventStore} SPI 抽象契约测试。
 *
 * <p>供所有 EventStore 实现（InMemory / JDBC / EventStoreDB 等）复用，
 * 验证以下契约：
 * <ul>
 *   <li>append 成功追加事件并递增版本</li>
 *   <li>版本冲突抛 {@link IllegalStateException}</li>
 *   <li>read 按聚合读取，版本升序</li>
 *   <li>readAll 全局分页，position 升序</li>
 *   <li>readAll 的 fromPosition 边界（包含）</li>
 *   <li>readAll 的 limit 限制</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
@DisplayName("EventStore SPI 契约")
public abstract class EventStoreContractTest {

    /**
     * 子类提供待测试的 EventStore 实例。
     *
     * @return 新建的、空的 EventStore 实例
     */
    protected abstract EventStore createEventStore();

    @Test
    @DisplayName("append 成功追加事件，read 返回按版本升序的事件列表")
    void append_and_read_shouldReturnEventsInVersionOrder() {
        EventStore store = createEventStore();

        store.append("agg-1", List.of("eventA", "eventB"), 0);

        List<StoredEvent> events = store.read("agg-1");
        assertThat(events).hasSize(2);
        assertThat(events.get(0).aggregateId()).isEqualTo("agg-1");
        assertThat(events.get(0).version()).isEqualTo(0);
        assertThat(events.get(0).event()).isEqualTo("eventA");
        assertThat(events.get(1).version()).isEqualTo(1);
        assertThat(events.get(1).event()).isEqualTo("eventB");
    }

    @Test
    @DisplayName("append 版本冲突应抛 IllegalStateException")
    void append_versionConflict_shouldThrowIllegalStateException() {
        EventStore store = createEventStore();

        store.append("agg-1", List.of("eventA"), 0);

        assertThatThrownBy(() -> store.append("agg-1", List.of("eventB"), 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Version conflict");
    }

    @Test
    @DisplayName("append 多次追加应递增版本")
    void append_multipleAppends_shouldIncrementVersion() {
        EventStore store = createEventStore();

        store.append("agg-1", List.of("a"), 0);
        store.append("agg-1", List.of("b"), 1);
        store.append("agg-1", List.of("c"), 2);

        List<StoredEvent> events = store.read("agg-1");
        assertThat(events).hasSize(3);
        assertThat(events.get(0).version()).isEqualTo(0);
        assertThat(events.get(1).version()).isEqualTo(1);
        assertThat(events.get(2).version()).isEqualTo(2);
    }

    @Test
    @DisplayName("read 不存在的聚合应返回空列表")
    void read_nonExistentAggregate_shouldReturnEmptyList() {
        EventStore store = createEventStore();

        List<StoredEvent> events = store.read("non-existent");

        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("readAll 应按全局 position 升序返回事件")
    void readAll_shouldReturnEventsInPositionOrder() {
        EventStore store = createEventStore();

        store.append("agg-1", List.of("a1", "a2"), 0);
        store.append("agg-2", List.of("b1"), 0);

        List<StoredEvent> all = store.readAll(0, 100);

        assertThat(all).hasSize(3);
        assertThat(all.get(0).event()).isEqualTo("a1");
        assertThat(all.get(0).position()).isEqualTo(1);
        assertThat(all.get(1).event()).isEqualTo("a2");
        assertThat(all.get(1).position()).isEqualTo(2);
        assertThat(all.get(2).event()).isEqualTo("b1");
        assertThat(all.get(2).position()).isEqualTo(3);
    }

    @Test
    @DisplayName("readAll fromPosition 边界：应包含 fromPosition 位置的事件")
    void readAll_fromPosition_shouldBeInclusive() {
        EventStore store = createEventStore();

        store.append("agg-1", List.of("a", "b", "c"), 0);

        // position 分别为 1, 2, 3
        List<StoredEvent> events = store.readAll(2, 100);

        assertThat(events).hasSize(2);
        assertThat(events.get(0).event()).isEqualTo("b");
        assertThat(events.get(0).position()).isEqualTo(2);
        assertThat(events.get(1).event()).isEqualTo("c");
        assertThat(events.get(1).position()).isEqualTo(3);
    }

    @Test
    @DisplayName("readAll limit 应限制返回条数")
    void readAll_shouldRespectLimit() {
        EventStore store = createEventStore();

        store.append("agg-1", List.of("a", "b", "c"), 0);

        List<StoredEvent> events = store.readAll(0, 2);

        assertThat(events).hasSize(2);
        assertThat(events.get(0).event()).isEqualTo("a");
        assertThat(events.get(1).event()).isEqualTo("b");
    }

    @Test
    @DisplayName("readAll 超出范围应返回空列表")
    void readAll_beyondRange_shouldReturnEmptyList() {
        EventStore store = createEventStore();

        store.append("agg-1", List.of("a"), 0);

        List<StoredEvent> events = store.readAll(100, 100);

        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("readAll 空存储应返回空列表")
    void readAll_emptyStore_shouldReturnEmptyList() {
        EventStore store = createEventStore();

        List<StoredEvent> events = store.readAll(0, 100);

        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("StoredEvent 应包含正确的 timestamp")
    void append_shouldRecordTimestamp() {
        EventStore store = createEventStore();

        store.append("agg-1", List.of("a"), 0);

        List<StoredEvent> events = store.read("agg-1");
        assertThat(events.get(0).timestamp()).isNotNull();
    }

    @Test
    @DisplayName("不同聚合的事件应独立存储")
    void read_differentAggregates_shouldBeIndependent() {
        EventStore store = createEventStore();

        store.append("agg-1", List.of("a1"), 0);
        store.append("agg-2", List.of("b1"), 0);
        store.append("agg-1", List.of("a2"), 1);

        assertThat(store.read("agg-1")).hasSize(2);
        assertThat(store.read("agg-2")).hasSize(1);
        assertThat(store.read("agg-1").get(0).event()).isEqualTo("a1");
        assertThat(store.read("agg-1").get(1).event()).isEqualTo("a2");
        assertThat(store.read("agg-2").get(0).event()).isEqualTo("b1");
    }
}
