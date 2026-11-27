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

    /** 保存 <code>translator</code> 对应的 <code>WebExceptionTranslator</code> 数据。 */
    private final WebExceptionTranslator translator;

    /** \u4f7f\u7528\u9ed8\u8ba4\u5f02\u5e38\u7ffb\u8bd1\u5668\u521b\u5efa\u5904\u7406\u5668\u3002 */
    @Inject
    public Ddd4jMicronautExceptionHandler() {
        this(new DefaultWebExceptionTranslator());
    }

    /**
     * \u4f7f\u7528\u6307\u5b9a\u5f02\u5e38\u7ffb\u8bd1\u5668\u521b\u5efa\u5904\u7406\u5668\u3002
     * @param translator \u5f02\u5e38\u7ffb\u8bd1\u5668
     */
    public Ddd4jMicronautExceptionHandler(WebExceptionTranslator translator) {
        this.translator = Objects.requireNonNull(translator, "translator must not be null");
    }

    /**
     * \u5c06 Micronaut \u8bf7\u6c42\u5f02\u5e38\u8f6c\u6362\u4e3a\u5e26\u72b6\u6001\u548c\u6b63\u6587\u7684\u54cd\u5e94\u3002
     * @param request \u5f53\u524d\u8fdb\u5165\u8c03\u7528\u94fe\u7684 HTTP \u8bf7\u6c42
     * @param exception \u5f53\u524d\u8bf7\u6c42\u5904\u7406\u65f6\u629b\u51fa\u7684\u5f02\u5e38
     * @return \u7edf\u4e00\u7684 Micronaut HTTP \u9519\u8bef\u54cd\u5e94
     */
    @Override
    public HttpResponse<?> handle(HttpRequest request, Throwable exception) {
        WebError error = translator.translate(exception);
        if (error.status() >= 500) {
            log.error("Unhandled HTTP request failure: {} {}", request.getMethodName(), request.getPath(), exception);
        }
        return HttpResponse.status(error.status(), error.message()).body(error.toResponse());
    }
}
