package io.ddd4j.web.vertx;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.ddd4j.runtime.health.RuntimeReadinessRegistry;
import io.ddd4j.web.core.auth.AuthenticationMode;
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.error.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.auth.PathWebAccessPolicy;
import io.ddd4j.web.core.health.ReadinessEndpoint;
import io.ddd4j.web.core.health.ReadinessResponse;
import io.ddd4j.web.core.error.WebError;
import io.ddd4j.web.core.error.WebExceptionTranslator;
import io.ddd4j.web.core.context.WebHeaders;
import io.ddd4j.web.core.idempotency.WebIdempotencyLifecycle;
import io.ddd4j.web.core.observability.WebOtelSupport;
import io.ddd4j.web.core.context.WebRequestContext;
import io.ddd4j.web.core.context.WebRequestContextFactory;
import io.ddd4j.web.core.context.WebRequestData;
import io.ddd4j.web.core.context.WebRequestLifecycle;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Vert.x Router 的标准请求上下文、认证、幂等和异常处理链。
 *
 * <p>集成 OTel 分布式追踪：通过 {@link WebOtelSupport} 反射调用 WebOtelIntegration。
 */
@Slf4j
public final class Ddd4jVertxWeb {

    /** \u4fdd\u5b58 Vert.x \u8bf7\u6c42\u5904\u7406\u72b6\u6001\u548c\u6e05\u7406\u53e5\u67c4\u7684\u952e\u3002 */
    private static final String STATE_KEY = Ddd4jVertxWeb.class.getName() + ".state";
    /** \u4fdd\u5b58\u6846\u67b6\u8bf7\u6c42\u670d\u52a1\u7aef Span \u7684\u952e\u3002 */
    private static final String OTEL_SPAN_KEY = Ddd4jVertxWeb.class.getName() + ".otelSpan";

    /** \u6839\u636e\u6846\u67b6\u8bf7\u6c42\u5143\u6570\u636e\u88c5\u914d\u5171\u7528 Web \u8bf7\u6c42\u4e0a\u4e0b\u6587\u3002 */
    private final WebRequestContextFactory contextFactory;
    /** \u5f00\u542f\u548c\u5173\u95ed\u5f53\u524d Web \u8bf7\u6c42\u4e0a\u4e0b\u6587\u7684\u7ec4\u4ef6\u3002 */
    private final WebRequestLifecycle requestLifecycle;
    /** \u5c06\u6846\u67b6\u5f02\u5e38\u6620\u5c04\u4e3a\u7edf\u4e00 WebError \u7684\u8f6c\u6362\u5668\u3002 */
    private final WebExceptionTranslator exceptionTranslator;
    /** \u5728\u8bf7\u6c42\u5904\u7406\u9636\u6bb5\u83b7\u53d6\u53ca\u91ca\u653e\u5e42\u7b49\u4fdd\u62a4\u7684\u7ec4\u4ef6\u3002 */
    private final Optional<WebIdempotencyLifecycle> idempotencyLifecycle;
    /** \u5c06 Vert.x \u5e94\u7528\u7ed3\u679c\u5199\u6210 HTTP JSON \u6b63\u6587\u7684\u7f16\u7801\u5668\u3002 */
    private final Function<Object, String> jsonEncoder;
    /** \u63d0\u4f9b\u4f9d\u8d56\u5c31\u7eea\u72b6\u6001\u7684 Web \u7aef\u70b9\u3002 */
    private final ReadinessEndpoint readinessEndpoint;

    /** \u4f7f\u7528\u9ed8\u8ba4\u8ba4\u8bc1\u3001\u9519\u8bef\u5904\u7406\u548c\u5c31\u7eea\u914d\u7f6e\u521b\u5efa Web \u9002\u914d\u5668\u3002 */
    public Ddd4jVertxWeb() {
        this(new WebRequestContextFactory(), new WebRequestLifecycle(new BearerSubjectAuthenticator(),
                        new PathWebAccessPolicy(List.of("/health", "/health/readiness", "/health/liveness",
                                ReadinessEndpoint.PATH),
                                AuthenticationMode.REQUIRED)),
                new DefaultWebExceptionTranslator(), null, Json::encode, new RuntimeReadinessRegistry());
    }

    /**
     * 使用应用 Runtime 的 registry 安装标准 readiness 端点。
     *
     * @param readinessRegistry 应用 Runtime 管理的就绪状态注册表
     */
    public Ddd4jVertxWeb(RuntimeReadinessRegistry readinessRegistry) {
        this(new WebRequestContextFactory(), new WebRequestLifecycle(new BearerSubjectAuthenticator(),
                        new PathWebAccessPolicy(List.of("/health", "/health/readiness", "/health/liveness",
                                ReadinessEndpoint.PATH),
                                AuthenticationMode.REQUIRED)),
                new DefaultWebExceptionTranslator(), null, Json::encode, readinessRegistry);
    }

    /**
     * \u4f7f\u7528\u6307\u5b9a Web \u7ec4\u4ef6\u548c\u9ed8\u8ba4\u5c31\u7eea\u6ce8\u518c\u8868\u521b\u5efa\u9002\u914d\u5668\u3002
     * @param contextFactory \u8bf7\u6c42\u4e0a\u4e0b\u6587\u5de5\u5382
     * @param requestLifecycle \u8bf7\u6c42\u751f\u547d\u5468\u671f
     * @param exceptionTranslator \u5f02\u5e38\u7ffb\u8bd1\u5668
     * @param idempotencyLifecycle \u5e42\u7b49\u751f\u547d\u5468\u671f\uff0c\u53ef\u4e3a\u7a7a
     * @param jsonEncoder JSON \u7f16\u7801\u5668
     */
    public Ddd4jVertxWeb(WebRequestContextFactory contextFactory, WebRequestLifecycle requestLifecycle,
                         WebExceptionTranslator exceptionTranslator,
                         WebIdempotencyLifecycle idempotencyLifecycle,
                         Function<Object, String> jsonEncoder) {
        this(contextFactory, requestLifecycle, exceptionTranslator, idempotencyLifecycle, jsonEncoder,
                new RuntimeReadinessRegistry());
    }

    /**
     * \u4f7f\u7528\u6307\u5b9a Web \u7ec4\u4ef6\u548c\u5c31\u7eea\u6ce8\u518c\u8868\u521b\u5efa\u9002\u914d\u5668\u3002
     * @param contextFactory \u8bf7\u6c42\u4e0a\u4e0b\u6587\u5de5\u5382
     * @param requestLifecycle \u8bf7\u6c42\u751f\u547d\u5468\u671f
     * @param exceptionTranslator \u5f02\u5e38\u7ffb\u8bd1\u5668
     * @param idempotencyLifecycle \u5e42\u7b49\u751f\u547d\u5468\u671f\uff0c\u53ef\u4e3a\u7a7a
     * @param jsonEncoder JSON \u7f16\u7801\u5668
     * @param readinessRegistry \u5c31\u7eea\u6ce8\u518c\u8868
     */
    public Ddd4jVertxWeb(WebRequestContextFactory contextFactory, WebRequestLifecycle requestLifecycle,
                         WebExceptionTranslator exceptionTranslator,
                         WebIdempotencyLifecycle idempotencyLifecycle,
                         Function<Object, String> jsonEncoder,
                         RuntimeReadinessRegistry readinessRegistry) {
        this.contextFactory = Objects.requireNonNull(contextFactory, "contextFactory must not be null");
        this.requestLifecycle = Objects.requireNonNull(requestLifecycle, "requestLifecycle must not be null");
        this.exceptionTranslator = Objects.requireNonNull(exceptionTranslator,
                "exceptionTranslator must not be null");
        this.idempotencyLifecycle = Optional.ofNullable(idempotencyLifecycle);
        this.jsonEncoder = Objects.requireNonNull(jsonEncoder, "jsonEncoder must not be null");
        RuntimeReadinessRegistry registry = Objects.requireNonNull(readinessRegistry,
                "readinessRegistry must not be null");
        this.readinessEndpoint = new ReadinessEndpoint(() -> registry.readiness().ready());
    }

    /**
     * \u5c06\u8bf7\u6c42\u4e0a\u4e0b\u6587\u3001\u8ba4\u8bc1\u3001\u9519\u8bef\u5904\u7406\u53ca\u5c31\u7eea\u7aef\u70b9\u5b89\u88c5\u5230\u8def\u7531\u5668\u3002
     * @param router Vert.x \u8def\u7531\u5668
     */
    public void install(Router router) {
        Router target = Objects.requireNonNull(router, "router must not be null");
        target.route().handler(contextHandler());
        target.route().handler(authenticationHandler());
        target.route().failureHandler(failureHandler());
        target.get(ReadinessEndpoint.PATH).handler(this::readiness);
    }

    /**
     * \u5c06\u5f53\u524d\u8fd0\u884c\u65f6\u5c31\u7eea\u72b6\u6001\u5199\u5165 Vert.x \u54cd\u5e94\u3002
     * @param context \u5f53\u524d\u6846\u67b6\u8c03\u7528\u6240\u643a\u5e26\u7684\u8bf7\u6c42\u4e0a\u4e0b\u6587
     */
    private void readiness(RoutingContext context) {
        ReadinessResponse response = readinessEndpoint.readiness();
        context.response().setStatusCode(response.httpStatus())
                .putHeader("Content-Type", "application/json")
                .end(jsonEncoder.apply(response));
    }

    /**
     * \u521b\u5efa\u521d\u59cb\u5316\u8bf7\u6c42\u4e0a\u4e0b\u6587\u4e0e\u8ffd\u8e2a\u7684\u5904\u7406\u5668\u3002
     * @return \u8bf7\u6c42\u4e0a\u4e0b\u6587\u5904\u7406\u5668
     */
    public Handler<RoutingContext> contextHandler() {
        return routingContext -> {
            // OTel: 提取上游 TraceContext 并开启 SERVER span
            Map<String, String> headers = extractHeaders(routingContext);
            Object span = WebOtelSupport.startServerSpan(
                    routingContext.request().method().name(),
                    routingContext.normalizedPath(),
                    headers);
            routingContext.put(OTEL_SPAN_KEY, span);
            WebOtelSupport.activate(span);

            WebRequestContext requestContext = createContext(routingContext);
            routingContext.put(STATE_KEY, new RequestState());
            Ddd4jVertxContext.bindRequest(routingContext, requestContext);
            routingContext.response().putHeader(WebHeaders.REQUEST_ID, requestContext.requestId());
            routingContext.response().putHeader(WebHeaders.TRACE_ID, requestContext.traceId());
            routingContext.addEndHandler(ignored -> finish(routingContext));
            routingContext.next();
        };
    }

    /**
     * \u521b\u5efa\u8ba4\u8bc1\u4e0e\u5e42\u7b49\u68c0\u67e5\u5904\u7406\u5668\u3002
     * @return \u8ba4\u8bc1\u5904\u7406\u5668
     */
    public Handler<RoutingContext> authenticationHandler() {
        return routingContext -> {
            WebRequestContext requestContext = Ddd4jVertxContext.request(routingContext).orElseThrow();
            routingContext.vertx().executeBlocking(() -> authenticate(routingContext, requestContext))
                    .onComplete(result -> routingContext.vertx().runOnContext(ignored -> {
                        if (result.failed()) {
                            routingContext.fail(result.cause());
                            return;
                        }
                        AuthenticationResult authenticationResult = result.result();
                        authenticationResult.authentication().ifPresent(authentication ->
                                Ddd4jVertxContext.bindSubject(routingContext, authentication.subject()));
                        RequestState state = routingContext.get(STATE_KEY);
                        authenticationResult.idempotencyScope().ifPresent(state::idempotencyScope);
                        routingContext.next();
                    }));
        };
    }

    /**
     * \u521b\u5efa\u7edf\u4e00\u5f02\u5e38\u7ffb\u8bd1\u548c\u54cd\u5e94\u5904\u7406\u5668\u3002
     * @return \u5f02\u5e38\u5904\u7406\u5668
     */
    public Handler<RoutingContext> failureHandler() {
        return routingContext -> {
            Throwable failure = Objects.nonNull(routingContext.failure())
                    ? routingContext.failure()
                    : new IllegalStateException("HTTP request failed with status " + routingContext.statusCode());
            RequestState state = routingContext.get(STATE_KEY);
            if (Objects.nonNull(state)) {
                state.failed();
            }
            WebError error = exceptionTranslator.translate(failure);
            if (error.status() >= 500) {
                log.error("Unhandled Vert.x request failure: {} {}", routingContext.request().method(),
                        routingContext.normalizedPath(), failure);
            }
            // OTel: 记录异常
            Object span = routingContext.get(OTEL_SPAN_KEY);
            if (Objects.nonNull(span)) {
                WebOtelSupport.recordError(span, failure);
                WebOtelSupport.endServerSpan(span, error.status());
            }
            if (!routingContext.response().ended()) {
                routingContext.response().setStatusCode(error.status())
                        .putHeader("Content-Type", "application/json")
                        .end(jsonEncoder.apply(error.toResponse()));
            }
        };
    }

    /**
     * \u6536\u96c6 HTTP \u8bf7\u6c42\u4e2d\u53ef\u7528\u7684\u5934\u540d\u79f0\u53ca\u5176\u503c\u3002
     * @param context \u5f53\u524d\u6846\u67b6 HTTP \u8bf7\u6c42\u6216\u4ea4\u6362\u4e0a\u4e0b\u6587
     * @return \u4f9b\u8bf7\u6c42\u4e0a\u4e0b\u6587\u8bfb\u53d6\u7684\u8bf7\u6c42\u5934\u6620\u5c04
     */
    private static Map<String, String> extractHeaders(RoutingContext context) {
        Map<String, String> headers = new HashMap<>();
        context.request().headers().forEach(entry -> {
            String key = entry.getKey();
            String value = entry.getValue();
            if (Objects.nonNull(key) && Objects.nonNull(value)) {
                headers.put(key, value);
            }
        });
        return headers;
    }

    /**
     * \u6309\u8def\u7531\u8bbf\u95ee\u7b56\u7565\u89e3\u6790\u4e3b\u4f53\u5e76\u53d6\u5f97\u9700\u8981\u7684\u5e42\u7b49\u4fdd\u62a4\u3002
     * @param routingContext \u5f53\u524d Vert.x \u8def\u7531\u8bf7\u6c42\u4e0a\u4e0b\u6587
     * @param requestContext \u5f53\u524d HTTP \u8bf7\u6c42\u5df2\u7ecf\u89e3\u6790\u7684 DDD4J \u4e0a\u4e0b\u6587
     * @return \u8ba4\u8bc1\u4e0e\u53ef\u7528\u5e42\u7b49\u4f5c\u7528\u57df\u7684\u7ec4\u5408
     */
    private AuthenticationResult authenticate(RoutingContext routingContext, WebRequestContext requestContext) {
        Optional<BearerSubjectAuthenticator.Authentication> authentication = requestLifecycle
                .authenticate(requestContext);
        Optional<WebIdempotencyLifecycle.Scope> idempotencyScope = idempotencyLifecycle.flatMap(lifecycle ->
                lifecycle.open(requestContext,
                        routingContext.request().getHeader(WebHeaders.IDEMPOTENCY_KEY)));
        return new AuthenticationResult(authentication, idempotencyScope);
    }

    /**
     * \u4ece\u6846\u67b6\u8bf7\u6c42\u63d0\u53d6\u79df\u6237\u3001\u8ddf\u8e2a\u6807\u8bc6\u3001\u8ba4\u8bc1\u548c\u8def\u7531\u4fe1\u606f\uff0c\u7ec4\u88c5\u5171\u7528\u4e0a\u4e0b\u6587\u3002
     * @param context \u5f53\u524d\u6846\u67b6 HTTP \u8bf7\u6c42
     * @return \u4f9b\u4e0a\u4e0b\u6587\u548c\u8ba4\u8bc1\u5904\u7406\u94fe\u4f7f\u7528\u7684\u8bf7\u6c42\u4e0a\u4e0b\u6587
     */
    private WebRequestContext createContext(RoutingContext context) {
        Locale locale = Objects.isNull(context.preferredLanguage())
                ? Locale.getDefault() : Locale.forLanguageTag(context.preferredLanguage().tag());
        return contextFactory.create(new WebRequestData(
                context.request().getHeader(WebHeaders.REQUEST_ID),
                context.request().getHeader(WebHeaders.TRACE_ID),
                context.request().getHeader(WebHeaders.TENANT_ID),
                context.request().getHeader(WebHeaders.AUTHORIZATION),
                locale,
                context.request().getHeader(WebHeaders.FORWARDED_FOR),
                context.request().getHeader("X-Real-IP"),
                context.request().remoteAddress().host(),
                context.request().method().name(),
                context.normalizedPath()));
    }

    /**
     * \u6839\u636e\u6700\u7ec8\u54cd\u5e94\u7ed3\u675f\u8ddf\u8e2a\u5e76\u6e05\u7406 Vert.x \u8bf7\u6c42\u4f5c\u7528\u57df\u3002
     * @param context \u5f53\u524d\u6846\u67b6\u8c03\u7528\u6240\u643a\u5e26\u7684\u8bf7\u6c42\u4e0a\u4e0b\u6587
     */
    private void finish(RoutingContext context) {
        RequestState state = context.remove(STATE_KEY);
        if (Objects.isNull(state)) {
            return;
        }
        boolean successful = !state.failed && context.response().getStatusCode() < 400;
        context.vertx().executeBlocking(() -> {
            state.close(successful);
            return null;
        }).onFailure(exception -> log.error("Unable to close Vert.x request state", exception));

        // OTel: 结束 span
        Object span = context.remove(OTEL_SPAN_KEY);
        if (Objects.nonNull(span)) {
            int status = context.response().getStatusCode() > 0
                    ? context.response().getStatusCode() : 200;
            if (state.failed) {
                WebOtelSupport.recordError(span, new RuntimeException("request failed"));
            }
            WebOtelSupport.endServerSpan(span, status);
        }
    }

    /** \u4fdd\u5b58 Vert.x \u5f53\u524d\u8bf7\u6c42\u7684\u8ba4\u8bc1\u7ed3\u679c\u53ca\u5df2\u53d6\u5f97\u7684\u5e42\u7b49\u4f5c\u7528\u57df\u3002 */
    private final static class AuthenticationResult {

        /** Java 序列化版本标识。 */
        private static final long serialVersionUID = 0L;

        /** \u5f53\u524d\u8bf7\u6c42\u7684\u8ba4\u8bc1\u7ed3\u679c\uff0c\u516c\u5f00\u8def\u7531\u53ef\u4ee5\u4e0d\u643a\u5e26\u4e3b\u4f53\u3002 */
        private final Optional<BearerSubjectAuthenticator.Authentication> authentication;

        /** \u5f53\u524d\u8bf7\u6c42\u53d6\u5f97\u7684\u5e42\u7b49\u5904\u7406\u4f5c\u7528\u57df\u3002 */
        private final Optional<WebIdempotencyLifecycle.Scope> idempotencyScope;

        /**
         * \u5c06\u8fdb\u5165\u8def\u7531\u524d\u53d6\u5f97\u7684\u8ba4\u8bc1\u7ed3\u679c\u548c\u5e42\u7b49\u4f5c\u7528\u57df\u4e00\u8d77\u4fdd\u5b58\u3002
         * @param authentication \u5f53\u524d\u8bf7\u6c42\u5df2\u53d6\u5f97\u7684\u8ba4\u8bc1\u7ed3\u679c
         * @param idempotencyScope \u5f53\u524d\u8bf7\u6c42\u83b7\u53d6\u7684\u53ef\u9009\u5e42\u7b49\u4f5c\u7528\u57df
         */
        @JsonCreator()
        private AuthenticationResult(@JsonProperty("authentication") Optional<BearerSubjectAuthenticator.Authentication> authentication, @JsonProperty("idempotencyScope") Optional<WebIdempotencyLifecycle.Scope> idempotencyScope) {
            this.authentication = authentication;
            this.idempotencyScope = idempotencyScope;
        }

        /**
         * \u8bfb\u53d6<code>authentication</code> \u5b57\u6bb5\u3002
         * @return <code>authentication</code> \u5b57\u6bb5
         */
        @JsonProperty("authentication")
        public Optional<BearerSubjectAuthenticator.Authentication> authentication() {
            return authentication;
        }

        /**
         * \u8bfb\u53d6<code>idempotencyScope</code> \u5b57\u6bb5\u3002
         * @return <code>idempotencyScope</code> \u5b57\u6bb5
         */
        @JsonProperty("idempotencyScope")
        public Optional<WebIdempotencyLifecycle.Scope> idempotencyScope() {
            return idempotencyScope;
        }

        /**
         * 执行 <code>equals</code>，调用 <code>obj.getClass</code>。
         *
         * @param obj 传入此操作的 <code>Object</code> 类型 <code>obj</code>
         * @return 按条件返回 <code>true</code>、<code>false</code>
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                return false;
            }
            AuthenticationResult other = (AuthenticationResult) obj;
            return Objects.equals(this.authentication, other.authentication) && Objects.equals(this.idempotencyScope, other.idempotencyScope);
        }

        /**
         * 读取<code>result</code> 的当前值。
         *
         * @return 返回表达式 <code>result</code>
         */
        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(authentication);
            result = 31 * result + Objects.hashCode(idempotencyScope);
            return result;
        }

        /**
         * 按 <code>to String</code> 的契约计算并返回结果。
         *
         * @return 当前操作得到的 <code>String</code> 结果
         */
        @Override
        public String toString() {
            return "AuthenticationResult[authentication=" + authentication + ", idempotencyScope=" + idempotencyScope + "]";
        }
    }

    /** \u5728\u4e00\u6b21 HTTP \u8bf7\u6c42\u7684\u5904\u7406\u9636\u6bb5\u4e4b\u95f4\u4fdd\u5b58\u5e42\u7b49\u72b6\u6001\u53ca\u6e05\u7406\u8fdb\u5ea6\u3002 */
    private static final class RequestState {

        /** \u5f53\u524d\u8bf7\u6c42\u53d6\u5f97\u7684\u5e42\u7b49\u5904\u7406\u4f5c\u7528\u57df\u3002 */
        private WebIdempotencyLifecycle.Scope idempotencyScope;
        /** \u5f53\u524d\u8bf7\u6c42\u5904\u7406\u94fe\u662f\u5426\u5df2\u7ecf\u62a5\u544a\u5931\u8d25\u3002 */
        private boolean failed;

        /**
         * \u8bbe\u7f6e<code>idempotencyScope</code> \u5b57\u6bb5\u3002
         * @param scope \u8981\u5199\u5165\u7684\u503c
         */
        private void idempotencyScope(WebIdempotencyLifecycle.Scope scope) {
            this.idempotencyScope = scope;
        }

        /** \u6807\u8bb0\u5f53\u524d\u8bf7\u6c42\u4e0d\u80fd\u4ee5\u6210\u529f\u72b6\u6001\u5b8c\u6210\u5e42\u7b49\u5904\u7406\u3002 */
        private void failed() {
            failed = true;
        }

        /**
         * \u6309\u8bf7\u6c42\u6700\u7ec8\u72b6\u6001\u5173\u95ed\u5e42\u7b49\u53ca\u5176\u4ed6\u8bf7\u6c42\u4f5c\u7528\u57df\u3002
         * @param successful \u8bf7\u6c42\u662f\u5426\u6309\u6b63\u5e38\u4e1a\u52a1\u7ed3\u679c\u5b8c\u6210
         */
        private void close(boolean successful) {
            if (Objects.isNull(idempotencyScope)) {
                return;
            }
            if (successful) {
                idempotencyScope.complete();
            }
            idempotencyScope.close();
        }
    }
}
