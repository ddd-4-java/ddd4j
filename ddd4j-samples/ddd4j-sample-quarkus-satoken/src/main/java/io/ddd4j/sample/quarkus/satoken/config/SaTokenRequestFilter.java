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

    /**
     * 记录进入认证过滤器之前线程绑定的请求属性键。
     */
    private static final String PREVIOUS = SaTokenRequestFilter.class.getName() + ".previous";
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
     * 从当前被检查元素读取同类鉴权注解。
     *
     * @param <A>     当前示例所使用的数据或框架类型
     * @param element 当前被检查注解的资源方法或类
     * @param type    需要查找的原生鉴权注解类型
     * @return 类型匹配的注解，缺少时为空
     */
    private static <A extends java.lang.annotation.Annotation> A getAnnotation(AnnotatedElement element, Class<A> type) {
        return Objects.nonNull(element) ? element.getAnnotation(type) : null;
    }

    /**
     * 将认证失败状态和说明组装为示例错误响应。
     *
     * @param status  商品状态条件文本
     * @param message 供调用方诊断拒绝原因的响应正文
     * @return 当前请求的错误 HTTP 响应
     */
    private static Response failure(Response.Status status, String message) {
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(R.of(status.getStatusCode(), message))
                .build();
    }

    /**
     * 在 JAX-RS 调用资源方法之前装配并验证当前原生认证上下文。
     *
     * @param requestContext 当前 JAX-RS 请求上下文
     * @throws IOException 装配示例或处理当前 HTTP 请求失败
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

    /**
     * 按已匹配资源的认证、角色和权限注解执行访问检查。
     *
     * @return 未通过授权时用于终止请求的响应
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
     * 按原生注解的匹配模式检查当前账号角色。
     *
     * @param check 原生 Sa-Token 角色或权限注解
     * @return 角色要求满足时返回 true
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
     * 按原生注解的匹配模式检查当前账号权限。
     *
     * @param check 原生 Sa-Token 角色或权限注解
     * @return 权限要求满足时返回 true
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
     * 在匹配到的资源方法和类上查找指定鉴权注解。
     *
     * @param <A>  当前示例所使用的数据或框架类型
     * @param type 需要查找的原生鉴权注解类型
     * @return 匹配到的鉴权注解，没有声明时为空
     */
    private <A extends java.lang.annotation.Annotation> A findAnnotation(Class<A> type) {
        A methodAnnotation = getAnnotation(resourceInfo.getResourceMethod(), type);
        return Objects.nonNull(methodAnnotation)
                ? methodAnnotation
                : getAnnotation(resourceInfo.getResourceClass(), type);
    }
}
