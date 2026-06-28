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
 * Slf4j MDC 日志上下文拦截器（Spring WebMVC）。
 *
 * <p><b>迁移说明</b>：自 2.0.x 起，本类将从 {@code ddd4j-spring} 下移到
 * {@code ddd4j-boot-web-webmvc}（具体框架项目）。本类保留为过渡期兼容入口，
 * 新业务请直接依赖 {@code ddd4j-boot-web-webmvc}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @deprecated 自 2.0.x 起下移到 {@code ddd4j-boot-web-webmvc.Slf4jMDCInterceptor}
 */
@Deprecated
public class Slf4jMDCInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        MDC.put("requestId", Objects.toString(request.getHeader(XHeaders.X_REQUEST_ID), UUID.randomUUID().toString()));
        MDC.put("requestURL", request.getRequestURL().toString());
        MDC.put("requestURI", request.getRequestURI());
        MDC.put("queryString", request.getQueryString());
        // 改用纯 Java 解析，移除对 IpKit.getRemoteAddr(HttpServletRequest) 的依赖
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
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           ModelAndView modelAndView) throws Exception {
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception exception) throws Exception {
        MDC.clear();
    }

}
