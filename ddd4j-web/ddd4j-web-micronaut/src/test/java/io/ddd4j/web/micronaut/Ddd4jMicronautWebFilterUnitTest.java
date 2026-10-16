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

class Ddd4jMicronautWebFilterUnitTest {

    private static final String REQUEST_ID = "reqId-001";

    private WebRequestContextFactory contextFactory() {
        return new WebRequestContextFactory(() -> REQUEST_ID, ClientIpResolver.remoteAddressOnly());
    }

    private WebRequestLifecycle requestLifecycle() {
        return new WebRequestLifecycle(new BearerSubjectAuthenticator(),
                new PathWebAccessPolicy(Collections.singletonList("/test"), AuthenticationMode.REQUIRED));
    }

    @Test
    void secondConstructorRejectsNullContextFactory() {
        assertThatNullPointerException().isThrownBy(() ->
                new Ddd4jMicronautWebFilter(null, requestLifecycle(), null));
    }

    @Test
    void secondConstructorRejectsNullRequestLifecycle() {
        assertThatNullPointerException().isThrownBy(() ->
                new Ddd4jMicronautWebFilter(contextFactory(), null, null));
    }

    @Test
    void secondConstructorAcceptsNullIdempotency() {
        assertThat(new Ddd4jMicronautWebFilter(contextFactory(), requestLifecycle(), null)).isNotNull();
    }

    @Test
    void publicPathProceedsThroughMicronautThreeFilterChain() {
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
