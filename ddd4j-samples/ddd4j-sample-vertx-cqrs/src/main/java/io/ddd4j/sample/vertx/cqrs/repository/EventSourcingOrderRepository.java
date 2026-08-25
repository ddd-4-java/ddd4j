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
package io.ddd4j.sample.vertx.cqrs.repository;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.sample.vertx.cqrs.cqrs.InMemoryEventStore;
import io.ddd4j.sample.order.domain.Order;
import io.ddd4j.sample.order.domain.OrderRepository;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 EventStore 的订单仓储实现（事件溯源，Micronaut 运行时）。
 *
 * <p>写侧：持久化聚合根产生的领域事件到 EventStore。
 * 读侧：通过 orderNo 索引查找订单 ID，用于幂等性检查。
 */

public class EventSourcingOrderRepository implements OrderRepository {

    private final InMemoryEventStore eventStore;

    /** orderNo -> aggregateId 映射（幂等性检查）。 */
    private final Map<String, String> orderNoIndex = new ConcurrentHashMap<>();

    /** orderId -> Order 缓存（简化实现，避免从事件重建）。 */
    private final Map<String, Order> orderCache = new ConcurrentHashMap<>();

    public EventSourcingOrderRepository(InMemoryEventStore eventStore) {
        this.eventStore = Objects.requireNonNull(eventStore, "eventStore must not be null");
    }

    @Override
    public void save(Order order) {
        if (!order.domainEvents().isEmpty()) {
            List<DomainEvent<?>> events = order.pullDomainEvents();
            long currentVersion = orderCache.containsKey(order.id())
                    ? eventStore.read(order.id()).size() : 0;
            List<Object> payloads = new ArrayList<>(events);
            eventStore.append(order.id(), payloads, currentVersion);
            orderNoIndex.put(order.orderNo(), order.id());
            orderCache.put(order.id(), order);
        }
    }

    @Override
    public Optional<Order> findById(String orderId) {
        return Optional.ofNullable(orderCache.get(orderId));
    }

    @Override
    public Optional<Order> findByOrderNo(String orderNo) {
        return Optional.ofNullable(orderNoIndex.get(orderNo))
                .flatMap(this::findById);
    }

    @Override
    public List<Order> findAll(int offset, int limit) {
        throw new UnsupportedOperationException("findAll not supported in event sourcing repository");
    }

    @Override
    public long count() {
        return orderNoIndex.size();
    }
}
