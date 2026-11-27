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
package io.ddd4j.web.micronaut;

import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.auth.PathWebAccessPolicy;
import io.ddd4j.web.core.context.ClientIpResolver;
import io.ddd4j.web.core.context.WebHeaders;
import io.ddd4j.web.core.context.WebRequestContextFactory;
import io.ddd4j.web.core.context.WebRequestLifecycle;
import io.micronaut.core.propagation.MutablePropagatedContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.filter.FilterContinuation;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.net.InetSocketAddress;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.*;

/** \u9a8c\u8bc1 <code>Ddd4jMicronautWebFilterUnit</code> \u5728 <code>secondConstructor</code> \u7b49\u573a\u666f\u4e2d\u7684\u884c\u4e3a\u3002 */
class Ddd4jMicronautWebFilterUnitTest {

    /** \u6784\u9020\u672c\u7ec4\u6d4b\u8bd5\u8f93\u5165\u65f6\u590d\u7528\u7684\u56fa\u5b9a\u6807\u8bc6\u6216\u540d\u79f0\u3002 */
    private static final String REQUEST_ID = "reqId-001";

    /**
     * \u521b\u5efa <code>new WebRequestContextFactory</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new WebRequestContextFactory(()-&gt;REQUEST_ID, ClientIpResolver.remoteAddressOnly())</code>
     */
    private WebRequestContextFactory newContextFactory() {
        return new WebRequestContextFactory(
                () -> REQUEST_ID,
                ClientIpResolver.remoteAddressOnly());
    }

    /**
     * \u6784\u9020 <code>WebRequestLifecycle</code>\u3001<code>BearerSubjectAuthenticator</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>WebRequestLifecycle</code> \u7ed3\u679c
     */
    private WebRequestLifecycle newLifecycle() {
        return new WebRequestLifecycle(
                new BearerSubjectAuthenticator(),
                new PathWebAccessPolicy(java.util.List.of("/test"), io.ddd4j.web.core.auth.AuthenticationMode.REQUIRED));
    }

    /**
     * \u9a8c\u8bc1<code>new Ddd4jMicronautWebFilter(
     *                         null, newLifecycle(), null)</code> \u629b\u51fa <code>NullPointerException</code>\u3002
     */
    @Test
    void secondConstructorRejectsNullContextFactory() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Ddd4jMicronautWebFilter(
                        null, newLifecycle(), null));
    }

    /**
     * \u9a8c\u8bc1<code>new Ddd4jMicronautWebFilter(
     *                         newContextFactory(), null, null)</code> \u629b\u51fa <code>NullPointerException</code>\u3002
     */
    @Test
    void secondConstructorRejectsNullRequestLifecycle() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Ddd4jMicronautWebFilter(
                        newContextFactory(), null, null));
    }

    /** \u9a8c\u8bc1<code>filter</code>\u975e\u7a7a\u3002 */
    @Test
    void secondConstructorAcceptsNullIdempotency() {
        Ddd4jMicronautWebFilter filter = new Ddd4jMicronautWebFilter(
                newContextFactory(), newLifecycle(), null);
        assertThat(filter).isNotNull();
    }

    /** \u9a8c\u8bc1<code>Flux.from(result).blockFirst()</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>response</code>\u3002 */
    @Test
    void publicPathProceedsThroughMicronautFilterChain() {
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

        Publisher<MutableHttpResponse<?>> result = filter.filter(request, continuation, propagated);

        assertThat(Flux.from(result).blockFirst()).isSameAs(response);
        verify(continuation).proceed();
        verify(response).header(WebHeaders.REQUEST_ID, REQUEST_ID);
    }
}
