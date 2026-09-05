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
import io.ddd4j.core.cqrs.eventstore.InMemoryEventStore;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.StringEntityType;
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

    /** 聚合类型（当前 core EventStore SPI 需显式 aggregateType 定位流）。 */
    private static final String AGGREGATE_TYPE = "Order";

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
                    ? eventStore.read(AGGREGATE_TYPE, aggregateId(order.id())).size() : 0;
            List<DomainEvent<?>> payloads = new ArrayList<>(events);
            eventStore.append(AGGREGATE_TYPE, aggregateId(order.id()), payloads, currentVersion);
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

    /**
     * 字符串聚合根标识适配器：core EventStore SPI 以 {@link AggregateRootId} 定位流，
     * 样例订单以字符串为 ID（与 2.0.x 旧 r2dbc StringAggregateRootId 同构）。
     */private final class OrderAggregateId {
        private final String value;

        public OrderAggregateId(String value) {
            this.value = value;
        }
        public String value() { return value; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
        OrderAggregateId other = (OrderAggregateId) o;
            return Objects.equals(this.value, other.value);
        }
        @Override
        public int hashCode() { return java.util.Objects.hash(value); }
        @Override
        public String toString() {
            return "OrderAggregateId{" + "value=" + value + "}";
        }
        private static final EntityType TYPE = new StringEntityType("Order");

        @Override
        public EntityType getType() {
            return TYPE;
        }

        @Override
        public String asString() {
            return value;
        }

        @Override
        public String asTypedString() {
            return TYPE.asString() + ":" + value;
        }
    
    }

    private static AggregateRootId aggregateId(String value) {
        return new OrderAggregateId(value);
    }
}
