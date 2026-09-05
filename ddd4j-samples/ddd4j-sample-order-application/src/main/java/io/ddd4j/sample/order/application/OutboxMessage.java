package io.ddd4j.sample.order.application;

import java.util.Objects;
import java.time.Instant;

public final class OutboxMessage {
        private final String id;
        private final String aggregateId;
        private final String eventType;
        private final Object payload;
        private final Instant occurredAt;

        public OutboxMessage(String id, String aggregateId, String eventType, Object payload, Instant occurredAt) {
            this.id = id;
            this.aggregateId = aggregateId;
            this.eventType = eventType;
            this.payload = payload;
            this.occurredAt = occurredAt;
        }
        public String id() { return id; }
        public String aggregateId() { return aggregateId; }
        public String eventType() { return eventType; }
        public Object payload() { return payload; }
        public Instant occurredAt() { return occurredAt; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
        OutboxMessage other = (OutboxMessage) o;
            return Objects.equals(this.id, other.id) && Objects.equals(this.aggregateId, other.aggregateId) && Objects.equals(this.eventType, other.eventType) && Objects.equals(this.payload, other.payload) && Objects.equals(this.occurredAt, other.occurredAt);
        }
        @Override
        public int hashCode() { return java.util.Objects.hash(id, aggregateId, eventType, payload, occurredAt); }
        @Override
        public String toString() {
            return "OutboxMessage{" + "id=" + id + ", " + "aggregateId=" + aggregateId + ", " + "eventType=" + eventType + ", " + "payload=" + payload + ", " + "occurredAt=" + occurredAt + "}";
        }
    }
