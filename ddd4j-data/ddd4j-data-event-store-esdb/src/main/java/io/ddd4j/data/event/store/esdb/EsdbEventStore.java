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

    /** \u8d1f\u8d23\u8f6c\u6362\u6d4b\u8bd5\u6216\u534f\u8bae\u6b63\u6587\u4e2d JSON \u6570\u636e\u7684 Jackson \u6620\u5c04\u5668\u3002 */
    private static final ObjectMapper METADATA_MAPPER = JsonMapper.builder().build();
    /** \u8bfb\u5199 EventStoreDB \u4e8b\u4ef6\u6d41\u7684\u539f\u751f\u5ba2\u6237\u7aef\u3002 */
    private final EventStoreDBClient client;
    /** \u62fc\u63a5\u5230\u4e8b\u4ef6\u5b58\u50a8\u6d41\u540d\u524d\u7684\u5e94\u7528\u524d\u7f00\u3002 */
    private final String streamPrefix;
    /** \u5c06\u9886\u57df\u4e8b\u4ef6\u8d1f\u8f7d\u5199\u4e3a\u5b58\u50a8\u683c\u5f0f\u5e76\u4ece\u5b58\u50a8\u683c\u5f0f\u6062\u590d\u7684\u5e8f\u5217\u5316\u5668\u3002 */
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
     * \u88c5\u914d\u8fde\u63a5\u5230\u6307\u5b9a\u539f\u751f\u5ba2\u6237\u7aef\u7684\u4e8b\u4ef6\u5b58\u50a8\u3002
     * @param client EventStoreDB \u539f\u751f\u5ba2\u6237\u7aef
     * @param streamPrefix \u62fc\u63a5\u5230\u805a\u5408\u6d41\u540d\u524d\u7684\u547d\u540d\u7a7a\u95f4\u524d\u7f00
     * @param serializer \u4e8b\u4ef6\u8d1f\u8f7d\u7684\u5e8f\u5217\u5316\u5668
     */
    EsdbEventStore(EventStoreDBClient client, String streamPrefix, EventPayloadSerializer serializer) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.streamPrefix = Objects.requireNonNull(streamPrefix, "streamPrefix must not be null");
        this.serializer = Objects.requireNonNull(serializer, "serializer must not be null");
    }

    /**
     * \u628a\u805a\u5408\u7248\u672c\u8f6c\u6362\u4e3a EventStoreDB \u7684\u9884\u671f\u4fee\u8ba2\uff0c\u96f6\u7248\u672c\u8981\u6c42\u76ee\u6807\u6d41\u5c1a\u4e0d\u5b58\u5728\u3002
     * @param expectedVersion \u672c\u6b21\u8ffd\u52a0\u671f\u671b\u5339\u914d\u7684\u805a\u5408\u5f53\u524d\u7248\u672c
     * @return \u53d1\u9001\u81f3 EventStoreDB \u7684\u4fee\u8ba2\u6761\u4ef6
     */
    static ExpectedRevision toExpectedRevision(long expectedVersion) {
        return expectedVersion == 0 ? ExpectedRevision.noStream() : ExpectedRevision.expectedRevision(expectedVersion - 1);
    }

    /**
     * \u5c06\u4ece\u96f6\u8d77\u7b97\u7684\u5b58\u50a8\u4fee\u8ba2\u53f7\u6362\u7b97\u4e3a\u4e8b\u4ef6\u6570\u3002
     * @param revision \u4e8b\u4ef6\u5b58\u50a8\u8fd4\u56de\u7684\u6d41\u4fee\u8ba2
     * @return \u6d41\u4e2d\u5305\u542b\u7684\u4e8b\u4ef6\u6570\uff0c\u8d1f\u4fee\u8ba2\u53f7\u6309\u96f6\u5904\u7406
     */
    private static long toEventCount(ExpectedRevision revision) {
        return revision.toRawLong() < 0 ? 0L : revision.toRawLong() + 1L;
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public List<StoredEvent> read(String aggregateType, AggregateRootId aggregateId, long fromVersion, long toVersion) {
        return read(aggregateType, aggregateId).stream()
                .filter(event -> event.version() >= fromVersion && event.version() <= toVersion).toList();
    }

    /** {@inheritDoc} */
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
     * \u4f7f\u7528\u524d\u7f00\u3001\u805a\u5408\u7c7b\u578b\u548c\u805a\u5408\u6807\u8bc6\u6784\u9020 EventStoreDB \u6d41\u540d\u3002
     * @param aggregateType \u805a\u5408\u7c7b\u578b\u540d\u79f0
     * @param aggregateId \u805a\u5408\u6839\u6807\u8bc6
     * @return \u7528\u4e8e\u8bfb\u5199\u5f53\u524d\u805a\u5408\u7684\u4e8b\u4ef6\u6d41\u540d\u79f0
     */
    private String streamName(String aggregateType, AggregateRootId aggregateId) {
        return streamPrefix + aggregateType + "::" + aggregateId.asString();
    }

    /**
     * \u5e8f\u5217\u5316\u9886\u57df\u4e8b\u4ef6\u5e76\u5728\u539f\u751f EventData \u4e0a\u4fdd\u7559\u805a\u5408\u8eab\u4efd\u5143\u6570\u636e\u3002
     * @param aggregateType \u805a\u5408\u7c7b\u578b\u540d\u79f0
     * @param aggregateId \u805a\u5408\u6839\u6807\u8bc6
     * @param event \u8981\u5e8f\u5217\u5316\u6216\u8f6c\u6362\u7684\u4e8b\u4ef6
     * @return \u5f85\u63d0\u4ea4\u7ed9 EventStoreDB \u7684\u539f\u751f\u4e8b\u4ef6\u8bb0\u5f55
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
     * \u6839\u636e\u5df2\u89e3\u6790\u8bb0\u5f55\u53ca\u5143\u6570\u636e\u91cd\u5efa\u9886\u57df\u8d1f\u8f7d\u548c\u6301\u4e45\u5316\u4fe1\u5c01\u3002
     * @param resolved EventStoreDB \u8fd4\u56de\u7684\u5df2\u89e3\u6790\u4e8b\u4ef6\u8bb0\u5f55
     * @param expectedType \u5f53\u524d\u8bfb\u53d6\u8bf7\u6c42\u671f\u671b\u7684\u805a\u5408\u7c7b\u578b
     * @param expectedId \u5f53\u524d\u8bfb\u53d6\u8bf7\u6c42\u671f\u671b\u7684\u805a\u5408\u6807\u8bc6
     * @return \u6620\u5c04\u540e\u7684\u5b58\u50a8\u4e8b\u4ef6
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
        /** \u5b57\u7b26\u4e32\u683c\u5f0f\u7684\u805a\u5408\u6807\u8bc6\u7c7b\u578b\u3002 */
        private static final StringEntityType TYPE = new StringEntityType("String");
        /** \u4ece\u6301\u4e45\u5316\u8bb0\u5f55\u8bfb\u53d6\u7684\u805a\u5408\u6807\u8bc6\u539f\u503c\u3002 */
        private final String value;

        /**
         * \u628a\u6301\u4e45\u5316\u6807\u8bc6\u6062\u590d\u6210\u5b57\u7b26\u4e32\u805a\u5408\u6839\u6807\u8bc6\u3002
         * @param value \u5b58\u50a8\u7684\u6807\u8bc6\u539f\u503c
         */
        @JsonCreator()
        private StringAggregateRootId(@JsonProperty("value") String value) {
            this.value = value;
        }

        /** {@inheritDoc} */
        @Override
        public EntityType getType() {
            return TYPE;
        }

        /** {@inheritDoc} */
        @Override
        @JsonValue
        public String asString() {
            return value;
        }

        /** {@inheritDoc} */
        @Override
        public String asTypedString() {
            return TYPE.asString() + ":" + value;
        }

        /**
         * \u8bfb\u53d6<code>value</code> \u5b57\u6bb5\u3002
         * @return <code>value</code> \u5b57\u6bb5
         */
        @JsonProperty("value")
        public String value() {
            return value;
        }

        /** {@inheritDoc} */
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

        /** {@inheritDoc} */
        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(value);
            return result;
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return "StringAggregateRootId[value=" + value + "]";
        }
    }
}
