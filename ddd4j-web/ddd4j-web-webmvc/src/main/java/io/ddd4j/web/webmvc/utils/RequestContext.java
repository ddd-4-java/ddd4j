package io.ddd4j.web.webmvc.utils;

import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.kit.lang.JsonKit;
import jakarta.servlet.http.HttpServletRequest;
import lombok.experimental.UtilityClass;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.util.*;

@UtilityClass
/**
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class RequestContext {
    public HttpServletRequest get() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (Objects.isNull(attributes)) {
            return null;
        }
        return attributes.getRequest();
    }

    public String getUrl() {
        HttpServletRequest request = get();
        if (Objects.isNull(request)) {
            return null;
        }
        return request.getRequestURL().toString();
    }

    public String getUri() {
        HttpServletRequest request = get();
        if (Objects.isNull(request)) {
            return null;
        }
        return request.getRequestURI();
    }

    public String getParams() {
        if (ThreadContext.contains(ContextConstants.REQUEST_PARAMS)) {
            return ThreadContext.get(ContextConstants.REQUEST_PARAMS);
        }
        HttpServletRequest request = get();
        if (Objects.isNull(request)) {
            return null;
        }
        if (Objects.equals(request.getMethod(), "GET")) {
            if (Objects.nonNull(request.getParameterMap()) && !request.getParameterMap().isEmpty()) {
                return JsonKit.toJson(request.getParameterMap().entrySet());
            }
        } else {
            try {
                // 使用ContentCachingRequestWrapper包装原始请求
                ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request, 1024 * 1024);
                return new String(requestWrapper.getContentAsByteArray());
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public Map<String, String> getHeaders() {
        HttpServletRequest request = get();
        if (Objects.isNull(request)) {
            return Collections.emptyMap();
        }
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
        if (Objects.isNull(request)) {
            return null;
        }
        return request.getHeader(header);
    }

    public String getOrDefault(String header, String defaultValue) {
        String o = getHeader(header);
        if (Objects.isNull(o)) {
            return defaultValue;
        }
        return o;
    }

    public Integer getOrDefault(String header, Integer defaultValue) {
        try {
            String headerValue = getHeader(header);
            if (Objects.isNull(headerValue)) {
                return defaultValue;
            }
            return Integer.valueOf(headerValue);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

}
