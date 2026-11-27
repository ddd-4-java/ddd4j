/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

    /** \u8bfb\u53d6 WebFlux \u8bf7\u6c42\u9519\u8bef\u53ca\u5176\u5c5e\u6027\u7684\u7ec4\u4ef6\u3002 */
    private final GlobalErrorAttributes errorAttributes;
    /** \u7528\u4e8e\u8bfb\u5199 JSON \u6570\u636e\u7684 Jackson \u6620\u5c04\u5668\u3002 */
    private final ObjectMapper objectMapper;

    /**
     * @param exceptionTranslator 参数 exceptionTranslator
     * @param errorAttributes 错误属性组装器
     * @param objectMapper    JSON 序列化
     */
    public GlobalErrorWebExceptionHandler(GlobalErrorAttributes errorAttributes, ObjectMapper objectMapper,
                                          WebExceptionTranslator exceptionTranslator) {
        super(exceptionTranslator);
        this.errorAttributes = Objects.requireNonNull(errorAttributes, "errorAttributes must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * 返回当前适配实现对应的运行框架名称。
     *
     * @return 框架标识
     */
    @Override
    protected String frameworkName() {
        return "WebFlux";
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
        WebError error = translate(ex);
        logUnhandled(ex, error);

        response.setRawStatusCode(error.status());
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
