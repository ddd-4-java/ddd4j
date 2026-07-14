package io.ddd4j.web.quarkus;

import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.web.core.BearerSubjectAuthenticator;
import io.ddd4j.web.core.WebContextScope;
import io.ddd4j.web.core.WebHeaders;
import io.ddd4j.web.core.WebRequestContext;
import io.vertx.core.http.HttpServerRequest;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Opens the shared Web context and optionally authenticates a standard Bearer token. */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class Ddd4jQuarkusWebFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String SCOPE_PROPERTY = Ddd4jQuarkusWebFilter.class.getName() + ".scope";

    private final BearerSubjectAuthenticator authenticator = new BearerSubjectAuthenticator();

    @Context
    HttpServerRequest request;

    @ConfigProperty(name = "ddd4j.quarkus.web.bearer-required", defaultValue = "false")
    boolean bearerRequired;

    @ConfigProperty(name = "ddd4j.quarkus.web.protected-path-prefix", defaultValue = "/")
    String protectedPathPrefix;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String requestId = requestContext.getHeaderString(WebHeaders.REQUEST_ID);
        if (StrKit.isBlank(requestId)) {
            requestId = UUID.randomUUID().toString();
        }
        String path = requestContext.getUriInfo().getRequestUri().getPath();
        WebRequestContext context = new WebRequestContext(requestId,
                requestContext.getHeaderString(WebHeaders.TRACE_ID),
                requestContext.getHeaderString(WebHeaders.TENANT_ID),
                requestContext.getHeaderString(WebHeaders.AUTHORIZATION), resolveLocale(requestContext),
                clientIp(), requestContext.getMethod(), path);
        WebContextScope scope = WebContextScope.open(context);
        requestContext.setProperty(SCOPE_PROPERTY, scope);
        requestContext.setProperty(WebHeaders.REQUEST_ID, requestId);
        try {
            if (bearerRequired && path.startsWith(protectedPathPrefix) && !isPublicPath(path)) {
                ThreadContext.bind(authenticator.authenticateSubject(context.authorization()).subject());
            }
        } catch (RuntimeException exception) {
            scope.close();
            requestContext.removeProperty(SCOPE_PROPERTY);
            throw exception;
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        Object requestId = requestContext.getProperty(WebHeaders.REQUEST_ID);
        if (Objects.nonNull(requestId)) {
            responseContext.getHeaders().putSingle(WebHeaders.REQUEST_ID, requestId);
        }
        closeScope(requestContext);
    }

    private Locale resolveLocale(ContainerRequestContext context) {
        return Objects.nonNull(context.getLanguage()) ? context.getLanguage() : Locale.getDefault();
    }

    private String clientIp() {
        String forwarded = request.getHeader(WebHeaders.FORWARDED_FOR);
        if (StrKit.isNotBlank(forwarded)) {
            return forwarded.split(",", 2)[0].trim();
        }
        return Objects.nonNull(request.remoteAddress()) ? request.remoteAddress().hostAddress() : null;
    }

    private void closeScope(ContainerRequestContext requestContext) {
        Object value = requestContext.getProperty(SCOPE_PROPERTY);
        if (value instanceof WebContextScope scope) {
            scope.close();
            requestContext.removeProperty(SCOPE_PROPERTY);
        }
    }

    private boolean isPublicPath(String path) {
        return "/health".equals(path) || path.startsWith("/q/health");
    }
}
