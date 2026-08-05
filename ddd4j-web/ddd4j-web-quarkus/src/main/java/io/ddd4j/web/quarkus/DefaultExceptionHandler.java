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

/** Maps Quarkus request failures to the shared ddd4j HTTP error contract. */
@Provider
@Slf4j
public class DefaultExceptionHandler implements ExceptionMapper<RuntimeException> {

    private final WebExceptionTranslator translator;

    public DefaultExceptionHandler() {
        this(new DefaultWebExceptionTranslator());
    }

    public DefaultExceptionHandler(WebExceptionTranslator translator) {
        this.translator = Objects.requireNonNull(translator, "translator must not be null");
    }

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
