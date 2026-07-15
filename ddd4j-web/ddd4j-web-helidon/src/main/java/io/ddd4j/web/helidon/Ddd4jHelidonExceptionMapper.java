package io.ddd4j.web.helidon;

import io.ddd4j.web.core.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.WebError;
import io.ddd4j.web.core.WebExceptionTranslator;
import io.ddd4j.kit.lang.StrKit;
import jakarta.ws.rs.WebApplicationException;
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

    private final WebExceptionTranslator translator;

    public Ddd4jHelidonExceptionMapper() {
        this(new DefaultWebExceptionTranslator());
    }

    public Ddd4jHelidonExceptionMapper(WebExceptionTranslator translator) {
        this.translator = Objects.requireNonNull(translator, "translator must not be null");
    }

    @Override
    public Response toResponse(RuntimeException exception) {
        WebError error = translate(exception);
        if (error.status() >= 500) {
            log.error("Unhandled Helidon HTTP request failure", exception);
        }
        return Response.status(error.status()).type(MediaType.APPLICATION_JSON_TYPE)
                .entity(error.toResponse()).build();
    }

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
