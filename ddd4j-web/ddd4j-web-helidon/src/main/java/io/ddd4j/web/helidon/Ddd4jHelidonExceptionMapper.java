package io.ddd4j.web.helidon;

import io.ddd4j.web.core.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.WebError;
import io.ddd4j.web.core.WebExceptionTranslator;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

/**
 * Helidon MP 全局异常映射。
 */
@Slf4j
@Provider
public final class Ddd4jHelidonExceptionMapper implements ExceptionMapper<Throwable> {

    private final WebExceptionTranslator translator = new DefaultWebExceptionTranslator();

    @Override
    public Response toResponse(Throwable exception) {
        WebError error = translator.translate(exception);
        if (error.status() >= 500) {
            log.error("Unhandled Helidon HTTP request failure", exception);
        }
        return Response.status(error.status()).entity(error.toResponse()).build();
    }
}
