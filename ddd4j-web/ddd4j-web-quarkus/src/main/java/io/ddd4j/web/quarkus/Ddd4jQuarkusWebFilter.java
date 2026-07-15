package io.ddd4j.web.quarkus;

import io.ddd4j.web.core.BearerSubjectAuthenticator;
import io.ddd4j.web.core.CacheIdempotencyGuard;
import io.ddd4j.web.core.ClientIpResolver;
import io.ddd4j.web.core.RequestIdGenerator;
import io.ddd4j.web.core.SynchronousWebRequestSession;
import io.ddd4j.web.core.WebHeaders;
import io.ddd4j.web.core.WebIdempotencyLifecycle;
import io.ddd4j.web.core.WebRequestContext;
import io.ddd4j.web.core.WebRequestContextFactory;
import io.ddd4j.web.core.WebRequestData;
import io.ddd4j.web.core.WebRequestLifecycle;
import io.vertx.ext.web.RoutingContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;
import org.jboss.resteasy.reactive.server.ServerResponseFilter;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Quarkus REST 同步请求上下文、Bearer Subject 与幂等过滤器。
 */
public class Ddd4jQuarkusWebFilter {

    static final String SESSION_PROPERTY = Ddd4jQuarkusWebFilter.class.getName() + ".session";
    static final String CONTEXT_PROPERTY = Ddd4jQuarkusWebFilter.class.getName() + ".context";

    private final WebRequestContextFactory contextFactory;
    private final WebRequestLifecycle requestLifecycle;
    private final WebIdempotencyLifecycle idempotencyLifecycle;

    @Inject
    RoutingContext routingContext;

    public Ddd4jQuarkusWebFilter() {
        this(Ddd4jQuarkusWebConfiguration.load());
    }

    public Ddd4jQuarkusWebFilter(Ddd4jQuarkusWebConfiguration configuration) {
        Ddd4jQuarkusWebConfiguration config = Objects.requireNonNull(configuration,
                "configuration must not be null");
        ClientIpResolver clientIpResolver = config.isTrustForwardedHeaders()
                ? ClientIpResolver.trustedProxy() : ClientIpResolver.remoteAddressOnly();
        this.contextFactory = new WebRequestContextFactory(RequestIdGenerator.uuid(), clientIpResolver);
        this.requestLifecycle = new WebRequestLifecycle(new BearerSubjectAuthenticator(), config.accessPolicy());
        this.idempotencyLifecycle = config.isIdempotencyEnabled()
                ? new WebIdempotencyLifecycle(new CacheIdempotencyGuard(config.getIdempotencyCacheName()),
                        config.getIdempotencyTtl()) : null;
    }

    Ddd4jQuarkusWebFilter(WebRequestContextFactory contextFactory,
                          WebRequestLifecycle requestLifecycle,
                          WebIdempotencyLifecycle idempotencyLifecycle) {
        this.contextFactory = Objects.requireNonNull(contextFactory, "contextFactory must not be null");
        this.requestLifecycle = Objects.requireNonNull(requestLifecycle, "requestLifecycle must not be null");
        this.idempotencyLifecycle = idempotencyLifecycle;
    }

    @ServerRequestFilter(priority = Priorities.AUTHENTICATION)
    public void request(ContainerRequestContext request) {
        WebRequestContext context = createContext(request);
        SynchronousWebRequestSession session = SynchronousWebRequestSession.open(context, requestLifecycle,
                idempotencyLifecycle, request.getHeaderString(WebHeaders.IDEMPOTENCY_KEY));
        request.setProperty(CONTEXT_PROPERTY, context);
        request.setProperty(SESSION_PROPERTY, session);
    }

    @ServerResponseFilter(priority = Priorities.USER)
    public void response(ContainerRequestContext request, ContainerResponseContext response) {
        Object contextValue = request.getProperty(CONTEXT_PROPERTY);
        if (contextValue instanceof WebRequestContext context) {
            response.getHeaders().putSingle(WebHeaders.REQUEST_ID, context.requestId());
            response.getHeaders().putSingle(WebHeaders.TRACE_ID, context.traceId());
        }
        Object sessionValue = request.getProperty(SESSION_PROPERTY);
        if (sessionValue instanceof SynchronousWebRequestSession session) {
            session.complete(response.getStatus() < 400);
        }
        request.removeProperty(CONTEXT_PROPERTY);
        request.removeProperty(SESSION_PROPERTY);
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
        if (Objects.isNull(routingContext) || Objects.isNull(routingContext.request().remoteAddress())) {
            return null;
        }
        return routingContext.request().remoteAddress().hostAddress();
    }
}
