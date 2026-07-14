package io.ddd4j.sample.order.application;

import java.time.Instant;

public record OutboxMessage(String id, String aggregateId, String eventType, Object payload,
                            Instant occurredAt) {
}
