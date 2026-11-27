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

import io.ddd4j.web.webmvc.config.BaseWebProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 日志 Web 拦截器。
 * <p>打印请求路径、请求方法及响应耗时，用于请求日志记录。</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class LogWebInterceptor extends BaseWebInterceptor {
    /**
     * 请求开始时间（线程本地）
     */
    final ThreadLocal<LocalDateTime> beginTime = new ThreadLocal<>();
    /**
     * Web 基础配置属性
     */
    @Autowired
    BaseWebProperties baseWebProperties;

    /**
     * 获取拦截的请求路径模式。
     *
     * <p>此实现调用 <code>baseWebProperties.getLog().getIncludes().split(",")</code>。</p>
     *
     * @return 实际返回<code>baseWebProperties.getLog().getIncludes().split(",")</code>
     */
    @Override
    public String[] pathPatterns() {
        return baseWebProperties.getLog().getIncludes().split(",");
    }

    /**
     * 获取不拦截的请求路径模式。
     *
     * <p>此实现调用 <code>baseWebProperties.getLog().getExcludes().split(",")</code>。</p>
     *
     * @return 实际返回<code>baseWebProperties.getLog().getExcludes().split(",")</code>
     */
    @Override
    public String[] excludePathPatterns() {
        return baseWebProperties.getLog().getExcludes().split(",");
    }

    /**
     * \u8bc6\u522b MVC \u63a7\u5236\u5668\u8bf7\u6c42\u5e76\u8bb0\u5f55\u8bf7\u6c42\u5f00\u59cb\u4fe1\u606f\u3002
     * @param request \u5f53\u524d\u8fdb\u5165\u8c03\u7528\u94fe\u7684 HTTP \u8bf7\u6c42
     * @param response \u5f53\u524d\u51c6\u5907\u5199\u51fa\u7684 HTTP \u54cd\u5e94
     * @param handler MVC \u5f53\u524d\u5339\u914d\u7684\u8bf7\u6c42\u5904\u7406\u5668
     * @return \u5141\u8bb8\u5f53\u524d\u8bf7\u6c42\u7ee7\u7eed\u6267\u884c\u65f6\u8fd4\u56de true
     * @throws Exception \u5f53\u524d\u6846\u67b6\u8bf7\u6c42\u5904\u7406\u3001\u6d88\u606f\u8bfb\u5199\u6216\u53c2\u6570\u8f6c\u6362\u5931\u8d25
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (handler.getClass() == ResourceHttpRequestHandler.class) {
            return Boolean.TRUE;
        }
        HandlerMethod method = (HandlerMethod) handler;
        String className = method.getBeanType().getSimpleName();
        String methodName = method.getMethod().getName();
        log.info("==> Request {} {} -> {}.{}()", request.getMethod(), request.getRequestURI(), className, methodName);
        beginTime.set(LocalDateTime.now());
        return true;
    }

    /**
     * \u8bb0\u5f55\u8bf7\u6c42\u5904\u7406\u8017\u65f6\u5e76\u6e05\u7406\u7ebf\u7a0b\u4e2d\u7684\u8ba1\u65f6\u4fe1\u606f\u3002
     * @param request \u5f53\u524d\u8fdb\u5165\u8c03\u7528\u94fe\u7684 HTTP \u8bf7\u6c42
     * @param response \u5f53\u524d\u51c6\u5907\u5199\u51fa\u7684 HTTP \u54cd\u5e94
     * @param handler MVC \u5f53\u524d\u5339\u914d\u7684\u8bf7\u6c42\u5904\u7406\u5668
     * @param modelAndView \u8bf7\u6c42\u5904\u7406\u5668\u8fd4\u56de\u7684\u89c6\u56fe\u53ca\u6a21\u578b
     * @throws Exception \u5f53\u524d\u6846\u67b6\u8bf7\u6c42\u5904\u7406\u3001\u6d88\u606f\u8bfb\u5199\u6216\u53c2\u6570\u8f6c\u6362\u5931\u8d25
     */
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        try {
            log.info("<== Response in {} ms {} {}",
                    Duration.between(beginTime.get(), LocalDateTime.now()).toMillis(),
                    request.getMethod(),
                    request.getRequestURI());
        } catch (Exception ignored) {
        } finally {
            beginTime.remove();
        }

    }

    /**
     * \u8fd4\u56de\u8bf7\u6c42\u65e5\u5fd7\u62e6\u622a\u5668\u6267\u884c\u4f18\u5148\u7ea7\u3002
     * @return \u5f53\u524d\u62e6\u622a\u5668\u7684 Spring \u6392\u5e8f\u503c
     */
    @Override
    public int getOrder() {
        return -600;
    }
}
