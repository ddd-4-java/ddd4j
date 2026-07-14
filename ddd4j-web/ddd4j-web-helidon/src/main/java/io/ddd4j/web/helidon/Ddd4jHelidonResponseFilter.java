package io.ddd4j.web.helidon;

import io.ddd4j.web.core.WebContextScope;
import io.ddd4j.web.core.WebHeaders;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

import java.util.Objects;

/**
 * 回传 requestId 并保证请求上下文关闭。
 */
@Provider
@Priority(Priorities.USER)
public final class Ddd4jHelidonResponseFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        Object requestId = request.getProperty(Ddd4jHelidonRequestFilter.REQUEST_ID_PROPERTY);
        if (Objects.nonNull(requestId)) {
            response.getHeaders().putSingle(WebHeaders.REQUEST_ID, requestId);
        }
        Object scope = request.getProperty(Ddd4jHelidonRequestFilter.SCOPE_PROPERTY);
        if (scope instanceof WebContextScope contextScope) {
            contextScope.close();
        }
    }
}
