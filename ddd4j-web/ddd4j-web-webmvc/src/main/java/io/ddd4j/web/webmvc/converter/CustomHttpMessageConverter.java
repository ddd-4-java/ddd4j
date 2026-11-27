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

    /**
     * 用于读写 JSON 数据的 Jackson 映射器。
     */
    private final ObjectMapper objectMapper;

    /**
     * 创建使用指定 Jackson 映射器的 HTTP 消息转换器。
     *
     * @param objectMapper Jackson 映射器
     */
    public CustomHttpMessageConverter(ObjectMapper objectMapper) {
        super(MediaType.APPLICATION_JSON, new MediaType("application", "*+json"));
        this.objectMapper = objectMapper;
    }

    /**
     * 将 Java 对象序列化并写入 HTTP 输出消息。
     *
     * @param object        待写入或格式化的 Java 对象
     * @param type          目标 Java 类型或声明的泛型类型
     * @param outputMessage 即将写出响应体的 Spring HTTP 输出对象
     * @throws IOException                     当前框架请求处理、消息读写或参数转换失败
     * @throws HttpMessageNotWritableException 当前框架请求处理、消息读写或参数转换失败
     */
    @Override
    protected void writeInternal(Object object, @Nullable Type type, HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotWritableException {
        Charset charset = getContentTypeCharset(outputMessage.getHeaders().getContentType());
        objectMapper.writeValue(
                new OutputStreamWriter(outputMessage.getBody(), charset), object);
    }

    /**
     * 把 Spring HTTP 正文读取成指定的 Java 类。
     *
     * @param clazz        目标对象的运行时 Java 类型
     * @param inputMessage 从中读取正文的 Spring HTTP 输入对象
     * @return 转换后的 Java 对象
     * @throws IOException                     当前框架请求处理、消息读写或参数转换失败
     * @throws HttpMessageNotReadableException 当前框架请求处理、消息读写或参数转换失败
     */
    @Override
    protected Object readInternal(Class<?> clazz, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
        Charset charset = getContentTypeCharset(inputMessage.getHeaders().getContentType());
        return objectMapper.readValue(
                new InputStreamReader(inputMessage.getBody(), charset), clazz);
    }

    /**
     * 结合上下文和目标泛型类型反序列化 HTTP 正文。
     *
     * @param type         目标 Java 类型或声明的泛型类型
     * @param contextClass 为泛型转换提供上下文的 Java 类型
     * @param inputMessage 从中读取正文的 Spring HTTP 输入对象
     * @return 转换后的目标 Java 对象
     * @throws IOException                     当前框架请求处理、消息读写或参数转换失败
     * @throws HttpMessageNotReadableException 当前框架请求处理、消息读写或参数转换失败
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
     * 读取媒体类型声明的字符集，未声明时用 UTF-8。
     *
     * @param contentType 需要检查字符编码的 HTTP 媒体类型
     * @return HTTP 正文编码使用的字符集
     */
    private Charset getContentTypeCharset(@Nullable MediaType contentType) {
        if (contentType != null && contentType.getCharset() != null) {
            return contentType.getCharset();
        }
        return StandardCharsets.UTF_8;
    }
}
