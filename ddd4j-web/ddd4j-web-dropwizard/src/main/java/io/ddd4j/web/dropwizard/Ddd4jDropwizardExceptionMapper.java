package io.ddd4j.web.dropwizard;

import io.ddd4j.web.core.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.WebError;
import io.ddd4j.web.core.WebExceptionTranslator;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * Dropwizard Jersey 全局异常映射。
 */
@Slf4j
public final class Ddd4jDropwizardExceptionMapper implements ExceptionMapper<Throwable> {

    private final WebExceptionTranslator translator = new DefaultWebExceptionTranslator();

    @Override
    public Response toResponse(Throwable exception) {
        WebError error = translator.translate(exception);
        if (error.status() >= 500) {
            log.error("Unhandled Dropwizard HTTP request failure", exception);
        }
        return Response.status(error.status()).entity(error.toResponse()).build();
    }
}
