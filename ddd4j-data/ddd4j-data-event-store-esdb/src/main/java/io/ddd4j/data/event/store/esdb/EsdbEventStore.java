/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.data.event.store.esdb;

import java.util.Arrays;
import com.eventstore.dbclient.AppendToStreamOptions;
import com.eventstore.dbclient.EventData;
import com.eventstore.dbclient.EventDataBuilder;
import com.eventstore.dbclient.EventStoreDBClient;
import com.eventstore.dbclient.ExpectedRevision;
import com.eventstore.dbclient.Position;
import com.eventstore.dbclient.ReadAllOptions;
import com.eventstore.dbclient.ReadResult;
import com.eventstore.dbclient.ReadStreamOptions;
import com.eventstore.dbclient.RecordedEvent;
import com.eventstore.dbclient.ResolvedEvent;
import com.eventstore.dbclient.StreamNotFoundException;
import com.eventstore.dbclient.WrongExpectedVersionException;
import com.fasterxml.jackson.annotation.JsonValue;
import io.ddd4j.core.constant.EventStoreConstants;
import io.ddd4j.core.cqrs.eventstore.AggregateVersionConflictException;
import io.ddd4j.core.cqrs.eventstore.EventStore;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.core.cqrs.eventstore.jackson.EventPayloadSerializer;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.EventId;
import io.ddd4j.core.ddd.event.StringEntityType;
import io.ddd4j.kit.lang.JsonKit;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;

/** EventStoreDB 的强类型 EventStore adapter。 */
public class EsdbEventStore implements EventStore {

    private final EventStoreDBClient client;
    private final String streamPrefix;
    private final EventPayloadSerializer serializer;

    public EsdbEventStore(EventStoreDBClient client) {
        this(client, "");
    }

    public EsdbEventStore(EventStoreDBClient client, String streamPrefix) {
        this(client, streamPrefix, new EventPayloadSerializer(JsonMapper.builder().findAndAddModules().build()));
    }

    EsdbEventStore(EventStoreDBClient client, String streamPrefix, EventPayloadSerializer serializer) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.streamPrefix = Objects.requireNonNull(streamPrefix, "streamPrefix must not be null");
        this.serializer = Objects.requireNonNull(serializer, "serializer must not be null");
    }

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

    @Override
    public List<StoredEvent> read(String aggregateType, AggregateRootId aggregateId) {
        String streamName = streamName(aggregateType, aggregateId);
        try {
            ReadResult result = client.readStream(streamName, ReadStreamOptions.get().forwards().fromStart()
                    .maxCount(EventStoreConstants.ESDB_DEFAULT_READ_LIMIT)).join();
            return result.getEvents().stream().map(event -> toStoredEvent(event, aggregateType, aggregateId)).toList();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof StreamNotFoundException) return Arrays.asList();
            throw new IllegalStateException("Failed to read events from stream: " + streamName, exception.getCause());
        }
    }

    @Override
    public List<StoredEvent> read(String aggregateType, AggregateRootId aggregateId, long fromVersion, long toVersion) {
        return read(aggregateType, aggregateId).stream()
                .filter(event -> event.version() >= fromVersion && event.version() <= toVersion).toList();
    }

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

    static ExpectedRevision toExpectedRevision(long expectedVersion) {
        return expectedVersion == 0 ? ExpectedRevision.noStream() : ExpectedRevision.expectedRevision(expectedVersion - 1);
    }

    private static long toEventCount(ExpectedRevision revision) {
        return revision.toRawLong() < 0 ? 0L : revision.toRawLong() + 1L;
    }

    private String streamName(String aggregateType, AggregateRootId aggregateId) {
        return streamPrefix + aggregateType + "::" + aggregateId.asString();
    }

    private EventData toEventData(String aggregateType, AggregateRootId aggregateId, DomainEvent<?> event) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("aggregateType", aggregateType);
        metadata.put("aggregateId", aggregateId.asString());
        if (event.getCorrelationId() != null) metadata.put("correlationId", event.getCorrelationId().asString());
        if (event.getCausationId() != null) metadata.put("causationId", event.getCausationId().asString());
        return EventDataBuilder.json(event.getEventId().asUuid(), event.getClass().getName(), serializer.serialize(event).getBytes(StandardCharsets.UTF_8))
                .metadataAsJson(metadata).build();
    }

    private StoredEvent toStoredEvent(ResolvedEvent resolved, String expectedType, AggregateRootId expectedId) {
        RecordedEvent recorded = resolved.getEvent();
        Map<String, Object> metadata = JsonKit.toMap(new String(recorded.getUserMetadata(), StandardCharsets.UTF_8));
        String aggregateType = expectedType == null ? String.valueOf(metadata.get("aggregateType")) : expectedType;
        AggregateRootId aggregateId = expectedId == null
                ? new StringAggregateRootId(String.valueOf(metadata.get("aggregateId"))) : expectedId;
        DomainEvent<?> payload = serializer.deserialize(new String(recorded.getEventData(), StandardCharsets.UTF_8), resolveEventType(recorded.getEventType()));
        return new StoredEvent(new EventId(recorded.getEventId()), aggregateType, aggregateId, recorded.getRevision() + 1L,
                recorded.getPosition().getCommitUnsigned(), ZonedDateTime.ofInstant(recorded.getCreated(), ZoneOffset.UTC), payload,
                EventId.valueOf((String) metadata.get("correlationId")), EventId.valueOf((String) metadata.get("causationId")));
    }

    @SuppressWarnings("unchecked")
    private Class<? extends DomainEvent<?>> resolveEventType(String eventType) {
        try { return (Class<? extends DomainEvent<?>>) Class.forName(eventType); }
        catch (ClassNotFoundException exception) { throw new IllegalStateException("Unknown event type: " + eventType, exception); }
    }private static final class StringAggregateRootId implements AggregateRootId {
        private final String value;

        public StringAggregateRootId(String value) {
            this.value = value;
        }
        public String value() { return value; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof StringAggregateRootId)) return false;
            StringAggregateRootId other = (StringAggregateRootId) o;
            return Objects.equals(this.value, other.value);
        }
        @Override
        public int hashCode() {
            return java.util.Objects.hash(value);
        }
        @Override
        public String toString() {
            return "StringAggregateRootId{" + "value=" + value + "}";
        }
        private static final StringEntityType TYPE = new StringEntityType("String");
        @Override public EntityType getType() { return TYPE; }
        @Override @JsonValue public String asString() { return value; }
        @Override public String asTypedString() { return TYPE.asString() + ":" + value; }
    
    }
}
