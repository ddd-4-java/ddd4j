package io.ddd4j.boot.web.utils;

import io.ddd4j.boot.core.context.ThreadContext;
import io.ddd4j.boot.core.contract.constant.ContextConstants;
import io.ddd4j.boot.core.utils.JsonKit;
import lombok.experimental.UtilityClass;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.ContentCachingRequestWrapper;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

@UtilityClass
public class RequestContext {
    public HttpServletRequest get() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) return null;
        return attributes.getRequest();
    }

    public String getUrl() {
        HttpServletRequest request = get();
        if (request == null) return null;
        return request.getRequestURL().toString();
    }

    public String getUri() {
        HttpServletRequest request = get();
        if (request == null) return null;
        return request.getRequestURI();
    }

    public String getParams() {
        if (ThreadContext.contains(ContextConstants.REQUEST_PARAMS)) {
            return ThreadContext.get(ContextConstants.REQUEST_PARAMS);
        }
        HttpServletRequest request = get();
        if (request == null) return null;
        if (Objects.equals(request.getMethod(), "GET")) {
            if (request.getParameterMap() != null && !request.getParameterMap().isEmpty()) {
                return JsonKit.toJson(request.getParameterMap().entrySet());
            }
        } else {
            try {
                // 使用ContentCachingRequestWrapper包装原始请求
                ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
                return new String(requestWrapper.getContentAsByteArray());
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public Map<String, String> getHeaders() {
        HttpServletRequest request = get();
        if (request == null) return Collections.emptyMap();
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = request.getHeader(headerName);
            headers.put(headerName, headerValue);
        }
        return headers;
    }

    public String getHeader(String header) {
        HttpServletRequest request = get();
        if (request == null) return null;
        return request.getHeader(header);
    }

    public String getOrDefault(String header, String defaultValue) {
        String o = getHeader(header);
        if (o == null) return defaultValue;
        return o;
    }

    public Integer getOrDefault(String header, Integer defaultValue) {
        try {
            String headerValue = getHeader(header);
            if (headerValue == null) {
                return defaultValue;
            }
            return Integer.valueOf(headerValue);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

}
