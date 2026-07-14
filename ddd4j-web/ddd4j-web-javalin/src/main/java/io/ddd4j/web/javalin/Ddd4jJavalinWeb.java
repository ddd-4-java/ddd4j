package io.ddd4j.web.javalin;

import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.web.core.BearerSubjectAuthenticator;
import io.ddd4j.web.core.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.WebContextScope;
import io.ddd4j.web.core.WebError;
import io.ddd4j.web.core.WebExceptionTranslator;
import io.ddd4j.web.core.WebHeaders;
import io.ddd4j.web.core.WebRequestContext;
import io.ddd4j.web.javalin.util.WebKit;
import io.javalin.Javalin;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 为 Javalin 安装统一请求上下文、Bearer Subject、异常与清理处理链。
 */
@Slf4j
public final class Ddd4jJavalinWeb {

    private static final String SCOPE_ATTRIBUTE = Ddd4jJavalinWeb.class.getName() + ".scope";

    private final BearerSubjectAuthenticator authenticator;
    private final WebExceptionTranslator translator;
    private final Predicate<String> publicPath;

    public Ddd4jJavalinWeb() {
        this(new BearerSubjectAuthenticator(), new DefaultWebExceptionTranslator(), Ddd4jJavalinWeb::isPublicPath);
    }

    public Ddd4jJavalinWeb(BearerSubjectAuthenticator authenticator, WebExceptionTranslator translator,
                           Predicate<String> publicPath) {
        this.authenticator = Objects.requireNonNull(authenticator, "authenticator must not be null");
        this.translator = Objects.requireNonNull(translator, "translator must not be null");
        this.publicPath = Objects.requireNonNull(publicPath, "publicPath must not be null");
    }

    public void install(Javalin app) {
        Objects.requireNonNull(app, "app must not be null");
        app.unsafe.routes.before(this::openContext);
        app.unsafe.routes.after(this::closeContext);
        app.unsafe.routes.exception(Exception.class, this::handleException);
    }

    private void openContext(Context context) {
        String requestId = context.header(WebHeaders.REQUEST_ID);
        if (StrKit.isBlank(requestId)) {
            requestId = UUID.randomUUID().toString();
        }
        WebRequestContext requestContext = new WebRequestContext(requestId, context.header(WebHeaders.TRACE_ID),
                context.header(WebHeaders.TENANT_ID), context.header(WebHeaders.AUTHORIZATION), resolveLocale(context),
                WebKit.getClientIp(context), context.method().name(), context.path());
        context.attribute(SCOPE_ATTRIBUTE, WebContextScope.open(requestContext));
        context.header(WebHeaders.REQUEST_ID, requestId);
        try {
            if (!publicPath.test(context.path())) {
                ThreadContext.bind(authenticator.authenticateSubject(
                        context.header(WebHeaders.AUTHORIZATION)).subject());
            }
        } catch (RuntimeException exception) {
            closeContext(context);
            throw exception;
        }
    }

    private void closeContext(Context context) {
        WebContextScope scope = context.attribute(SCOPE_ATTRIBUTE);
        if (Objects.nonNull(scope)) {
            scope.close();
            context.attribute(SCOPE_ATTRIBUTE, null);
        }
    }

    private void handleException(Exception exception, Context context) {
        WebError error = translator.translate(exception);
        if (error.status() >= 500) {
            log.error("Unhandled Javalin request failure: {} {}", context.method(), context.path(), exception);
        }
        context.status(error.status()).json(error.toResponse());
        closeContext(context);
    }

    private Locale resolveLocale(Context context) {
        String language = context.header("Accept-Language");
        return StrKit.isBlank(language) ? Locale.getDefault() : Locale.forLanguageTag(language.split(",", 2)[0]);
    }

    private static boolean isPublicPath(String path) {
        return "/health".equals(path) || "/health/readiness".equals(path) || "/health/liveness".equals(path);
    }
}
