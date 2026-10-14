package io.ddd4j.web.quarkus;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;

/**
 * Quarkus 1.x RESTEasy 响应过滤器，完成请求上下文和追踪资源的清理。
 */
@Provider
@ApplicationScoped
@Priority(Priorities.USER)
public class Ddd4jQuarkusWebResponseFilter implements ContainerResponseFilter {

    @Inject
    Ddd4jQuarkusWebFilter webFilter;

    /** 供 Quarkus 1.x RESTEasy Provider 创建响应过滤器。 */
    public Ddd4jQuarkusWebResponseFilter() {
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        webFilter.response(request, response);
    }
}
