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

package io.ddd4j.core.ddd.repository;

import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.model.AggregateRoot;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * {@link EventSourcingRepository} 默认实现：基于既有 {@link EventStore} SPI 的最小可用事件溯源仓储。
 *
 * <p>对应规格：{@code complete-cqrs-es-base-capabilities/event-sourcing-repository}。
 *
 * <h3>语义</h3>
 * <ul>
 *   <li><b>append 追加</b>：{@code add} 以期望版本 0 追加未提交事件；{@code update} 以
 *       本实例跟踪的流版本为期望版本追加——仓储不改写、不删除既有事件</li>
 *   <li><b>replay 重放</b>：{@code read} 从事件存储读取事件并按版本升序
 *       {@code loadFromHistory} 重建聚合；{@code read(id, version)} 重放至指定历史版本（闭合区间）</li>
 *   <li><b>乐观并发冲突</b>：期望版本与流实际版本不一致时抛
 *       {@code AggregateVersionConflictException}，事件流不出现部分写入</li>
 * </ul>
 *
 * <h3>版本跟踪边界</h3>
 * <p>版本位在本实例内维护（{@code ConcurrentMap}），跨实例／跨节点的并发推进由
 * {@link EventStore} 乐观锁兜底拒绝——两个并发更新者各自持有过期版本时，后提交者必然冲突。
 *
 * @param <M>  聚合根类型
 * @param <ID> 聚合根标识类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 1.0.x
 */
public class DefaultEventSourcingRepository<M extends AggregateRoot<ID>, ID extends Serializable>
        implements EventSourcingRepository<M, ID> {

    private final String aggregateType;
    private final Class<M> aggregateClass;
    private final Function<ID, ? extends AggregateRootId> idAdapter;
    private final EventStore eventStore;
    private final ConcurrentMap<ID, Long> trackedVersions = new ConcurrentHashMap<>();

    /**
     * 创建事件溯源仓储。
     *
     * @param aggregateType  聚合类型（事件存储流定位键，如 {@code "Order"}）
     * @param aggregateClass 聚合根类型（须提供可访问的无参构造，供重放重建实例化）
     * @param idAdapter      聚合根标识 → {@link AggregateRootId} 适配函数
     * @param eventStore     事件存储
     */
    public DefaultEventSourcingRepository(String aggregateType, Class<M> aggregateClass,
                                          Function<ID, ? extends AggregateRootId> idAdapter, EventStore eventStore) {
        this.aggregateType = Objects.requireNonNull(aggregateType, "aggregateType must not be null");
        this.aggregateClass = Objects.requireNonNull(aggregateClass, "aggregateClass must not be null");
        this.idAdapter = Objects.requireNonNull(idAdapter, "idAdapter must not be null");
        this.eventStore = Objects.requireNonNull(eventStore, "eventStore must not be null");
    }

    /**
     * 读取并重放聚合至最新版本。
     *
     * @param aggregateId 聚合根标识
     * @return 重放重建的聚合根
     * @throws NoSuchElementException 事件流不存在（无任何事件）
     */
    @Override
    public M read(ID aggregateId) {
        List<StoredEvent> events = eventStore.read(aggregateType, resolve(aggregateId));
        if (events.isEmpty()) {
            throw new NoSuchElementException("Aggregate not found: " + aggregateType + "#" + aggregateId);
        }
        trackedVersions.put(aggregateId, events.get(events.size() - 1).version());
        return replay(events);
    }

    /**
     * 读取并重放聚合至指定历史版本（闭合区间 1..version）。
     *
     * @param aggregateId 聚合根标识
     * @param version     历史版本号
     * @return 重放到指定版本的聚合根
     * @throws NoSuchElementException 指定版本区间无事件
     */
    @Override
    public M read(ID aggregateId, int version) {
        List<StoredEvent> events = eventStore.read(aggregateType, resolve(aggregateId), 1L, version);
        if (events.isEmpty()) {
            throw new NoSuchElementException(
                    "Aggregate not found at version " + version + ": " + aggregateType + "#" + aggregateId);
        }
        trackedVersions.put(aggregateId, events.get(events.size() - 1).version());
        return replay(events);
    }

    /**
     * 新建聚合：以期望版本 0 追加全部未提交事件（append-only）。
     *
     * @param aggregate 新创建的聚合根
     */
    @Override
    public void add(M aggregate) {
        Objects.requireNonNull(aggregate, "aggregate must not be null");
        ID aggregateId = requireId(aggregate);
        List<DomainEvent<?>> pending = aggregate.pullDomainEvents();
        eventStore.append(aggregateType, resolve(aggregateId), pending, 0L);
        trackedVersions.put(aggregateId, (long) pending.size());
    }

    /**
     * 更新聚合：以本实例跟踪的流版本为期望版本追加未提交事件（append-only）。
     *
     * @param aggregate 携带未提交事件的聚合根
     * @throws io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException
     *         期望版本与事件流实际版本不一致（并发更新者已推进流版本）
     */
    @Override
    public void update(M aggregate) {
        Objects.requireNonNull(aggregate, "aggregate must not be null");
        ID aggregateId = requireId(aggregate);
        long expectedVersion = trackedVersions.getOrDefault(aggregateId, currentStreamVersion(aggregateId));
        List<DomainEvent<?>> pending = aggregate.pullDomainEvents();
        eventStore.append(aggregateType, resolve(aggregateId), pending, expectedVersion);
        trackedVersions.put(aggregateId, expectedVersion + pending.size());
    }

    /**
     * 重放事件重建聚合状态（{@code loadFromHistory} 语义，产物不携带未提交事件）。
     *
     * @param events 按版本升序的持久化事件
     * @return 重建的聚合根
     */
    private M replay(List<StoredEvent> events) {
        List<DomainEvent<?>> history = new ArrayList<>(events.size());
        for (StoredEvent stored : events) {
            history.add(stored.payload());
        }
        M aggregate = newAggregate();
        aggregate.loadFromHistory(history);
        return aggregate;
    }

    private long currentStreamVersion(ID aggregateId) {
        List<StoredEvent> stored = eventStore.read(aggregateType, resolve(aggregateId));
        return stored.isEmpty() ? 0L : stored.get(stored.size() - 1).version();
    }

    /**
     * 反射实例化聚合根（重放重建用）。
     *
     * @return 空白聚合根实例
     * @throws IllegalStateException 聚合根缺少可访问的无参构造
     */
    private M newAggregate() {
        try {
            Constructor<M> constructor = aggregateClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Aggregate " + aggregateClass.getName() + " must expose a no-arg constructor", e);
        }
    }

    private ID requireId(M aggregate) {
        return Objects.requireNonNull(aggregate.id(), "aggregate id must not be null");
    }

    private AggregateRootId resolve(ID aggregateId) {
        return Objects.requireNonNull(idAdapter.apply(aggregateId),
                "idAdapter must not return null for id: " + aggregateId);
    }
}
