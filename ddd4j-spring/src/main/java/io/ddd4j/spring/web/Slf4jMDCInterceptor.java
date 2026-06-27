package io.ddd4j.spring.web;

import io.ddd4j.core.XHeaders;
import io.ddd4j.kit.web.IpKit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.util.Objects;
import java.util.UUID;

/**
 * Slf4j MDC 日志上下文拦截器。
 * <p>
 * 在请求处理前设置 MDC 上下文（requestId / requestURL / remoteAddr 等），
 * 请求完成后清理 MDC，便于日志追踪。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class Slf4jMDCInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        MDC.put("requestId", Objects.toString(request.getHeader(XHeaders.X_REQUEST_ID), UUID.randomUUID().toString()));
        MDC.put("requestURL", request.getRequestURL().toString());
        MDC.put("requestURI", request.getRequestURI());
        MDC.put("queryString", request.getQueryString());
        MDC.put("remoteAddr", IpKit.getRemoteAddr(request));
        MDC.put("remoteHost", request.getRemoteHost());
        MDC.put("remotePort", String.valueOf(request.getRemotePort()));
        MDC.put("localAddr", request.getLocalAddr());
        MDC.put("localName", request.getLocalName());

        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           ModelAndView modelAndView) throws Exception {
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception exception) throws Exception {
        MDC.clear();
    }

}
