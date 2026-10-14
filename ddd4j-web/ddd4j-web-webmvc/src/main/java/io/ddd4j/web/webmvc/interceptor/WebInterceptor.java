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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Web 拦截器 SPI（Servlet 风格）。
 *
 * <p>业务方通过 Spring 配置注册 {@link WebInterceptor} Bean，{@link io.ddd4j.web.webmvc.config.BaseWebConfig}
 * 在启动期扫描所有 Bean 并绑定到 Servlet Filter Chain。
 *
 * <p>这是 ddd4j 的「框架统一拦截器契约」——所有 Web 模块（webmvc / webflux）的拦截器实现这一接口，
 * 业务方在不同部署时不需要改代码。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public interface WebInterceptor {

    /**
     * 拦截器顺序（数字越小越先执行）。
     * @return 拦截器顺序
     */
    int getOrder();

    /**
     * 请求预处理。
     * @throws Exception 请求处理失败时抛出
     * @return 是否继续处理请求
     * @param response HTTP 响应
     * @param request HTTP 请求
     * @param handler 目标处理器
     */
    boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception;

    /**
     * 请求完成后清理（默认空实现）。
     * @throws Exception 请求处理失败时抛出
     * @param response HTTP 响应
     * @param request HTTP 请求
     * @param handler 目标处理器
     * @param ex 捕获的异常
     */
    default void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
    }
}
