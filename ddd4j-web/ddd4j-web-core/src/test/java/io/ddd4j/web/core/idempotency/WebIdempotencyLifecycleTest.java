package io.ddd4j.web.core.idempotency;

import io.ddd4j.web.core.context.WebRequestContext;
import io.ddd4j.web.core.error.WebStatusException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebIdempotencyLifecycleTest {

    @Mock
    private IdempotencyGuard guard;

    private static WebRequestContext request() {
        return new WebRequestContext("r-1", "t-1", "tenant-a", null,
                Locale.CHINA, "127.0.0.1", "POST", "/api/orders");
    }

    @Test
    void openReturnsEmptyForBlankKey() {
        WebIdempotencyLifecycle lifecycle = new WebIdempotencyLifecycle(guard);
        assertFalse(lifecycle.open(request(), "  ").isPresent());
        assertFalse(lifecycle.open(request(), null).isPresent());
    }

    @Test
    void openAcquiresLeaseAndScopeCompletesAndCloses() {
        when(guard.acquireLease(any(String.class), any(Duration.class)))
                .thenReturn(Optional.of(new IdempotencyLease("tenant-a:POST:/api/orders:order-1",
                        null, Duration.ofMinutes(5))));

        WebIdempotencyLifecycle lifecycle = new WebIdempotencyLifecycle(guard);
        Optional<WebIdempotencyLifecycle.Scope> scope = lifecycle.open(request(), "order-1");

        assertTrue(scope.isPresent());
        scope.get().complete();
        verify(guard).complete(any(IdempotencyLease.class));
        scope.get().close();
    }

    @Test
    void closeWithoutCompleteReleasesLease() {
        when(guard.acquireLease(any(String.class), any(Duration.class)))
                .thenReturn(Optional.of(new IdempotencyLease("tenant-a:POST:/api/orders:order-1",
                        null, Duration.ofMinutes(5))));

        WebIdempotencyLifecycle lifecycle = new WebIdempotencyLifecycle(guard);
        Optional<WebIdempotencyLifecycle.Scope> scope = lifecycle.open(request(), "order-1");

        scope.get().close();
        verify(guard).release(any(IdempotencyLease.class));
    }

    @Test
    void openThrowsConflictWhenLeaseUnavailable() {
        when(guard.acquireLease(any(String.class), any(Duration.class)))
                .thenReturn(Optional.empty());

        WebIdempotencyLifecycle lifecycle = new WebIdempotencyLifecycle(guard);
        assertThrows(WebStatusException.class, () -> lifecycle.open(request(), "order-1"));
    }

    @Test
    void constructorValidatesArguments() {
        assertThrows(NullPointerException.class, () -> new WebIdempotencyLifecycle(null));
        assertThrows(NullPointerException.class,
                () -> new WebIdempotencyLifecycle(guard, null));
        assertThrows(IllegalArgumentException.class,
                () -> new WebIdempotencyLifecycle(guard, Duration.ZERO));
    }

    @Test
    void openRejectsNullContextWithKey() {
        WebIdempotencyLifecycle lifecycle = new WebIdempotencyLifecycle(guard);
        assertThrows(NullPointerException.class, () -> lifecycle.open(null, "order-1"));
    }

    @Test
    void openUsesUnderscoreForBlankStorageValues() {
        when(guard.acquireLease(any(String.class), any(Duration.class)))
                .thenReturn(Optional.of(new IdempotencyLease("_:_:/:order-1", null, Duration.ofMinutes(5))));
        WebRequestContext blank = new WebRequestContext("r-1", null, null, null,
                null, null, null, null);

        WebIdempotencyLifecycle lifecycle = new WebIdempotencyLifecycle(guard);
        Optional<WebIdempotencyLifecycle.Scope> scope = lifecycle.open(blank, "order-1");

        assertTrue(scope.isPresent());
        verify(guard).acquireLease("_:_:/:order-1", Duration.ofMinutes(5));
        scope.get().close();
    }

    @Test
    void scopeCompleteAfterCloseIsNoOp() {
        when(guard.acquireLease(any(String.class), any(Duration.class)))
                .thenReturn(Optional.of(new IdempotencyLease("tenant-a:POST:/api/orders:order-1",
                        null, Duration.ofMinutes(5))));

        WebIdempotencyLifecycle lifecycle = new WebIdempotencyLifecycle(guard);
        WebIdempotencyLifecycle.Scope scope = lifecycle.open(request(), "order-1").orElseThrow();

        scope.close();
        scope.complete();

        verify(guard).release(any(IdempotencyLease.class));
        verify(guard, never()).complete(any(IdempotencyLease.class));
    }

    @Test
    void scopeCloseTwiceIsNoOp() {
        when(guard.acquireLease(any(String.class), any(Duration.class)))
                .thenReturn(Optional.of(new IdempotencyLease("tenant-a:POST:/api/orders:order-1",
                        null, Duration.ofMinutes(5))));

        WebIdempotencyLifecycle lifecycle = new WebIdempotencyLifecycle(guard);
        WebIdempotencyLifecycle.Scope scope = lifecycle.open(request(), "order-1").orElseThrow();

        scope.close();
        scope.close();

        verify(guard, times(1)).release(any(IdempotencyLease.class));
    }
}
