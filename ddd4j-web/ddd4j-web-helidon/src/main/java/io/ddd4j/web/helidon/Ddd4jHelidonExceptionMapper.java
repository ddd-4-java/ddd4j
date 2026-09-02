package io.ddd4j.web.helidon;

import io.ddd4j.web.core.error.WebError;
import io.ddd4j.web.core.error.WebExceptionTranslator;
import io.ddd4j.web.error.BaseErrorConfiguration;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Helidon MP 全局异常映射。
 *
 * <p>翻译、5xx 日志与响应体构造复用 {@link BaseErrorConfiguration}，
 * 此处只保留 JAX-RS 的 {@code Response} 写出与 {@link WebApplicationException} 归一。
 */
@Provider
public final class Ddd4jHelidonExceptionMapper extends BaseErrorConfiguration implements ExceptionMapper<RuntimeException> {

    public Ddd4jHelidonExceptionMapper() {
        super();
    }

    public Ddd4jHelidonExceptionMapper(WebExceptionTranslator translator) {
        super(translator);
    }

    @Override
    protected String frameworkName() {
        return "Helidon";
    }

    /**
     * 先归一 JAX-RS {@link WebApplicationException}（保留容器给出的状态与消息），
     * 其余异常走 ddd4j 通用翻译。
     */
    @Override
    protected WebError doTranslate(Throwable exception) {
        if (exception instanceof WebApplicationException webException) {
            return httpStatusError(webException.getResponse().getStatus(),
                    webException.getResponse().getStatusInfo().getReasonPhrase(), webException.getMessage());
        }
        return super.doTranslate(exception);
    }

    @Override
    public Response toResponse(RuntimeException exception) {
        WebError error = translate(exception);
        logUnhandled(exception, error);
        return Response.status(error.status()).type(MediaType.APPLICATION_JSON_TYPE)
                .entity(toResponse(error)).build();
    }
}
