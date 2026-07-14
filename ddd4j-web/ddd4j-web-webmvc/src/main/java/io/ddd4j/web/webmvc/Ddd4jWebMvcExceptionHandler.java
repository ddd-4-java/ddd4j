package io.ddd4j.web.webmvc;

import io.ddd4j.core.api.R;
import io.ddd4j.web.core.WebError;
import io.ddd4j.web.core.WebExceptionTranslator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Objects;

/**
 * 将 WebMVC 异常统一翻译为真实 HTTP 状态和 {@link R} 响应体。
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class Ddd4jWebMvcExceptionHandler {

    private final WebExceptionTranslator exceptionTranslator;

    public Ddd4jWebMvcExceptionHandler(WebExceptionTranslator exceptionTranslator) {
        this.exceptionTranslator = Objects.requireNonNull(exceptionTranslator,
                "exceptionTranslator must not be null");
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<R<Object>> handle(Throwable throwable) {
        WebError error = exceptionTranslator.translate(throwable);
        if (error.status() >= 500) {
            log.error("Unhandled WebMVC request failure", throwable);
        }
        return ResponseEntity.status(error.status()).body(error.toResponse());
    }
}
