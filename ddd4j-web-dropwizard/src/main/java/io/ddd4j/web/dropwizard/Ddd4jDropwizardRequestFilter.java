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
package io.ddd4j.web.dropwizard;

import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.idempotency.CacheIdempotencyGuard;
import io.ddd4j.web.core.context.ClientIpResolver;
import io.ddd4j.web.core.auth.PathWebAccessPolicy;
import io.ddd4j.web.core.context.RequestIdGenerator;
import io.ddd4j.web.core.context.SynchronousWebRequestSession;
import io.ddd4j.web.core.context.WebHeaders;
import io.ddd4j.web.core.idempotency.WebIdempotencyLifecycle;
import io.ddd4j.web.core.observability.WebOtelSupport;
import io.ddd4j.web.core.context.WebRequestContext;
import io.ddd4j.web.core.context.WebRequestContextFactory;
import io.ddd4j.web.core.context.WebRequestData;
import io.ddd4j.web.core.context.WebRequestLifecycle;
import javax.annotation.Priority;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Context;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Dropwizard Jersey 请求上下文、Bearer Subject 与幂等过滤器。
 *
 * <p>集成 OTel 分布式追踪：通过 {@link WebOtelSupport} 反射调用 WebOtelIntegration。
 */
@Priority(Priorities.AUTHENTICATION)
public final class Ddd4jDropwizardRequestFilter implements ContainerRequestFilter {

    static final String SESSION_PROPERTY = Ddd4jDropwizardRequestFilter.class.getName() + ".session";
    static final String CONTEXT_PROPERTY = Ddd4jDropwizardRequestFilter.class.getName() + ".context";
    static final String OTEL_SPAN_PROPERTY = Ddd4jDropwizardRequestFilter.class.getName() + ".otelSpan";
    static final String OTEL_SCOPE_PROPERTY = Ddd4jDropwizardRequestFilter.class.getName() + ".otelScope";

    private final WebRequestContextFactory contextFactory;
    private final WebRequestLifecycle requestLifecycle;
    private final WebIdempotencyLifecycle idempotencyLifecycle;

    @Context
    private HttpServletRequest servletRequest;

    public Ddd4jDropwizardRequestFilter() {
        this(new Ddd4jDropwizardWebConfiguration());
    }

    public Ddd4jDropwizardRequestFilter(Ddd4jDropwizardWebConfiguration configuration) {
        Ddd4jDropwizardWebConfiguration config = Objects.requireNonNull(configuration,
                "configuration must not be null");
        ClientIpResolver clientIpResolver = config.isTrustForwardedHeaders()
                ? ClientIpResolver.trustedProxy() : ClientIpResolver.remoteAddressOnly();
        this.contextFactory = new WebRequestContextFactory(RequestIdGenerator.uuid(), clientIpResolver);
        this.requestLifecycle = new WebRequestLifecycle(new BearerSubjectAuthenticator(),
                new PathWebAccessPolicy(config.getPublicPaths(), config.getDefaultAuthenticationMode()));
        this.idempotencyLifecycle = config.isIdempotencyEnabled()
                ? new WebIdempotencyLifecycle(new CacheIdempotencyGuard(config.getIdempotencyCacheName()),
                        config.getIdempotencyTtl()) : null;
    }

    public Ddd4jDropwizardRequestFilter(WebRequestContextFactory contextFactory,
                                        WebRequestLifecycle requestLifecycle,
                                        WebIdempotencyLifecycle idempotencyLifecycle) {
        this.contextFactory = Objects.requireNonNull(contextFactory, "contextFactory must not be null");
        this.requestLifecycle = Objects.requireNonNull(requestLifecycle, "requestLifecycle must not be null");
        this.idempotencyLifecycle = idempotencyLifecycle;
    }

    @Override
    public void filter(ContainerRequestContext request) {
        // OTel: 提取上游 TraceContext 并开启 SERVER span
        Object span = WebOtelSupport.startServerSpan(
                request.getMethod(),
                request.getUriInfo().getRequestUri().getPath(),
                extractRequestHeaders(request));
        AutoCloseable scope = WebOtelSupport.activate(span);
        request.setProperty(OTEL_SPAN_PROPERTY, span);
        request.setProperty(OTEL_SCOPE_PROPERTY, scope);

        try {
            WebRequestContext context = createContext(request);
            SynchronousWebRequestSession session = SynchronousWebRequestSession.open(context, requestLifecycle,
                    idempotencyLifecycle, request.getHeaderString(WebHeaders.IDEMPOTENCY_KEY));
            request.setProperty(CONTEXT_PROPERTY, context);
            request.setProperty(SESSION_PROPERTY, session);
        } catch (RuntimeException exception) {
            WebOtelSupport.recordError(span, exception);
            throw exception;
        }
    }

    private static Map<String, String> extractRequestHeaders(ContainerRequestContext request) {
        Map<String, String> headers = new HashMap<>();
        request.getHeaders().forEach((k, v) -> {
            if (Objects.nonNull(v) && !v.isEmpty()) {
                headers.put(k, v.get(0));
            }
        });
        return headers;
    }

    private WebRequestContext createContext(ContainerRequestContext request) {
        return contextFactory.create(new WebRequestData(
                request.getHeaderString(WebHeaders.REQUEST_ID),
                request.getHeaderString(WebHeaders.TRACE_ID),
                request.getHeaderString(WebHeaders.TENANT_ID),
                request.getHeaderString(WebHeaders.AUTHORIZATION),
                Optional.ofNullable(request.getLanguage()).orElse(Locale.getDefault()),
                request.getHeaderString(WebHeaders.FORWARDED_FOR),
                request.getHeaderString("X-Real-IP"),
                remoteAddress(),
                request.getMethod(),
                request.getUriInfo().getRequestUri().getPath()));
    }

    private String remoteAddress() {
        if (Objects.isNull(servletRequest)) {
            return null;
        }
        String address = servletRequest.getRemoteAddr();
        int port = servletRequest.getRemotePort();
        return Objects.nonNull(address) ? new InetSocketAddress(address, port).getHostString() : null;
    }
}
