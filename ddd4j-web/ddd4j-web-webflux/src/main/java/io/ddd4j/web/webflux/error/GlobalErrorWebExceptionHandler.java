/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
package io.ddd4j.web.webflux.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * WebFlux 全局错误 Web 处理器（纯 Spring Framework {@link WebExceptionHandler}，不依赖 Boot {@code AbstractErrorWebExceptionHandler}）。
 */
@Component
@Order(-2)
public class GlobalErrorWebExceptionHandler implements WebExceptionHandler {

    private final GlobalErrorAttributes errorAttributes;
    private final ObjectMapper objectMapper;

    /**
     * @param errorAttributes 错误属性组装器
     * @param objectMapper    JSON 序列化
     */
    public GlobalErrorWebExceptionHandler(GlobalErrorAttributes errorAttributes, ObjectMapper objectMapper) {
        this.errorAttributes = errorAttributes;
        this.objectMapper = objectMapper;
    }

    /**
     * 捕获未处理异常，写入 JSON 响应（HTTP 200，与历史 Boot 实现保持一致）。
     */
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        errorAttributes.storeError(exchange, ex);
        Map<String, Object> errorPropertiesMap = errorAttributes.assembleError(ex);

        response.setStatusCode(HttpStatus.OK);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] bodyBytes;
        try {
            bodyBytes = objectMapper.writeValueAsBytes(errorPropertiesMap);
        } catch (JsonProcessingException jsonEx) {
            bodyBytes = ("{\"data\":\"INTERNAL SERVER ERROR\"}").getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bodyBytes);
        return response.writeWith(Mono.just(buffer));
    }
}
