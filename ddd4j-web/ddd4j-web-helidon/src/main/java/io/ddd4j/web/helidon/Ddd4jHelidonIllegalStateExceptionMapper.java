package io.ddd4j.web.helidon;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * 覆盖 Jersey 的默认 IllegalStateException 映射，保持 ddd4j 的 409 语义。
 */
@Provider
@Priority(Priorities.USER)
public final class Ddd4jHelidonIllegalStateExceptionMapper implements ExceptionMapper<IllegalStateException> {

    private final Ddd4jHelidonExceptionMapper delegate = new Ddd4jHelidonExceptionMapper();

    @Override
    public Response toResponse(IllegalStateException exception) {
        return delegate.toResponse(exception);
    }
}
