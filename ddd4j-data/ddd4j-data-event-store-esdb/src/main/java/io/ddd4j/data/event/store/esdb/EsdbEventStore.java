/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.data.event.store.esdb;

import com.eventstore.dbclient.*;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.ddd4j.core.constant.EventStoreConstants;
import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.core.cqrs.eventstore.jackson.EventPayloadSerializer;
import io.ddd4j.core.ddd.event.*;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletionException;

/**
 * EventStoreDB 的强类型 EventStore adapter。
 */
public class EsdbEventStore implements EventStore {

    /**
     * 负责转换测试或协议正文中 JSON 数据的 Jackson 映射器。
     */
    private static final ObjectMapper METADATA_MAPPER = JsonMapper.builder().build();
    /**
     * 读写 EventStoreDB 事件流的原生客户端。
     */
    private final EventStoreDBClient client;
    /**
     * 拼接到事件存储流名前的应用前缀。
     */
    private final String streamPrefix;
    /**
     * 将领域事件负载写为存储格式并从存储格式恢复的序列化器。
     */
    private final EventPayloadSerializer serializer;

    /**
     * 创建不带流前缀的事件存储适配器。
     *
     * @param client EventStoreDB 客户端
     */
    public EsdbEventStore(EventStoreDBClient client) {
        this(client, "");
    }

    /**
     * 创建带流前缀的事件存储适配器。
     *
     * @param client       EventStoreDB 客户端
     * @param streamPrefix 事件流名称前缀
     */
    public EsdbEventStore(EventStoreDBClient client, String streamPrefix) {
        this(client, streamPrefix, new EventPayloadSerializer(JsonMapper.builder().findAndAddModules().build()));
    }

    /**
     * 装配连接到指定原生客户端的事件存储。
     *
     * @param client       EventStoreDB 原生客户端
     * @param streamPrefix 拼接到聚合流名前的命名空间前缀
     * @param serializer   事件负载的序列化器
     */
    EsdbEventStore(EventStoreDBClient client, String streamPrefix, EventPayloadSerializer serializer) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.streamPrefix = Objects.requireNonNull(streamPrefix, "streamPrefix must not be null");
        this.serializer = Objects.requireNonNull(serializer, "serializer must not be null");
    }

    /**
     * 把聚合版本转换为 EventStoreDB 的预期修订，零版本要求目标流尚不存在。
     *
     * @param expectedVersion 本次追加期望匹配的聚合当前版本
     * @return 发送至 EventStoreDB 的修订条件
     */
    static ExpectedRevision toExpectedRevision(long expectedVersion) {
        return expectedVersion == 0 ? ExpectedRevision.noStream() : ExpectedRevision.expectedRevision(expectedVersion - 1);
    }

    /**
     * 将从零起算的存储修订号换算为事件数。
     *
     * @param revision 事件存储返回的流修订
     * @return 流中包含的事件数，负修订号按零处理
     */
    private static long toEventCount(ExpectedRevision revision) {
        return revision.toRawLong() < 0 ? 0L : revision.toRawLong() + 1L;
    }

    /**
     * 向指定聚合的事件流追加这一批领域事件。
     *
     * <p>此实现调用 <code>events.isEmpty()</code>。</p>
     *
     * @param aggregateType   聚合类型
     * @param aggregateId     聚合 ID
     * @param events          要追加的事件（非空列表）
     * @param expectedVersion 期望的当前版本号（乐观锁，空流为 0）
     */
    @Override
    public void append(String aggregateType, AggregateRootId aggregateId,
                       List<? extends DomainEvent<?>> events, long expectedVersion) {
        Objects.requireNonNull(aggregateType, "aggregateType must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(events, "events must not be null");
        if (events.isEmpty()) return;
        String streamName = streamName(aggregateType, aggregateId);
        EventData[] data = events.stream().map(event -> toEventData(aggregateType, aggregateId, event)).toArray(EventData[]::new);
        try {
            client.appendToStream(streamName, AppendToStreamOptions.get().expectedRevision(toExpectedRevision(expectedVersion)), data).join();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof WrongExpectedVersionException conflict) {
                throw new AggregateVersionConflictException(aggregateType, aggregateId.asString(), expectedVersion,
                        toEventCount(conflict.getActualVersion()));
            }
            throw new IllegalStateException("Failed to append events to stream: " + streamName, exception.getCause());
        }
    }

    /**
     * 按聚合查询其事件流并还原已存储的事件。
     *
     * <p>此实现调用 <code>streamName(aggregateType, aggregateId)</code>。</p>
     *
     * @param aggregateType 聚合类型
     * @param aggregateId   聚合 ID
     * @return 实际返回<code>List.of()</code>
     */
    @Override
    public List<StoredEvent> read(String aggregateType, AggregateRootId aggregateId) {
        String streamName = streamName(aggregateType, aggregateId);
        try {
            ReadResult result = client.readStream(streamName, ReadStreamOptions.get().forwards().fromStart()
                    .maxCount(EventStoreConstants.ESDB_DEFAULT_READ_LIMIT)).join();
            return result.getEvents().stream().map(event -> toStoredEvent(event, aggregateType, aggregateId)).toList();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof StreamNotFoundException) return List.of();
            throw new IllegalStateException("Failed to read events from stream: " + streamName, exception.getCause());
        }
    }

    /**
     * 按聚合查询其事件流并还原已存储的事件。
     *
     * <p>此实现调用 <code>read(aggregateType, aggregateId).stream()</code>。</p>
     *
     * @param aggregateType 聚合类型
     * @param aggregateId   聚合 ID
     * @param fromVersion   起始版本号（含）
     * @param toVersion     结束版本号（含）
     * @return 版本区间内的持久化事件，按版本升序
     */
    @Override
    public List<StoredEvent> read(String aggregateType, AggregateRootId aggregateId, long fromVersion, long toVersion) {
        return read(aggregateType, aggregateId).stream()
                .filter(event -> event.version() >= fromVersion && event.version() <= toVersion).toList();
    }

    /**
     * 按全局位置读取用于投影或重放的存储事件。
     *
     * <p>满足 <code>limit &lt;= 0</code> 时抛出 <code>IllegalArgumentException</code>。 此实现调用 <code>ReadAllOptions.get().forwards().fromPosition(new Position(fromPosition, fromPosition))</code>。</p>
     *
     * @param fromPosition 起始 position（含）
     * @param limit        最大读取数量
     * @return 实际返回<code>events</code>
     */
    @Override
    public List<StoredEvent> readAll(long fromPosition, int limit) {
        if (limit <= 0) throw new IllegalArgumentException("limit must be positive");
        try {
            ReadResult result = client.readAll(ReadAllOptions.get().forwards()
                    .fromPosition(new Position(fromPosition, fromPosition)).maxCount(Math.max(limit * 2L, EventStoreConstants.ESDB_DEFAULT_READ_LIMIT))).join();
            List<StoredEvent> events = new ArrayList<>();
            for (ResolvedEvent resolved : result.getEvents()) {
                RecordedEvent recorded = resolved.getEvent();
                if (recorded == null || recorded.getStreamId().startsWith(EventStoreConstants.ESDB_SYSTEM_STREAM_PREFIX)
                        || (!streamPrefix.isEmpty() && !recorded.getStreamId().startsWith(streamPrefix))) continue;
                if (recorded.getPosition().getCommitUnsigned() < fromPosition) continue;
                events.add(toStoredEvent(resolved, null, null));
                if (events.size() == limit) break;
            }
            return events;
        } catch (CompletionException exception) {
            throw new IllegalStateException("Failed to read EventStoreDB global log", exception.getCause());
        }
    }

    /**
     * 使用前缀、聚合类型和聚合标识构造 EventStoreDB 流名。
     *
     * @param aggregateType 聚合类型名称
     * @param aggregateId   聚合根标识
     * @return 用于读写当前聚合的事件流名称
     */
    private String streamName(String aggregateType, AggregateRootId aggregateId) {
        return streamPrefix + aggregateType + "::" + aggregateId.asString();
    }

    /**
     * 序列化领域事件并在原生 EventData 上保留聚合身份元数据。
     *
     * @param aggregateType 聚合类型名称
     * @param aggregateId   聚合根标识
     * @param event         要序列化或转换的事件
     * @return 待提交给 EventStoreDB 的原生事件记录
     */
    private EventData toEventData(String aggregateType, AggregateRootId aggregateId, DomainEvent<?> event) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("aggregateType", aggregateType);
        metadata.put("aggregateId", aggregateId.asString());
        if (event.getCorrelationId() != null) metadata.put("correlationId", event.getCorrelationId().asString());
        if (event.getCausationId() != null) metadata.put("causationId", event.getCausationId().asString());
        return EventDataBuilder.json(event.getEventId().asUuid(), event.getClass().getName(), serializer.serialize(event).getBytes(StandardCharsets.UTF_8))
                .metadataAsJson(metadata).build();
    }

    /**
     * 根据已解析记录及元数据重建领域负载和持久化信封。
     *
     * @param resolved     EventStoreDB 返回的已解析事件记录
     * @param expectedType 当前读取请求期望的聚合类型
     * @param expectedId   当前读取请求期望的聚合标识
     * @return 映射后的存储事件
     */
    private StoredEvent toStoredEvent(ResolvedEvent resolved, String expectedType, AggregateRootId expectedId) {
        RecordedEvent recorded = resolved.getEvent();
        Map<String, Object> metadata;
        try {
            metadata = METADATA_MAPPER.readValue(
                    new String(recorded.getUserMetadata(), StandardCharsets.UTF_8),
                    new TypeReference<Map<String, Object>>() {
                    });
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid ESDB event metadata", exception);
        }
        if (Objects.isNull(metadata)) {
            throw new IllegalStateException("Invalid ESDB event metadata");
        }
        String aggregateType = expectedType == null ? String.valueOf(metadata.get("aggregateType")) : expectedType;
        AggregateRootId aggregateId = expectedId == null
                ? new StringAggregateRootId(String.valueOf(metadata.get("aggregateId"))) : expectedId;
        DomainEvent<?> payload = serializer.deserialize(new String(recorded.getEventData(), StandardCharsets.UTF_8), resolveEventType(recorded.getEventType()));
        return new StoredEvent(new EventId(recorded.getEventId()), aggregateType, aggregateId, recorded.getRevision() + 1L,
                recorded.getPosition().getCommitUnsigned(), ZonedDateTime.ofInstant(recorded.getCreated(), ZoneOffset.UTC), payload,
                EventId.valueOf((String) metadata.get("correlationId")), EventId.valueOf((String) metadata.get("causationId")));
    }

    /**
     * 按持久化的全限定类名解析事件负载类型。
     *
     * @param eventType 事件的 Java 类型或持久化类型名称
     * @return 用于反序列化的领域事件类
     */
    @SuppressWarnings("unchecked")
    private Class<? extends DomainEvent<?>> resolveEventType(String eventType) {
        try {
            return (Class<? extends DomainEvent<?>>) Class.forName(eventType);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Unknown event type: " + eventType, exception);
        }
    }

    /**
     * 将事件存储中的字符串标识适配为聚合根标识。
     */
    private final static class StringAggregateRootId implements AggregateRootId {

        /**
         * Java 序列化版本标识。
         */
        private static final long serialVersionUID = 0L;
        /**
         * 字符串格式的聚合标识类型。
         */
        private static final StringEntityType TYPE = new StringEntityType("String");
        /**
         * 从持久化记录读取的聚合标识原值。
         */
        private final String value;

        /**
         * 把持久化标识恢复成字符串聚合根标识。
         *
         * @param value 存储的标识原值
         */
        @JsonCreator()
        private StringAggregateRootId(@JsonProperty("value") String value) {
            this.value = value;
        }

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
         * 读取<code>value</code> 字段。
         *
         * @return <code>value</code> 字段
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
