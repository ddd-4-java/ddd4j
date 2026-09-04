package io.ddd4j.web.core;

import io.ddd4j.cache.CacheKit;
import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.exception.IdempotentException;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.auth.BearerTokenResolver;
import io.ddd4j.web.core.auth.WebAccessPolicy;
import io.ddd4j.web.core.context.ClientIpResolver;
import io.ddd4j.web.core.context.SynchronousWebRequestSession;
import io.ddd4j.web.core.context.WebContextScope;
import io.ddd4j.web.core.context.WebRequestContext;
import io.ddd4j.web.core.context.WebRequestContextFactory;
import io.ddd4j.web.core.context.WebRequestData;
import io.ddd4j.web.core.context.WebRequestLifecycle;
import io.ddd4j.web.core.error.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.error.WebStatusException;
import io.ddd4j.web.core.idempotency.CacheIdempotencyGuard;
import io.ddd4j.web.core.idempotency.IdempotencyLease;
import io.ddd4j.web.core.idempotency.WebIdempotencyLifecycle;

class WebCoreContractTest {

    @AfterEach
    void clearContext() {
        ThreadContext.clear();
        BaseContext.clear();
        CacheKit.unregister("web-core-test");
        CacheKit.unregister("web-lifecycle-test");
        CacheKit.unregister("web-session-test");
    }

    @Test
    void shouldResolveOnlyBearerAuthorization() {
        BearerTokenResolver resolver = new BearerTokenResolver();

        assertThat(resolver.resolve("Bearer token-1")).contains("token-1");
        assertThat(resolver.resolve("bearer token-2")).contains("token-2");
        assertThat(resolver.resolve("Basic abc")).isEmpty();
        assertThat(resolver.resolve("Bearer ")).isEmpty();
    }

    @Test
    void shouldTranslateKnownExceptions() {
        DefaultWebExceptionTranslator translator = new DefaultWebExceptionTranslator();

        assertThat(translator.translate(new IllegalArgumentException("bad")).status()).isEqualTo(400);
        assertThat(translator.translate(new IdempotentException("duplicate")).status()).isEqualTo(409);
        assertThat(translator.translate(new RuntimeException("failure")).status()).isEqualTo(500);
        assertThat(translator.translate(new WebStatusException(401, "missing")).status()).isEqualTo(401);
    }

    @Test
    void shouldRestoreThreadContextAfterRequest() {
        ThreadContext.put(ContextConstants.TENANT_ID, "outer");
        WebRequestContext context = new WebRequestContext("r-1", "t-1", "tenant-a", "Bearer token",
                Locale.CHINA, "127.0.0.1", "get", "/orders");

        try (WebContextScope ignored = WebContextScope.open(context)) {
            String tenantId = ThreadContext.get(ContextConstants.TENANT_ID);
            String traceId = ThreadContext.get(WebContextScope.TRACE_ID);
            assertThat(tenantId).isEqualTo("tenant-a");
            assertThat(traceId).isEqualTo("t-1");
        }

        String restoredTenantId = ThreadContext.get(ContextConstants.TENANT_ID);
        String clearedTraceId = ThreadContext.get(WebContextScope.TRACE_ID);
        assertThat(restoredTenantId).isEqualTo("outer");
        assertThat(clearedTraceId).isNull();
    }

    @Test
    void shouldGuardIdempotentExecution() {
        CacheIdempotencyGuard guard = newGuard("web-core-test");

        assertThat(guard.acquire("payment-1", Duration.ofMinutes(1))).isTrue();
        assertThat(guard.acquire("payment-1", Duration.ofMinutes(1))).isFalse();
        guard.release("payment-1");
        assertThat(guard.acquire("payment-1", Duration.ofMinutes(1))).isTrue();
        guard.complete("payment-1");
        assertThat(guard.acquire("payment-1", Duration.ofMinutes(1))).isFalse();
    }

    @Test
    void shouldRejectUnregisteredIdempotencyCache() {
        CacheIdempotencyGuard guard = new CacheIdempotencyGuard("missing-idempotency-cache");

        assertThatThrownBy(() -> guard.acquire("payment-1", Duration.ofMinutes(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be explicitly registered");
    }

    @Test
    void shouldNotReleaseLeaseOwnedByLaterRequest() {
        CacheIdempotencyGuard guard = newGuard("web-core-test");
        IdempotencyLease expiredLease = guard.acquireLease("payment-lease", Duration.ofMinutes(1)).get();
        CacheKit.invalidate("web-core-test", "payment-lease");
        IdempotencyLease activeLease = guard.acquireLease("payment-lease", Duration.ofMinutes(1)).get();

        guard.release(expiredLease);

        assertThat(guard.acquireLease("payment-lease", Duration.ofMinutes(1))).isEmpty();
        guard.release(activeLease);
        assertThat(guard.acquireLease("payment-lease", Duration.ofMinutes(1))).isPresent();
    }

    @Test
    void shouldAuthenticateBearerWithRegisteredSubject() {
        Subject subject = mock(Subject.class);
        AuthPrincipal principal = new AuthPrincipal().setUserId("user-1");
        when(subject.verify("valid-token")).thenReturn(principal);
        SubjectProvider provider = provider(subject);
        BaseContext.inject(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, provider);

        BearerSubjectAuthenticator.Authentication authentication = new BearerSubjectAuthenticator()
                .authenticateSubject("Bearer valid-token");

        assertThat(authentication.principal()).isSameAs(principal);
        assertThat(authentication.subject()).isSameAs(subject);
        assertThat(authentication.token()).isEqualTo("valid-token");
    }

    @Test
    void shouldRejectMissingAndInvalidBearer() {
        Subject subject = mock(Subject.class);
        SubjectProvider provider = provider(subject);
        BaseContext.inject(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, provider);

        assertThatThrownBy(() -> new BearerSubjectAuthenticator().authenticate(""))
                .isInstanceOf(WebStatusException.class)
                .extracting("status")
                .isEqualTo(401);
        assertThatThrownBy(() -> new BearerSubjectAuthenticator().authenticate("Bearer invalid"))
                .isInstanceOf(WebStatusException.class)
                .extracting("status")
                .isEqualTo(401);
    }

    @Test
    void shouldApplyRequiredOptionalAndDisabledAuthenticationModes() {
        Subject subject = mock(Subject.class);
        AuthPrincipal principal = new AuthPrincipal().setUserId("user-1");
        when(subject.verify("valid-token")).thenReturn(principal);
        BaseContext.inject(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, provider(subject));
        BearerSubjectAuthenticator authenticator = new BearerSubjectAuthenticator();
        WebRequestContext context = requestContext(null);

        Optional<BearerSubjectAuthenticator.Authentication> disabled = new WebRequestLifecycle(authenticator,
                WebAccessPolicy.disabled()).authenticate(context);
        Optional<BearerSubjectAuthenticator.Authentication> optional = new WebRequestLifecycle(authenticator,
                WebAccessPolicy.optional()).authenticate(context);

        assertThat(disabled).isEmpty();
        assertThat(optional).isEmpty();
        assertThatThrownBy(() -> new WebRequestLifecycle(authenticator, WebAccessPolicy.required())
                .authenticate(context)).isInstanceOf(WebStatusException.class)
                .extracting("status").isEqualTo(401);

        WebRequestContext authenticatedContext = requestContext("Bearer valid-token");
        assertThat(new WebRequestLifecycle(authenticator, WebAccessPolicy.optional())
                .authenticate(authenticatedContext)).get().extracting(authentication -> authentication.principal())
                .isSameAs(principal);
    }

    @Test
    void shouldCreateNormalizedRequestContextWithTrustedProxyPolicy() {
        WebRequestContextFactory factory = new WebRequestContextFactory(() -> "generated-request",
                ClientIpResolver.trustedProxy());

        WebRequestContext context = factory.create(new WebRequestData(" ", null, "tenant-a", null,
                Locale.CHINA, "203.0.113.10, 10.0.0.1", "10.0.0.2", "127.0.0.1", "post", "/orders"));

        assertThat(context.requestId()).isEqualTo("generated-request");
        assertThat(context.traceId()).isEqualTo("generated-request");
        assertThat(context.clientIp()).isEqualTo("203.0.113.10");
        assertThat(context.method()).isEqualTo("POST");
    }

    @Test
    void shouldIgnoreForwardedHeadersUnlessProxyIsTrusted() {
        ClientIpResolver resolver = ClientIpResolver.remoteAddressOnly();

        assertThat(resolver.resolve("203.0.113.10", "203.0.113.11", "127.0.0.1"))
                .isEqualTo("127.0.0.1");
    }

    @Test
    void shouldCompleteOrReleaseWebIdempotencyScope() {
        CacheIdempotencyGuard guard = newGuard("web-lifecycle-test");
        WebIdempotencyLifecycle lifecycle = new WebIdempotencyLifecycle(guard);
        WebRequestContext context = requestContext(null);

        WebIdempotencyLifecycle.Scope released = lifecycle.open(context, "release-key").get();
        released.close();
        assertThat(lifecycle.open(context, "release-key")).isPresent();

        WebIdempotencyLifecycle.Scope completed = lifecycle.open(context, "complete-key").get();
        completed.complete();
        completed.close();
        assertThatThrownBy(() -> lifecycle.open(context, "complete-key"))
                .isInstanceOf(WebStatusException.class)
                .extracting("status").isEqualTo(409);
    }

    @Test
    void shouldCompleteSynchronousRequestSessionAndRestoreContext() {
        Subject subject = mock(Subject.class);
        AuthPrincipal principal = new AuthPrincipal().setUserId("user-1");
        when(subject.verify("valid-token")).thenReturn(principal);
        BaseContext.inject(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, provider(subject));
        WebRequestContext context = requestContext("Bearer valid-token");
        WebRequestLifecycle requestLifecycle = new WebRequestLifecycle(new BearerSubjectAuthenticator(),
                WebAccessPolicy.required());
        WebIdempotencyLifecycle idempotencyLifecycle = new WebIdempotencyLifecycle(
                newGuard("web-session-test"));

        try (SynchronousWebRequestSession session = SynchronousWebRequestSession.open(context, requestLifecycle,
                idempotencyLifecycle, "request-key")) {
            assertThat(session.requestContext()).isEqualTo(context);
            session.complete(true);
        }

        String requestId = ThreadContext.get(WebContextScope.REQUEST_ID);
        assertThat(requestId).isNull();
        assertThatThrownBy(() -> idempotencyLifecycle.open(context, "request-key"))
                .isInstanceOf(WebStatusException.class)
                .extracting("status").isEqualTo(409);
    }

    private WebRequestContext requestContext(String authorization) {
        return new WebRequestContext("request-1", "trace-1", "tenant-a", authorization,
                Locale.CHINA, "127.0.0.1", "GET", "/orders");
    }

    private SubjectProvider provider(Subject subject) {
        return new SubjectProvider() {
            @Override
            public Subject getSubject() {
                return subject;
            }
        };
    }

    private CacheIdempotencyGuard newGuard(String cacheName) {
        CacheKit.build(cacheName, 300L);
        return new CacheIdempotencyGuard(cacheName);
    }
}
