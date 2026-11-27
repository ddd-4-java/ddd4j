package io.ddd4j.web.webmvc;

import io.ddd4j.core.api.R;
import io.ddd4j.web.core.error.WebError;
import io.ddd4j.web.core.error.WebExceptionTranslator;
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

    /** 保存 <code>exception Translator</code> 对应的 <code>WebExceptionTranslator</code> 数据。 */
    private final WebExceptionTranslator exceptionTranslator;

    /**
     * \u521b\u5efa Ddd4jWebMvcExceptionHandler \u5b9e\u4f8b\u3002
     * @param exceptionTranslator \u5f02\u5e38\u8f6c\u6362\u5668
     */
    public Ddd4jWebMvcExceptionHandler(WebExceptionTranslator exceptionTranslator) {
        this.exceptionTranslator = Objects.requireNonNull(exceptionTranslator,
                "exceptionTranslator must not be null");
    }

    /**
     * \u5c06\u5f02\u5e38\u8f6c\u6362\u4e3a HTTP \u54cd\u5e94\u3002
     * @param throwable \u6355\u83b7\u7684\u5f02\u5e38
     * @return HTTP \u9519\u8bef\u54cd\u5e94
     */
    @ExceptionHandler(Throwable.class)
    public ResponseEntity<R<Object>> handle(Throwable throwable) {
        WebError error = exceptionTranslator.translate(throwable);
        if (error.status() >= 500) {
            log.error("Unhandled WebMVC request failure", throwable);
        }
        return ResponseEntity.status(error.status()).body(error.toResponse());
    }
}
