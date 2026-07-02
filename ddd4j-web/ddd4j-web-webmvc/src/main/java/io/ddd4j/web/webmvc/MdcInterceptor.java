package io.ddd4j.web.webmvc.webmvc;

import io.ddd4j.core.constant.XHeaders;
import io.ddd4j.kit.web.IpKit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Objects;
import java.util.UUID;

/**
 * Spring WebMVC MDC logging context interceptor.
 */
public class MdcInterceptor implements HandlerInterceptor {

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

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        MDC.clear();
    }
}
