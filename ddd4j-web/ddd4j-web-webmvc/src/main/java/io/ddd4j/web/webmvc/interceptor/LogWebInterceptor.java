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
     * 识别 MVC 控制器请求并记录请求开始信息。
     *
     * @param request  当前进入调用链的 HTTP 请求
     * @param response 当前准备写出的 HTTP 响应
     * @param handler  MVC 当前匹配的请求处理器
     * @return 允许当前请求继续执行时返回 true
     * @throws Exception 当前框架请求处理、消息读写或参数转换失败
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
     * 记录请求处理耗时并清理线程中的计时信息。
     *
     * @param request      当前进入调用链的 HTTP 请求
     * @param response     当前准备写出的 HTTP 响应
     * @param handler      MVC 当前匹配的请求处理器
     * @param modelAndView 请求处理器返回的视图及模型
     * @throws Exception 当前框架请求处理、消息读写或参数转换失败
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
     * 返回请求日志拦截器执行优先级。
     *
     * @return 当前拦截器的 Spring 排序值
     */
    @Override
    public int getOrder() {
        return -600;
    }
}
