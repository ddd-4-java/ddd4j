package io.ddd4j.web.dropwizard;

import io.ddd4j.web.core.WebContextScope;
import io.ddd4j.web.core.WebHeaders;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;

import java.util.Objects;

/**
 * 回传 requestId 并关闭 Dropwizard 请求上下文。
 */
public final class Ddd4jDropwizardResponseFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        Object requestId = request.getProperty(Ddd4jDropwizardRequestFilter.REQUEST_ID_PROPERTY);
        if (Objects.nonNull(requestId)) {
            response.getHeaders().putSingle(WebHeaders.REQUEST_ID, requestId);
        }
        Object scope = request.getProperty(Ddd4jDropwizardRequestFilter.SCOPE_PROPERTY);
        if (scope instanceof WebContextScope contextScope) {
            contextScope.close();
        }
    }
}
