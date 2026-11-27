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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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
     * @return 获取的整型数值
     */
    int getOrder();

    /**
     * \u8bf7\u6c42\u9884\u5904\u7406\u3002
     * @param request \u8bf7\u6c42\u5bf9\u8c61
     * @param response \u54cd\u5e94\u5bf9\u8c61
     * @param handler \u5904\u7406\u5668
     * @return \u6761\u4ef6\u6210\u7acb\uff08\u6216\u64cd\u4f5c\u6210\u529f\uff09\u8fd4\u56de true\uff0c\u5426\u5219\u8fd4\u56de false
     * @throws java.lang.Exception \u6267\u884c\u5bf9\u5e94\u64cd\u4f5c\u5931\u8d25\u65f6\u629b\u51fa
     *
     * @throws Exception \u8bf7\u6c42\u5904\u7406\u5931\u8d25\u65f6\u629b\u51fa
     */
    boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception;

    /**
     * \u8bf7\u6c42\u5b8c\u6210\u540e\u6e05\u7406\uff08\u9ed8\u8ba4\u7a7a\u5b9e\u73b0\uff09\u3002
     * @param request \u8bf7\u6c42\u5bf9\u8c61
     * @param response \u54cd\u5e94\u5bf9\u8c61
     * @param handler \u5904\u7406\u5668
     * @param ex \u5f02\u5e38\u5bf9\u8c61
     * @throws java.lang.Exception \u6267\u884c\u5bf9\u5e94\u64cd\u4f5c\u5931\u8d25\u65f6\u629b\u51fa
     *
     * @throws Exception \u8bf7\u6c42\u5904\u7406\u5931\u8d25\u65f6\u629b\u51fa
     */
    default void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
    }
}
