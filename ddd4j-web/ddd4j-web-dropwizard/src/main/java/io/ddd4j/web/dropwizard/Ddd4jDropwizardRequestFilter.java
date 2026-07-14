package io.ddd4j.web.dropwizard;

import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.web.core.BearerSubjectAuthenticator;
import io.ddd4j.web.core.WebContextScope;
import io.ddd4j.web.core.WebHeaders;
import io.ddd4j.web.core.WebRequestContext;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Dropwizard Jersey 请求上下文与 Bearer Subject 过滤器。
 */
@Priority(Priorities.AUTHENTICATION)
public final class Ddd4jDropwizardRequestFilter implements ContainerRequestFilter {

    static final String SCOPE_PROPERTY = Ddd4jDropwizardRequestFilter.class.getName() + ".scope";
    static final String REQUEST_ID_PROPERTY = Ddd4jDropwizardRequestFilter.class.getName() + ".requestId";

    private final BearerSubjectAuthenticator authenticator;
    private final Predicate<String> publicPath;

    public Ddd4jDropwizardRequestFilter() {
        this(new BearerSubjectAuthenticator(), Ddd4jDropwizardRequestFilter::isPublicPath);
    }

    public Ddd4jDropwizardRequestFilter(BearerSubjectAuthenticator authenticator, Predicate<String> publicPath) {
        this.authenticator = authenticator;
        this.publicPath = publicPath;
    }

    @Override
    public void filter(ContainerRequestContext request) {
        String path = "/" + request.getUriInfo().getPath();
        String requestId = request.getHeaderString(WebHeaders.REQUEST_ID);
        if (StrKit.isBlank(requestId)) {
            requestId = UUID.randomUUID().toString();
        }
        Locale locale = request.getLanguage();
        WebRequestContext context = new WebRequestContext(requestId, request.getHeaderString(WebHeaders.TRACE_ID),
                request.getHeaderString(WebHeaders.TENANT_ID), request.getHeaderString(WebHeaders.AUTHORIZATION), locale,
                clientIp(request), request.getMethod(), path);
        request.setProperty(SCOPE_PROPERTY, WebContextScope.open(context));
        request.setProperty(REQUEST_ID_PROPERTY, requestId);
        try {
            if (!publicPath.test(path)) {
                ThreadContext.bind(authenticator.authenticateSubject(
                        request.getHeaderString(WebHeaders.AUTHORIZATION)).subject());
            }
        } catch (RuntimeException exception) {
            closeScope(request);
            throw exception;
        }
    }

    private void closeScope(ContainerRequestContext request) {
        Object scope = request.getProperty(SCOPE_PROPERTY);
        if (scope instanceof WebContextScope contextScope) {
            contextScope.close();
            request.removeProperty(SCOPE_PROPERTY);
        }
    }

    private String clientIp(ContainerRequestContext request) {
        String forwardedFor = request.getHeaderString(WebHeaders.FORWARDED_FOR);
        return StrKit.isBlank(forwardedFor) ? null : forwardedFor.split(",", 2)[0].trim();
    }

    private static boolean isPublicPath(String path) {
        return "/health".equals(path) || path.startsWith("/healthcheck");
    }
}
