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

    /** \u8bb0\u5f55\u8fdb\u5165\u8ba4\u8bc1\u8fc7\u6ee4\u5668\u4e4b\u524d\u7ebf\u7a0b\u7ed1\u5b9a\u7684\u8bf7\u6c42\u5c5e\u6027\u952e\u3002 */
    private static final String PREVIOUS = ShiroRequestFilter.class.getName() + ".previous";
    /** \u4ece\u8ba4\u8bc1\u8bf7\u6c42\u5934\u63d0\u53d6\u4ee4\u724c\u65f6\u8bc6\u522b\u7684\u534f\u8bae\u524d\u7f00\u3002 */
    private static final String BEARER_PREFIX = "Bearer ";
    /** \u8bfb\u53d6\u5df2\u5339\u914d JAX-RS \u8d44\u6e90\u548c\u65b9\u6cd5\u7684\u6ce8\u89e3\u4fe1\u606f\u3002 */
    @Context
    ResourceInfo resourceInfo;

    /**
     * \u5728 JAX-RS \u8c03\u7528\u8d44\u6e90\u65b9\u6cd5\u4e4b\u524d\u88c5\u914d\u5e76\u9a8c\u8bc1\u5f53\u524d\u539f\u751f\u8ba4\u8bc1\u4e0a\u4e0b\u6587\u3002
     * @param requestContext \u5f53\u524d JAX-RS \u8bf7\u6c42\u4e0a\u4e0b\u6587
     * @throws IOException \u88c5\u914d\u793a\u4f8b\u6216\u5904\u7406\u5f53\u524d HTTP \u8bf7\u6c42\u5931\u8d25
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
                requestContext.abortWith(Response.status(401).entity(R.fail(401, "unauthenticated")).build());
                return;
            }
            String method = resourceInfo.getResourceMethod().getName();
            String permission = "cancelOrder".equals(method) ? "order:cancel" : "order:pay";
            if ("POST".equals(requestContext.getMethod()) && !subject.isPermitted(permission)) {
                requestContext.abortWith(Response.status(403).entity(R.fail(403, "required permission is missing")).build());
            }
        }
    }

    /**
     * \u5728 JAX-RS \u751f\u6210\u54cd\u5e94\u540e\u6e05\u7406\u5f53\u524d\u8bf7\u6c42\u7684\u539f\u751f\u8ba4\u8bc1\u7ed1\u5b9a\u3002
     * @param requestContext \u5f53\u524d JAX-RS \u8bf7\u6c42\u4e0a\u4e0b\u6587
     * @param responseContext \u5f53\u524d JAX-RS \u54cd\u5e94\u4e0a\u4e0b\u6587
     * @throws IOException \u88c5\u914d\u793a\u4f8b\u6216\u5904\u7406\u5f53\u524d HTTP \u8bf7\u6c42\u5931\u8d25
     */
    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        Runnable cleanup = (Runnable) requestContext.getProperty(PREVIOUS + ".cleanup");
        if (Objects.nonNull(cleanup)) {
            cleanup.run();
        }
    }
}
