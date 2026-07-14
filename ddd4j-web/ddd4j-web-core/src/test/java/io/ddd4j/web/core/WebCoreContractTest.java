package io.ddd4j.web.core;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebCoreContractTest {

    @AfterEach
    void clearContext() {
        ThreadContext.clear();
        BaseContext.clear();
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
        CacheIdempotencyGuard guard = new CacheIdempotencyGuard("web-core-test");

        assertThat(guard.acquire("payment-1", Duration.ofMinutes(1))).isTrue();
        assertThat(guard.acquire("payment-1", Duration.ofMinutes(1))).isFalse();
        guard.release("payment-1");
        assertThat(guard.acquire("payment-1", Duration.ofMinutes(1))).isTrue();
        guard.complete("payment-1");
        assertThat(guard.acquire("payment-1", Duration.ofMinutes(1))).isFalse();
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

    private SubjectProvider provider(Subject subject) {
        return new SubjectProvider() {
            @Override
            public Subject getSubject() {
                return subject;
            }
        };
    }
}
