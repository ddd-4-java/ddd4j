package io.ddd4j.sample.auth.multilogin.event;

import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.auth.AuthRequest;

import java.time.Instant;

public record LoginSucceededEvent(AuthRequest request, AuthPrincipal principal, String token, Instant occurredAt) {
}
