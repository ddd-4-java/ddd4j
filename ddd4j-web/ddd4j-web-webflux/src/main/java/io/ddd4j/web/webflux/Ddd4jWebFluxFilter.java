package io.ddd4j.web.webflux;

import io.ddd4j.web.core.BearerSubjectAuthenticator;
import io.ddd4j.web.core.BearerSubjectAuthenticator.Authentication;
import io.ddd4j.web.core.WebAccessPolicy;
import io.ddd4j.web.core.WebHeaders;
import io.ddd4j.web.core.WebIdempotencyLifecycle;
import io.ddd4j.web.core.WebOtelSupport;
import io.ddd4j.web.core.WebRequestContext;
import io.ddd4j.web.core.WebRequestContextFactory;
import io.ddd4j.web.core.WebRequestData;
import io.ddd4j.web.core.WebRequestLifecycle;
import org.springframework.http.HttpHeaders;
import org.springframework.util.CollectionUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * 使用 Reactor Context 传播请求状态，并将同步 Subject/Cache SPI 调度到阻塞工作线程。
 *
 * <p>集成 OTel 分布式追踪：通过 {@link WebOtelSupport} 反射调用 WebOtelIntegration。
 */
public final class Ddd4jWebFluxFilter implements WebFilter {

    private static final String OTEL_SPAN_KEY = Ddd4jWebFluxFilter.class.getName() + ".otelSpan";

    private final WebRequestContextFactory contextFactory;
    private final WebRequestLifecycle requestLifecycle;
    private final Optional<WebIdempotencyLifecycle> idempotencyLifecycle;
    private final Scheduler blockingScheduler;

    public Ddd4jWebFluxFilter(BearerSubjectAuthenticator authenticator) {
        this(new WebRequestContextFactory(), new WebRequestLifecycle(authenticator, WebAccessPolicy.required()), null);
    }

    public Ddd4jWebFluxFilter(BearerSubjectAuthenticator authenticator, Predicate<String> publicPath) {
        this(new WebRequestContextFactory(), new WebRequestLifecycle(authenticator,
                WebAccessPolicy.requiredExcept(publicPath)), null);
    }

    public Ddd4jWebFluxFilter(WebRequestContextFactory contextFactory, WebRequestLifecycle requestLifecycle,
                              WebIdempotencyLifecycle idempotencyLifecycle) {
        this(contextFactory, requestLifecycle, idempotencyLifecycle, Schedulers.boundedElastic());
    }

    public Ddd4jWebFluxFilter(WebRequestContextFactory contextFactory, WebRequestLifecycle requestLifecycle,
                              WebIdempotencyLifecycle idempotencyLifecycle, Scheduler blockingScheduler) {
        this.contextFactory = Objects.requireNonNull(contextFactory, "contextFactory must not be null");
        this.requestLifecycle = Objects.requireNonNull(requestLifecycle, "requestLifecycle must not be null");
        this.idempotencyLifecycle = Optional.ofNullable(idempotencyLifecycle);
        this.blockingScheduler = Objects.requireNonNull(blockingScheduler, "blockingScheduler must not be null");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // OTel: 提取上游 TraceContext 并开启 SERVER span
        Map<String, String> headers = extractHeaders(exchange);
        Object span = WebOtelSupport.startServerSpan(
                exchange.getRequest().getMethod().name(),
                exchange.getRequest().getPath().value(),
                headers);
        exchange.getAttributes().put(OTEL_SPAN_KEY, span);
        AutoCloseable scope = WebOtelSupport.activate(span);
        exchange.getAttributes().put(OTEL_SPAN_KEY + ".scope", scope);

        return Mono.defer(() -> {
            WebRequestContext requestContext = createContext(exchange);
            exchange.getResponse().getHeaders().set(WebHeaders.REQUEST_ID, requestContext.requestId());
            exchange.getResponse().getHeaders().set(WebHeaders.TRACE_ID, requestContext.traceId());
            Mono<Optional<Authentication>> authentication = Mono.fromCallable(
                    () -> requestLifecycle.authenticate(requestContext)).subscribeOn(blockingScheduler);
            Mono<Optional<WebIdempotencyLifecycle.Scope>> idempotency = Mono.fromCallable(
                    () -> openIdempotency(requestContext, exchange)).subscribeOn(blockingScheduler);
            return authentication.flatMap(result -> idempotency.flatMap(idem -> invoke(exchange, chain,
                    requestContext, result, idem)));
        }).doFinally(signalType -> {
            // OTel: 结束 span
            Object s = exchange.getAttributes().remove(OTEL_SPAN_KEY);
            if (Objects.nonNull(s)) {
                int status = Objects.nonNull(exchange.getResponse().getStatusCode())
                        ? exchange.getResponse().getStatusCode().value() : 200;
                WebOtelSupport.endServerSpan(s, status);
            }
            Object sc = exchange.getAttributes().remove(OTEL_SPAN_KEY + ".scope");
            if (sc instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) sc).close();
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private static Map<String, String> extractHeaders(ServerWebExchange exchange) {
        Map<String, String> headers = new HashMap<>();
        exchange.getRequest().getHeaders().forEach((k, v) -> {
            if (Objects.nonNull(v) && !v.isEmpty()) {
                headers.put(k, v.get(0));
            }
        });
        return headers;
    }

    private Mono<Void> invoke(ServerWebExchange exchange, WebFilterChain chain, WebRequestContext requestContext,
                              Optional<Authentication> authentication,
                              Optional<WebIdempotencyLifecycle.Scope> idempotencyScope) {
        Mono<Void> invocation = chain.filter(exchange)
                .contextWrite(context -> context.put(Ddd4jWebFluxContext.REQUEST_CONTEXT_KEY, requestContext));
        if (authentication.isPresent()) {
            invocation = invocation.contextWrite(context -> context.put(Ddd4jWebFluxContext.SUBJECT_KEY,
                    authentication.orElseThrow().subject()));
        }
        if (idempotencyScope.isEmpty()) {
            return invocation;
        }
        WebIdempotencyLifecycle.Scope scope = idempotencyScope.orElseThrow();
        Mono<Void> scopedInvocation = invocation;
        return Mono.usingWhen(Mono.just(scope), ignored -> scopedInvocation,
                ignored -> complete(scope),
                (ignored, throwable) -> release(scope),
                ignored -> release(scope));
    }

    private Optional<WebIdempotencyLifecycle.Scope> openIdempotency(WebRequestContext context,
                                                                     ServerWebExchange exchange) {
        return idempotencyLifecycle.flatMap(lifecycle -> lifecycle.open(context,
                exchange.getRequest().getHeaders().getFirst(WebHeaders.IDEMPOTENCY_KEY)));
    }

    private Mono<Void> complete(WebIdempotencyLifecycle.Scope scope) {
        return Mono.fromRunnable(() -> {
            scope.complete();
            scope.close();
        }).subscribeOn(blockingScheduler).then();
    }

    private Mono<Void> release(WebIdempotencyLifecycle.Scope scope) {
        return Mono.fromRunnable(scope::close).subscribeOn(blockingScheduler).then();
    }

    private WebRequestContext createContext(ServerWebExchange exchange) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        return contextFactory.create(new WebRequestData(
                headers.getFirst(WebHeaders.REQUEST_ID),
                headers.getFirst(WebHeaders.TRACE_ID),
                headers.getFirst(WebHeaders.TENANT_ID),
                headers.getFirst(WebHeaders.AUTHORIZATION),
                resolveLocale(headers),
                headers.getFirst(WebHeaders.FORWARDED_FOR),
                headers.getFirst("X-Real-IP"),
                remoteAddress(exchange),
                exchange.getRequest().getMethod().name(),
                exchange.getRequest().getPath().value()));
    }

    private Locale resolveLocale(HttpHeaders headers) {
        List<Locale> locales = headers.getAcceptLanguageAsLocales();
        return CollectionUtils.isEmpty(locales) ? Locale.getDefault() : locales.get(0);
    }

    private String remoteAddress(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (Objects.isNull(remoteAddress)) {
            return null;
        }
        return Objects.nonNull(remoteAddress.getAddress())
                ? remoteAddress.getAddress().getHostAddress() : remoteAddress.getHostString();
    }
}
