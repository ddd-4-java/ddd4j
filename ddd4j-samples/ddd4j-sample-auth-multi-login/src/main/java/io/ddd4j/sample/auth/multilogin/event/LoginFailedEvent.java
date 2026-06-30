package io.ddd4j.sample.auth.multilogin.event;

import io.ddd4j.core.subject.AuthRequest;

import java.time.Instant;

public record LoginFailedEvent(AuthRequest request, String reason, Instant occurredAt) {
}
