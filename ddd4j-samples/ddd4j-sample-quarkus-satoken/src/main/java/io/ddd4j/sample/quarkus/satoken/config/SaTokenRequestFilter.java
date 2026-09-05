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

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.context.mock.SaRequestForMock;
import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import cn.dev33.satoken.stp.StpUtil;
import io.ddd4j.core.api.R;
import io.ddd4j.core.util.SubjectKit;
import io.ddd4j.kit.lang.StrKit;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.ResourceInfo;
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

    private static final String BEARER_PREFIX = "Bearer ";

    @Context
    ResourceInfo resourceInfo;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        SaTokenContextMockUtil.clearContext();
        SaTokenContextMockUtil.setMockContext();

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
        if (StrKit.isNotEmpty(authorization) && authorization.startsWith(BEARER_PREFIX)) {
            request.headerMap.put(StpUtil.getTokenName(), authorization.substring(BEARER_PREFIX.length()));
        }

        Response denied = authorize();
        if (Objects.nonNull(denied)) {
            requestContext.abortWith(denied);
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        SaTokenContextMockUtil.clearContext();
    }

    private Response authorize() {
        SaCheckLogin login = findAnnotation(SaCheckLogin.class);
        SaCheckRole role = findAnnotation(SaCheckRole.class);
        SaCheckPermission permission = findAnnotation(SaCheckPermission.class);
        if (Objects.isNull(login) && Objects.isNull(role) && Objects.isNull(permission)) {
            return null;
        }
        if (!SubjectKit.isLogin()) {
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

    private boolean matchesRoles(SaCheckRole check) {
        List<String> roles = Arrays.asList(check.value());
        if (roles.isEmpty()) {
            return true;
        }
        return check.mode() == SaMode.AND
                ? roles.stream().allMatch(SubjectKit::hasRole)
                : roles.stream().anyMatch(SubjectKit::hasRole);
    }

    private boolean matchesPermissions(SaCheckPermission check) {
        List<String> permissions = Arrays.asList(check.value());
        if (permissions.isEmpty()) {
            return true;
        }
        return check.mode() == SaMode.AND
                ? permissions.stream().allMatch(SubjectKit::hasPermission)
                : permissions.stream().anyMatch(SubjectKit::hasPermission);
    }

    private <A extends java.lang.annotation.Annotation> A findAnnotation(Class<A> type) {
        A methodAnnotation = getAnnotation(resourceInfo.getResourceMethod(), type);
        return Objects.nonNull(methodAnnotation)
                ? methodAnnotation
                : getAnnotation(resourceInfo.getResourceClass(), type);
    }

    private static <A extends java.lang.annotation.Annotation> A getAnnotation(AnnotatedElement element, Class<A> type) {
        return Objects.nonNull(element) ? element.getAnnotation(type) : null;
    }

    private static Response failure(Response.Status status, String message) {
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(R.fail(status.getStatusCode(), message))
                .build();
    }
}
