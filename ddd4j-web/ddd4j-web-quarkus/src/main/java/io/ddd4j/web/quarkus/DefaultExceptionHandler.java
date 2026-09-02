package io.ddd4j.web.quarkus;

import io.ddd4j.web.core.error.WebError;
import io.ddd4j.web.core.error.WebExceptionTranslator;
import io.ddd4j.web.error.BaseErrorConfiguration;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Maps Quarkus request failures to the shared ddd4j HTTP error contract. */
@Provider
public class DefaultExceptionHandler extends BaseErrorConfiguration implements ExceptionMapper<RuntimeException> {

    public DefaultExceptionHandler() {
        super();
    }

    public DefaultExceptionHandler(WebExceptionTranslator translator) {
        super(translator);
    }

    @Override
    protected String frameworkName() {
        return "Quarkus";
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
        return Response.status(error.status())
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(toResponse(error))
                .build();
    }
}
