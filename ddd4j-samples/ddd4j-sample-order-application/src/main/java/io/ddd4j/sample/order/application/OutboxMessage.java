package io.ddd4j.sample.order.application;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

import java.time.Instant;

public final class OutboxMessage {

    private static final long serialVersionUID = 0L;

    private final String id;

    private final String aggregateId;

    private final String eventType;

    private final Object payload;

    private final Instant occurredAt;

    @JsonCreator()
    public OutboxMessage(@JsonProperty("id") String id, @JsonProperty("aggregateId") String aggregateId, @JsonProperty("eventType") String eventType, @JsonProperty("payload") Object payload, @JsonProperty("occurredAt") Instant occurredAt) {
        this.id = id;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.occurredAt = occurredAt;
    }

    @JsonProperty("id")
    public String id() {
        return id;
    }

    @JsonProperty("aggregateId")
    public String aggregateId() {
        return aggregateId;
    }

    @JsonProperty("eventType")
    public String eventType() {
        return eventType;
    }

    @JsonProperty("payload")
    public Object payload() {
        return payload;
    }

    @JsonProperty("occurredAt")
    public Instant occurredAt() {
        return occurredAt;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (Objects.isNull(obj) || getClass() != obj.getClass()) {
            return false;
        }
        OutboxMessage other = (OutboxMessage) obj;
        return Objects.equals(this.id, other.id) && Objects.equals(this.aggregateId, other.aggregateId) && Objects.equals(this.eventType, other.eventType) && Objects.equals(this.payload, other.payload) && Objects.equals(this.occurredAt, other.occurredAt);
    }

    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Objects.hashCode(id);
        result = 31 * result + Objects.hashCode(aggregateId);
        result = 31 * result + Objects.hashCode(eventType);
        result = 31 * result + Objects.hashCode(payload);
        result = 31 * result + Objects.hashCode(occurredAt);
        return result;
    }

    @Override
    public String toString() {
        return "OutboxMessage[id=" + id + ", aggregateId=" + aggregateId + ", eventType=" + eventType + ", payload=" + payload + ", occurredAt=" + occurredAt + "]";
    }
}
