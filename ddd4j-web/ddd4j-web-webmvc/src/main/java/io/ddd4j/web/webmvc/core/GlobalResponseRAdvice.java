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
package io.ddd4j.web.webmvc.core;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import io.ddd4j.annotation.api.RawResponse;
import io.ddd4j.core.api.IR;
import io.ddd4j.core.api.R;
import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.core.exception.BizRuntimeException;
import io.ddd4j.web.webmvc.config.BaseWebProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Objects;

/**
 * 全局响应体包装处理器。
 * <p>将所有控制器返回值统一包装为 {@link R} 格式，支持 String 类型特殊处理及 {@link RawResponse} 注解跳过包装。</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@RestControllerAdvice
public class GlobalResponseRAdvice implements ResponseBodyAdvice<Object> {
    /**
     * MVC ObjectMapper
     */
    @Autowired
    @Qualifier("mvcObjectMapper")
    ObjectMapper objectMapper;
    /**
     * Web 基础配置属性
     */
    @Autowired
    BaseWebProperties baseWebProperties;

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> aClass) {
        return baseWebProperties.getMvc().getEnableRResponse()
                && !IR.class.isAssignableFrom(returnType.getParameterType())
                && !returnType.hasMethodAnnotation(RawResponse.class);
    }

    @Override
    public Object beforeBodyWrite(Object data, MethodParameter returnType, MediaType mediaType,
                                  Class<? extends HttpMessageConverter<?>> aClass,
                                  ServerHttpRequest serverHttpRequest, ServerHttpResponse serverHttpResponse) {
        // 兼容声明为 Object 等宽泛类型、但运行时已显式返回 R 的控制器方法。
        if (data instanceof IR) {
            return data;
        }
        if (Objects.isNull(data) || returnType.getParameterType().isAssignableFrom(void.class)) {
            return R.ok();
        }
        if (AggregateRoot.class.isAssignableFrom(returnType.getParameterType())) {
            return R.ok(data);
        }
        // String类型不能直接包装
        if (returnType.getGenericParameterType().equals(String.class)) {
            try {
                //将数据包装在R对象里后转换为json串进行返回
                return objectMapper.writeValueAsString(R.ok(data));
            } catch (JacksonException e) {
                throw new BizRuntimeException(e);
            }
        }
        //否则直接包装成R对象返回
        return R.ok(data);
    }
}
