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
     * \u628a\u8bf7\u6c42\u6807\u8bc6\u548c\u8bf7\u6c42\u8def\u5f84\u7ed1\u5b9a\u5230\u65e5\u5fd7 MDC\u3002
     * @param request \u5f53\u524d\u8fdb\u5165\u8c03\u7528\u94fe\u7684 HTTP \u8bf7\u6c42
     * @param response \u5f53\u524d\u51c6\u5907\u5199\u51fa\u7684 HTTP \u54cd\u5e94
     * @param handler MVC \u5f53\u524d\u5339\u914d\u7684\u8bf7\u6c42\u5904\u7406\u5668
     * @return \u5141\u8bb8\u8bf7\u6c42\u8fdb\u5165\u540e\u7eed MVC \u5904\u7406\u94fe\u65f6\u8fd4\u56de true
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
     * \u4fdd\u7559 MVC \u89c6\u56fe\u6e32\u67d3\u524d\u4ecd\u9700\u4f7f\u7528\u7684 MDC \u4e0a\u4e0b\u6587\u3002
     * @param request \u5f53\u524d\u8fdb\u5165\u8c03\u7528\u94fe\u7684 HTTP \u8bf7\u6c42
     * @param response \u5f53\u524d\u51c6\u5907\u5199\u51fa\u7684 HTTP \u54cd\u5e94
     * @param handler MVC \u5f53\u524d\u5339\u914d\u7684\u8bf7\u6c42\u5904\u7406\u5668
     * @param modelAndView \u8bf7\u6c42\u5904\u7406\u5668\u8fd4\u56de\u7684\u89c6\u56fe\u53ca\u6a21\u578b
     * @throws Exception \u5f53\u524d\u6846\u67b6\u8bf7\u6c42\u5904\u7406\u3001\u6d88\u606f\u8bfb\u5199\u6216\u53c2\u6570\u8f6c\u6362\u5931\u8d25
     */
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           ModelAndView modelAndView) throws Exception {
    }

    /**
     * \u6e05\u9664\u5f53\u524d\u8bf7\u6c42\u7ebf\u7a0b\u4e0a\u7684 MDC \u4fe1\u606f\u3002
     * @param request \u5f53\u524d\u8fdb\u5165\u8c03\u7528\u94fe\u7684 HTTP \u8bf7\u6c42
     * @param response \u5f53\u524d\u51c6\u5907\u5199\u51fa\u7684 HTTP \u54cd\u5e94
     * @param handler MVC \u5f53\u524d\u5339\u914d\u7684\u8bf7\u6c42\u5904\u7406\u5668
     * @param exception \u5f53\u524d\u8bf7\u6c42\u5904\u7406\u65f6\u629b\u51fa\u7684\u5f02\u5e38
     * @throws Exception \u5f53\u524d\u6846\u67b6\u8bf7\u6c42\u5904\u7406\u3001\u6d88\u606f\u8bfb\u5199\u6216\u53c2\u6570\u8f6c\u6362\u5931\u8d25
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception exception) throws Exception {
        MDC.clear();
    }

}
