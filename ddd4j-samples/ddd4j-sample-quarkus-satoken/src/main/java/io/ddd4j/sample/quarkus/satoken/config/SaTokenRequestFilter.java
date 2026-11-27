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
package io.ddd4j.sample.quarkus.satoken.config;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.context.mock.SaRequestForMock;
import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import cn.dev33.satoken.context.model.SaTokenContextModelBox;
import cn.dev33.satoken.stp.StpUtil;
import io.ddd4j.core.api.R;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.sample.quarkus.satoken.CatalogTradeResource;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;
import java.lang.reflect.AnnotatedElement;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Quarkus REST 与 Sa-Token 的请求级桥接。
 *
 * <p>Sa-Token Core 本身不感知 JAX-RS 请求。该过滤器为每个同步 REST 请求建立并清理
 * Sa-Token 上下文，将标准 Bearer Token 映射到 Sa-Token token header，并执行资源方法上的
 * {@code @SaCheckLogin}/{@code @SaCheckRole}/{@code @SaCheckPermission} 契约。
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class SaTokenRequestFilter implements ContainerRequestFilter, ContainerResponseFilter {

    /** \u8bb0\u5f55\u8fdb\u5165\u8ba4\u8bc1\u8fc7\u6ee4\u5668\u4e4b\u524d\u7ebf\u7a0b\u7ed1\u5b9a\u7684\u8bf7\u6c42\u5c5e\u6027\u952e\u3002 */
    private static final String PREVIOUS = SaTokenRequestFilter.class.getName() + ".previous";
    /** \u4ece\u8ba4\u8bc1\u8bf7\u6c42\u5934\u63d0\u53d6\u4ee4\u724c\u65f6\u8bc6\u522b\u7684\u534f\u8bae\u524d\u7f00\u3002 */
    private static final String BEARER_PREFIX = "Bearer ";

    /** \u8bfb\u53d6\u5df2\u5339\u914d JAX-RS \u8d44\u6e90\u548c\u65b9\u6cd5\u7684\u6ce8\u89e3\u4fe1\u606f\u3002 */
    @Context
    ResourceInfo resourceInfo;

    /**
     * \u4ece\u5f53\u524d\u88ab\u68c0\u67e5\u5143\u7d20\u8bfb\u53d6\u540c\u7c7b\u9274\u6743\u6ce8\u89e3\u3002
     * @param <A> \u5f53\u524d\u793a\u4f8b\u6240\u4f7f\u7528\u7684\u6570\u636e\u6216\u6846\u67b6\u7c7b\u578b
     * @param element \u5f53\u524d\u88ab\u68c0\u67e5\u6ce8\u89e3\u7684\u8d44\u6e90\u65b9\u6cd5\u6216\u7c7b
     * @param type \u9700\u8981\u67e5\u627e\u7684\u539f\u751f\u9274\u6743\u6ce8\u89e3\u7c7b\u578b
     * @return \u7c7b\u578b\u5339\u914d\u7684\u6ce8\u89e3\uff0c\u7f3a\u5c11\u65f6\u4e3a\u7a7a
     */
    private static <A extends java.lang.annotation.Annotation> A getAnnotation(AnnotatedElement element, Class<A> type) {
        return Objects.nonNull(element) ? element.getAnnotation(type) : null;
    }

    /**
     * \u5c06\u8ba4\u8bc1\u5931\u8d25\u72b6\u6001\u548c\u8bf4\u660e\u7ec4\u88c5\u4e3a\u793a\u4f8b\u9519\u8bef\u54cd\u5e94\u3002
     * @param status \u5546\u54c1\u72b6\u6001\u6761\u4ef6\u6587\u672c
     * @param message \u4f9b\u8c03\u7528\u65b9\u8bca\u65ad\u62d2\u7edd\u539f\u56e0\u7684\u54cd\u5e94\u6b63\u6587
     * @return \u5f53\u524d\u8bf7\u6c42\u7684\u9519\u8bef HTTP \u54cd\u5e94
     */
    private static Response failure(Response.Status status, String message) {
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(R.fail(status.getStatusCode(), message))
                .build();
    }

    /**
     * \u5728 JAX-RS \u8c03\u7528\u8d44\u6e90\u65b9\u6cd5\u4e4b\u524d\u88c5\u914d\u5e76\u9a8c\u8bc1\u5f53\u524d\u539f\u751f\u8ba4\u8bc1\u4e0a\u4e0b\u6587\u3002
     * @param requestContext \u5f53\u524d JAX-RS \u8bf7\u6c42\u4e0a\u4e0b\u6587
     * @throws IOException \u88c5\u914d\u793a\u4f8b\u6216\u5904\u7406\u5f53\u524d HTTP \u8bf7\u6c42\u5931\u8d25
     */
    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        var context = SaManager.getSaTokenContext();
        if (context.isValid()) {
            requestContext.setProperty(PREVIOUS, context.getModelBox());
        }
        SaTokenContextMockUtil.setMockContext();
        SaTokenContextModelBox owned = context.getModelBox();
        Runnable cleanup = () -> {
            if (context.isValid() && context.getModelBox() == owned) {
                context.clearContext();
                SaTokenContextModelBox previous = (SaTokenContextModelBox) requestContext.getProperty(PREVIOUS);
                if (Objects.nonNull(previous)) {
                    context.setContext(previous.getRequest(), previous.getResponse(), previous.getStorage());
                }
            }
        };
        requestContext.setProperty(PREVIOUS + ".cleanup", cleanup);
        ((org.jboss.resteasy.reactive.server.spi.ResteasyReactiveContainerRequestContext) requestContext)
                .getServerRequestContext().registerCompletionCallback(failure -> cleanup.run());

        SaRequestForMock request = (SaRequestForMock) SaHolder.getRequest();
        request.requestPath = requestContext.getUriInfo().getPath();
        request.url = requestContext.getUriInfo().getRequestUri().toString();
        request.method = requestContext.getMethod();
        request.host = requestContext.getUriInfo().getBaseUri().getHost();
        requestContext.getHeaders().forEach((name, values) -> {
            if (Objects.nonNull(values) && !values.isEmpty()) {
                request.headerMap.put(name, values.get(0));
            }
        });
        requestContext.getUriInfo().getQueryParameters().forEach((name, values) -> {
            if (Objects.nonNull(values) && !values.isEmpty()) {
                request.parameterMap.put(name, values.get(0));
            }
        });
        requestContext.getCookies().forEach((name, cookie) -> request.cookieMap.put(name, cookie.getValue()));

        String authorization = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (StrKit.isNotEmpty(authorization) && authorization.regionMatches(
                true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            request.headerMap.put(StpUtil.getTokenName(), authorization.substring(BEARER_PREFIX.length()).trim());
        }

        Response denied = authorize();
        if (Objects.nonNull(denied)) {
            requestContext.abortWith(denied);
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

    /**
     * \u6309\u5df2\u5339\u914d\u8d44\u6e90\u7684\u8ba4\u8bc1\u3001\u89d2\u8272\u548c\u6743\u9650\u6ce8\u89e3\u6267\u884c\u8bbf\u95ee\u68c0\u67e5\u3002
     * @return \u672a\u901a\u8fc7\u6388\u6743\u65f6\u7528\u4e8e\u7ec8\u6b62\u8bf7\u6c42\u7684\u54cd\u5e94
     */
    private Response authorize() {
        Class<?> resourceClass = resourceInfo.getResourceClass();
        if (Objects.nonNull(resourceClass) && CatalogTradeResource.class.isAssignableFrom(resourceClass)) {
            if (!StpUtil.isLogin()) {
                return failure(Response.Status.UNAUTHORIZED, "unauthenticated");
            }
            String operation = resourceInfo.getResourceMethod().getName();
            if ("goods".equals(operation) || "order".equals(operation)) {
                return null;
            }
            String permission = "cancelOrder".equals(operation) ? "order:cancel" : "order:pay";
            return StpUtil.hasPermission(permission) ? null
                    : failure(Response.Status.FORBIDDEN, "required permission is missing");
        }

        SaCheckLogin login = findAnnotation(SaCheckLogin.class);
        SaCheckRole role = findAnnotation(SaCheckRole.class);
        SaCheckPermission permission = findAnnotation(SaCheckPermission.class);
        if (Objects.isNull(login) && Objects.isNull(role) && Objects.isNull(permission)) {
            return null;
        }
        if (!StpUtil.isLogin()) {
            return failure(Response.Status.UNAUTHORIZED, "unauthenticated");
        }
        if (Objects.nonNull(role) && !matchesRoles(role)) {
            return failure(Response.Status.FORBIDDEN, "required role is missing");
        }
        if (Objects.nonNull(permission) && !matchesPermissions(permission)) {
            return failure(Response.Status.FORBIDDEN, "required permission is missing");
        }
        return null;
    }

    /**
     * \u6309\u539f\u751f\u6ce8\u89e3\u7684\u5339\u914d\u6a21\u5f0f\u68c0\u67e5\u5f53\u524d\u8d26\u53f7\u89d2\u8272\u3002
     * @param check \u539f\u751f Sa-Token \u89d2\u8272\u6216\u6743\u9650\u6ce8\u89e3
     * @return \u89d2\u8272\u8981\u6c42\u6ee1\u8db3\u65f6\u8fd4\u56de true
     */
    private boolean matchesRoles(SaCheckRole check) {
        List<String> roles = Arrays.asList(check.value());
        if (roles.isEmpty()) {
            return true;
        }
        return check.mode() == SaMode.AND
                ? roles.stream().allMatch(StpUtil::hasRole)
                : roles.stream().anyMatch(StpUtil::hasRole);
    }

    /**
     * \u6309\u539f\u751f\u6ce8\u89e3\u7684\u5339\u914d\u6a21\u5f0f\u68c0\u67e5\u5f53\u524d\u8d26\u53f7\u6743\u9650\u3002
     * @param check \u539f\u751f Sa-Token \u89d2\u8272\u6216\u6743\u9650\u6ce8\u89e3
     * @return \u6743\u9650\u8981\u6c42\u6ee1\u8db3\u65f6\u8fd4\u56de true
     */
    private boolean matchesPermissions(SaCheckPermission check) {
        List<String> permissions = Arrays.asList(check.value());
        if (permissions.isEmpty()) {
            return true;
        }
        return check.mode() == SaMode.AND
                ? permissions.stream().allMatch(StpUtil::hasPermission)
                : permissions.stream().anyMatch(StpUtil::hasPermission);
    }

    /**
     * \u5728\u5339\u914d\u5230\u7684\u8d44\u6e90\u65b9\u6cd5\u548c\u7c7b\u4e0a\u67e5\u627e\u6307\u5b9a\u9274\u6743\u6ce8\u89e3\u3002
     * @param <A> \u5f53\u524d\u793a\u4f8b\u6240\u4f7f\u7528\u7684\u6570\u636e\u6216\u6846\u67b6\u7c7b\u578b
     * @param type \u9700\u8981\u67e5\u627e\u7684\u539f\u751f\u9274\u6743\u6ce8\u89e3\u7c7b\u578b
     * @return \u5339\u914d\u5230\u7684\u9274\u6743\u6ce8\u89e3\uff0c\u6ca1\u6709\u58f0\u660e\u65f6\u4e3a\u7a7a
     */
    private <A extends java.lang.annotation.Annotation> A findAnnotation(Class<A> type) {
        A methodAnnotation = getAnnotation(resourceInfo.getResourceMethod(), type);
        return Objects.nonNull(methodAnnotation)
                ? methodAnnotation
                : getAnnotation(resourceInfo.getResourceClass(), type);
    }
}
