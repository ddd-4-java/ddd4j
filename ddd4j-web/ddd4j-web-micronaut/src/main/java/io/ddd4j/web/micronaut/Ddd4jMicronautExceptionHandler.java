package io.ddd4j.web.micronaut;

import io.ddd4j.web.core.error.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.error.WebError;
import io.ddd4j.web.core.error.WebExceptionTranslator;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * Micronaut 全局异常到 ddd4j R 响应的映射。
 */
@Slf4j
@Singleton
public final class Ddd4jMicronautExceptionHandler implements ExceptionHandler<Throwable, HttpResponse<?>> {

    private final WebExceptionTranslator translator;

    @Inject
    public Ddd4jMicronautExceptionHandler() {
        this(new DefaultWebExceptionTranslator());
    }

    public Ddd4jMicronautExceptionHandler(WebExceptionTranslator translator) {
        this.translator = Objects.requireNonNull(translator, "translator must not be null");
    }

    @Override
    public HttpResponse<?> handle(HttpRequest request, Throwable exception) {
        WebError error = translator.translate(exception);
        if (error.status() >= 500) {
            log.error("Unhandled HTTP request failure: {} {}", request.getMethodName(), request.getPath(), exception);
        }
        return HttpResponse.status(error.status(), error.message()).body(error.toResponse());
    }
}
