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

/** \u9a8c\u8bc1\u5e42\u7b49\u79df\u7ea6\u7684\u83b7\u53d6\u3001\u5b8c\u6210\u548c\u91ca\u653e\u3002 */
@ExtendWith(MockitoExtension.class)
class WebIdempotencyLifecycleTest {

    /** \u7528\u4e8e\u9694\u79bb <code>IdempotencyGuard</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
    @Mock
    private IdempotencyGuard guard;

    /**
     * \u521b\u5efa <code>new WebRequestContext</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new WebRequestContext("r-1", "t-1", "tenant-a", null, Locale.CHINA, "127.0.0.1", "POST", "/api/orders")</code>
     */
    private static WebRequestContext request() {
        return new WebRequestContext("r-1", "t-1", "tenant-a", null,
                Locale.CHINA, "127.0.0.1", "POST", "/api/orders");
    }

    /** \u9a8c\u8bc1<code>lifecycle.open(request(), " ").isPresent()</code>\u4e3a\u5047\uff0c<code>lifecycle.open(request(), null).isPresent()</code>\u4e3a\u5047\u3002 */
    @Test
    void openReturnsEmptyForBlankKey() {
        WebIdempotencyLifecycle lifecycle = new WebIdempotencyLifecycle(guard);
        assertFalse(lifecycle.open(request(), "  ").isPresent());
        assertFalse(lifecycle.open(request(), null).isPresent());
    }

    /** \u9a8c\u8bc1<code>scope.isPresent()</code>\u4e3a\u771f\u3002 */
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

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>guard.acquireLease()</code>\u3001<code>any()</code>\u3001<code>Duration.ofMinutes()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
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

    /** \u9a8c\u8bc1<code>lifecycle.open(request(), "order-1")</code> \u629b\u51fa <code>WebStatusException.class</code>\u3002 */
    @Test
    void openThrowsConflictWhenLeaseUnavailable() {
        when(guard.acquireLease(any(String.class), any(Duration.class)))
                .thenReturn(Optional.empty());

        WebIdempotencyLifecycle lifecycle = new WebIdempotencyLifecycle(guard);
        assertThrows(WebStatusException.class, () -> lifecycle.open(request(), "order-1"));
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>NullPointerException.class</code>\uff0c\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
    @Test
    void constructorValidatesArguments() {
        assertThrows(NullPointerException.class, () -> new WebIdempotencyLifecycle(null));
        assertThrows(NullPointerException.class,
                () -> new WebIdempotencyLifecycle(guard, null));
        assertThrows(IllegalArgumentException.class,
                () -> new WebIdempotencyLifecycle(guard, Duration.ZERO));
    }

    /** \u9a8c\u8bc1<code>lifecycle.open(null, "order-1")</code> \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void openRejectsNullContextWithKey() {
        WebIdempotencyLifecycle lifecycle = new WebIdempotencyLifecycle(guard);
        assertThrows(NullPointerException.class, () -> lifecycle.open(null, "order-1"));
    }

    /** \u9a8c\u8bc1<code>scope.isPresent()</code>\u4e3a\u771f\u3002 */
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

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>guard.acquireLease()</code>\u3001<code>any()</code>\u3001<code>Duration.ofMinutes()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
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

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>guard.acquireLease()</code>\u3001<code>any()</code>\u3001<code>Duration.ofMinutes()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
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
