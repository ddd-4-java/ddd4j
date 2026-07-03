package io.ddd4j.web.quarkus;

import io.vertx.core.http.HttpServerRequest;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * 全局请求日志过滤器：记录客户端地址、HTTP 方法、路径与租户 ID，写入名为 {@code access} 的日志。
 * <p>
 * 对标 ddd4j-web 的 {@code LogWebInterceptor}（Spring HandlerInterceptor），
 * Quarkus 轨道采用 JAX-RS {@link ContainerRequestFilter} 方案。
 * </p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Provider
@Slf4j(topic = "access")
public class AccessLogFilter implements ContainerRequestFilter {

    /** Vert.x HTTP 服务端请求（由 JAX-RS 注入） */
    @Context
    HttpServerRequest request;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String method = requestContext.getMethod();
        String path = requestContext.getUriInfo().getPath();
        String tenantId = WebUtils.getTenantId(request);
        String address = request.remoteAddress().hostAddress();

        if ("POST".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method) || "GET".equalsIgnoreCase(method)) {
            log.info("{}:{} {} tenantId={}", address, method, path, tenantId);
        }
    }
}
