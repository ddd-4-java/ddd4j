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

    /**
     * 提供同步适配器底层读写行为的响应式事件存储。
     */
    private final R2dbcAsyncEventStore asyncEventStore;

    /**
     * 使用指定连接工厂创建同步适配器。
     *
     * @param connectionFactory R2DBC 连接工厂
     */
    public R2dbcEventStore(ConnectionFactory connectionFactory) {
        this(new R2dbcAsyncEventStore(connectionFactory));
    }

    /**
     * 为异步事件存储提供同步事件存储入口。
     *
     * @param asyncEventStore 负责实际事件读写的响应式存储
     */
    R2dbcEventStore(R2dbcAsyncEventStore asyncEventStore) {
        this.asyncEventStore = Objects.requireNonNull(asyncEventStore, "asyncEventStore must not be null");
    }

    /**
     * 将响应式事件读取结果转成同步事件存储的记录结构。
     *
     * @param event 要序列化或转换的事件
     * @return 具有相同身份、版本和负载的存储事件
     */
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

    /**
     * 在同步边界追加领域事件。
     *
     * @param aggregateType   聚合类型
     * @param aggregateId     聚合根标识
     * @param events          按顺序追加或回放的领域事件
     * @param expectedVersion 预期的当前聚合版本
     */
    @Override
    public void append(String aggregateType, AggregateRootId aggregateId,
                       List<? extends DomainEvent<?>> events, long expectedVersion) {
        Objects.requireNonNull(events, "events must not be null");
        asyncEventStore.append(aggregateType, aggregateId, Flux.fromIterable(events), expectedVersion).block();
    }

    /**
     * 在同步边界读取完整聚合流。
     *
     * @param aggregateType 聚合类型
     * @param aggregateId   聚合根标识
     * @return 按版本顺序返回的领域事件流
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
     *
     * @param aggregateType 聚合类型
     * @param aggregateId   聚合根标识
     * @param fromVersion   读取起始版本
     * @param toVersion     读取结束版本
     * @return 按版本顺序返回的领域事件流
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
     *
     * @param fromPosition 起始流位置
     * @param limit        本次最多读取的记录数
     * @return 按全局位置排序的事件
     */
    @Override
    public List<StoredEvent> readAll(long fromPosition, int limit) {
        return asyncEventStore.readAll(fromPosition, limit)
                .map(R2dbcEventStore::toStoredEvent)
                .collectList()
                .block();
    }
}
