package io.ddd4j.boot.core.web.servlet.handler;

import io.ddd4j.boot.core.XHeaders;
import io.ddd4j.boot.core.sequence.Sequence;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.biz.utils.WebUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.util.Objects;

public class Slf4jMDCInterceptor implements HandlerInterceptor {

    private final Sequence sequence;

    public Slf4jMDCInterceptor(Sequence sequence) {
        this.sequence = sequence;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)

            throws Exception {

        MDC.put("requestId", Objects.toString(request.getHeader(XHeaders.X_REQUEST_ID), sequence.nextId().toString()));
        MDC.put("requestURL", request.getRequestURL().toString());
        MDC.put("requestURI", request.getRequestURI());
        MDC.put("queryString", request.getQueryString());
        MDC.put("remoteAddr", WebUtils.getRemoteAddr(request));
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
