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
package io.ddd4j.web.webmvc.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractGenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.lang.Nullable;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 自定义 Jackson 2 {@link HttpMessageConverter}。
 *
 * <p>2.0.x 使用 Jackson 2 {@link ObjectMapper}；本类避免将 MVC 转换器实现
 * 与 Spring 默认转换器的内部配置耦合。</p>
 */
public class CustomHttpMessageConverter extends AbstractGenericHttpMessageConverter<Object> {

    private final ObjectMapper objectMapper;

    public CustomHttpMessageConverter(ObjectMapper objectMapper) {
        super(MediaType.APPLICATION_JSON, new MediaType("application", "*+json"));
        this.objectMapper = objectMapper;
    }

    @Override
    protected void writeInternal(Object object, @Nullable Type type, HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotWritableException {
        Charset charset = getContentTypeCharset(outputMessage.getHeaders().getContentType());
        objectMapper.writeValue(
                new OutputStreamWriter(outputMessage.getBody(), charset), object);
    }

    @Override
    protected Object readInternal(Class<?> clazz, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
        Charset charset = getContentTypeCharset(inputMessage.getHeaders().getContentType());
        return objectMapper.readValue(
                new InputStreamReader(inputMessage.getBody(), charset), clazz);
    }

    @Override
    public Object read(Type type, @Nullable Class<?> contextClass, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
        Charset charset = getContentTypeCharset(inputMessage.getHeaders().getContentType());
        return objectMapper.readValue(
                new InputStreamReader(inputMessage.getBody(), charset),
                objectMapper.constructType(type));
    }

    private Charset getContentTypeCharset(@Nullable MediaType contentType) {
        if (contentType != null && contentType.getCharset() != null) {
            return contentType.getCharset();
        }
        return StandardCharsets.UTF_8;
    }
}
