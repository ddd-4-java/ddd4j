package io.ddd4j.web.javalin;

import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.runtime.health.RuntimeReadinessRegistry;
import io.ddd4j.web.core.auth.AuthenticationMode;
import io.ddd4j.web.core.auth.BearerSubjectAuthenticator;
import io.ddd4j.web.core.error.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.auth.PathWebAccessPolicy;
import io.ddd4j.web.core.health.ReadinessEndpoint;
import io.ddd4j.web.core.health.ReadinessResponse;
import io.ddd4j.web.core.context.WebContextScope;
import io.ddd4j.web.core.error.WebError;
import io.ddd4j.web.core.error.WebExceptionTranslator;
import io.ddd4j.web.core.context.WebHeaders;
import io.ddd4j.web.core.idempotency.WebIdempotencyLifecycle;
import io.ddd4j.web.core.observability.WebOtelSupport;
import io.ddd4j.web.core.context.WebRequestContext;
import io.ddd4j.web.core.context.WebRequestContextFactory;
import io.ddd4j.web.core.context.WebRequestData;
import io.ddd4j.web.core.context.WebRequestLifecycle;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 在 Javalin 创建阶段安装统一请求上下文、Bearer Subject、异常与幂等处理链。
 *
 * <p>集成 OTel 分布式追踪：通过 {@link WebOtelSupport} 反射调用 WebOtelIntegration。
 */
@Slf4j
public final class Ddd4jJavalinWeb {

    /** \u4fdd\u5b58\u5f53\u524d\u8bf7\u6c42\u4f5c\u7528\u57df\u53ca\u6e05\u7406\u8fdb\u5ea6\u7684\u5c5e\u6027\u952e\u3002 */
    private static final String STATE_ATTRIBUTE = Ddd4jJavalinWeb.class.getName() + ".state";
    /** \u5728 Javalin \u8bf7\u6c42\u5c5e\u6027\u4e2d\u4fdd\u5b58\u670d\u52a1\u7aef Span \u7684\u952e\u3002 */
    private static final String OTEL_SPAN_ATTR = Ddd4jJavalinWeb.class.getName() + ".otelSpan";

    /** \u6839\u636e\u6846\u67b6\u8bf7\u6c42\u5143\u6570\u636e\u88c5\u914d\u5171\u7528 Web \u8bf7\u6c42\u4e0a\u4e0b\u6587\u3002 */
    private final WebRequestContextFactory contextFactory;
    /** \u5f00\u542f\u548c\u5173\u95ed\u5f53\u524d Web \u8bf7\u6c42\u4e0a\u4e0b\u6587\u7684\u7ec4\u4ef6\u3002 */
    private final WebRequestLifecycle requestLifecycle;
    /** \u5c06\u6846\u67b6\u5f02\u5e38\u6620\u5c04\u4e3a\u7edf\u4e00 WebError \u7684\u8f6c\u6362\u5668\u3002 */
    private final WebExceptionTranslator exceptionTranslator;
    /** \u5728\u8bf7\u6c42\u5904\u7406\u9636\u6bb5\u83b7\u53d6\u53ca\u91ca\u653e\u5e42\u7b49\u4fdd\u62a4\u7684\u7ec4\u4ef6\u3002 */
    private final Optional<WebIdempotencyLifecycle> idempotencyLifecycle;
    /** \u63d0\u4f9b\u4f9d\u8d56\u5c31\u7eea\u72b6\u6001\u7684 Web \u7aef\u70b9\u3002 */
    private final ReadinessEndpoint readinessEndpoint;

    /** \u4f7f\u7528\u9ed8\u8ba4\u8bf7\u6c42\u751f\u547d\u5468\u671f\u548c\u5c31\u7eea\u6ce8\u518c\u8868\u521b\u5efa Web \u9002\u914d\u5668\u3002 */
    public Ddd4jJavalinWeb() {
        this(new WebRequestContextFactory(), new WebRequestLifecycle(new BearerSubjectAuthenticator(),
                        new PathWebAccessPolicy(List.of("/health", "/health/readiness", "/health/liveness",
                                ReadinessEndpoint.PATH),
                                AuthenticationMode.REQUIRED)),
                new DefaultWebExceptionTranslator(), null, new RuntimeReadinessRegistry());
    }

    /**
     * 使用应用 Runtime 的 registry 注册标准 readiness 端点。
     *
     * @param readinessRegistry 应用 Runtime 管理的就绪状态注册表
     */
    public Ddd4jJavalinWeb(RuntimeReadinessRegistry readinessRegistry) {
        this(new WebRequestContextFactory(), new WebRequestLifecycle(new BearerSubjectAuthenticator(),
                        new PathWebAccessPolicy(List.of("/health", "/health/readiness", "/health/liveness",
                                ReadinessEndpoint.PATH),
                                AuthenticationMode.REQUIRED)),
                new DefaultWebExceptionTranslator(), null, readinessRegistry);
    }

    /**
     * \u4f7f\u7528\u6307\u5b9a\u8bf7\u6c42\u751f\u547d\u5468\u671f\u548c\u5f02\u5e38\u8f6c\u6362\u5668\u521b\u5efa Web \u9002\u914d\u5668\u3002
     * @param contextFactory \u8bf7\u6c42\u4e0a\u4e0b\u6587\u5de5\u5382
     * @param requestLifecycle \u8bf7\u6c42\u751f\u547d\u5468\u671f
     * @param exceptionTranslator \u5f02\u5e38\u8f6c\u6362\u5668
     * @param idempotencyLifecycle \u5e42\u7b49\u8bf7\u6c42\u751f\u547d\u5468\u671f
     */
    public Ddd4jJavalinWeb(WebRequestContextFactory contextFactory, WebRequestLifecycle requestLifecycle,
                           WebExceptionTranslator exceptionTranslator,
                           WebIdempotencyLifecycle idempotencyLifecycle) {
        this(contextFactory, requestLifecycle, exceptionTranslator, idempotencyLifecycle,
                new RuntimeReadinessRegistry());
    }

    /**
     * \u4f7f\u7528\u6307\u5b9a\u4f9d\u8d56\u548c\u5c31\u7eea\u6ce8\u518c\u8868\u521b\u5efa Web \u9002\u914d\u5668\u3002
     * @param contextFactory \u8bf7\u6c42\u4e0a\u4e0b\u6587\u5de5\u5382
     * @param requestLifecycle \u8bf7\u6c42\u751f\u547d\u5468\u671f
     * @param exceptionTranslator \u5f02\u5e38\u8f6c\u6362\u5668
     * @param idempotencyLifecycle \u5e42\u7b49\u8bf7\u6c42\u751f\u547d\u5468\u671f
     * @param readinessRegistry \u5c31\u7eea\u72b6\u6001\u6ce8\u518c\u8868
     */
    public Ddd4jJavalinWeb(WebRequestContextFactory contextFactory, WebRequestLifecycle requestLifecycle,
                           WebExceptionTranslator exceptionTranslator,
                           WebIdempotencyLifecycle idempotencyLifecycle,
                           RuntimeReadinessRegistry readinessRegistry) {
        this.contextFactory = Objects.requireNonNull(contextFactory, "contextFactory must not be null");
        this.requestLifecycle = Objects.requireNonNull(requestLifecycle, "requestLifecycle must not be null");
        this.exceptionTranslator = Objects.requireNonNull(exceptionTranslator,
                "exceptionTranslator must not be null");
        this.idempotencyLifecycle = Optional.ofNullable(idempotencyLifecycle);
        RuntimeReadinessRegistry registry = Objects.requireNonNull(readinessRegistry,
                "readinessRegistry must not be null");
        this.readinessEndpoint = new ReadinessEndpoint(() -> registry.readiness().ready());
    }

    /**
     * \u6ce8\u518c\u8bf7\u6c42\u4e0a\u4e0b\u6587\u3001\u5f02\u5e38\u5904\u7406\u548c\u5c31\u7eea\u7aef\u70b9\u3002
     * @param config Javalin \u914d\u7f6e
     */
    public void configure(JavalinConfig config) {
        JavalinConfig javalinConfig = Objects.requireNonNull(config, "config must not be null");
        javalinConfig.routes.before(this::openContext);
        javalinConfig.routes.after(this::completeContext);
        javalinConfig.routes.exception(Exception.class, this::handleException);
        javalinConfig.routes.get(ReadinessEndpoint.PATH, this::readiness);
    }

    /**
     * \u5c06\u5f53\u524d\u8fd0\u884c\u65f6\u5c31\u7eea\u72b6\u6001\u5199\u5165 Javalin \u54cd\u5e94\u3002
     * @param context \u5f53\u524d\u6846\u67b6\u8c03\u7528\u6240\u643a\u5e26\u7684\u8bf7\u6c42\u4e0a\u4e0b\u6587
     */
    private void readiness(Context context) {
        ReadinessResponse response = readinessEndpoint.readiness();
        context.status(response.httpStatus()).json(response);
    }

    /**
     * \u5728 Javalin \u8def\u7531\u5904\u7406\u524d\u542f\u52a8\u8ddf\u8e2a\u5e76\u7ed1\u5b9a\u8bf7\u6c42\u4e0a\u4e0b\u6587\u3002
     * @param context \u5f53\u524d\u6846\u67b6\u8c03\u7528\u6240\u643a\u5e26\u7684\u8bf7\u6c42\u4e0a\u4e0b\u6587
     */
    private void openContext(Context context) {
        // OTel: 提取上游 TraceContext 并开启 SERVER span
        Map<String, String> headers = extractHeaders(context);
        Object span = WebOtelSupport.startServerSpan(
                context.method().name(), context.path(), headers);
        context.attribute(OTEL_SPAN_ATTR, span);
        WebOtelSupport.activate(span);

        WebRequestContext requestContext = createContext(context);
        RequestState state = new RequestState(WebContextScope.open(requestContext));
        context.attribute(STATE_ATTRIBUTE, state);
        context.header(WebHeaders.REQUEST_ID, requestContext.requestId());
        context.header(WebHeaders.TRACE_ID, requestContext.traceId());
        try {
            requestLifecycle.authenticate(requestContext)
                    .ifPresent(authentication -> ThreadContext.bind(authentication.subject()));
            idempotencyLifecycle.flatMap(lifecycle -> lifecycle.open(requestContext,
                    context.header(WebHeaders.IDEMPOTENCY_KEY))).ifPresent(state::idempotencyScope);
        } catch (RuntimeException exception) {
            WebOtelSupport.recordError(span, exception);
            closeContext(context, false);
            throw exception;
        }
    }

    /**
     * \u6309 Javalin \u8bf7\u6c42\u6700\u7ec8\u7ed3\u679c\u5b8c\u6210\u8ddf\u8e2a\u548c\u4e0a\u4e0b\u6587\u6e05\u7406\u3002
     * @param context \u5f53\u524d\u6846\u67b6\u8c03\u7528\u6240\u643a\u5e26\u7684\u8bf7\u6c42\u4e0a\u4e0b\u6587
     */
    private void completeContext(Context context) {
        // OTel: 结束 span
        Object span = context.attribute(OTEL_SPAN_ATTR);
        if (Objects.nonNull(span)) {
            WebOtelSupport.endServerSpan(span, context.statusCode());
        }
        closeContext(context, context.statusCode() < 400);
    }

    /**
     * \u5c06 Javalin \u8def\u7531\u5f02\u5e38\u8f6c\u6362\u5e76\u5199\u5165\u7edf\u4e00\u7684 HTTP \u9519\u8bef\u54cd\u5e94\u3002
     * @param exception \u5f53\u524d\u8bf7\u6c42\u5904\u7406\u65f6\u629b\u51fa\u7684\u5f02\u5e38
     * @param context \u5f53\u524d\u6846\u67b6\u8c03\u7528\u6240\u643a\u5e26\u7684\u8bf7\u6c42\u4e0a\u4e0b\u6587
     */
    private void handleException(Exception exception, Context context) {
        WebError error = exceptionTranslator.translate(exception);
        if (error.status() >= 500) {
            log.error("Unhandled Javalin request failure: {} {}", context.method(), context.path(), exception);
        }
        // OTel: 记录异常
        Object span = context.attribute(OTEL_SPAN_ATTR);
        if (Objects.nonNull(span)) {
            WebOtelSupport.recordError(span, exception);
            WebOtelSupport.endServerSpan(span, error.status());
        }
        context.status(error.status()).json(error.toResponse());
        closeContext(context, false);
    }

    /**
     * \u6536\u96c6 HTTP \u8bf7\u6c42\u4e2d\u53ef\u7528\u7684\u5934\u540d\u79f0\u53ca\u5176\u503c\u3002
     * @param context \u5f53\u524d\u6846\u67b6 HTTP \u8bf7\u6c42\u6216\u4ea4\u6362\u4e0a\u4e0b\u6587
     * @return \u4f9b\u8bf7\u6c42\u4e0a\u4e0b\u6587\u8bfb\u53d6\u7684\u8bf7\u6c42\u5934\u6620\u5c04
     */
    private static Map<String, String> extractHeaders(Context context) {
        Map<String, String> headers = new HashMap<>();
        context.headerMap().forEach((k, v) -> {
            if (Objects.nonNull(v)) {
                headers.put(k, v);
            }
        });
        return headers;
    }

    /**
     * \u4ece\u6846\u67b6\u8bf7\u6c42\u63d0\u53d6\u79df\u6237\u3001\u8ddf\u8e2a\u6807\u8bc6\u3001\u8ba4\u8bc1\u548c\u8def\u7531\u4fe1\u606f\uff0c\u7ec4\u88c5\u5171\u7528\u4e0a\u4e0b\u6587\u3002
     * @param context \u5f53\u524d\u6846\u67b6 HTTP \u8bf7\u6c42
     * @return \u4f9b\u4e0a\u4e0b\u6587\u548c\u8ba4\u8bc1\u5904\u7406\u94fe\u4f7f\u7528\u7684\u8bf7\u6c42\u4e0a\u4e0b\u6587
     */
    private WebRequestContext createContext(Context context) {
        return contextFactory.create(new WebRequestData(
                context.header(WebHeaders.REQUEST_ID),
                context.header(WebHeaders.TRACE_ID),
                context.header(WebHeaders.TENANT_ID),
                context.header(WebHeaders.AUTHORIZATION),
                resolveLocale(context),
                context.header(WebHeaders.FORWARDED_FOR),
                context.header("X-Real-IP"),
                context.req().getRemoteAddr(),
                context.method().name(),
                context.path()));
    }

    /**
     * \u5173\u95ed\u5e76\u79fb\u9664\u8be5 Javalin \u8bf7\u6c42\u5df2\u4fdd\u5b58\u7684\u4f5c\u7528\u57df\u3002
     * @param context \u5f53\u524d\u6846\u67b6\u8c03\u7528\u6240\u643a\u5e26\u7684\u8bf7\u6c42\u4e0a\u4e0b\u6587
     * @param successful \u8bf7\u6c42\u662f\u5426\u6309\u6b63\u5e38\u4e1a\u52a1\u7ed3\u679c\u5b8c\u6210
     */
    private void closeContext(Context context, boolean successful) {
        RequestState state = context.attribute(STATE_ATTRIBUTE);
        if (Objects.nonNull(state)) {
            state.close(successful);
            context.attribute(STATE_ATTRIBUTE, null);
        }
    }

    /**
     * \u4f7f\u7528\u8bf7\u6c42\u8bed\u8a00\u504f\u597d\u5217\u8868\u4e2d\u7684\u7b2c\u4e00\u9879\uff0c\u672a\u63d0\u4f9b\u504f\u597d\u65f6\u7528 JVM \u9ed8\u8ba4\u533a\u57df\u3002
     * @param context \u63d0\u4f9b\u8bed\u8a00\u504f\u597d\u7684\u8bf7\u6c42\u6216\u8bf7\u6c42\u5934
     * @return \u672c\u6b21\u8bf7\u6c42\u4f7f\u7528\u7684\u533a\u57df
     */
    private Locale resolveLocale(Context context) {
        String language = context.header("Accept-Language");
        return StrKit.isBlank(language) ? Locale.getDefault() : Locale.forLanguageTag(language.split(",", 2)[0]);
    }

    /** \u5355\u6b21\u8bf7\u6c42\u7684\u5904\u7406\u72b6\u6001\uff0c\u805a\u5408\u8bf7\u6c42\u4e0a\u4e0b\u6587\u4f5c\u7528\u57df\u3001\u8ffd\u8e2a\u4f5c\u7528\u57df\u4e0e\u5e42\u7b49\u4f5c\u7528\u57df\u5e76\u6309\u5e8f\u5173\u95ed\u3002 */
    private static final class RequestState {

        /** \u5f53\u524d\u8bf7\u6c42\u83b7\u5f97\u7684 DDD4J \u7ebf\u7a0b\u4e0a\u4e0b\u6587\u4f5c\u7528\u57df\u3002 */
        private final WebContextScope contextScope;
        /** \u5f53\u524d\u8bf7\u6c42\u53d6\u5f97\u7684\u5e42\u7b49\u5904\u7406\u4f5c\u7528\u57df\u3002 */
        private WebIdempotencyLifecycle.Scope idempotencyScope;

        /**
         * 初始化 <code>RequestState</code> 使用的 <code>contextScope</code>。
         *
         * @param contextScope \u7ba1\u7406\u5f53\u524d\u7ebf\u7a0b\u8bf7\u6c42\u8d44\u6e90\u7684\u4f5c\u7528\u57df
         */
        private RequestState(WebContextScope contextScope) {
            this.contextScope = contextScope;
        }

        /**
         * \u8bbe\u7f6e<code>idempotencyScope</code> \u5b57\u6bb5\u3002
         * @param scope \u8981\u5199\u5165\u7684\u503c
         */
        private void idempotencyScope(WebIdempotencyLifecycle.Scope scope) {
            this.idempotencyScope = scope;
        }

        /**
         * \u6309\u8bf7\u6c42\u6700\u7ec8\u72b6\u6001\u5173\u95ed\u5e42\u7b49\u53ca\u5176\u4ed6\u8bf7\u6c42\u4f5c\u7528\u57df\u3002
         * @param successful \u8bf7\u6c42\u662f\u5426\u6309\u6b63\u5e38\u4e1a\u52a1\u7ed3\u679c\u5b8c\u6210
         */
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
