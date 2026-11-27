/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.web.micronaut;

import io.ddd4j.web.core.auth.AuthenticationMode;
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.auth.PathWebAccessPolicy;
import io.ddd4j.web.core.context.ClientIpResolver;
import io.ddd4j.web.core.context.WebHeaders;
import io.ddd4j.web.core.context.WebRequestContextFactory;
import io.ddd4j.web.core.context.WebRequestLifecycle;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.filter.FilterChain;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** \u9a8c\u8bc1 <code>Ddd4jMicronautWebFilterUnit</code> \u5728 <code>secondConstructor</code> \u7b49\u573a\u666f\u4e2d\u7684\u884c\u4e3a\u3002 */
class Ddd4jMicronautWebFilterUnitTest {

    /** \u6784\u9020\u672c\u7ec4\u6d4b\u8bd5\u8f93\u5165\u65f6\u590d\u7528\u7684\u56fa\u5b9a\u6807\u8bc6\u6216\u540d\u79f0\u3002 */
    private static final String REQUEST_ID = "reqId-001";

    /**
     * 执行 <code>context Factory</code>，调用 <code>ClientIpResolver.remoteAddressOnly</code>。
     *
     * @return 当前操作得到的 <code>WebRequestContextFactory</code> 结果
     */
    private WebRequestContextFactory contextFactory() {
        return new WebRequestContextFactory(() -> REQUEST_ID, ClientIpResolver.remoteAddressOnly());
    }

    /**
     * 执行 <code>request Lifecycle</code>，调用 <code>Collections.singletonList</code>。
     *
     * @return 当前操作得到的 <code>WebRequestLifecycle</code> 结果
     */
    private WebRequestLifecycle requestLifecycle() {
        return new WebRequestLifecycle(new BearerSubjectAuthenticator(),
                new PathWebAccessPolicy(Collections.singletonList("/test"), AuthenticationMode.REQUIRED));
    }

    /**
     * \u9a8c\u8bc1<code>new Ddd4jMicronautWebFilter(
     *                         null, newLifecycle(), null)</code> \u629b\u51fa <code>NullPointerException</code>\u3002
     */
    @Test
    void secondConstructorRejectsNullContextFactory() {
        assertThatNullPointerException().isThrownBy(() ->
                new Ddd4jMicronautWebFilter(null, requestLifecycle(), null));
    }

    /**
     * \u9a8c\u8bc1<code>new Ddd4jMicronautWebFilter(
     *                         newContextFactory(), null, null)</code> \u629b\u51fa <code>NullPointerException</code>\u3002
     */
    @Test
    void secondConstructorRejectsNullRequestLifecycle() {
        assertThatNullPointerException().isThrownBy(() ->
                new Ddd4jMicronautWebFilter(contextFactory(), null, null));
    }

    /** \u9a8c\u8bc1<code>filter</code>\u975e\u7a7a\u3002 */
    @Test
    void secondConstructorAcceptsNullIdempotency() {
        assertThat(new Ddd4jMicronautWebFilter(contextFactory(), requestLifecycle(), null)).isNotNull();
    }

    /** \u9a8c\u8bc1<code>Flux.from(result).blockFirst()</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>response</code>\u3002 */
    @Test
    void publicPathProceedsThroughMicronautFilterChain() {
        Ddd4jMicronautWebFilter filter = new Ddd4jMicronautWebFilter(
                contextFactory(), requestLifecycle(), null);
        HttpRequest<?> request = mock(HttpRequest.class);
        HttpHeaders headers = mock(HttpHeaders.class);
        when(request.getHeaders()).thenReturn(headers);
        when(request.getMethodName()).thenReturn("GET");
        when(request.getPath()).thenReturn("/test");
        when(request.getLocale()).thenReturn(Optional.of(Locale.US));
        when(request.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8080));

        FilterChain chain = mock(FilterChain.class);
        MutableHttpResponse<?> response = mock(MutableHttpResponse.class);
        when(response.getStatus()).thenReturn(HttpStatus.OK);
        when(chain.proceed(request)).thenAnswer(ignored -> Flux.just(response));

        Publisher<? extends HttpResponse<?>> result = filter.doFilter(request, chain);

        assertThat(Flux.from(result).blockFirst()).isSameAs(response);
        verify(chain).proceed(request);
        verify(response).header(WebHeaders.REQUEST_ID, REQUEST_ID);
        assertThat(Ddd4jMicronautContext.current()).isEmpty();
    }
}
