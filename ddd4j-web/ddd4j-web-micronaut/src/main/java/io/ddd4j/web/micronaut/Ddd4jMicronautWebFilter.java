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
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator.Authentication;
import io.ddd4j.web.core.idempotency.CacheIdempotencyGuard;
import io.ddd4j.web.core.context.ClientIpResolver;
import io.ddd4j.web.core.auth.PathWebAccessPolicy;
import io.ddd4j.web.core.context.RequestIdGenerator;
import io.ddd4j.web.core.context.WebHeaders;
import io.ddd4j.web.core.idempotency.WebIdempotencyLifecycle;
import io.ddd4j.web.core.observability.WebOtelSupport;
import io.ddd4j.web.core.context.WebRequestContext;
import io.ddd4j.web.core.context.WebRequestContextFactory;
import io.ddd4j.web.core.context.WebRequestData;
import io.ddd4j.web.core.context.WebRequestLifecycle;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpFilter;
import io.micronaut.http.filter.FilterChain;
import jakarta.inject.Inject;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Micronaut 4 Filter Method 请求上下文、Bearer Subject 与幂等适配器。
 *
 * <p>集成 OTel 分布式追踪：通过 {@link WebOtelSupport} 反射调用 WebOtelIntegration。
 */@Filter("/**")
public final class Ddd4jMicronautWebFilter implements HttpFilter {

    private final WebRequestContextFactory contextFactory;
    private final WebRequestLifecycle requestLifecycle;
    private final Optional<WebIdempotencyLifecycle> idempotencyLifecycle;

    @Inject
    public Ddd4jMicronautWebFilter(Ddd4jMicronautWebConfiguration configuration) {
        Ddd4jMicronautWebConfiguration config = Objects.requireNonNull(configuration,
                "configuration must not be null");
        ClientIpResolver clientIpResolver = config.isTrustForwardedHeaders()
                ? ClientIpResolver.trustedProxy() : ClientIpResolver.remoteAddressOnly();
        this.contextFactory = new WebRequestContextFactory(RequestIdGenerator.uuid(), clientIpResolver);
        this.requestLifecycle = new WebRequestLifecycle(new BearerSubjectAuthenticator(),
                new PathWebAccessPolicy(config.getPublicPaths(), config.getDefaultAuthenticationMode()));
        this.idempotencyLifecycle = config.isIdempotencyEnabled()
                ? Optional.of(new WebIdempotencyLifecycle(
                        new CacheIdempotencyGuard(config.getIdempotencyCacheName()), config.getIdempotencyTtl()))
                : Optional.empty();
    }

    public Ddd4jMicronautWebFilter(WebRequestContextFactory contextFactory, WebRequestLifecycle requestLifecycle,
                                   WebIdempotencyLifecycle idempotencyLifecycle) {
        this.contextFactory = Objects.requireNonNull(contextFactory, "contextFactory must not be null");
        this.requestLifecycle = Objects.requireNonNull(requestLifecycle, "requestLifecycle must not be null");
        this.idempotencyLifecycle = Optional.ofNullable(idempotencyLifecycle);
    }

    @Override
    public Publisher<? extends HttpResponse<?>> doFilter(HttpRequest<?> request, FilterChain chain) {
        // OTel: 提取上游 TraceContext 并开启 SERVER span
        Map<String, String> headers = extractRequestHeaders(request);
        Object span = WebOtelSupport.startServerSpan(
                request.getMethodName(), request.getPath(), headers);
        WebOtelSupport.activate(span);

        WebRequestContext requestContext = createContext(request);
        Optional<Authentication> authentication = requestLifecycle.authenticate(requestContext);
        if (authentication.isPresent()) {
            Ddd4jMicronautContext.set(new Ddd4jMicronautContext(requestContext,
                    Optional.of(authentication.get().subject())));
        } else {
            Ddd4jMicronautContext.set(new Ddd4jMicronautContext(requestContext, Optional.empty()));
        }
        Optional<WebIdempotencyLifecycle.Scope> idempotencyScope = idempotencyLifecycle.flatMap(lifecycle ->
                lifecycle.open(requestContext, request.getHeaders().get(WebHeaders.IDEMPOTENCY_KEY)));
        try {
            return Flux.from(chain.proceed(request))
                    .doOnNext(response -> {
                        addResponseHeaders(response, requestContext);
                        closeIdempotency(idempotencyScope, response.getStatus().getCode() < 400);
                        // OTel: 结束 span
                        WebOtelSupport.endServerSpan(span, response.getStatus().getCode());
                        Ddd4jMicronautContext.clear();
                    })
                    .doOnError(throwable -> {
                        WebOtelSupport.recordError(span, throwable);
                        WebOtelSupport.endServerSpan(span, 500);
                        closeIdempotency(idempotencyScope, false);
                        Ddd4jMicronautContext.clear();
                    })
                    .doOnCancel(() -> {
                        WebOtelSupport.recordError(span, new RuntimeException("cancelled"));
                        WebOtelSupport.endServerSpan(span, 500);
                        closeIdempotency(idempotencyScope, false);
                        Ddd4jMicronautContext.clear();
                    });
        } catch (RuntimeException exception) {
            WebOtelSupport.recordError(span, exception);
            WebOtelSupport.endServerSpan(span, 500);
            closeIdempotency(idempotencyScope, false);
            Ddd4jMicronautContext.clear();
            throw exception;
        }
    }

    private static Map<String, String> extractRequestHeaders(HttpRequest<?> request) {
        Map<String, String> headers = new HashMap<>();
        request.getHeaders().forEach((k, v) -> {
            if (Objects.nonNull(v) && !v.isEmpty()) {
                headers.put(k, v.get(0));
            }
        });
        return headers;
    }

    private WebRequestContext createContext(HttpRequest<?> request) {
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        String remoteHost = Objects.nonNull(remoteAddress) && Objects.nonNull(remoteAddress.getAddress())
                ? remoteAddress.getAddress().getHostAddress() : "unknown";
        return contextFactory.create(new WebRequestData(
                request.getHeaders().get(WebHeaders.REQUEST_ID),
                request.getHeaders().get(WebHeaders.TRACE_ID),
                request.getHeaders().get(WebHeaders.TENANT_ID),
                request.getHeaders().get(WebHeaders.AUTHORIZATION),
                request.getLocale().orElse(Locale.getDefault()),
                request.getHeaders().get(WebHeaders.FORWARDED_FOR),
                request.getHeaders().get("X-Real-IP"),
                remoteHost,
                request.getMethodName(),
                request.getPath()));
    }

    private void addResponseHeaders(HttpResponse<?> response, WebRequestContext context) {
        if (response instanceof MutableHttpResponse) {
            MutableHttpResponse<?> mutable = (MutableHttpResponse<?>) response;
            mutable.header(WebHeaders.REQUEST_ID, context.requestId());
            mutable.header(WebHeaders.TRACE_ID, context.traceId());
        }
    }

    private void closeIdempotency(Optional<WebIdempotencyLifecycle.Scope> scope, boolean successful) {
        scope.ifPresent(idempotencyScope -> {
            if (successful) {
                idempotencyScope.complete();
            }
            idempotencyScope.close();
        });
    }
}
