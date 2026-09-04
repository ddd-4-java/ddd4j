package io.ddd4j.web.webmvc;

import io.ddd4j.core.api.R;
import io.ddd4j.web.core.error.WebError;
import io.ddd4j.web.core.error.WebExceptionTranslator;
import io.ddd4j.web.error.BaseErrorConfiguration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Objects;

/**
 * 将 WebMVC 异常统一翻译为真实 HTTP 状态和 {@link R} 响应体。
 *
 * <p>翻译、5xx 日志与响应体构造复用 {@link BaseErrorConfiguration}，此处只保留
 * Spring MVC 的 {@code @ExceptionHandler} 响应写出。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class Ddd4jWebMvcExceptionHandler extends BaseErrorConfiguration {

    public Ddd4jWebMvcExceptionHandler(WebExceptionTranslator exceptionTranslator) {
        super(Objects.requireNonNull(exceptionTranslator, "exceptionTranslator must not be null"));
    }

    @Override
    protected String frameworkName() {
        return "WebMVC";
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<R<Object>> handle(Throwable throwable) {
        WebError error = translate(throwable);
        logUnhandled(throwable, error);
        return ResponseEntity.status(error.status()).body(toResponse(error));
    }
}
