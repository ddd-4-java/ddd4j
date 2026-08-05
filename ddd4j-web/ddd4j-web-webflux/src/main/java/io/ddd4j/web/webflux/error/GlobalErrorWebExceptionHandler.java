/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
package io.ddd4j.web.webflux.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.web.core.error.WebError;
import io.ddd4j.web.core.error.WebExceptionTranslator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * WebFlux 全局错误 Web 处理器（纯 Spring Framework {@link WebExceptionHandler}，不依赖 Boot {@code AbstractErrorWebExceptionHandler}）。
 */
@Component
@Order(-2)
@Slf4j
public class GlobalErrorWebExceptionHandler implements WebExceptionHandler {

    private final GlobalErrorAttributes errorAttributes;
    private final ObjectMapper objectMapper;
    private final WebExceptionTranslator exceptionTranslator;

    /**
     * @param errorAttributes 错误属性组装器
     * @param objectMapper    JSON 序列化
     */
    public GlobalErrorWebExceptionHandler(GlobalErrorAttributes errorAttributes, ObjectMapper objectMapper,
                                          WebExceptionTranslator exceptionTranslator) {
        this.errorAttributes = Objects.requireNonNull(errorAttributes, "errorAttributes must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.exceptionTranslator = Objects.requireNonNull(exceptionTranslator,
                "exceptionTranslator must not be null");
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
        WebError error = exceptionTranslator.translate(ex);
        if (error.status() >= 500) {
            log.error("Unhandled WebFlux request failure", ex);
        }

        response.setStatusCode(HttpStatusCode.valueOf(error.status()));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] bodyBytes;
        try {
            bodyBytes = objectMapper.writeValueAsBytes(error.toResponse());
        } catch (JsonProcessingException jsonEx) {
            log.error("Unable to serialize WebFlux error response", jsonEx);
            bodyBytes = ("{\"code\":500,\"msg\":\"Internal Server Error\",\"data\":null}")
                    .getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bodyBytes);
        return response.writeWith(Mono.just(buffer));
    }
}
