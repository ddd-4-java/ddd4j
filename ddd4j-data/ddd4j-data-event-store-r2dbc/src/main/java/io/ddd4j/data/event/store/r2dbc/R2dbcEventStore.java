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

    /** \u63d0\u4f9b\u540c\u6b65\u9002\u914d\u5668\u5e95\u5c42\u8bfb\u5199\u884c\u4e3a\u7684\u54cd\u5e94\u5f0f\u4e8b\u4ef6\u5b58\u50a8\u3002 */
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
     * \u4e3a\u5f02\u6b65\u4e8b\u4ef6\u5b58\u50a8\u63d0\u4f9b\u540c\u6b65\u4e8b\u4ef6\u5b58\u50a8\u5165\u53e3\u3002
     * @param asyncEventStore \u8d1f\u8d23\u5b9e\u9645\u4e8b\u4ef6\u8bfb\u5199\u7684\u54cd\u5e94\u5f0f\u5b58\u50a8
     */
    R2dbcEventStore(R2dbcAsyncEventStore asyncEventStore) {
        this.asyncEventStore = Objects.requireNonNull(asyncEventStore, "asyncEventStore must not be null");
    }

    /**
     * \u5c06\u54cd\u5e94\u5f0f\u4e8b\u4ef6\u8bfb\u53d6\u7ed3\u679c\u8f6c\u6210\u540c\u6b65\u4e8b\u4ef6\u5b58\u50a8\u7684\u8bb0\u5f55\u7ed3\u6784\u3002
     * @param event \u8981\u5e8f\u5217\u5316\u6216\u8f6c\u6362\u7684\u4e8b\u4ef6
     * @return \u5177\u6709\u76f8\u540c\u8eab\u4efd\u3001\u7248\u672c\u548c\u8d1f\u8f7d\u7684\u5b58\u50a8\u4e8b\u4ef6
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
     * \u5728\u540c\u6b65\u8fb9\u754c\u8ffd\u52a0\u9886\u57df\u4e8b\u4ef6\u3002
     *
     * @param aggregateType \u805a\u5408\u7c7b\u578b
     * @param aggregateId \u805a\u5408\u6839\u6807\u8bc6
     * @param events \u6309\u987a\u5e8f\u8ffd\u52a0\u6216\u56de\u653e\u7684\u9886\u57df\u4e8b\u4ef6
     * @param expectedVersion \u9884\u671f\u7684\u5f53\u524d\u805a\u5408\u7248\u672c
     */
    @Override
    public void append(String aggregateType, AggregateRootId aggregateId,
                       List<? extends DomainEvent<?>> events, long expectedVersion) {
        Objects.requireNonNull(events, "events must not be null");
        asyncEventStore.append(aggregateType, aggregateId, Flux.fromIterable(events), expectedVersion).block();
    }

    /**
     * \u5728\u540c\u6b65\u8fb9\u754c\u8bfb\u53d6\u5b8c\u6574\u805a\u5408\u6d41\u3002
     *
     * @param aggregateType \u805a\u5408\u7c7b\u578b
     * @param aggregateId \u805a\u5408\u6839\u6807\u8bc6
     * @return \u6309\u7248\u672c\u987a\u5e8f\u8fd4\u56de\u7684\u9886\u57df\u4e8b\u4ef6\u6d41
     */
    @Override
    public List<StoredEvent> read(String aggregateType, AggregateRootId aggregateId) {
        return asyncEventStore.read(aggregateType, aggregateId)
                .map(R2dbcEventStore::toStoredEvent)
                .collectList()
                .block();
    }

    /**
     * \u5728\u540c\u6b65\u8fb9\u754c\u8bfb\u53d6\u95ed\u5408\u7248\u672c\u533a\u95f4\u3002
     *
     * @param aggregateType \u805a\u5408\u7c7b\u578b
     * @param aggregateId \u805a\u5408\u6839\u6807\u8bc6
     * @param fromVersion \u8bfb\u53d6\u8d77\u59cb\u7248\u672c
     * @param toVersion \u8bfb\u53d6\u7ed3\u675f\u7248\u672c
     * @return \u6309\u7248\u672c\u987a\u5e8f\u8fd4\u56de\u7684\u9886\u57df\u4e8b\u4ef6\u6d41
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
     * \u5728\u540c\u6b65\u8fb9\u754c\u8bfb\u53d6\u5168\u5c40\u4e8b\u4ef6\u6d41\u5206\u9875\u3002
     *
     * @param fromPosition \u8d77\u59cb\u6d41\u4f4d\u7f6e
     * @param limit \u672c\u6b21\u6700\u591a\u8bfb\u53d6\u7684\u8bb0\u5f55\u6570
     * @return \u6309\u5168\u5c40\u4f4d\u7f6e\u6392\u5e8f\u7684\u4e8b\u4ef6
     */
    @Override
    public List<StoredEvent> readAll(long fromPosition, int limit) {
        return asyncEventStore.readAll(fromPosition, limit)
                .map(R2dbcEventStore::toStoredEvent)
                .collectList()
                .block();
    }
}
