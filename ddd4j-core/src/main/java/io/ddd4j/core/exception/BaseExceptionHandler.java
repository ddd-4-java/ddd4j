/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 */
package io.ddd4j.core.exception;

import io.ddd4j.core.util.WebUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.Map;
import java.util.Objects;

@Slf4j
public abstract class BaseExceptionHandler {

    public static final String STATUS_FAIL = "fail";
    public static final String STATUS_ERROR = "error";

    protected static final String XML_HTTP_REQUEST = "XMLHttpRequest";
    protected static final String X_REQUESTED_WITH = "X-Requested-With";

    protected boolean isAjaxRequest(HttpServletRequest request) {
        return XML_HTTP_REQUEST.equalsIgnoreCase(request.getHeader(X_REQUESTED_WITH));
    }

    protected void logException(Exception ex) {
        HttpServletRequest request = WebUtils.getHttpServletRequest();
        if (Objects.nonNull(request)) {
            log.error("URI : {} Request Fail. IP >> {} ", request.getRequestURI(), WebUtils.getRemoteAddr(request));
        }
        log.error(ex.getMessage(), ex);
    }

    protected void logException(Exception ex, Map<String, Object> detailMap) {
        HttpServletRequest request = WebUtils.getHttpServletRequest();
        if (Objects.nonNull(request)) {
            log.error("URI : {} Request Fail. IP >> {} ", request.getRequestURI(), WebUtils.getRemoteAddr(request));
        }
        for (final Map.Entry<String, Object> entry : detailMap.entrySet()) {
            Object val = entry.getValue();
            if (val instanceof String) {
                MDC.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        log.error(ex.getMessage(), ex);
    }

}
