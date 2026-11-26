package io.ddd4j.web.dropwizard;

import io.ddd4j.web.core.error.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.error.WebError;
import io.ddd4j.web.core.error.WebExceptionTranslator;
import io.ddd4j.kit.lang.StrKit;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * Dropwizard Jersey 全局异常映射。
 */
@Slf4j
public final class Ddd4jDropwizardExceptionMapper implements ExceptionMapper<Throwable> {

    private final WebExceptionTranslator translator;

    public Ddd4jDropwizardExceptionMapper() {
        this(new DefaultWebExceptionTranslator());
    }

    public Ddd4jDropwizardExceptionMapper(WebExceptionTranslator translator) {
        this.translator = Objects.requireNonNull(translator, "translator must not be null");
    }

    @Override
    public Response toResponse(Throwable exception) {
        WebError error = translate(exception);
        if (error.status() >= 500) {
            log.error("Unhandled Dropwizard HTTP request failure", exception);
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

    private WebError translate(Throwable exception) {
        if (exception instanceof WebApplicationException webException) {
            int status = webException.getResponse().getStatus();
            String message = StrKit.isBlank(webException.getMessage())
                    ? webException.getResponse().getStatusInfo().getReasonPhrase() : webException.getMessage();
            return new WebError(status, status, message, null);
        }
        return translator.translate(exception);
    }
}
