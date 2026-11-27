/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.sample.quarkus.shiro.config;

import io.ddd4j.core.api.R;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.sample.quarkus.shiro.CatalogTradeResource;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;

import java.io.IOException;
import java.util.Objects;

/**
 * 将 RFC 6750 Bearer Token 作为 Shiro Session ID 恢复为当前请求 Subject。
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class ShiroRequestFilter implements ContainerRequestFilter, ContainerResponseFilter {

    /**
     * 记录进入认证过滤器之前线程绑定的请求属性键。
     */
    private static final String PREVIOUS = ShiroRequestFilter.class.getName() + ".previous";
    /**
     * 从认证请求头提取令牌时识别的协议前缀。
     */
    private static final String BEARER_PREFIX = "Bearer ";
    /**
     * 读取已匹配 JAX-RS 资源和方法的注解信息。
     */
    @Context
    ResourceInfo resourceInfo;

    /**
     * 在 JAX-RS 调用资源方法之前装配并验证当前原生认证上下文。
     *
     * @param requestContext 当前 JAX-RS 请求上下文
     * @throws IOException 装配示例或处理当前 HTTP 请求失败
     */
    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        Subject previous = ThreadContext.getSubject();
        if (Objects.nonNull(previous)) {
            requestContext.setProperty(PREVIOUS, previous);
        }
        String authorization = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
        Subject.Builder builder = new Subject.Builder(SecurityUtils.getSecurityManager());
        if (StrKit.isNotEmpty(authorization) && authorization.regionMatches(
                true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            String token = authorization.substring(BEARER_PREFIX.length()).trim();
            if (StrKit.isNotEmpty(token)) {
                builder.sessionId(token);
            }
        }
        Subject subject = builder.buildSubject();
        ThreadContext.bind(subject);
        Runnable cleanup = () -> {
            if (ThreadContext.getSubject() == subject) {
                ThreadContext.unbindSubject();
                if (Objects.nonNull(previous)) {
                    ThreadContext.bind(previous);
                }
            }
        };
        requestContext.setProperty(PREVIOUS + ".cleanup", cleanup);
        ((org.jboss.resteasy.reactive.server.spi.ResteasyReactiveContainerRequestContext) requestContext)
                .getServerRequestContext().registerCompletionCallback(failure -> cleanup.run());
        if (Objects.nonNull(resourceInfo.getResourceClass())
                && CatalogTradeResource.class.isAssignableFrom(resourceInfo.getResourceClass())) {
            if (!subject.isAuthenticated()) {
                requestContext.abortWith(Response.status(401).entity(R.of(401, "unauthenticated")).build());
                return;
            }
            String method = resourceInfo.getResourceMethod().getName();
            String permission = "cancelOrder".equals(method) ? "order:cancel" : "order:pay";
            if ("POST".equals(requestContext.getMethod()) && !subject.isPermitted(permission)) {
                requestContext.abortWith(Response.status(403).entity(R.of(403, "required permission is missing")).build());
            }
        }
    }

    /**
     * 在 JAX-RS 生成响应后清理当前请求的原生认证绑定。
     *
     * @param requestContext  当前 JAX-RS 请求上下文
     * @param responseContext 当前 JAX-RS 响应上下文
     * @throws IOException 装配示例或处理当前 HTTP 请求失败
     */
    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        Runnable cleanup = (Runnable) requestContext.getProperty(PREVIOUS + ".cleanup");
        if (Objects.nonNull(cleanup)) {
            cleanup.run();
        }
    }
}
