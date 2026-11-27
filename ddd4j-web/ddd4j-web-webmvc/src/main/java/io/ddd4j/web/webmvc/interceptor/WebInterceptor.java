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
     * \u62e6\u622a\u5668\u987a\u5e8f\uff08\u6570\u5b57\u8d8a\u5c0f\u8d8a\u5148\u6267\u884c\uff09\u3002
     *
     * @return \u62e6\u622a\u5668\u987a\u5e8f
     */
    int getOrder();

    /**
     * \u8bf7\u6c42\u9884\u5904\u7406\u3002
     *
     * @throws Exception \u8bf7\u6c42\u5904\u7406\u5931\u8d25\u65f6\u629b\u51fa
     * @return \u662f\u5426\u7ee7\u7eed\u5904\u7406\u8bf7\u6c42
     * @param response HTTP \u54cd\u5e94
     * @param request HTTP \u8bf7\u6c42
     * @param handler \u76ee\u6807\u5904\u7406\u5668
     */
    boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception;

    /**
     * \u8bf7\u6c42\u5b8c\u6210\u540e\u6e05\u7406\uff08\u9ed8\u8ba4\u7a7a\u5b9e\u73b0\uff09\u3002
     *
     * @throws Exception \u8bf7\u6c42\u5904\u7406\u5931\u8d25\u65f6\u629b\u51fa
     * @param response HTTP \u54cd\u5e94
     * @param request HTTP \u8bf7\u6c42
     * @param handler \u76ee\u6807\u5904\u7406\u5668
     * @param ex \u6355\u83b7\u7684\u5f02\u5e38
     */
    default void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
    }
}
