package io.ddd4j.web.helidon;

import io.ddd4j.web.core.BearerSubjectAuthenticator;
import io.ddd4j.web.core.CacheIdempotencyGuard;
import io.ddd4j.web.core.ClientIpResolver;
import io.ddd4j.web.core.PathWebAccessPolicy;
import io.ddd4j.web.core.RequestIdGenerator;
import io.ddd4j.web.core.SynchronousWebRequestSession;
import io.ddd4j.web.core.WebHeaders;
import io.ddd4j.web.core.WebIdempotencyLifecycle;
import io.ddd4j.web.core.WebRequestContext;
import io.ddd4j.web.core.WebRequestContextFactory;
import io.ddd4j.web.core.WebRequestData;
import io.ddd4j.web.core.WebRequestLifecycle;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Helidon MP 请求上下文与 Bearer Subject 过滤器。
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public final class Ddd4jHelidonRequestFilter implements ContainerRequestFilter {

    static final String SESSION_PROPERTY = Ddd4jHelidonRequestFilter.class.getName() + ".session";
    static final String CONTEXT_PROPERTY = Ddd4jHelidonRequestFilter.class.getName() + ".context";

    private final WebRequestContextFactory contextFactory;
    private final WebRequestLifecycle requestLifecycle;
    private final WebIdempotencyLifecycle idempotencyLifecycle;

    public Ddd4jHelidonRequestFilter() {
        this(Ddd4jHelidonWebConfiguration.load());
    }

    public Ddd4jHelidonRequestFilter(Ddd4jHelidonWebConfiguration configuration) {
        Ddd4jHelidonWebConfiguration config = Objects.requireNonNull(configuration,
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

    public Ddd4jHelidonRequestFilter(WebRequestContextFactory contextFactory,
                                     WebRequestLifecycle requestLifecycle,
                                     WebIdempotencyLifecycle idempotencyLifecycle) {
        this.contextFactory = Objects.requireNonNull(contextFactory, "contextFactory must not be null");
        this.requestLifecycle = Objects.requireNonNull(requestLifecycle, "requestLifecycle must not be null");
        this.idempotencyLifecycle = idempotencyLifecycle;
    }

    @Override
    public void filter(ContainerRequestContext request) {
        WebRequestContext context = createContext(request);
        SynchronousWebRequestSession session = SynchronousWebRequestSession.open(context, requestLifecycle,
                idempotencyLifecycle, request.getHeaderString(WebHeaders.IDEMPOTENCY_KEY));
        request.setProperty(CONTEXT_PROPERTY, context);
        request.setProperty(SESSION_PROPERTY, session);
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
                null,
                request.getMethod(),
                request.getUriInfo().getRequestUri().getPath()));
    }
}
