package io.ddd4j.web.webmvc;

import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.web.core.BearerSubjectAuthenticator;
import io.ddd4j.web.core.WebAccessPolicy;
import io.ddd4j.web.core.WebContextScope;
import io.ddd4j.web.core.WebHeaders;
import io.ddd4j.web.core.WebIdempotencyLifecycle;
import io.ddd4j.web.core.WebRequestContext;
import io.ddd4j.web.core.WebRequestContextFactory;
import io.ddd4j.web.core.WebRequestData;
import io.ddd4j.web.core.WebRequestLifecycle;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Spring WebMVC 的统一请求上下文、Bearer Subject 与幂等生命周期拦截器。
 */
public final class Ddd4jWebMvcInterceptor implements HandlerInterceptor {

    private static final String STATE_ATTRIBUTE = Ddd4jWebMvcInterceptor.class.getName() + ".state";

    private final WebRequestContextFactory contextFactory;
    private final WebRequestLifecycle requestLifecycle;
    private final Optional<WebIdempotencyLifecycle> idempotencyLifecycle;

    public Ddd4jWebMvcInterceptor(BearerSubjectAuthenticator authenticator) {
        this(new WebRequestContextFactory(), new WebRequestLifecycle(authenticator, WebAccessPolicy.required()), null);
    }

    public Ddd4jWebMvcInterceptor(BearerSubjectAuthenticator authenticator, Predicate<String> publicPath) {
        this(new WebRequestContextFactory(), new WebRequestLifecycle(authenticator,
                WebAccessPolicy.requiredExcept(publicPath)), null);
    }

    public Ddd4jWebMvcInterceptor(WebRequestContextFactory contextFactory, WebRequestLifecycle requestLifecycle,
                                  WebIdempotencyLifecycle idempotencyLifecycle) {
        this.contextFactory = Objects.requireNonNull(contextFactory, "contextFactory must not be null");
        this.requestLifecycle = Objects.requireNonNull(requestLifecycle, "requestLifecycle must not be null");
        this.idempotencyLifecycle = Optional.ofNullable(idempotencyLifecycle);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        WebRequestContext requestContext = createContext(request);
        RequestState state = new RequestState(WebContextScope.open(requestContext));
        request.setAttribute(STATE_ATTRIBUTE, state);
        response.setHeader(WebHeaders.REQUEST_ID, requestContext.requestId());
        response.setHeader(WebHeaders.TRACE_ID, requestContext.traceId());
        try {
            requestLifecycle.authenticate(requestContext)
                    .ifPresent(authentication -> ThreadContext.bind(authentication.subject()));
            idempotencyLifecycle.flatMap(lifecycle -> lifecycle.open(requestContext,
                    request.getHeader(WebHeaders.IDEMPOTENCY_KEY))).ifPresent(state::idempotencyScope);
            return true;
        } catch (RuntimeException exception) {
            state.close(false);
            request.removeAttribute(STATE_ATTRIBUTE);
            throw exception;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception exception) {
        Object attribute = request.getAttribute(STATE_ATTRIBUTE);
        if (attribute instanceof RequestState state) {
            state.close(Objects.isNull(exception) && response.getStatus() < 400);
            request.removeAttribute(STATE_ATTRIBUTE);
        }
    }

    private WebRequestContext createContext(HttpServletRequest request) {
        return contextFactory.create(new WebRequestData(
                request.getHeader(WebHeaders.REQUEST_ID),
                request.getHeader(WebHeaders.TRACE_ID),
                request.getHeader(WebHeaders.TENANT_ID),
                request.getHeader(WebHeaders.AUTHORIZATION),
                request.getLocale(),
                request.getHeader(WebHeaders.FORWARDED_FOR),
                request.getHeader("X-Real-IP"),
                request.getRemoteAddr(),
                request.getMethod(),
                request.getRequestURI()));
    }

    private static final class RequestState {

        private final WebContextScope contextScope;
        private WebIdempotencyLifecycle.Scope idempotencyScope;

        private RequestState(WebContextScope contextScope) {
            this.contextScope = contextScope;
        }

        private void idempotencyScope(WebIdempotencyLifecycle.Scope scope) {
            this.idempotencyScope = scope;
        }

        private void close(boolean successful) {
            if (Objects.nonNull(idempotencyScope)) {
                if (successful) {
                    idempotencyScope.complete();
                }
                idempotencyScope.close();
            }
            contextScope.close();
        }
    }
}
