package io.ddd4j.web.core.context;

import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.auth.WebAccessPolicy;
import io.ddd4j.web.core.error.WebStatusException;
import io.ddd4j.web.core.idempotency.IdempotencyGuard;
import io.ddd4j.web.core.idempotency.IdempotencyLease;
import io.ddd4j.web.core.idempotency.WebIdempotencyLifecycle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SynchronousWebRequestSessionTest {

    @Mock
    private IdempotencyGuard guard;

    private static WebRequestContext request() {
        return new WebRequestContext("r-1", "t-1", "tenant-a", "Bearer token",
                Locale.CHINA, "127.0.0.1", "POST", "/api/orders");
    }

    private static WebRequestLifecycle lifecycle() {
        return new WebRequestLifecycle(new BearerSubjectAuthenticator(), WebAccessPolicy.disabled());
    }

    @Test
    void openAndCompleteSuccessfully() {
        when(guard.acquireLease(any(String.class), any(Duration.class)))
                .thenReturn(Optional.of(new IdempotencyLease("k", null, Duration.ofMinutes(5))));
        WebIdempotencyLifecycle idempotencyLifecycle = new WebIdempotencyLifecycle(guard);

        SynchronousWebRequestSession session = SynchronousWebRequestSession.open(
                request(), lifecycle(), idempotencyLifecycle, "order-1");

        assertNotNull(session.requestContext());
        session.complete(true);
        session.close();
    }

    @Test
    void openAndCompleteAsFailure() {
        when(guard.acquireLease(any(String.class), any(Duration.class)))
                .thenReturn(Optional.of(new IdempotencyLease("k", null, Duration.ofMinutes(5))));
        WebIdempotencyLifecycle idempotencyLifecycle = new WebIdempotencyLifecycle(guard);

        SynchronousWebRequestSession session = SynchronousWebRequestSession.open(
                request(), lifecycle(), idempotencyLifecycle, "order-1");

        session.complete(false);
    }

    @Test
    void openWithoutIdempotencyLifecycle() {
        SynchronousWebRequestSession session = SynchronousWebRequestSession.open(
                request(), lifecycle(), null, null);

        assertNotNull(session.requestContext());
        session.complete(true);
    }

    @Test
    void openRejectsNullArguments() {
        assertThrows(NullPointerException.class, () -> SynchronousWebRequestSession.open(
                null, lifecycle(), null, null));
        assertThrows(NullPointerException.class, () -> SynchronousWebRequestSession.open(
                request(), null, null, null));
    }

    @Test
    void openPropagatesAuthenticationFailure() {
        WebRequestLifecycle requiredLifecycle = new WebRequestLifecycle(
                new BearerSubjectAuthenticator(), WebAccessPolicy.required());
        WebRequestContext unauthenticated = new WebRequestContext("r-1", "t-1", "tenant-a", null,
                Locale.CHINA, "127.0.0.1", "POST", "/api/orders");

        assertThrows(WebStatusException.class, () -> SynchronousWebRequestSession.open(
                unauthenticated, requiredLifecycle, null, null));
    }
}
