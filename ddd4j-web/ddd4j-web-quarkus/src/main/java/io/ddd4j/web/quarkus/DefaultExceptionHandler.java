package io.ddd4j.web.quarkus;

import io.ddd4j.web.core.error.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.error.WebError;
import io.ddd4j.web.core.error.WebExceptionTranslator;
import io.ddd4j.kit.lang.StrKit;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * Maps Quarkus request failures to the shared ddd4j HTTP error contract.
 */
@Provider
@Slf4j
public class DefaultExceptionHandler implements ExceptionMapper<RuntimeException> {

    /** 保存 <code>translator</code> 对应的 <code>WebExceptionTranslator</code> 数据。 */
    private final WebExceptionTranslator translator;

    /** \u4f7f\u7528\u9ed8\u8ba4\u5f02\u5e38\u7ffb\u8bd1\u5668\u521b\u5efa\u5904\u7406\u5668\u3002 */
    public DefaultExceptionHandler() {
        this(new DefaultWebExceptionTranslator());
    }

    /**
     * \u4f7f\u7528\u6307\u5b9a\u5f02\u5e38\u7ffb\u8bd1\u5668\u521b\u5efa\u5904\u7406\u5668\u3002
     * @param translator \u5f02\u5e38\u7ffb\u8bd1\u5668
     */
    public DefaultExceptionHandler(WebExceptionTranslator translator) {
        this.translator = Objects.requireNonNull(translator, "translator must not be null");
    }

    /**
     * \u5c06\u8fd0\u884c\u65f6\u5f02\u5e38\u6620\u5c04\u4e3a Quarkus \u53ef\u8fd4\u56de\u7684 HTTP \u9519\u8bef\u3002
     * @param exception \u5f53\u524d\u8bf7\u6c42\u5904\u7406\u65f6\u629b\u51fa\u7684\u5f02\u5e38
     * @return \u6846\u67b6\u54cd\u5e94\u4e2d\u7684\u72b6\u6001\u548c\u9519\u8bef\u6b63\u6587
     */
    @Override
    public Response toResponse(RuntimeException exception) {
        WebError error = translate(exception);
        if (error.status() >= 500) {
            log.error("Unhandled Quarkus request failure", exception);
        }
        return Response.status(error.status())
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(error.toResponse())
                .build();
    }

    /**
     * 执行 <code>translate</code>，调用 <code>webException.getResponse</code>、<code>StrKit.isBlank</code>、<code>webException.getMessage</code>。
     *
     * @param exception 传入此操作的 <code>RuntimeException</code> 类型 <code>exception</code>
     * @return 返回表达式 <code>translator.translate(exception)</code>
     */
    private WebError translate(RuntimeException exception) {
        if (exception instanceof WebApplicationException webException) {
            int status = webException.getResponse().getStatus();
            String message = StrKit.isBlank(webException.getMessage())
                    ? webException.getResponse().getStatusInfo().getReasonPhrase() : webException.getMessage();
            return new WebError(status, status, message, null);
        }
        return translator.translate(exception);
    }
}
