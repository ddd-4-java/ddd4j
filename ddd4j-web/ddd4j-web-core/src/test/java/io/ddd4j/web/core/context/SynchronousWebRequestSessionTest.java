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

/** \u9a8c\u8bc1 <code>SynchronousWebRequestSession</code> \u5728<code>open</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
@ExtendWith(MockitoExtension.class)
class SynchronousWebRequestSessionTest {

    /** \u7528\u4e8e\u9694\u79bb <code>IdempotencyGuard</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
    @Mock
    private IdempotencyGuard guard;

    /**
     * \u521b\u5efa <code>new WebRequestContext</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new WebRequestContext("r-1", "t-1", "tenant-a", "Bearer token", Locale.CHINA, "127.0.0.1", "POST", "/api/orders")</code>
     */
    private static WebRequestContext request() {
        return new WebRequestContext("r-1", "t-1", "tenant-a", "Bearer token",
                Locale.CHINA, "127.0.0.1", "POST", "/api/orders");
    }

    /**
     * \u521b\u5efa <code>new WebRequestLifecycle</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new WebRequestLifecycle(new BearerSubjectAuthenticator(), WebAccessPolicy.disabled())</code>
     */
    private static WebRequestLifecycle lifecycle() {
        return new WebRequestLifecycle(new BearerSubjectAuthenticator(), WebAccessPolicy.disabled());
    }

    /** \u9a8c\u8bc1<code>session.requestContext()</code>\u975e\u7a7a\u3002 */
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

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>guard.acquireLease()</code>\u3001<code>any()</code>\u3001<code>Duration.ofMinutes()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void openAndCompleteAsFailure() {
        when(guard.acquireLease(any(String.class), any(Duration.class)))
                .thenReturn(Optional.of(new IdempotencyLease("k", null, Duration.ofMinutes(5))));
        WebIdempotencyLifecycle idempotencyLifecycle = new WebIdempotencyLifecycle(guard);

        SynchronousWebRequestSession session = SynchronousWebRequestSession.open(
                request(), lifecycle(), idempotencyLifecycle, "order-1");

        session.complete(false);
    }

    /** \u9a8c\u8bc1<code>session.requestContext()</code>\u975e\u7a7a\u3002 */
    @Test
    void openWithoutIdempotencyLifecycle() {
        SynchronousWebRequestSession session = SynchronousWebRequestSession.open(
                request(), lifecycle(), null, null);

        assertNotNull(session.requestContext());
        session.complete(true);
    }

    /** \u9a8c\u8bc1<code>SynchronousWebRequestSession.open( null, lifecycle(), null, null)</code> \u629b\u51fa <code>NullPointerException.class</code>\uff0c<code>SynchronousWebRequestSession.open( request(), null, null, null)</code> \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void openRejectsNullArguments() {
        assertThrows(NullPointerException.class, () -> SynchronousWebRequestSession.open(
                null, lifecycle(), null, null));
        assertThrows(NullPointerException.class, () -> SynchronousWebRequestSession.open(
                request(), null, null, null));
    }

    /** \u9a8c\u8bc1<code>SynchronousWebRequestSession.open( unauthenticated, requiredLifecycle, null, null)</code> \u629b\u51fa <code>WebStatusException.class</code>\u3002 */
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
