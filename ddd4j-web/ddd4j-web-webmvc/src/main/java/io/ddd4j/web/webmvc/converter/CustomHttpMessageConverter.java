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
 * 自定义 Jackson 2 {@link org.springframework.http.converter.HttpMessageConverter}。
 *
 * <p>2.0.x 使用 Jackson 2 {@link ObjectMapper}；本类避免将 MVC 转换器实现
 * 与 Spring 默认转换器的内部配置耦合。</p>
 */
public class CustomHttpMessageConverter extends AbstractGenericHttpMessageConverter<Object> {

    /** \u7528\u4e8e\u8bfb\u5199 JSON \u6570\u636e\u7684 Jackson \u6620\u5c04\u5668\u3002 */
    private final ObjectMapper objectMapper;

    /**
     * \u521b\u5efa\u4f7f\u7528\u6307\u5b9a Jackson \u6620\u5c04\u5668\u7684 HTTP \u6d88\u606f\u8f6c\u6362\u5668\u3002
     * @param objectMapper Jackson \u6620\u5c04\u5668
     */
    public CustomHttpMessageConverter(ObjectMapper objectMapper) {
        super(MediaType.APPLICATION_JSON, new MediaType("application", "*+json"));
        this.objectMapper = objectMapper;
    }

    /**
     * \u5c06 Java \u5bf9\u8c61\u5e8f\u5217\u5316\u5e76\u5199\u5165 HTTP \u8f93\u51fa\u6d88\u606f\u3002
     * @param object \u5f85\u5199\u5165\u6216\u683c\u5f0f\u5316\u7684 Java \u5bf9\u8c61
     * @param type \u76ee\u6807 Java \u7c7b\u578b\u6216\u58f0\u660e\u7684\u6cdb\u578b\u7c7b\u578b
     * @param outputMessage \u5373\u5c06\u5199\u51fa\u54cd\u5e94\u4f53\u7684 Spring HTTP \u8f93\u51fa\u5bf9\u8c61
     * @throws IOException \u5f53\u524d\u6846\u67b6\u8bf7\u6c42\u5904\u7406\u3001\u6d88\u606f\u8bfb\u5199\u6216\u53c2\u6570\u8f6c\u6362\u5931\u8d25
     * @throws HttpMessageNotWritableException \u5f53\u524d\u6846\u67b6\u8bf7\u6c42\u5904\u7406\u3001\u6d88\u606f\u8bfb\u5199\u6216\u53c2\u6570\u8f6c\u6362\u5931\u8d25
     */
    @Override
    protected void writeInternal(Object object, @Nullable Type type, HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotWritableException {
        Charset charset = getContentTypeCharset(outputMessage.getHeaders().getContentType());
        objectMapper.writeValue(
                new OutputStreamWriter(outputMessage.getBody(), charset), object);
    }

    /**
     * \u628a Spring HTTP \u6b63\u6587\u8bfb\u53d6\u6210\u6307\u5b9a\u7684 Java \u7c7b\u3002
     * @param clazz \u76ee\u6807\u5bf9\u8c61\u7684\u8fd0\u884c\u65f6 Java \u7c7b\u578b
     * @param inputMessage \u4ece\u4e2d\u8bfb\u53d6\u6b63\u6587\u7684 Spring HTTP \u8f93\u5165\u5bf9\u8c61
     * @return \u8f6c\u6362\u540e\u7684 Java \u5bf9\u8c61
     * @throws IOException \u5f53\u524d\u6846\u67b6\u8bf7\u6c42\u5904\u7406\u3001\u6d88\u606f\u8bfb\u5199\u6216\u53c2\u6570\u8f6c\u6362\u5931\u8d25
     * @throws HttpMessageNotReadableException \u5f53\u524d\u6846\u67b6\u8bf7\u6c42\u5904\u7406\u3001\u6d88\u606f\u8bfb\u5199\u6216\u53c2\u6570\u8f6c\u6362\u5931\u8d25
     */
    @Override
    protected Object readInternal(Class<?> clazz, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
        Charset charset = getContentTypeCharset(inputMessage.getHeaders().getContentType());
        return objectMapper.readValue(
                new InputStreamReader(inputMessage.getBody(), charset), clazz);
    }

    /**
     * \u7ed3\u5408\u4e0a\u4e0b\u6587\u548c\u76ee\u6807\u6cdb\u578b\u7c7b\u578b\u53cd\u5e8f\u5217\u5316 HTTP \u6b63\u6587\u3002
     * @param type \u76ee\u6807 Java \u7c7b\u578b\u6216\u58f0\u660e\u7684\u6cdb\u578b\u7c7b\u578b
     * @param contextClass \u4e3a\u6cdb\u578b\u8f6c\u6362\u63d0\u4f9b\u4e0a\u4e0b\u6587\u7684 Java \u7c7b\u578b
     * @param inputMessage \u4ece\u4e2d\u8bfb\u53d6\u6b63\u6587\u7684 Spring HTTP \u8f93\u5165\u5bf9\u8c61
     * @return \u8f6c\u6362\u540e\u7684\u76ee\u6807 Java \u5bf9\u8c61
     * @throws IOException \u5f53\u524d\u6846\u67b6\u8bf7\u6c42\u5904\u7406\u3001\u6d88\u606f\u8bfb\u5199\u6216\u53c2\u6570\u8f6c\u6362\u5931\u8d25
     * @throws HttpMessageNotReadableException \u5f53\u524d\u6846\u67b6\u8bf7\u6c42\u5904\u7406\u3001\u6d88\u606f\u8bfb\u5199\u6216\u53c2\u6570\u8f6c\u6362\u5931\u8d25
     */
    @Override
    public Object read(Type type, @Nullable Class<?> contextClass, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
        Charset charset = getContentTypeCharset(inputMessage.getHeaders().getContentType());
        return objectMapper.readValue(
                new InputStreamReader(inputMessage.getBody(), charset),
                objectMapper.constructType(type));
    }

    /**
     * \u8bfb\u53d6\u5a92\u4f53\u7c7b\u578b\u58f0\u660e\u7684\u5b57\u7b26\u96c6\uff0c\u672a\u58f0\u660e\u65f6\u7528 UTF-8\u3002
     * @param contentType \u9700\u8981\u68c0\u67e5\u5b57\u7b26\u7f16\u7801\u7684 HTTP \u5a92\u4f53\u7c7b\u578b
     * @return HTTP \u6b63\u6587\u7f16\u7801\u4f7f\u7528\u7684\u5b57\u7b26\u96c6
     */
    private Charset getContentTypeCharset(@Nullable MediaType contentType) {
        if (contentType != null && contentType.getCharset() != null) {
            return contentType.getCharset();
        }
        return StandardCharsets.UTF_8;
    }
}
