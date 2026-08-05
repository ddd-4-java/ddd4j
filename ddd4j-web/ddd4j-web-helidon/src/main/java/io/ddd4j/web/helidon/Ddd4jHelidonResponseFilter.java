package io.ddd4j.web.helidon;

import io.ddd4j.web.core.context.SynchronousWebRequestSession;
import io.ddd4j.web.core.context.WebHeaders;
import io.ddd4j.web.core.context.WebRequestContext;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

/**
 * 回传请求标识，并按响应状态提交或释放幂等请求会话。
 */
@Provider
@Priority(Priorities.USER)
public final class Ddd4jHelidonResponseFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        Object contextValue = request.getProperty(Ddd4jHelidonRequestFilter.CONTEXT_PROPERTY);
        if (contextValue instanceof WebRequestContext context) {
            response.getHeaders().putSingle(WebHeaders.REQUEST_ID, context.requestId());
            response.getHeaders().putSingle(WebHeaders.TRACE_ID, context.traceId());
        }
        Object sessionValue = request.getProperty(Ddd4jHelidonRequestFilter.SESSION_PROPERTY);
        if (sessionValue instanceof SynchronousWebRequestSession session) {
            session.complete(response.getStatus() < 400);
        }
        request.removeProperty(Ddd4jHelidonRequestFilter.CONTEXT_PROPERTY);
        request.removeProperty(Ddd4jHelidonRequestFilter.SESSION_PROPERTY);
    }
}
