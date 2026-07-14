package io.ddd4j.web.micronaut;

import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.web.core.BearerSubjectAuthenticator;
import io.ddd4j.web.core.WebContextScope;
import io.ddd4j.web.core.WebHeaders;
import io.ddd4j.web.core.WebRequestContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Micronaut 请求上下文与 Bearer Subject 适配器。
 */
@Filter("/**")
public final class Ddd4jMicronautWebFilter implements HttpServerFilter {

    private final BearerSubjectAuthenticator authenticator;
    private final Predicate<String> publicPath;

    public Ddd4jMicronautWebFilter() {
        this(new BearerSubjectAuthenticator(), Ddd4jMicronautWebFilter::isPublicPath);
    }

    public Ddd4jMicronautWebFilter(BearerSubjectAuthenticator authenticator, Predicate<String> publicPath) {
        this.authenticator = authenticator;
        this.publicPath = publicPath;
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        return Flux.defer(() -> {
            String requestId = requestId(request);
            return Flux.using(
                    () -> WebContextScope.open(toContext(request, requestId)),
                    scope -> proceed(request, chain, requestId),
                    WebContextScope::close);
        });
    }

    private Publisher<MutableHttpResponse<?>> proceed(HttpRequest<?> request, ServerFilterChain chain, String requestId) {
        if (!publicPath.test(request.getPath())) {
            ThreadContext.bind(authenticator.authenticateSubject(
                    request.getHeaders().get(WebHeaders.AUTHORIZATION)).subject());
        }
        return Flux.from(chain.proceed(request))
                .doOnNext(response -> response.header(WebHeaders.REQUEST_ID, requestId));
    }

    private WebRequestContext toContext(HttpRequest<?> request, String requestId) {
        String forwardedFor = request.getHeaders().get(WebHeaders.FORWARDED_FOR);
        String clientIp = StrKit.isBlank(forwardedFor)
                ? request.getRemoteAddress().getAddress().getHostAddress()
                : forwardedFor.split(",", 2)[0].trim();
        Locale locale = request.getLocale().orElse(Locale.getDefault());
        return new WebRequestContext(requestId, request.getHeaders().get(WebHeaders.TRACE_ID),
                request.getHeaders().get(WebHeaders.TENANT_ID), request.getHeaders().get(WebHeaders.AUTHORIZATION),
                locale, clientIp, request.getMethodName(), request.getPath());
    }

    private String requestId(HttpRequest<?> request) {
        String requestId = request.getHeaders().get(WebHeaders.REQUEST_ID);
        return StrKit.isBlank(requestId) ? UUID.randomUUID().toString() : requestId;
    }

    private static boolean isPublicPath(String path) {
        return "/health".equals(path) || "/health/readiness".equals(path) || "/health/liveness".equals(path);
    }
}
