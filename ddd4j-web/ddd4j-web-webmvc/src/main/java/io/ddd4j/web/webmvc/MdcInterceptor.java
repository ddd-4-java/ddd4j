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
package io.ddd4j.web.webmvc;

import io.ddd4j.core.constant.XHeaders;
import io.ddd4j.kit.web.IpKit;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Objects;
import java.util.UUID;

/**
 * Spring WebMVC MDC 日志上下文拦截器。
 * <p>在请求处理前将请求 ID、URL、客户端地址等信息注入 MDC，请求完成后清理。</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class MdcInterceptor implements HandlerInterceptor {

    /**
     * \u628a\u5f53\u524d\u8bf7\u6c42\u7684\u6807\u8bc6\u548c\u8def\u5f84\u4fe1\u606f\u5199\u5165\u65e5\u5fd7 MDC\u3002
     * @param request \u5f53\u524d\u8fdb\u5165\u8c03\u7528\u94fe\u7684 HTTP \u8bf7\u6c42
     * @param response \u5f53\u524d\u51c6\u5907\u5199\u51fa\u7684 HTTP \u54cd\u5e94
     * @param handler MVC \u5f53\u524d\u5339\u914d\u7684\u8bf7\u6c42\u5904\u7406\u5668
     * @return \u5141\u8bb8\u7ee7\u7eed\u5904\u7406\u5f53\u524d\u8bf7\u6c42\u65f6\u8fd4\u56de true
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
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
     * \u6e05\u9664\u5f53\u524d MVC \u8bf7\u6c42\u7ebf\u7a0b\u7559\u4e0b\u7684 MDC \u503c\u3002
     * @param request \u5f53\u524d\u8fdb\u5165\u8c03\u7528\u94fe\u7684 HTTP \u8bf7\u6c42
     * @param response \u5f53\u524d\u51c6\u5907\u5199\u51fa\u7684 HTTP \u54cd\u5e94
     * @param handler MVC \u5f53\u524d\u5339\u914d\u7684\u8bf7\u6c42\u5904\u7406\u5668
     * @param exception \u5f53\u524d\u8bf7\u6c42\u5904\u7406\u65f6\u629b\u51fa\u7684\u5f02\u5e38
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        MDC.clear();
    }
}
