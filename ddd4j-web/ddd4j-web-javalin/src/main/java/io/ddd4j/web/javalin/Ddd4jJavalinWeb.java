package io.ddd4j.web.javalin;

import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.web.core.AuthenticationMode;
import io.ddd4j.web.core.BearerSubjectAuthenticator;
import io.ddd4j.web.core.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.PathWebAccessPolicy;
import io.ddd4j.web.core.WebContextScope;
import io.ddd4j.web.core.WebError;
import io.ddd4j.web.core.WebExceptionTranslator;
import io.ddd4j.web.core.WebHeaders;
import io.ddd4j.web.core.WebIdempotencyLifecycle;
import io.ddd4j.web.core.WebRequestContext;
import io.ddd4j.web.core.WebRequestContextFactory;
import io.ddd4j.web.core.WebRequestData;
import io.ddd4j.web.core.WebRequestLifecycle;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * 在 Javalin 创建阶段安装统一请求上下文、Bearer Subject、异常与幂等处理链。
 */
@Slf4j
public final class Ddd4jJavalinWeb {

    private static final String STATE_ATTRIBUTE = Ddd4jJavalinWeb.class.getName() + ".state";

    private final WebRequestContextFactory contextFactory;
    private final WebRequestLifecycle requestLifecycle;
    private final WebExceptionTranslator exceptionTranslator;
    private final Optional<WebIdempotencyLifecycle> idempotencyLifecycle;

    public Ddd4jJavalinWeb() {
        this(new WebRequestContextFactory(), new WebRequestLifecycle(new BearerSubjectAuthenticator(),
                        new PathWebAccessPolicy(List.of("/health", "/health/readiness", "/health/liveness"),
                                AuthenticationMode.REQUIRED)),
                new DefaultWebExceptionTranslator(), null);
    }

    public Ddd4jJavalinWeb(WebRequestContextFactory contextFactory, WebRequestLifecycle requestLifecycle,
                           WebExceptionTranslator exceptionTranslator,
                           WebIdempotencyLifecycle idempotencyLifecycle) {
        this.contextFactory = Objects.requireNonNull(contextFactory, "contextFactory must not be null");
        this.requestLifecycle = Objects.requireNonNull(requestLifecycle, "requestLifecycle must not be null");
        this.exceptionTranslator = Objects.requireNonNull(exceptionTranslator,
                "exceptionTranslator must not be null");
        this.idempotencyLifecycle = Optional.ofNullable(idempotencyLifecycle);
    }

    public void configure(JavalinConfig config) {
        JavalinConfig javalinConfig = Objects.requireNonNull(config, "config must not be null");
        javalinConfig.routes.before(this::openContext);
        javalinConfig.routes.after(this::completeContext);
        javalinConfig.routes.exception(Exception.class, this::handleException);
    }

    private void openContext(Context context) {
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
            closeContext(context, false);
            throw exception;
        }
    }

    private void completeContext(Context context) {
        closeContext(context, context.statusCode() < 400);
    }

    private void handleException(Exception exception, Context context) {
        WebError error = exceptionTranslator.translate(exception);
        if (error.status() >= 500) {
            log.error("Unhandled Javalin request failure: {} {}", context.method(), context.path(), exception);
        }
        context.status(error.status()).json(error.toResponse());
        closeContext(context, false);
    }

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

    private void closeContext(Context context, boolean successful) {
        RequestState state = context.attribute(STATE_ATTRIBUTE);
        if (Objects.nonNull(state)) {
            state.close(successful);
            context.attribute(STATE_ATTRIBUTE, null);
        }
    }

    private Locale resolveLocale(Context context) {
        String language = context.header("Accept-Language");
        return StrKit.isBlank(language) ? Locale.getDefault() : Locale.forLanguageTag(language.split(",", 2)[0]);
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
