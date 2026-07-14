package io.ddd4j.web.micronaut;

import io.ddd4j.web.core.BearerSubjectAuthenticator;
import io.ddd4j.web.core.CacheIdempotencyGuard;
import io.ddd4j.web.core.ClientIpResolver;
import io.ddd4j.web.core.PathWebAccessPolicy;
import io.ddd4j.web.core.RequestIdGenerator;
import io.ddd4j.web.core.WebHeaders;
import io.ddd4j.web.core.WebIdempotencyLifecycle;
import io.ddd4j.web.core.WebRequestContext;
import io.ddd4j.web.core.WebRequestContextFactory;
import io.ddd4j.web.core.WebRequestData;
import io.ddd4j.web.core.WebRequestLifecycle;
import io.micronaut.core.propagation.MutablePropagatedContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.filter.FilterContinuation;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.inject.Inject;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Micronaut 4 Filter Method 请求上下文、Bearer Subject 与幂等适配器。
 */
@ServerFilter(ServerFilter.MATCH_ALL_PATTERN)
public final class Ddd4jMicronautWebFilter {

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

    @RequestFilter
    @ExecuteOn(TaskExecutors.BLOCKING)
    public Publisher<MutableHttpResponse<?>> filter(HttpRequest<?> request,
                                                     FilterContinuation<Publisher<MutableHttpResponse<?>>> continuation,
                                                     MutablePropagatedContext propagatedContext) {
        WebRequestContext requestContext = createContext(request);
        Optional<BearerSubjectAuthenticator.Authentication> authentication = requestLifecycle
                .authenticate(requestContext);
        propagatedContext.add(new Ddd4jMicronautContext(requestContext,
                authentication.map(BearerSubjectAuthenticator.Authentication::subject)));
        Optional<WebIdempotencyLifecycle.Scope> idempotencyScope = idempotencyLifecycle.flatMap(lifecycle ->
                lifecycle.open(requestContext, request.getHeaders().get(WebHeaders.IDEMPOTENCY_KEY)));
        return Flux.from(continuation.proceed())
                .doOnNext(response -> {
                    addResponseHeaders(response, requestContext);
                    closeIdempotency(idempotencyScope, response.getStatus().getCode() < 400);
                })
                .doOnError(throwable -> closeIdempotency(idempotencyScope, false))
                .doOnCancel(() -> closeIdempotency(idempotencyScope, false));
    }

    private WebRequestContext createContext(HttpRequest<?> request) {
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        String remoteHost = Objects.nonNull(remoteAddress.getAddress())
                ? remoteAddress.getAddress().getHostAddress() : remoteAddress.getHostString();
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

    private void addResponseHeaders(MutableHttpResponse<?> response, WebRequestContext context) {
        response.header(WebHeaders.REQUEST_ID, context.requestId());
        response.header(WebHeaders.TRACE_ID, context.traceId());
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
