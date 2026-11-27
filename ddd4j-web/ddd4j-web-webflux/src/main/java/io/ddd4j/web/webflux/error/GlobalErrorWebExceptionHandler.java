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

    /** \u8bfb\u53d6 WebFlux \u8bf7\u6c42\u9519\u8bef\u53ca\u5176\u5c5e\u6027\u7684\u7ec4\u4ef6\u3002 */
    private final GlobalErrorAttributes errorAttributes;
    /** \u7528\u4e8e\u8bfb\u5199 JSON \u6570\u636e\u7684 Jackson \u6620\u5c04\u5668\u3002 */
    private final ObjectMapper objectMapper;
    /** 保存 <code>exception Translator</code> 对应的 <code>WebExceptionTranslator</code> 数据。 */
    private final WebExceptionTranslator exceptionTranslator;

    /**
     * 初始化 <code>GlobalErrorWebExceptionHandler</code> 使用的 <code>errorAttributes</code>、<code>objectMapper</code>、<code>exceptionTranslator</code>。
     *
     * @param errorAttributes \u9519\u8bef\u5c5e\u6027\u7ec4\u88c5\u5668
     * @param objectMapper JSON \u5e8f\u5217\u5316
     * @param exceptionTranslator \u5f02\u5e38\u7ffb\u8bd1\u5668
     */
    public GlobalErrorWebExceptionHandler(GlobalErrorAttributes errorAttributes, ObjectMapper objectMapper,
                                          WebExceptionTranslator exceptionTranslator) {
        this.errorAttributes = Objects.requireNonNull(errorAttributes, "errorAttributes must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.exceptionTranslator = Objects.requireNonNull(exceptionTranslator,
                "exceptionTranslator must not be null");
    }

    /**
     * \u6355\u83b7\u672a\u5904\u7406\u5f02\u5e38\uff0c\u5e76\u6309\u7edf\u4e00\u5f02\u5e38\u8bed\u4e49\u5199\u5165 HTTP \u72b6\u6001\u548c\u54cd\u5e94\u4f53\u3002
     *
     * @param exchange \u6b63\u5728\u5904\u7406\u7684 WebFlux \u8bf7\u6c42\u548c\u54cd\u5e94
     * @param ex \u5f85\u5904\u7406\u7684 WebFlux \u8bf7\u6c42\u5f02\u5e38
     * @return \u5728\u9519\u8bef\u54cd\u5e94\u63d0\u4ea4\u540e\u5b8c\u6210\u7684\u54cd\u5e94\u5f0f\u4fe1\u53f7
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
