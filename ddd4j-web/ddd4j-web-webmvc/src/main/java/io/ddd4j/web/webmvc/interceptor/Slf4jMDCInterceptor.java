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
import io.ddd4j.kit.web.IpKit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.util.Objects;
import java.util.UUID;

/**
 * Slf4j MDC 日志上下文拦截器（Spring WebMVC）。
 *
 * <p>该实现依赖 Servlet API 与 Spring MVC，归属 {@code ddd4j-web-webmvc}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class Slf4jMDCInterceptor implements HandlerInterceptor {

    /**
     * 把请求标识和请求路径绑定到日志 MDC。
     *
     * @param request  当前进入调用链的 HTTP 请求
     * @param response 当前准备写出的 HTTP 响应
     * @param handler  MVC 当前匹配的请求处理器
     * @return 允许请求进入后续 MVC 处理链时返回 true
     * @throws Exception 当前框架请求处理、消息读写或参数转换失败
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        MDC.put("requestId", Objects.toString(request.getHeader(XHeaders.X_REQUEST_ID), UUID.randomUUID().toString()));
        MDC.put("requestURL", request.getRequestURL().toString());
        MDC.put("requestURI", request.getRequestURI());
        MDC.put("queryString", request.getQueryString());
        MDC.put("remoteAddr", IpKit.parseRemoteAddr(
                request.getHeader("X-Forwarded-For"),
                request.getHeader("X-Real-IP"),
                request.getRemoteAddr()));
        MDC.put("remoteHost", request.getRemoteHost());
        MDC.put("remotePort", String.valueOf(request.getRemotePort()));
        MDC.put("localAddr", request.getLocalAddr());
        MDC.put("localName", request.getLocalName());

        return true;
    }

    /**
     * 保留 MVC 视图渲染前仍需使用的 MDC 上下文。
     *
     * @param request      当前进入调用链的 HTTP 请求
     * @param response     当前准备写出的 HTTP 响应
     * @param handler      MVC 当前匹配的请求处理器
     * @param modelAndView 请求处理器返回的视图及模型
     * @throws Exception 当前框架请求处理、消息读写或参数转换失败
     */
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           ModelAndView modelAndView) throws Exception {
    }

    /**
     * 清除当前请求线程上的 MDC 信息。
     *
     * @param request   当前进入调用链的 HTTP 请求
     * @param response  当前准备写出的 HTTP 响应
     * @param handler   MVC 当前匹配的请求处理器
     * @param exception 当前请求处理时抛出的异常
     * @throws Exception 当前框架请求处理、消息读写或参数转换失败
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception exception) throws Exception {
        MDC.clear();
    }

}
