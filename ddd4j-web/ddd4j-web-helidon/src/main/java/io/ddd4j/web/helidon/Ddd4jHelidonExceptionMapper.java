package io.ddd4j.web.helidon;

import io.ddd4j.web.core.error.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.error.WebError;
import io.ddd4j.web.core.error.WebExceptionTranslator;
import io.ddd4j.kit.lang.StrKit;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * Helidon MP 全局异常映射。
 */
@Slf4j
@Provider
public final class Ddd4jHelidonExceptionMapper implements ExceptionMapper<RuntimeException> {

    /** 保存 <code>translator</code> 对应的 <code>WebExceptionTranslator</code> 数据。 */
    private final WebExceptionTranslator translator;

    /** \u4f7f\u7528\u9ed8\u8ba4\u5f02\u5e38\u7ffb\u8bd1\u5668\u521b\u5efa\u6620\u5c04\u5668\u3002 */
    public Ddd4jHelidonExceptionMapper() {
        this(new DefaultWebExceptionTranslator());
    }

    /**
     * \u4f7f\u7528\u6307\u5b9a\u5f02\u5e38\u7ffb\u8bd1\u5668\u521b\u5efa\u6620\u5c04\u5668\u3002
     * @param translator \u5f02\u5e38\u7ffb\u8bd1\u5668
     */
    public Ddd4jHelidonExceptionMapper(WebExceptionTranslator translator) {
        this.translator = Objects.requireNonNull(translator, "translator must not be null");
    }

    /**
     * \u5c06\u6846\u67b6\u5f02\u5e38\u8f6c\u6362\u6210\u5e26 HTTP \u72b6\u6001\u7801\u548c JSON \u6b63\u6587\u7684\u5171\u7528\u9519\u8bef\u54cd\u5e94\u3002
     * @param exception \u5f53\u524d HTTP \u8bf7\u6c42\u629b\u51fa\u7684\u5f02\u5e38
     * @return \u63d0\u4ea4\u7ed9\u8fd0\u884c\u6846\u67b6\u7684\u5f02\u5e38\u54cd\u5e94
     */
    @Override
    public Response toResponse(RuntimeException exception) {
        WebError error = translate(exception);
        if (error.status() >= 500) {
            log.error("Unhandled Helidon HTTP request failure", exception);
        }
        Response.ResponseBuilder response = Response.status(error.status())
                .type(MediaType.APPLICATION_JSON_TYPE).entity(error.toResponse());
        if (error.status() == Response.Status.METHOD_NOT_ALLOWED.getStatusCode()
                && exception instanceof WebApplicationException webException) {
            var allowedMethods = webException.getResponse().getHeaders().get(HttpHeaders.ALLOW);
            if (Objects.nonNull(allowedMethods)) {
                allowedMethods.forEach(value -> response.header(HttpHeaders.ALLOW, value));
            }
        }
        return response.build();
    }

    /**
     * 执行 <code>translate</code>，调用 <code>webException.getResponse</code>、<code>StrKit.isBlank</code>、<code>webException.getMessage</code>。
     *
     * @param exception 传入此操作的 <code>Throwable</code> 类型 <code>exception</code>
     * @return 返回表达式 <code>translator.translate(exception)</code>
     */
    WebError translate(Throwable exception) {
        if (exception instanceof WebApplicationException webException) {
            int status = webException.getResponse().getStatus();
            String message = StrKit.isBlank(webException.getMessage())
                    ? webException.getResponse().getStatusInfo().getReasonPhrase() : webException.getMessage();
            return new WebError(status, status, message, null);
        }
        return translator.translate(exception);
    }
}
