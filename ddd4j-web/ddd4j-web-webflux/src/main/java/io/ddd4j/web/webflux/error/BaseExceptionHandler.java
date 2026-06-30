/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
package io.ddd4j.web.webflux.error;

import io.ddd4j.core.Constants;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.Map;

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
        log.error(Constants.bizMarker, ex.getMessage(), ex);
    }

    protected void logException(Exception ex, Map<String, Object> detailMap) {

        for (final Map.Entry<String, Object> entry : detailMap.entrySet()) {
            Object val = entry.getValue();
            if (val instanceof String) {
                MDC.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }

        log.error(Constants.bizMarker, ex.getMessage(), ex);
    }

    protected void logException(Exception ex, String code) {

        MDC.put("clazz", ex.getClass().getName());
        MDC.put("type", ex.getClass().getSimpleName());
        MDC.put("code", code);
        MDC.put("msg", ex.getClass().getSimpleName());

        // 自身类.class.isAssignableFrom(自身类或子类.class)
        // Exception.class.isAssignableFrom(ex.getClass())

        log.error(Constants.bizMarker, ex.getMessage(), ex);
    }

}
