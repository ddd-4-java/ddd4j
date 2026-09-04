package io.ddd4j.web.error;

import io.ddd4j.core.api.R;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.web.core.error.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.error.WebError;
import io.ddd4j.web.core.error.WebExceptionTranslator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * 各 Web 框架统一异常处理的抽象基类（模板方法）。
 *
 * <p>收敛各框架适配器重复的异常处理管线：
 * <ol>
 *     <li>持有 {@link WebExceptionTranslator}（默认 {@link DefaultWebExceptionTranslator}），
 *         将 {@code BizRuntimeException} 等通用异常翻译为带 HTTP 状态的 {@link WebError}；</li>
 *     <li>翻译结果经 {@link WebErrorResponseBuilder} 统一转换为 {@link R} 响应体；</li>
 *     <li>status &gt;= 500 时按未处理错误记录 error 日志。</li>
 * </ol>
 *
 * <p>子类只保留框架特定逻辑：响应写出方式（Spring {@code ResponseEntity}、JAX-RS
 * {@code Response}、Micronaut {@code HttpResponse} 等）与框架特定异常的预处理
 * （覆盖 {@link #doTranslate(Throwable)}）。
 */
public abstract class BaseErrorConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(BaseErrorConfiguration.class);

    private final WebExceptionTranslator translator;
    private final WebErrorResponseBuilder responseBuilder;

    protected BaseErrorConfiguration() {
        this(new DefaultWebExceptionTranslator());
    }

    protected BaseErrorConfiguration(WebExceptionTranslator translator) {
        this(translator, WebErrorResponseBuilder.defaults());
    }

    protected BaseErrorConfiguration(WebExceptionTranslator translator, WebErrorResponseBuilder responseBuilder) {
        this.translator = Objects.requireNonNull(translator, "translator must not be null");
        this.responseBuilder = Objects.requireNonNull(responseBuilder, "responseBuilder must not be null");
    }

    /**
     * 统一异常翻译入口：框架特定异常归一后交给 {@link WebExceptionTranslator} 翻译。
     */
    public final WebError translate(Throwable throwable) {
        return doTranslate(throwable);
    }

    /**
     * 策略翻译，子类可覆盖以先归一框架特定异常（如 JAX-RS {@code WebApplicationException}），
     * 再委托 {@code super.doTranslate} 走通用翻译。
     */
    protected WebError doTranslate(Throwable throwable) {
        return translator.translate(throwable);
    }

    /**
     * 统一 {@link R} 响应体。
     */
    public final R<Object> toResponse(WebError error) {
        return responseBuilder.toResponse(error);
    }

    /**
     * status &gt;= 500 视为未处理的服务端错误。
     */
    protected final boolean isServerError(WebError error) {
        return responseBuilder.isServerError(error);
    }

    /**
     * JSON 序列化失败时的兜底响应体。
     */
    protected final byte[] fallbackBody() {
        return responseBuilder.fallbackBody();
    }

    /**
     * 统一的未处理错误日志：status &gt;= 500 时按 error 级别记录，子类在写响应前调用。
     */
    protected void logUnhandled(Throwable throwable, WebError error) {
        if (isServerError(error)) {
            LOG.error("Unhandled {} request failure", frameworkName(), throwable);
        }
    }

    /**
     * 框架已给出 HTTP 状态时的兜底错误表示：消息为空时回退到状态短语。
     */
    protected static WebError httpStatusError(int status, String fallbackMessage, String message) {
        return new WebError(status, status, StrKit.isBlank(message) ? fallbackMessage : message, null);
    }

    /**
     * 框架名，用于未处理错误日志定位。
     */
    protected abstract String frameworkName();
}
