package io.ddd4j.web.dropwizard;

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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Context;

import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Dropwizard Jersey 请求上下文、Bearer Subject 与幂等过滤器。
 */
@Priority(Priorities.AUTHENTICATION)
public final class Ddd4jDropwizardRequestFilter implements ContainerRequestFilter {

    static final String SESSION_PROPERTY = Ddd4jDropwizardRequestFilter.class.getName() + ".session";
    static final String CONTEXT_PROPERTY = Ddd4jDropwizardRequestFilter.class.getName() + ".context";

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
