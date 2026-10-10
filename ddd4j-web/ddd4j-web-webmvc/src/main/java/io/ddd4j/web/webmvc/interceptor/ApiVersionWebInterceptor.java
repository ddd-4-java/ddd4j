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
package io.ddd4j.web.webmvc.interceptor;

import io.ddd4j.core.constant.XHeaders;
import io.ddd4j.web.core.version.ApiVersion;
import io.ddd4j.web.core.version.ApiVersionResolver;
import io.ddd4j.web.core.version.ApiVersionResult;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Objects;

/**
 * Spring WebMVC 的 API 版本解析拦截器。
 *
 * <p>在 {@code preHandle} 阶段按冻结优先级（{@code X-App-Version} 请求头 &gt; URL 路径版本段
 * &gt; 配置默认版本）解析客户端 API 版本，并挂载到请求属性
 * {@link #API_VERSION_ATTRIBUTE}；随后 {@code Ddd4jWebMvcInterceptor} 创建
 * {@code WebRequestContext} 时读取该属性，使业务处理器可从上下文读到解析出的 {@link ApiVersion}。
 *
 * <p>严格模式下非法版本值由解析器抛出 {@code ApiVersionRejectedException}（4xx），
 * 沿既有异常翻译链转为拒绝响应。支持通过 {@link #isEnabled()} 关闭（关闭后 preHandle 直接放行）。
 *
 * <p><b>注册说明</b>：必须先于 {@code Ddd4jWebMvcInterceptor} 执行，故顺序取
 * {@link Ordered#HIGHEST_PRECEDENCE}（既有两个拦截器顺移为 {@code MIN_VALUE+1}/{@code MIN_VALUE+2}）；
 * 由 {@code ApiVersionWebMvcConfiguration} 注册，未注册（或配置关闭不注册）时完全回退旧行为。
 *
 * @see ApiVersionResolver
 * @see io.ddd4j.web.webmvc.ApiVersionWebMvcConfiguration
 */
public class ApiVersionWebInterceptor implements HandlerInterceptor, Ordered {

    /** 解析出的 API 版本在请求属性中的挂载键，供 {@code Ddd4jWebMvcInterceptor} 读取。 */
    public static final String API_VERSION_ATTRIBUTE = ApiVersionWebInterceptor.class.getName() + ".apiVersion";

    /** 版本解析器。 */
    private final ApiVersionResolver resolver;

    /** 是否启用；false 时 preHandle 直接放行且不挂载任何属性。 */
    private final boolean enabled;

    /**
     * 以启用状态创建拦截器。
     *
     * @param resolver 版本解析器（非 null）
     */
    public ApiVersionWebInterceptor(ApiVersionResolver resolver) {
        this(resolver, true);
    }

    /**
     * 创建拦截器。
     *
     * @param resolver 版本解析器（非 null）
     * @param enabled 是否启用；false 时完全旁路解析逻辑
     */
    public ApiVersionWebInterceptor(ApiVersionResolver resolver, boolean enabled) {
        this.resolver = Objects.requireNonNull(resolver, "resolver must not be null");
        this.enabled = enabled;
    }

    /**
     * 是否启用本拦截器的解析逻辑。
     *
     * @return 启用返回 {@code true}
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 拦截顺序：必须先于上下文拦截器执行。
     *
     * @return {@link Ordered#HIGHEST_PRECEDENCE}
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    /**
     * 解析 API 版本并挂载请求属性。
     *
     * @param request 当前请求
     * @param response 当前响应
     * @param handler 已匹配的处理器
     * @return 恒为 {@code true}（放行后续拦截器与处理器）
     * @throws io.ddd4j.web.core.version.ApiVersionRejectedException 严格模式下版本值非法时抛出
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!enabled) {
            return true;
        }
        String headerVersion = request.getHeader(XHeaders.X_APP_VERSION);
        String path = normalizePath(request);
        ApiVersionResult result = resolver.resolve(headerVersion, path);
        request.setAttribute(API_VERSION_ATTRIBUTE, result.version());
        return true;
    }

    /**
     * 去除上下文路径后的请求路径，避免上下文路径段被误判为版本段。
     *
     * @param request 当前请求
     * @return 归一化路径
     */
    private String normalizePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (Objects.nonNull(contextPath) && !contextPath.isEmpty() && Objects.nonNull(uri)
                && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }
}
