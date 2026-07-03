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

/**
 * 请求上下文工具类。
 * <p>提供当前 HTTP 请求、URL、参数、请求头等信息的便捷获取方法。</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@UtilityClass
public class RequestContext {

    /**
     * 获取当前 HTTP 请求对象。
     *
     * @return HttpServletRequest，可能为 null
     */
    public HttpServletRequest get() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (Objects.isNull(attributes)) {
            return null;
        }
        return attributes.getRequest();
    }

    /**
     * 获取当前请求的完整 URL。
     *
     * @return 请求 URL
     */
    public String getUrl() {
        HttpServletRequest request = get();
        if (Objects.isNull(request)) {
            return null;
        }
        return request.getRequestURL().toString();
    }

    /**
     * 获取当前请求的 URI。
     *
     * @return 请求 URI
     */
    public String getUri() {
        HttpServletRequest request = get();
        if (Objects.isNull(request)) {
            return null;
        }
        return request.getRequestURI();
    }

    /**
     * 获取当前请求的参数。
     * <p>对于 GET 请求返回查询参数，对于 POST 请求返回请求体内容。</p>
     *
     * @return 请求参数字符串
     */
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

    /**
     * 获取所有请求头。
     *
     * @return 请求头 Map
     */
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

    /**
     * 获取指定请求头的值。
     *
     * @param header 请求头名称
     * @return 请求头值
     */
    public String getHeader(String header) {
        HttpServletRequest request = get();
        if (Objects.isNull(request)) {
            return null;
        }
        return request.getHeader(header);
    }

    /**
     * 获取指定请求头值，不存在时返回默认值。
     *
     * @param header       请求头名称
     * @param defaultValue 默认值
     * @return 请求头值或默认值
     */
    public String getOrDefault(String header, String defaultValue) {
        String o = getHeader(header);
        if (Objects.isNull(o)) {
            return defaultValue;
        }
        return o;
    }

    /**
     * 获取指定请求头值（整型），不存在或解析失败时返回默认值。
     *
     * @param header       请求头名称
     * @param defaultValue 默认值
     * @return 请求头整数值或默认值
     */
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
