package io.ddd4j.web.webflux;

import io.ddd4j.core.subject.Subject;
import io.ddd4j.web.core.BearerSubjectAuthenticator;
import io.ddd4j.web.core.WebHeaders;
import io.ddd4j.web.core.WebRequestContext;
import org.springframework.http.HttpHeaders;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 使用 Reactor Context 传播请求状态，避免用 ThreadLocal 模拟响应式上下文。
 */
public final class Ddd4jWebFluxFilter implements WebFilter {

    private final BearerSubjectAuthenticator authenticator;
    private final Predicate<String> publicPath;

    public Ddd4jWebFluxFilter(BearerSubjectAuthenticator authenticator) {
        this(authenticator, Ddd4jWebFluxFilter::isPublicPath);
    }

    public Ddd4jWebFluxFilter(BearerSubjectAuthenticator authenticator, Predicate<String> publicPath) {
        this.authenticator = Objects.requireNonNull(authenticator, "authenticator must not be null");
        this.publicPath = Objects.requireNonNull(publicPath, "publicPath must not be null");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return Mono.defer(() -> {
            WebRequestContext requestContext = createContext(exchange);
            exchange.getResponse().getHeaders().set(WebHeaders.REQUEST_ID, requestContext.requestId());
            Mono<Void> invocation = chain.filter(exchange)
                    .contextWrite(context -> context.put(Ddd4jWebFluxContext.REQUEST_CONTEXT_KEY, requestContext));
            if (publicPath.test(requestContext.path())) {
                return invocation;
            }
            Subject subject = authenticator.authenticateSubject(requestContext.authorization()).subject();
            return invocation.contextWrite(context -> context.put(Ddd4jWebFluxContext.SUBJECT_KEY, subject));
        });
    }

    private WebRequestContext createContext(ServerWebExchange exchange) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        String requestId = headers.getFirst(WebHeaders.REQUEST_ID);
        if (!StringUtils.hasText(requestId)) {
            requestId = UUID.randomUUID().toString();
        }
        return new WebRequestContext(requestId, headers.getFirst(WebHeaders.TRACE_ID),
                headers.getFirst(WebHeaders.TENANT_ID), headers.getFirst(WebHeaders.AUTHORIZATION),
                resolveLocale(headers), resolveClientIp(exchange), exchange.getRequest().getMethod().name(),
                exchange.getRequest().getPath().value());
    }

    private Locale resolveLocale(HttpHeaders headers) {
        List<Locale> locales = headers.getAcceptLanguageAsLocales();
        return CollectionUtils.isEmpty(locales) ? Locale.getDefault() : locales.get(0);
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst(WebHeaders.FORWARDED_FOR);
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",", 2)[0].trim();
        }
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        return Objects.nonNull(remoteAddress) ? remoteAddress.getAddress().getHostAddress() : "";
    }

    private static boolean isPublicPath(String path) {
        return "/health".equals(path) || "/health/readiness".equals(path) || "/health/liveness".equals(path)
                || path.startsWith("/assets/") || path.startsWith("/webjars/");
    }
}
