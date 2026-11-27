/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.web.core;
import io.ddd4j.web.core.context.WebRequestLifecycle;
import io.ddd4j.web.core.context.WebRequestData;
import io.ddd4j.web.core.context.WebRequestContextFactory;
import io.ddd4j.web.core.context.WebRequestContext;
import io.ddd4j.web.core.context.WebContextScope;
import io.ddd4j.web.core.context.SynchronousWebRequestSession;
import io.ddd4j.web.core.context.ClientIpResolver;

import io.ddd4j.cache.CacheKit;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.exception.IdempotentException;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.auth.BearerTokenResolver;
import io.ddd4j.web.core.auth.WebAccessPolicy;
import io.ddd4j.web.core.context.*;
import io.ddd4j.web.core.error.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.error.WebStatusException;
import io.ddd4j.web.core.idempotency.CacheIdempotencyGuard;
import io.ddd4j.web.core.idempotency.IdempotencyLease;
import io.ddd4j.web.core.idempotency.WebIdempotencyLifecycle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 类型说明。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 1.0.x
 */
class WebCoreContractTest {

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>ThreadContext.clear()</code>\u3001<code>BaseContext.clear()</code>\u3001<code>CacheKit.unregister()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void clearContext() {
        ThreadContext.clear();
        BaseContext.clear();
        CacheKit.unregister("web-core-test");
        CacheKit.unregister("web-lifecycle-test");
        CacheKit.unregister("web-session-test");
    }

    /** \u9a8c\u8bc1<code>resolver.resolve("Bearer token-1")</code> \u5305\u542b <code>"token-1"</code>\uff0c<code>resolver.resolve("bearer token-2")</code> \u5305\u542b <code>"token-2"</code>\uff0c<code>resolver.resolve("Bearer ")</code>\u4e3a\u7a7a\u3002 */
    @Test
    void shouldResolveOnlyBearerAuthorization() {
        BearerTokenResolver resolver = new BearerTokenResolver();

        assertThat(resolver.resolve("Bearer token-1")).contains("token-1");
        assertThat(resolver.resolve("bearer token-2")).contains("token-2");
        assertThat(resolver.resolve("Basic abc")).isEmpty();
        assertThat(resolver.resolve("Bearer ")).isEmpty();
    }

    /** \u9a8c\u8bc1<code>translator.translate(new IllegalArgumentException("bad")).status()</code> \u7b49\u4e8e <code>400</code>\uff0c<code>translator.translate(new IdempotentException("duplicate")).status()</code> \u7b49\u4e8e <code>409</code>\uff0c<code>translator.translate(new WebStatusException(401, "missing")).status()</code> \u7b49\u4e8e <code>401</code>\u3002 */
    @Test
    void shouldTranslateKnownExceptions() {
        DefaultWebExceptionTranslator translator = new DefaultWebExceptionTranslator();

        assertThat(translator.translate(new IllegalArgumentException("bad")).status()).isEqualTo(400);
        assertThat(translator.translate(new IdempotentException("duplicate")).status()).isEqualTo(409);
        assertThat(translator.translate(new RuntimeException("failure")).status()).isEqualTo(500);
        assertThat(translator.translate(new WebStatusException(401, "missing")).status()).isEqualTo(401);
    }

    /** \u9a8c\u8bc1<code>tenantId</code> \u7b49\u4e8e <code>"tenant-a"</code>\uff0c<code>traceId</code> \u7b49\u4e8e <code>"t-1"</code>\uff0c<code>clearedTraceId</code>\u4e3a\u7a7a\u3002 */
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

    /** \u9a8c\u8bc1<code>guard.acquire("payment-1", Duration.ofMinutes(1))</code>\u4e3a\u771f\uff0c<code>guard.acquire("payment-1", Duration.ofMinutes(1))</code>\u4e3a\u5047\u3002 */
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

    /** \u9a8c\u8bc1<code>guard.acquire("payment-1", Duration.ofMinutes(1))</code> \u629b\u51fa <code>IllegalStateException.class</code>\u3002 */
    @Test
    void shouldRejectUnregisteredIdempotencyCache() {
        CacheIdempotencyGuard guard = new CacheIdempotencyGuard("missing-idempotency-cache");

        assertThatThrownBy(() -> guard.acquire("payment-1", Duration.ofMinutes(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be explicitly registered");
    }

    /** \u9a8c\u8bc1<code>guard.acquireLease("payment-lease", Duration.ofMinutes(1))</code>\u4e3a\u7a7a\uff0c<code>guard.acquireLease("payment-lease", Duration.ofMinutes(1))</code>\u5b58\u5728\u503c\u3002 */
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

    /** \u9a8c\u8bc1<code>authentication.principal()</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>principal</code>\uff0c<code>authentication.subject()</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>subject</code>\uff0c<code>authentication.token()</code> \u7b49\u4e8e <code>"valid-token"</code>\u3002 */
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

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>WebStatusException.class</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>disabled</code>\u4e3a\u7a7a\uff0c<code>optional</code>\u4e3a\u7a7a\uff0c\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>WebStatusException.class</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>context.requestId()</code> \u7b49\u4e8e <code>"generated-request"</code>\uff0c<code>context.traceId()</code> \u7b49\u4e8e <code>"generated-request"</code>\uff0c<code>context.method()</code> \u7b49\u4e8e <code>"POST"</code>\u3002 */
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

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>ClientIpResolver.remoteAddressOnly()</code>\u3001<code>resolver.resolve()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void shouldIgnoreForwardedHeadersUnlessProxyIsTrusted() {
        ClientIpResolver resolver = ClientIpResolver.remoteAddressOnly();

        assertThat(resolver.resolve("203.0.113.10", "203.0.113.11", "127.0.0.1"))
                .isEqualTo("127.0.0.1");
    }

    /** \u9a8c\u8bc1<code>lifecycle.open(context, "release-key")</code>\u5b58\u5728\u503c\uff0c<code>lifecycle.open(context, "complete-key")</code> \u629b\u51fa <code>WebStatusException.class</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>session.requestContext()</code> \u7b49\u4e8e <code>context</code>\uff0c<code>requestId</code>\u4e3a\u7a7a\uff0c<code>idempotencyLifecycle.open(context, "request-key")</code> \u629b\u51fa <code>WebStatusException.class</code>\u3002 */
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

    /**
     * \u521b\u5efa <code>new WebRequestContext</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @param authorization \u7528\u4e8e\u6784\u9020\u672c\u6b21\u8fd4\u56de\u7ed3\u679c\u7684 <code>String</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new WebRequestContext("request-1", "trace-1", "tenant-a", authorization, Locale.CHINA, "127.0.0.1", "GET", "/orders")</code>
     */
    private WebRequestContext requestContext(String authorization) {
        return new WebRequestContext("request-1", "trace-1", "tenant-a", authorization,
                Locale.CHINA, "127.0.0.1", "GET", "/orders");
    }

    /**
     * \u521b\u5efa <code>new SubjectProvider</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @param subject \u7528\u4e8e\u6784\u9020\u672c\u6b21\u8fd4\u56de\u7ed3\u679c\u7684 <code>Subject</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new SubjectProvider(){ &#64;Override public Subject getSubject() { return subject; } }</code>
     */
    private SubjectProvider provider(Subject subject) {
        return new SubjectProvider() {
            /**
             * 获取值。
             * @return 方法执行结果。
             */
            @Override
            public Subject getSubject() {
                return subject;
            }
        };
    }

    /**
     * \u521b\u5efa <code>new CacheIdempotencyGuard</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @param cacheName \u4f20\u7ed9 <code>CacheKit.build()</code> \u7684 <code>String</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new CacheIdempotencyGuard(cacheName)</code>
     */
    private CacheIdempotencyGuard newGuard(String cacheName) {
        CacheKit.build(cacheName, 300L);
        return new CacheIdempotencyGuard(cacheName);
    }
}
