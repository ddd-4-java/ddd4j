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

import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.kit.lang.JsonKit;
import io.ddd4j.spring.util.BeanKit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdvice;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;

/**
 * 全局请求体通知处理器。
 * <p>拦截所有请求体，将请求参数转换为 JSON 并存入 {@link ThreadContext}，同时记录请求日志。</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@RestControllerAdvice
@Slf4j(topic = "==> RequestBody")
public class GlobalRequestAdvice implements RequestBodyAdvice {

    /**
     * 声明当前全局 Advice 可参与 MVC 请求体读取。
     *
     * @param methodParameter 当前控制器参数的声明
     * @param type            目标 Java 类型或声明的泛型类型
     * @param aClass          当前消息体所使用的消息转换器类型
     * @return 当前 Advice 可以处理时返回 true
     */
    @Override
    public boolean supports(MethodParameter methodParameter, Type type, Class<? extends HttpMessageConverter<?>> aClass) {
        return true;
    }

    /**
     * 在消息转换前返回当前 Spring HTTP 输入对象。
     *
     * @param httpInputMessage 从中读取当前请求正文的 HTTP 输入对象
     * @param methodParameter  当前控制器参数的声明
     * @param type             目标 Java 类型或声明的泛型类型
     * @param aClass           当前消息体所使用的消息转换器类型
     * @return 继续交给消息转换器的输入消息
     * @throws IOException 当前框架请求处理、消息读写或参数转换失败
     */
    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage httpInputMessage, MethodParameter methodParameter, Type type, Class<? extends HttpMessageConverter<?>> aClass) throws IOException {
        return httpInputMessage;
    }

    /**
     * 在请求体转换完成后将读取结果交给控制器。
     *
     * @param o                Spring 已读出的正文或空正文对象
     * @param httpInputMessage 从中读取当前请求正文的 HTTP 输入对象
     * @param methodParameter  当前控制器参数的声明
     * @param type             目标 Java 类型或声明的泛型类型
     * @param aClass           当前消息体所使用的消息转换器类型
     * @return 提交给控制器的请求体对象
     */
    @Override
    public Object afterBodyRead(Object o, HttpInputMessage httpInputMessage, MethodParameter methodParameter, Type type, Class<? extends HttpMessageConverter<?>> aClass) {
        Map<String, Object> objectMap = BeanKit.toMapClean(o);
        String body = JsonKit.toJson(objectMap);
        ThreadContext.set(ContextConstants.REQUEST_BODY, body);
        log.info("{}", body);
        return o;
    }

    /**
     * 提供空请求体场景的处理回调。
     *
     * @param o                Spring 已读出的正文或空正文对象
     * @param httpInputMessage 从中读取当前请求正文的 HTTP 输入对象
     * @param methodParameter  当前控制器参数的声明
     * @param type             目标 Java 类型或声明的泛型类型
     * @param aClass           当前消息体所使用的消息转换器类型
     * @return 空请求体对应的控制器输入值
     */
    @Override
    public Object handleEmptyBody(Object o, HttpInputMessage httpInputMessage, MethodParameter methodParameter, Type type, Class<? extends HttpMessageConverter<?>> aClass) {
        return o;
    }
}
