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
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
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
 * <h2>语义</h2>
 * <ul>
 *   <li><b>append 追加</b>：{@code add} 以期望版本 0 追加未提交事件；{@code update} 以
 *       当前聚合实例的已加载或已追加版本为期望版本追加——仓储不改写、不删除既有事件</li>
 *   <li><b>replay 重放</b>：{@code read} 从事件存储读取事件并按版本升序
 *       {@code loadFromHistory} 重建聚合；{@code read(id, version)} 重放至指定历史版本（闭合区间）</li>
 *   <li><b>乐观并发冲突</b>：期望版本与流实际版本不一致时抛
 *       {@code AggregateVersionConflictException}，事件流不出现部分写入</li>
 * </ul>
 *
 * <h3>版本跟踪边界</h3>
 * <p>版本按聚合对象身份分别维护，同 ID 的不同副本不会相互刷新版本；弱引用避免仓储延长聚合生命周期。
 * {@link EventStore} 负责拒绝过期版本。未经本仓储加载或保存的实例仍按当前流版本追加，
 * 保留既有调用方式，但该路径无法校验外部快照的加载版本。
 *
 * @param <M>  聚合根类型
 * @param <ID> 聚合根标识类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 1.0.x
 */
public class DefaultEventSourcingRepository<M extends AggregateRoot<ID>, ID extends Serializable>
        implements EventSourcingRepository<M, ID> {

    /** \u7528\u4e8e\u533a\u5206\u805a\u5408\u7c7b\u578b\u7684\u903b\u8f91\u540d\u79f0\u3002 */
    private final String aggregateType;
    /** \u7528\u4e8e\u6784\u9020\u548c\u6062\u590d\u805a\u5408\u7684 Java \u7c7b\u578b\u3002 */
    private final Class<M> aggregateClass;
    /** \u5c06\u4ed3\u50a8\u4e3b\u952e\u8f6c\u6362\u6210\u4e8b\u4ef6\u5b58\u50a8\u805a\u5408\u6839\u6807\u8bc6\u7684\u9002\u914d\u5668\u3002 */
    private final Function<ID, ? extends AggregateRootId> idAdapter;
    /** \u8bfb\u53d6\u53ca\u8ffd\u52a0\u9886\u57df\u4e8b\u4ef6\u7684\u4e8b\u4ef6\u5b58\u50a8\u3002 */
    private final EventStore eventStore;
    /**
     * 按对象身份跟踪仓储已经读取或追加的聚合事件版本。
     */
    private final ConcurrentMap<AggregateReference<M>, Long> trackedVersions = new ConcurrentHashMap<>();
    /**
     * 已回收聚合的引用，用于移除其版本记录。
     */
    private final ReferenceQueue<M> collectedAggregates = new ReferenceQueue<>();

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
        M aggregate = replay(events);
        trackVersion(aggregate, events.get(events.size() - 1).version());
        return aggregate;
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
        M aggregate = replay(events);
        trackVersion(aggregate, events.get(events.size() - 1).version());
        return aggregate;
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
        List<DomainEvent<?>> pending = new ArrayList<>(aggregate.domainEvents());
        eventStore.append(aggregateType, resolve(aggregateId), pending, 0L);
        aggregate.clearDomainEvents();
        trackVersion(aggregate, pending.size());
    }

    /**
     * 更新聚合：以当前聚合实例的已加载或已追加版本追加未提交事件（append-only）。
     *
     * @param aggregate 携带未提交事件的聚合根
     * @throws io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException 期望版本与事件流实际版本不一致（并发更新者已推进流版本）
     */
    @Override
    public void update(M aggregate) {
        Objects.requireNonNull(aggregate, "aggregate must not be null");
        ID aggregateId = requireId(aggregate);
        discardCollectedAggregates();
        Long trackedVersion = trackedVersions.get(new AggregateReference<>(aggregate, null));
        long expectedVersion = Objects.nonNull(trackedVersion) ? trackedVersion : currentStreamVersion(aggregateId);
        List<DomainEvent<?>> pending = new ArrayList<>(aggregate.domainEvents());
        eventStore.append(aggregateType, resolve(aggregateId), pending, expectedVersion);
        aggregate.clearDomainEvents();
        trackVersion(aggregate, expectedVersion + pending.size());
    }

    /**
     * 记录当前聚合实例的持久化版本，其他同 ID 实例保持自己的版本。
     *
     * @param aggregate 当前加载或保存的聚合
     * @param version 已成功读取或追加的版本
     */
    private void trackVersion(M aggregate, long version) {
        discardCollectedAggregates();
        trackedVersions.put(new AggregateReference<>(aggregate, collectedAggregates), version);
    }

    /**
     * 清理不再被业务持有的聚合版本，不影响仍存活的旧快照。
     */
    private void discardCollectedAggregates() {
        Reference<? extends M> reference;
        while (Objects.nonNull(reference = collectedAggregates.poll())) {
            trackedVersions.remove(reference);
        }
    }

    /**
     * 聚合版本索引使用的弱引用键；领域 equals/hashCode 不能合并不同加载实例。
     *
     * @param <T> 聚合类型
     */
    private static final class AggregateReference<T> extends WeakReference<T> {
        private final int identityHashCode;

        private AggregateReference(T aggregate, ReferenceQueue<T> queue) {
            super(aggregate, queue);
            identityHashCode = System.identityHashCode(aggregate);
        }

        @Override
        public int hashCode() {
            return identityHashCode;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            T aggregate = get();
            return Objects.nonNull(aggregate) && other instanceof AggregateReference<?>
                    && aggregate == ((AggregateReference<?>) other).get();
        }
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

    /**
     * \u8bfb\u53d6\u805a\u5408\u5df2\u6301\u4e45\u5316\u7684\u5168\u90e8\u4e8b\u4ef6\u5e76\u53d6\u5f97\u6700\u540e\u4e00\u4e2a\u7248\u672c\u3002
     * @param aggregateId \u5f85\u67e5\u8be2\u7684\u805a\u5408\u6807\u8bc6
     * @return \u6700\u540e\u4e00\u4e2a\u4e8b\u4ef6\u7684\u7248\u672c\uff0c\u4e8b\u4ef6\u6d41\u4e3a\u7a7a\u65f6\u4e3a\u96f6
     */
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

    /**
     * \u4ece\u805a\u5408\u5bf9\u8c61\u4e2d\u53d6\u5f97\u975e\u7a7a\u6807\u8bc6\u3002
     * @param aggregate \u5f85\u5199\u5165\u4e8b\u4ef6\u7684\u805a\u5408
     * @return \u805a\u5408\u81ea\u8eab\u7684\u6807\u8bc6
     */
    private ID requireId(M aggregate) {
        return Objects.requireNonNull(aggregate.id(), "aggregate id must not be null");
    }

    /**
     * \u901a\u8fc7\u9002\u914d\u5668\u628a\u4ed3\u50a8\u6807\u8bc6\u89e3\u6790\u4e3a\u4e8b\u4ef6\u5b58\u50a8\u53ef\u7528\u7684\u6807\u8bc6\u3002
     * @param aggregateId \u4ed3\u50a8\u4f7f\u7528\u7684\u805a\u5408\u6807\u8bc6
     * @return \u8f6c\u6362\u540e\u7684\u4e8b\u4ef6\u5b58\u50a8\u6807\u8bc6
     */
    private AggregateRootId resolve(ID aggregateId) {
        return Objects.requireNonNull(idAdapter.apply(aggregateId),
                "idAdapter must not return null for id: " + aggregateId);
    }
}
