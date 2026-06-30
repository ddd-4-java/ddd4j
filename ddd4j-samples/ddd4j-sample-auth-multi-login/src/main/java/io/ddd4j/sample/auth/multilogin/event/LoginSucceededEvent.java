package io.ddd4j.sample.auth.multilogin.event;

import io.ddd4j.core.subject.AuthPrincipal;
import io.ddd4j.core.subject.AuthRequest;

import java.time.Instant;

public record LoginSucceededEvent(AuthRequest request, AuthPrincipal principal, String token, Instant occurredAt) {
}
