package io.ddd4j.web.dropwizard;

import io.ddd4j.web.core.SynchronousWebRequestSession;
import io.ddd4j.web.core.WebHeaders;
import io.ddd4j.web.core.WebRequestContext;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;

/**
 * 回传请求标识，并按响应状态提交或释放幂等请求会话。
 */
public final class Ddd4jDropwizardResponseFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        Object contextValue = request.getProperty(Ddd4jDropwizardRequestFilter.CONTEXT_PROPERTY);
        if (contextValue instanceof WebRequestContext context) {
            response.getHeaders().putSingle(WebHeaders.REQUEST_ID, context.requestId());
            response.getHeaders().putSingle(WebHeaders.TRACE_ID, context.traceId());
        }
        Object sessionValue = request.getProperty(Ddd4jDropwizardRequestFilter.SESSION_PROPERTY);
        if (sessionValue instanceof SynchronousWebRequestSession session) {
            session.complete(response.getStatus() < 400);
        }
        request.removeProperty(Ddd4jDropwizardRequestFilter.CONTEXT_PROPERTY);
        request.removeProperty(Ddd4jDropwizardRequestFilter.SESSION_PROPERTY);
    }
}
