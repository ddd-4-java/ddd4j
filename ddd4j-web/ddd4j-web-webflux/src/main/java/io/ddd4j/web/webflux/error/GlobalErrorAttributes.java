/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
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

    /**
     * 与 Boot {@code DefaultErrorAttributes.ERROR_ATTRIBUTE} 语义一致，便于 Router 与 {@link ServerWebExchange} 共用
     */
    public static final String ERROR_ATTRIBUTE = GlobalErrorAttributes.class.getName() + ".ERROR";

    /**
     * \u4ece {@link ServerRequest} \u8bfb\u53d6\u5df2\u5b58\u50a8\u7684\u5f02\u5e38\u3002
     *
     * @param request Router \u8bf7\u6c42
     * @return \u5df2\u5b58\u50a8\u5f02\u5e38\uff0c\u672a\u5b58\u50a8\u65f6\u4e3a\u7a7a
     */
    public Throwable getError(ServerRequest request) {
        return request.attribute(ERROR_ATTRIBUTE)
                .map(Throwable.class::cast)
                .orElse(null);
    }

    /**
     * \u4ece {@link ServerWebExchange} \u8bfb\u53d6\u5df2\u5b58\u50a8\u7684\u5f02\u5e38\u3002
     *
     * @param exchange Web \u4ea4\u6362\u4e0a\u4e0b\u6587
     * @return \u5df2\u5b58\u50a8\u5f02\u5e38\uff0c\u672a\u5b58\u50a8\u65f6\u4e3a\u7a7a
     */
    public Throwable getError(ServerWebExchange exchange) {
        return exchange.getAttribute(ERROR_ATTRIBUTE);
    }

    /**
     * \u5c06\u5f02\u5e38\u5199\u5165 exchange\uff0c\u4f9b\u540e\u7eed\u9519\u8bef\u5904\u7406\u94fe\u8bfb\u53d6\u3002
     *
     * @param exchange Web \u4ea4\u6362\u4e0a\u4e0b\u6587
     * @param error \u5f85\u5b58\u50a8\u5f02\u5e38
     */
    public void storeError(ServerWebExchange exchange, Throwable error) {
        exchange.getAttributes().put(ERROR_ATTRIBUTE, error);
    }

    /**
     * \u6309 Router \u98ce\u683c\u8bf7\u6c42\u7ec4\u88c5\u9519\u8bef JSON \u5b57\u6bb5\uff08\u517c\u5bb9\u5386\u53f2 API\uff09\u3002
     *
     * @param request Router \u8bf7\u6c42
     * @param includeStackTrace \u662f\u5426\u5305\u542b\u5806\u6808\u4fe1\u606f\uff1b\u517c\u5bb9\u53c2\u6570\uff0c\u5f53\u524d\u9519\u8bef\u5b57\u6bb5\u4e0d\u5305\u542b\u5806\u6808
     * @return \u9519\u8bef JSON \u5b57\u6bb5
     */
    public Map<String, Object> getErrorAttributes(ServerRequest request, boolean includeStackTrace) {
        return assembleError(getError(request));
    }

    /**
     * \u6309\u5f02\u5e38\u5b9e\u4f8b\u7ec4\u88c5\u9519\u8bef JSON \u5b57\u6bb5\u3002
     *
     * @param error \u5f02\u5e38\u5b9e\u4f8b\uff0c\u53ef\u4e3a\u7a7a
     * @return \u9519\u8bef JSON \u5b57\u6bb5
     */
    public Map<String, Object> assembleError(Throwable error) {
        Map<String, Object> errorAttributes = new LinkedHashMap<>();
        if (error instanceof ServerException) {
            errorAttributes.put("data", error.getMessage());
        } else if (Objects.nonNull(error)) {
            errorAttributes.put("code", HttpStatus.INTERNAL_SERVER_ERROR);
            errorAttributes.put("data", Objects.nonNull(error.getMessage()) ? error.getMessage() : "INTERNAL SERVER ERROR");
        } else {
            errorAttributes.put("code", HttpStatus.INTERNAL_SERVER_ERROR);
            errorAttributes.put("data", "INTERNAL SERVER ERROR");
        }
        return errorAttributes;
    }
}
