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
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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

    @Override
    public String[] pathPatterns() {
        return baseWebProperties.getLog().getIncludes().split(",");
    }

    @Override
    public String[] excludePathPatterns() {
        return baseWebProperties.getLog().getExcludes().split(",");
    }

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

    @Override
    public int getOrder() {
        return -600;
    }
}
