package io.ddd4j.web.webmvc.webmvc;

import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.kit.web.IpKit;
import io.ddd4j.web.core.BearerSubjectAuthenticator;
import io.ddd4j.web.core.WebContextScope;
import io.ddd4j.web.core.WebHeaders;
import io.ddd4j.web.core.WebRequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Spring WebMVC 的统一请求上下文与 Bearer Subject 拦截器。
 */
public final class Ddd4jWebMvcInterceptor implements HandlerInterceptor {

    private static final String SCOPE_ATTRIBUTE = Ddd4jWebMvcInterceptor.class.getName() + ".scope";

    private final BearerSubjectAuthenticator authenticator;
    private final Predicate<String> publicPath;

    public Ddd4jWebMvcInterceptor(BearerSubjectAuthenticator authenticator) {
        this(authenticator, Ddd4jWebMvcInterceptor::isPublicPath);
    }

    public Ddd4jWebMvcInterceptor(BearerSubjectAuthenticator authenticator, Predicate<String> publicPath) {
        this.authenticator = Objects.requireNonNull(authenticator, "authenticator must not be null");
        this.publicPath = Objects.requireNonNull(publicPath, "publicPath must not be null");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String requestId = request.getHeader(WebHeaders.REQUEST_ID);
        if (!StringUtils.hasText(requestId)) {
            requestId = UUID.randomUUID().toString();
        }
        WebRequestContext requestContext = new WebRequestContext(requestId, request.getHeader(WebHeaders.TRACE_ID),
                request.getHeader(WebHeaders.TENANT_ID), request.getHeader(WebHeaders.AUTHORIZATION),
                request.getLocale(), clientIp(request), request.getMethod(), request.getRequestURI());
        request.setAttribute(SCOPE_ATTRIBUTE, WebContextScope.open(requestContext));
        response.setHeader(WebHeaders.REQUEST_ID, requestId);
        try {
            if (!publicPath.test(request.getRequestURI())) {
                ThreadContext.bind(authenticator.authenticateSubject(
                        request.getHeader(WebHeaders.AUTHORIZATION)).subject());
            }
            return true;
        } catch (RuntimeException exception) {
            close(request);
            throw exception;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception exception) {
        close(request);
    }

    private void close(HttpServletRequest request) {
        Object attribute = request.getAttribute(SCOPE_ATTRIBUTE);
        if (attribute instanceof WebContextScope scope) {
            scope.close();
            request.removeAttribute(SCOPE_ATTRIBUTE);
        }
    }

    private String clientIp(HttpServletRequest request) {
        return IpKit.parseRemoteAddr(request.getHeader(WebHeaders.FORWARDED_FOR),
                request.getHeader("X-Real-IP"), request.getRemoteAddr());
    }

    private static boolean isPublicPath(String path) {
        return "/health".equals(path) || "/health/readiness".equals(path) || "/health/liveness".equals(path)
                || path.startsWith("/assets/") || path.startsWith("/webjars/") || path.startsWith("/css/")
                || path.startsWith("/js/") || path.startsWith("/images/");
    }
}
