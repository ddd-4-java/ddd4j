/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 */
package io.ddd4j.core.exception;

import com.alibaba.ttl.TransmittableThreadLocal;
import io.ddd4j.kit.web.IpKit;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import java.util.Objects;

/**
 * 统一异常处理基类（纯 Java，零框架依赖）
 * <p>
 * 使用静态 ThreadLocal 持有当前请求，由框架适配层（如 Spring Filter / Quarkus RequestScope）注入。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.4.x
 */
@Slf4j
public abstract class BaseExceptionHandler {

    public static final String STATUS_FAIL = "fail";
    public static final String STATUS_ERROR = "error";

    protected static final String XML_HTTP_REQUEST = "XMLHttpRequest";
    protected static final String X_REQUESTED_WITH = "X-Requested-With";

    private static final ThreadLocal<HttpServletRequest> REQUEST_HOLDER = new TransmittableThreadLocal<>();

    /**
     * 设置当前请求（由框架适配层调用，如 Spring OncePerRequestFilter / Quarkus RequestScope）
     */
    public static void setCurrentRequest(HttpServletRequest request) {
        REQUEST_HOLDER.set(request);
    }

    /**
     * 清除当前请求（由框架适配层在请求结束时调用）
     */
    public static void clearCurrentRequest() {
        REQUEST_HOLDER.remove();
    }

    /**
     * 获取当前请求（纯 Java ThreadLocal，无需 Spring RequestContextHolder）
     */
    protected static HttpServletRequest getCurrentRequest() {
        return REQUEST_HOLDER.get();
    }

    protected boolean isAjaxRequest(HttpServletRequest request) {
        return XML_HTTP_REQUEST.equalsIgnoreCase(request.getHeader(X_REQUESTED_WITH));
    }

    protected void logException(Exception ex) {
        HttpServletRequest request = getCurrentRequest();
        if (Objects.nonNull(request)) {
            log.error("URI : {} Request Fail. IP >> {} ", request.getRequestURI(), IpKit.getRemoteAddr(request));
        }
        log.error(ex.getMessage(), ex);
    }


}
