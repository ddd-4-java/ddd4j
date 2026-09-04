/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 */
package io.ddd4j.web.webflux.error;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.core.api.R;
import io.ddd4j.web.core.error.WebError;
import io.ddd4j.web.core.error.WebExceptionTranslator;
import io.ddd4j.web.error.BaseErrorConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * WebFlux 全局错误 Web 处理器（纯 Spring Framework {@link WebExceptionHandler}，不依赖 Boot {@code AbstractErrorWebExceptionHandler}）。
 *
 * <p>翻译、5xx 日志与 {@link R} 响应体构造复用 {@link BaseErrorConfiguration}，
 * 此处只保留 Reactive 流式写出与 JSON 序列化。
 */
@Component
@Order(-2)
@Slf4j
public class GlobalErrorWebExceptionHandler extends BaseErrorConfiguration implements WebExceptionHandler {

    private final GlobalErrorAttributes errorAttributes;
    private final ObjectMapper objectMapper;

    /**
     * @param errorAttributes 错误属性组装器
     * @param objectMapper    JSON 序列化
     */
    public GlobalErrorWebExceptionHandler(GlobalErrorAttributes errorAttributes, ObjectMapper objectMapper,
                                          WebExceptionTranslator exceptionTranslator) {
        super(exceptionTranslator);
        this.errorAttributes = Objects.requireNonNull(errorAttributes, "errorAttributes must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    protected String frameworkName() {
        return "WebFlux";
    }

    /**
     * 捕获未处理异常，并按统一异常语义写入 HTTP 状态和响应体。
     */
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        errorAttributes.storeError(exchange, ex);
        WebError error = translate(ex);
        logUnhandled(ex, error);

        response.setStatusCode(HttpStatus.valueOf(error.status()));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] bodyBytes;
        try {
            bodyBytes = objectMapper.writeValueAsBytes(toResponse(error));
        } catch (JacksonException jsonEx) {
            log.error("Unable to serialize WebFlux error response", jsonEx);
            bodyBytes = fallbackBody();
        }

        DataBuffer buffer = response.bufferFactory().wrap(bodyBytes);
        return response.writeWith(Mono.just(buffer));
    }
}
