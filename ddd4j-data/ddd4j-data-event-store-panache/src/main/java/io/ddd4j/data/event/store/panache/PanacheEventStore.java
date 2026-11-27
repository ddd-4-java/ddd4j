/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.data.event.store.panache;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

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
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.NoResultException;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 基于 Quarkus Hibernate ORM Panache 的强类型 {@link EventStore} 实现。
 *
 * <p>该实现与 JPA/JDBI adapter 共享 Core EventStore 契约：以聚合类型、聚合标识和
 * 版本定位流，保存事件元数据，并在读取时按显式事件类型反序列化。位置号遵循共同契约的
 * 已有的唯一约束和重试策略，避免丢失并发恢复能力。
 */
public class PanacheEventStore implements EventStore {

    /** \u6267\u884c JPA \u67e5\u8be2\u548c\u6301\u4e45\u5316\u64cd\u4f5c\u7684\u5b9e\u4f53\u7ba1\u7406\u5668\u3002 */
    private final EntityManager entityManager;
    /** \u5728\u6570\u636e\u5e93\u53ef\u91cd\u8bd5\u6545\u969c\u540e\u91cd\u65b0\u6267\u884c\u64cd\u4f5c\u7684\u7b56\u7565\u3002 */
    private final EventStoreRetry retry;
    /** \u5c06\u9886\u57df\u4e8b\u4ef6\u8d1f\u8f7d\u5199\u4e3a\u5b58\u50a8\u683c\u5f0f\u5e76\u4ece\u5b58\u50a8\u683c\u5f0f\u6062\u590d\u7684\u5e8f\u5217\u5316\u5668\u3002 */
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
     * @param retry         position 唯一约束冲突的重试策略
     */
    public PanacheEventStore(EntityManager entityManager, EventStoreRetry retry) {
        this(entityManager, retry, new EventPayloadSerializer(JsonMapper.builder().findAndAddModules().build()));
    }

    /**
     * \u7528\u7ed9\u5b9a\u503c\u521d\u59cb\u5316 <code>PanacheEventStore</code> \u5b9e\u4f8b\u3002
     * @param entityManager \u6267\u884c JPA \u67e5\u8be2\u548c\u6301\u4e45\u5316\u64cd\u4f5c\u7684\u5b9e\u4f53\u7ba1\u7406\u5668
     * @param retry <code>retry</code> \u5b57\u6bb5
     * @param serializer <code>serializer</code> \u5b57\u6bb5
     */
    PanacheEventStore(EntityManager entityManager, EventStoreRetry retry, EventPayloadSerializer serializer) {
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager must not be null");
        this.retry = Objects.requireNonNull(retry, "retry must not be null");
        this.serializer = Objects.requireNonNull(serializer, "serializer must not be null");
    }

    /**
     * \u4ee5\u805a\u5408\u7c7b\u578b\u548c\u805a\u5408\u6807\u8bc6\u8ffd\u52a0\u4e8b\u4ef6\uff1b\u7248\u672c\u51b2\u7a81\u4f7f\u7528\u7edf\u4e00\u5f02\u5e38\u8868\u793a\uff0c\u4f4d\u7f6e\u53f7\u51b2\u7a81\u53ef\u91cd\u8bd5\u3002
     *
     * @param aggregateType \u805a\u5408\u7c7b\u578b
     * @param aggregateId \u805a\u5408\u6839\u6807\u8bc6
     * @param events \u6309\u987a\u5e8f\u8ffd\u52a0\u6216\u56de\u653e\u7684\u9886\u57df\u4e8b\u4ef6
     * @param expectedVersion \u9884\u671f\u7684\u5f53\u524d\u805a\u5408\u7248\u672c
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
     * \u6309\u7248\u672c\u5347\u5e8f\u8bfb\u53d6\u4e00\u4e2a\u805a\u5408\u6d41\u3002
     *
     * @param aggregateType \u805a\u5408\u7c7b\u578b
     * @param aggregateId \u805a\u5408\u6839\u6807\u8bc6
     * @return \u6309\u7248\u672c\u987a\u5e8f\u8fd4\u56de\u7684\u9886\u57df\u4e8b\u4ef6\u6d41
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
                .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
    }

    /**
     * \u6309\u95ed\u5408\u7248\u672c\u533a\u95f4\u8bfb\u53d6\u4e00\u4e2a\u805a\u5408\u6d41\u3002
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
                .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
    }

    /**
     * \u6309\u5168\u5c40 position \u5347\u5e8f\u5206\u9875\u8bfb\u53d6\u4e8b\u4ef6\u3002
     *
     * @param fromPosition \u8d77\u59cb\u6d41\u4f4d\u7f6e
     * @param limit \u672c\u6b21\u6700\u591a\u8bfb\u53d6\u7684\u8bb0\u5f55\u6570
     * @return \u6309\u5168\u5c40\u4f4d\u7f6e\u6392\u5e8f\u7684\u4e8b\u4ef6
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
                .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
    }

    /**
     * \u7edf\u8ba1\u8be5\u805a\u5408\u5df2\u6301\u4e45\u5316\u7684\u4e8b\u4ef6\u4ee5\u83b7\u5f97\u5f53\u524d\u7248\u672c\u3002
     * @param aggregateType \u805a\u5408\u7c7b\u578b\u540d\u79f0
     * @param aggregateId \u805a\u5408\u6839\u6807\u8bc6
     * @return \u7528\u4e8e\u4e50\u89c2\u9501\u6821\u9a8c\u7684\u805a\u5408\u5f53\u524d\u7248\u672c
     */
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

    /**
     * \u5728\u4e8b\u4ef6\u8868\u6700\u5927\u5168\u5c40\u4f4d\u7f6e\u7684\u57fa\u7840\u4e0a\u751f\u6210\u4e0b\u4e00\u4f4d\u7f6e\u3002
     * @return \u51c6\u5907\u5199\u5165\u4e8b\u4ef6\u7684\u4f4d\u7f6e
     */
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

    /**
     * \u8bfb\u53d6\u6301\u4e45\u5316\u884c\u7684\u4e8b\u4ef6\u7c7b\u578b\u5e76\u89e3\u7801\u8d1f\u8f7d\uff0c\u6062\u590d\u5b58\u50a8\u4e8b\u4ef6\u3002
     * @param entity \u6570\u636e\u5e93\u8fd4\u56de\u7684\u6301\u4e45\u5316\u5b9e\u4f53
     * @return \u4f9b\u4e8b\u4ef6\u8bfb\u53d6\u5165\u53e3\u8fd4\u56de\u7684\u5b58\u50a8\u4e8b\u4ef6
     */
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

    /**
     * \u6309\u6301\u4e45\u5316\u7684\u5168\u9650\u5b9a\u7c7b\u540d\u89e3\u6790\u4e8b\u4ef6\u8d1f\u8f7d\u7c7b\u578b\u3002
     * @param eventType \u4e8b\u4ef6\u7684 Java \u7c7b\u578b\u6216\u6301\u4e45\u5316\u7c7b\u578b\u540d\u79f0
     * @return \u7528\u4e8e\u53cd\u5e8f\u5217\u5316\u7684\u9886\u57df\u4e8b\u4ef6\u7c7b
     */
    @SuppressWarnings("unchecked")
    private Class<? extends DomainEvent<?>> resolveEventType(String eventType) {
        try {
            return (Class<? extends DomainEvent<?>>) Class.forName(eventType);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Unknown event type: " + eventType, exception);
        }
    }

    /** \u5c06\u4e8b\u4ef6\u5b58\u50a8\u4e2d\u7684\u5b57\u7b26\u4e32\u6807\u8bc6\u9002\u914d\u4e3a\u805a\u5408\u6839\u6807\u8bc6\u3002 */
    private final static class StringAggregateRootId implements AggregateRootId {

        /** Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002 */
        private static final long serialVersionUID = 0L;

        /** \u4ece\u5b58\u50a8\u8bb0\u5f55\u4e2d\u8bfb\u53d6\u7684\u539f\u59cb\u5b57\u7b26\u4e32\u6807\u8bc6\u3002 */
        private final String value;

        /** \u7528\u4e8e\u63cf\u8ff0\u5b57\u7b26\u4e32\u805a\u5408\u6807\u8bc6\u7684\u5b9e\u4f53\u7c7b\u578b\u3002 */
        private static final StringEntityType TYPE = new StringEntityType("String");

        /**
         * 返回当前值或标识公开的类型。
         *
         * @return 实际返回<code>TYPE</code>，供调用方取得当前值
         */
        @Override
        public EntityType getType() {
            return TYPE;
        }

        /**
         * 返回聚合或实体标识的业务字符串。
         *
         * @return 不含类型标记的业务标识
         */
        @Override
        @JsonValue
        public String asString() {
            return value;
        }

        /**
         * 返回带类型前缀的标识字符串。
         *
         * <p>此实现调用 <code>TYPE.asString()</code>。</p>
         *
         * @return 包含类型标记的标识
         */
        @Override
        public String asTypedString() {
            return TYPE.asString() + ":" + value;
        }

        /**
         * \u4ece\u6301\u4e45\u5316\u5b57\u7b26\u4e32\u6062\u590d\u805a\u5408\u6839\u6807\u8bc6\u3002
         * @param value \u539f\u59cb\u6807\u8bc6\u6587\u672c
         */
        @JsonCreator()
        private StringAggregateRootId(@JsonProperty("value") String value) {
            this.value = value;
        }

        /**
         * \u8bfb\u53d6<code>value</code> \u5b57\u6bb5\u3002
         * @return <code>value</code> \u5b57\u6bb5
         */
        @JsonProperty("value")
        public String value() {
            return value;
        }

        /**
         * 根据<code>value</code>比较对象的值是否相等。
         *
         * <p>只比较运行时类型相同的对象。</p>
         *
         * @param obj 用来比较或转换的输入对象
         * @return 对象满足此实现的相等条件时为真
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                return false;
            }
            StringAggregateRootId other = (StringAggregateRootId) obj;
            return Objects.equals(this.value, other.value);
        }

        /**
         * 计算与对象相等条件一致的哈希值。
         *
         * @return 由参与相等比较的状态计算出的哈希值
         */
        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(value);
            return result;
        }

        /**
         * 生成用于调试和日志的对象描述，包含<code>value</code>。
         *
         * @return 表示当前对象状态的字符串
         */
        @Override
        public String toString() {
            return "StringAggregateRootId[value=" + value + "]";
        }
    }
}
