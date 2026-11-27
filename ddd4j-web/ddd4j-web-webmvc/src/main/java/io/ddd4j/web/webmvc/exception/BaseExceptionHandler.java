/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 */
package io.ddd4j.web.webmvc.exception;

import com.alibaba.ttl.TransmittableThreadLocal;
import io.ddd4j.kit.web.IpKit;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.Map;
import java.util.Objects;

/**
 * 统一异常处理基类。
 * <p>使用静态 ThreadLocal 持有当前请求，由框架适配层（如 Spring Filter / Quarkus RequestScope）注入。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j
public abstract class BaseExceptionHandler {

    /** \u5931\u8d25\u72b6\u6001\u503c\u3002 */
    public static final String STATUS_FAIL = "fail";
    /** \u9519\u8bef\u72b6\u6001\u503c\u3002 */
    public static final String STATUS_ERROR = "error";

    /** \u5f02\u6b65\u8bf7\u6c42\u6807\u8bc6\u503c\u3002 */
    protected static final String XML_HTTP_REQUEST = "XMLHttpRequest";
    /** \u5f02\u6b65\u8bf7\u6c42\u6807\u8bc6\u5934\u540d\u79f0\u3002 */
    protected static final String X_REQUESTED_WITH = "X-Requested-With";

    /** \u67e5\u627e\u5f53\u524d Servlet \u8bf7\u6c42\u53ca\u5176\u4e0a\u4e0b\u6587\u7684\u8bf7\u6c42\u6301\u6709\u5668\u3002 */
    private static final ThreadLocal<HttpServletRequest> REQUEST_HOLDER = new TransmittableThreadLocal<>();

    /**
     * 清除当前请求
     */
    public static void clearCurrentRequest() {
        REQUEST_HOLDER.remove();
    }

    /**
     * \u83b7\u53d6\u5f53\u524d\u8bf7\u6c42
     *
     * @return \u5f53\u524d HTTP \u8bf7\u6c42
     */
    protected static HttpServletRequest getCurrentRequest() {
        return REQUEST_HOLDER.get();
    }

    /**
     * \u8bbe\u7f6e\u5f53\u524d\u8bf7\u6c42\uff08\u7531\u6846\u67b6\u9002\u914d\u5c42\u8c03\u7528\uff09
     *
     * @param request HTTP \u8bf7\u6c42
     */
    public static void setCurrentRequest(HttpServletRequest request) {
        REQUEST_HOLDER.set(request);
    }

    /**
     * \u5224\u65ad\u8bf7\u6c42\u662f\u5426\u901a\u8fc7 XMLHttpRequest \u53d1\u8d77\u3002
     * @param request \u5f53\u524d HTTP \u8bf7\u6c42
     * @return \u8bf7\u6c42\u5934\u6807\u8bb0\u4e3a XMLHttpRequest \u65f6\u8fd4\u56de true
     */
    protected boolean isAjaxRequest(HttpServletRequest request) {
        return XML_HTTP_REQUEST.equalsIgnoreCase(request.getHeader(X_REQUESTED_WITH));
    }

    /**
     * \u8bb0\u5f55\u8bf7\u6c42\u5f02\u5e38\u3002
     * @param ex \u6355\u83b7\u7684\u5f02\u5e38
     */
    protected void logException(Exception ex) {
        HttpServletRequest request = getCurrentRequest();
        if (Objects.nonNull(request)) {
            log.error("URI : {} Request Fail. IP >> {} ", request.getRequestURI(), IpKit.getRemoteAddr(request));
        }
        log.error(ex.getMessage(), ex);
    }

    /**
     * \u8bb0\u5f55\u8bf7\u6c42\u5f02\u5e38\u3002
     * @param ex \u6355\u83b7\u7684\u5f02\u5e38
     * @param detailMap \u5f02\u5e38\u8be6\u60c5
     */
    protected void logException(Exception ex, Map<String, Object> detailMap) {
        HttpServletRequest request = getCurrentRequest();
        if (Objects.nonNull(request)) {
            log.error("URI : {} Request Fail. IP >> {} ", request.getRequestURI(), IpKit.getRemoteAddr(request));
        }
        for (Map.Entry<String, Object> entry : detailMap.entrySet()) {
            Object val = entry.getValue();
            if (val instanceof String) {
                MDC.put(entry.getKey(), String.valueOf(val));
            }
        }
        log.error(ex.getMessage(), ex);
    }

}
