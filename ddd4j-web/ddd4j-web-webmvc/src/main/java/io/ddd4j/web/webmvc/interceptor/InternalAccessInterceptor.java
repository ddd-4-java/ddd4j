package io.ddd4j.web.webmvc.interceptor;

import io.ddd4j.annotation.api.InternalAccess;
import io.ddd4j.web.webmvc.config.InternalAccessProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import java.util.HashSet;
import java.util.Set;

/**
 * Internal bearer-token allow-list interceptor for Spring MVC.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class InternalAccessInterceptor extends BaseWebInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final Set<String> bearerTokens;

    public InternalAccessInterceptor(InternalAccessProperties properties) {
        this.bearerTokens = new HashSet<>(java.util.Objects.isNull(properties) ? Set.of() : properties.getBearerTokens());
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (handler instanceof ResourceHttpRequestHandler || !(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        if (isInternalAccess(handlerMethod) || bearerTokens.isEmpty()) {
            return true;
        }
        String authorization = request.getHeader("Authorization");
        String token = resolveBearerToken(authorization);
        if (java.util.Objects.nonNull(token) && bearerTokens.contains(token)) {
            return true;
        }
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized internal access");
        return false;
    }

    @Override
    public int getOrder() {
        return -700;
    }

    private static boolean isInternalAccess(HandlerMethod handlerMethod) {
        return AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(), InternalAccess.class)
                || AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), InternalAccess.class);
    }

    private static String resolveBearerToken(String authorization) {
        if (java.util.Objects.isNull(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        return !org.springframework.util.StringUtils.hasLength(token) ? null : token;
    }
}
