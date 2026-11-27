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
     * preHandle() \u65b9\u6cd5\u3002
     * @param request \u8bf7\u6c42\u5bf9\u8c61\u3002
     * @param response \u54cd\u5e94\u5bf9\u8c61\u3002
     * @param handler \u5904\u7406\u5668\u3002
     * @return \u5224\u65ad\u7ed3\u679c\u3002
     *
     * @throws Exception \u5f53\u524d\u6846\u67b6\u8bf7\u6c42\u5904\u7406\u3001\u6d88\u606f\u8bfb\u5199\u6216\u53c2\u6570\u8f6c\u6362\u5931\u8d25
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
     * postHandle() 方法。
     * @param request 请求对象。
     * @param response 响应对象。
     * @param handler 处理器。
     * @param modelAndView modelAndView 参数。
     * @throws Exception 执行过程中出现异常时抛出。
     */
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           ModelAndView modelAndView) throws Exception {
    }

    /**
     * afterCompletion() 方法。
     * @param request 请求对象。
     * @param response 响应对象。
     * @param handler 处理器。
     * @param exception exception 参数。
     * @throws Exception 执行过程中出现异常时抛出。
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception exception) throws Exception {
        MDC.clear();
    }

}
