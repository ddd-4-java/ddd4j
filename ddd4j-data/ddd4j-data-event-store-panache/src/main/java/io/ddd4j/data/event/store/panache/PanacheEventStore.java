/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.data.event.store.panache;

import com.fasterxml.jackson.annotation.JsonValue;
import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.core.cqrs.eventstore.jackson.EventPayloadSerializer;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.EventId;
import io.ddd4j.core.ddd.event.StringEntityType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.NoResultException;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 基于 Quarkus Hibernate ORM Panache 的强类型 {@link EventStore} 实现。
 *
 * <p>该实现与 JPA/JDBI adapter 共享 Core EventStore 契约：以聚合类型、聚合标识和
 * 版本定位流，保存事件元数据，并在读取时按显式事件类型反序列化。位置号仍使用 3.0.x
 * 已有的唯一约束和重试策略，避免丢失并发恢复能力。
 */
public class PanacheEventStore implements EventStore {

    private final EntityManager entityManager;
    private final EventStoreRetry retry;
    private final EventPayloadSerializer serializer;

    /**
     * 创建 Panache 事件存储。
     *
     * @param entityManager JPA EntityManager
     */
    public PanacheEventStore(EntityManager entityManager) {
        this(entityManager, new EventStoreRetry(),
                new EventPayloadSerializer(JsonMapper.builder().findAndAddModules().build()));
    }

    /**
     * 创建带重试策略的 Panache 事件存储。
     *
     * @param entityManager JPA EntityManager
     * @param retry position 唯一约束冲突的重试策略
     */
    public PanacheEventStore(EntityManager entityManager, EventStoreRetry retry) {
        this(entityManager, retry, new EventPayloadSerializer(JsonMapper.builder().findAndAddModules().build()));
    }

    PanacheEventStore(EntityManager entityManager, EventStoreRetry retry, EventPayloadSerializer serializer) {
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager must not be null");
        this.retry = Objects.requireNonNull(retry, "retry must not be null");
        this.serializer = Objects.requireNonNull(serializer, "serializer must not be null");
    }

    /**
     * 以聚合类型和聚合标识追加事件；版本冲突使用统一异常表示，位置号冲突可重试。
     */
    @Override
    public void append(String aggregateType, AggregateRootId aggregateId,
                       List<? extends DomainEvent<?>> events, long expectedVersion) {
        Objects.requireNonNull(aggregateType, "aggregateType must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(events, "events must not be null");
        if (events.isEmpty()) {
            return;
        }

        retry.execute("append(" + aggregateType + ":" + aggregateId.asString() + ")", () -> {
            EntityTransaction transaction = entityManager.getTransaction();
            try {
                transaction.begin();
                entityManager.clear();

                long actualVersion = findCurrentVersion(aggregateType, aggregateId.asString());
                if (actualVersion != expectedVersion) {
                    throw new AggregateVersionConflictException(
                            aggregateType, aggregateId.asString(), expectedVersion, actualVersion);
                }

                long version = expectedVersion;
                long position = nextPosition();
                for (DomainEvent<?> event : events) {
                    PanacheStoredEventEntity entity = new PanacheStoredEventEntity();
                    entity.aggregateType = aggregateType;
                    entity.aggregateId = aggregateId.asString();
                    entity.version = ++version;
                    entity.position = position++;
                    entity.eventType = event.getClass().getName();
                    entity.eventId = event.getEventId().asString();
                    entity.correlationId = event.getCorrelationId() == null ? null : event.getCorrelationId().asString();
                    entity.causationId = event.getCausationId() == null ? null : event.getCausationId().asString();
                    entity.payload = serializer.serialize(event);
                    entity.timestamp = event.getEventTimestamp().toInstant();
                    entityManager.persist(entity);
                }

                entityManager.flush();
                entityManager.clear();
                transaction.commit();
            } catch (RuntimeException exception) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw exception;
            }
        });
    }

    /**
     * 按版本升序读取一个聚合流。
     */
    @Override
    public List<StoredEvent> read(String aggregateType, AggregateRootId aggregateId) {
        Objects.requireNonNull(aggregateType, "aggregateType must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        return entityManager.createQuery(
                        "SELECT e FROM PanacheStoredEventEntity e WHERE e.aggregateType = :aggregateType "
                                + "AND e.aggregateId = :aggregateId ORDER BY e.version ASC",
                        PanacheStoredEventEntity.class)
                .setParameter("aggregateType", aggregateType)
                .setParameter("aggregateId", aggregateId.asString())
                .getResultList()
                .stream()
                .map(this::toStoredEvent)
                .toList();
    }

    /**
     * 按闭合版本区间读取一个聚合流。
     */
    @Override
    public List<StoredEvent> read(String aggregateType, AggregateRootId aggregateId,
                                  long fromVersion, long toVersion) {
        Objects.requireNonNull(aggregateType, "aggregateType must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        return entityManager.createQuery(
                        "SELECT e FROM PanacheStoredEventEntity e WHERE e.aggregateType = :aggregateType "
                                + "AND e.aggregateId = :aggregateId AND e.version BETWEEN :fromVersion AND :toVersion "
                                + "ORDER BY e.version ASC",
                        PanacheStoredEventEntity.class)
                .setParameter("aggregateType", aggregateType)
                .setParameter("aggregateId", aggregateId.asString())
                .setParameter("fromVersion", fromVersion)
                .setParameter("toVersion", toVersion)
                .getResultList()
                .stream()
                .map(this::toStoredEvent)
                .toList();
    }

    /**
     * 按全局 position 升序分页读取事件。
     */
    @Override
    public List<StoredEvent> readAll(long fromPosition, int limit) {
        return entityManager.createQuery(
                        "SELECT e FROM PanacheStoredEventEntity e WHERE e.position >= :fromPosition "
                                + "ORDER BY e.position ASC",
                        PanacheStoredEventEntity.class)
                .setParameter("fromPosition", fromPosition)
                .setMaxResults(limit)
                .getResultList()
                .stream()
                .map(this::toStoredEvent)
                .toList();
    }

    private long findCurrentVersion(String aggregateType, String aggregateId) {
        try {
            Long count = entityManager.createQuery(
                            "SELECT COUNT(e) FROM PanacheStoredEventEntity e WHERE e.aggregateType = :aggregateType "
                                    + "AND e.aggregateId = :aggregateId",
                            Long.class)
                    .setParameter("aggregateType", aggregateType)
                    .setParameter("aggregateId", aggregateId)
                    .getSingleResult();
            return count == null ? 0L : count;
        } catch (NoResultException exception) {
            return 0L;
        }
    }

    private long nextPosition() {
        try {
            Long maximum = entityManager.createQuery(
                            "SELECT COALESCE(MAX(e.position), 0) FROM PanacheStoredEventEntity e", Long.class)
                    .getSingleResult();
            return (maximum == null ? 0L : maximum) + 1L;
        } catch (NoResultException exception) {
            return 1L;
        }
    }

    private StoredEvent toStoredEvent(PanacheStoredEventEntity entity) {
        DomainEvent<?> payload = serializer.deserialize(entity.payload, resolveEventType(entity.eventType));
        return new StoredEvent(
                EventId.valueOf(entity.eventId),
                entity.aggregateType,
                new StringAggregateRootId(entity.aggregateId),
                entity.version,
                entity.position,
                ZonedDateTime.ofInstant(entity.timestamp, ZoneOffset.UTC),
                payload,
                EventId.valueOf(entity.correlationId),
                EventId.valueOf(entity.causationId));
    }

    @SuppressWarnings("unchecked")
    private Class<? extends DomainEvent<?>> resolveEventType(String eventType) {
        try {
            return (Class<? extends DomainEvent<?>>) Class.forName(eventType);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Unknown event type: " + eventType, exception);
        }
    }

    private record StringAggregateRootId(String value) implements AggregateRootId {
        private static final StringEntityType TYPE = new StringEntityType("String");

        @Override
        public EntityType getType() {
            return TYPE;
        }

        @Override
        @JsonValue
        public String asString() {
            return value;
        }

        @Override
        public String asTypedString() {
            return TYPE.asString() + ":" + value;
        }
    }
}
