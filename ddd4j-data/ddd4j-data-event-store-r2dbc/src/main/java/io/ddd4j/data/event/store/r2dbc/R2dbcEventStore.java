/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.data.event.store.r2dbc;

import io.ddd4j.core.cqrs.eventstore.AsyncStoredEvent;
import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.r2dbc.spi.ConnectionFactory;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Objects;

/**
 * R2DBC 同步 EventStore 边界适配器。
 *
 * <p>持久化语义统一由 {@link R2dbcAsyncEventStore} 提供；本类仅在同步 SPI 边界阻塞，
 * 使同步和异步轨道使用同一张表、同一聚合唯一性规则和同一事件元数据映射，避免两套 SQL
 * 实现随版本演进再次漂移。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class R2dbcEventStore implements EventStore {

    private final R2dbcAsyncEventStore asyncEventStore;

    /**
     * 使用指定连接工厂创建同步适配器。
     *
     * @param connectionFactory R2DBC 连接工厂
     */
    public R2dbcEventStore(ConnectionFactory connectionFactory) {
        this(new R2dbcAsyncEventStore(connectionFactory));
    }

    R2dbcEventStore(R2dbcAsyncEventStore asyncEventStore) {
        this.asyncEventStore = Objects.requireNonNull(asyncEventStore, "asyncEventStore must not be null");
    }

    /**
     * 在同步边界追加领域事件。
     */
    @Override
    public void append(String aggregateType, AggregateRootId aggregateId,
                       List<? extends DomainEvent<?>> events, long expectedVersion) {
        Objects.requireNonNull(events, "events must not be null");
        asyncEventStore.append(aggregateType, aggregateId, Flux.fromIterable(events), expectedVersion).block();
    }

    /**
     * 在同步边界读取完整聚合流。
     */
    @Override
    public List<StoredEvent> read(String aggregateType, AggregateRootId aggregateId) {
        return asyncEventStore.read(aggregateType, aggregateId)
                .map(R2dbcEventStore::toStoredEvent)
                .collectList()
                .block();
    }

    /**
     * 在同步边界读取闭合版本区间。
     */
    @Override
    public List<StoredEvent> read(String aggregateType, AggregateRootId aggregateId,
                                  long fromVersion, long toVersion) {
        return asyncEventStore.read(aggregateType, aggregateId, fromVersion, toVersion)
                .map(R2dbcEventStore::toStoredEvent)
                .collectList()
                .block();
    }

    /**
     * 在同步边界读取全局事件流分页。
     */
    @Override
    public List<StoredEvent> readAll(long fromPosition, int limit) {
        return asyncEventStore.readAll(fromPosition, limit)
                .map(R2dbcEventStore::toStoredEvent)
                .collectList()
                .block();
    }

    private static StoredEvent toStoredEvent(AsyncStoredEvent event) {
        return new StoredEvent(
                event.eventId(),
                event.aggregateType(),
                event.aggregateId(),
                event.version(),
                event.position(),
                event.timestamp(),
                event.payload(),
                event.correlationId(),
                event.causationId());
    }
}