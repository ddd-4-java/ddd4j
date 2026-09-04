/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 */
package io.ddd4j.web.webflux.error;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;

import java.rmi.ServerException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * WebFlux 全局错误属性提取（纯 Spring Framework，不依赖 Boot {@code DefaultErrorAttributes}）。
 */
@Component
public class GlobalErrorAttributes {

    /** 与 Boot {@code DefaultErrorAttributes.ERROR_ATTRIBUTE} 语义一致，便于 Router 与 {@link ServerWebExchange} 共用 */
    public static final String ERROR_ATTRIBUTE = GlobalErrorAttributes.class.getName() + ".ERROR";

    /**
     * 从 {@link ServerRequest} 读取已存储的异常。
     */
    public Throwable getError(ServerRequest request) {
        return request.attribute(ERROR_ATTRIBUTE)
                .map(Throwable.class::cast)
                .orElse(null);
    }

    /**
     * 从 {@link ServerWebExchange} 读取已存储的异常。
     */
    public Throwable getError(ServerWebExchange exchange) {
        return exchange.getAttribute(ERROR_ATTRIBUTE);
    }

    /**
     * 将异常写入 exchange，供后续错误处理链读取。
     */
    public void storeError(ServerWebExchange exchange, Throwable error) {
        exchange.getAttributes().put(ERROR_ATTRIBUTE, error);
    }

    /**
     * 按 Router 风格请求组装错误 JSON 字段（兼容历史 API）。
     */
    public Map<String, Object> getErrorAttributes(ServerRequest request, boolean includeStackTrace) {
        return assembleError(getError(request));
    }

    /**
     * 按异常实例组装错误 JSON 字段。
     */
    public Map<String, Object> assembleError(Throwable error) {
        Map<String, Object> errorAttributes = new LinkedHashMap<>();
        if (error instanceof ServerException) {
            errorAttributes.put("data", error.getMessage());
        } else if (Objects.nonNull(error)) {
            errorAttributes.put("code", HttpStatus.INTERNAL_SERVER_ERROR);
            errorAttributes.put("data", error.getMessage() != null ? error.getMessage() : "INTERNAL SERVER ERROR");
        } else {
            errorAttributes.put("code", HttpStatus.INTERNAL_SERVER_ERROR);
            errorAttributes.put("data", "INTERNAL SERVER ERROR");
        }
        return errorAttributes;
    }
}
