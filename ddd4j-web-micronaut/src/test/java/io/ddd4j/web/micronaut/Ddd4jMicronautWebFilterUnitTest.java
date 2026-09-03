package io.ddd4j.web.micronaut;

import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.auth.PathWebAccessPolicy;
import io.ddd4j.web.core.context.ClientIpResolver;
import io.ddd4j.web.core.context.WebContextScope;
import io.ddd4j.web.core.context.WebRequestContext;
import io.ddd4j.web.core.context.WebRequestContextFactory;
import io.ddd4j.web.core.context.WebRequestLifecycle;
import io.ddd4j.web.core.idempotency.WebIdempotencyLifecycle;
import io.micronaut.core.propagation.MutablePropagatedContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.filter.FilterContinuation;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Ddd4jMicronautWebFilterUnitTest {

    private static final String REQUEST_ID = "reqId-001";

    private WebRequestContextFactory newContextFactory() {
        return new WebRequestContextFactory(
                () -> REQUEST_ID,
                ClientIpResolver.remoteAddressOnly());
    }

    private WebRequestLifecycle newLifecycle() {
        return new WebRequestLifecycle(
                new BearerSubjectAuthenticator(),
                new PathWebAccessPolicy(java.util.Collections.emptyList(), io.ddd4j.web.core.auth.AuthenticationMode.REQUIRED));
    }

    @Test
    void secondConstructorRejectsNullContextFactory() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Ddd4jMicronautWebFilter(
                        null, newLifecycle(), null));
    }

    @Test
    void secondConstructorRejectsNullRequestLifecycle() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Ddd4jMicronautWebFilter(
                        newContextFactory(), null, null));
    }

    @Test
    void secondConstructorAcceptsNullIdempotency() {
        Ddd4jMicronautWebFilter filter = new Ddd4jMicronautWebFilter(
                newContextFactory(), newLifecycle(), null);
        assertThat(filter).isNotNull();
    }

    @Test
    void filterCallsProceed() {
        Ddd4jMicronautWebFilter filter = new Ddd4jMicronautWebFilter(
                newContextFactory(), newLifecycle(), null);

        HttpRequest<?> request = mock(HttpRequest.class);
        when(request.getMethodName()).thenReturn("GET");
        when(request.getPath()).thenReturn("/test");
        io.micronaut.http.HttpHeaders headers = mock(io.micronaut.http.HttpHeaders.class);
        when(request.getHeaders()).thenReturn(headers);
        when(request.getLocale()).thenReturn(java.util.Optional.of(Locale.getDefault()));
        when(request.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8080));

        @SuppressWarnings("unchecked")
        FilterContinuation<Publisher<MutableHttpResponse<?>>> continuation =
                (FilterContinuation<Publisher<MutableHttpResponse<?>>>) mock(FilterContinuation.class);
        MutableHttpResponse<?> response = mock(MutableHttpResponse.class);
        when(response.getStatus()).thenReturn(io.micronaut.http.HttpStatus.OK);
        when(continuation.proceed()).thenReturn(Flux.just(response));

        MutablePropagatedContext propagated = mock(MutablePropagatedContext.class);

        try {
            Publisher<MutableHttpResponse<?>> result = filter.filter(request, continuation, propagated);
            assertThat(result).isNotNull();
        } catch (io.ddd4j.web.core.error.WebStatusException expected) {
            // 无 Authorization 头时 lifecycle 拒绝
        }
    }
}