package io.ddd4j.web.quarkus;

import io.ddd4j.web.core.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.WebError;
import io.ddd4j.web.core.WebExceptionTranslator;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

/** Maps Quarkus request failures to the shared ddd4j HTTP error contract. */
@Provider
@Slf4j
public class DefaultExceptionHandler implements ExceptionMapper<Exception> {

    private final WebExceptionTranslator translator = new DefaultWebExceptionTranslator();

    @Override
    public Response toResponse(Exception exception) {
        WebError error = translator.translate(exception);
        if (error.status() >= 500) {
            log.error("Unhandled Quarkus request failure", exception);
        }
        return Response.status(error.status()).entity(error.toResponse()).build();
    }
}
