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
     * \u58f0\u660e\u5f53\u524d\u5168\u5c40 Advice \u53ef\u53c2\u4e0e MVC \u8bf7\u6c42\u4f53\u8bfb\u53d6\u3002
     * @param methodParameter \u5f53\u524d\u63a7\u5236\u5668\u53c2\u6570\u7684\u58f0\u660e
     * @param type \u76ee\u6807 Java \u7c7b\u578b\u6216\u58f0\u660e\u7684\u6cdb\u578b\u7c7b\u578b
     * @param aClass \u5f53\u524d\u6d88\u606f\u4f53\u6240\u4f7f\u7528\u7684\u6d88\u606f\u8f6c\u6362\u5668\u7c7b\u578b
     * @return \u5f53\u524d Advice \u53ef\u4ee5\u5904\u7406\u65f6\u8fd4\u56de true
     */
    @Override
    public boolean supports(MethodParameter methodParameter, Type type, Class<? extends HttpMessageConverter<?>> aClass) {
        return true;
    }

    /**
     * \u5728\u6d88\u606f\u8f6c\u6362\u524d\u8fd4\u56de\u5f53\u524d Spring HTTP \u8f93\u5165\u5bf9\u8c61\u3002
     * @param httpInputMessage \u4ece\u4e2d\u8bfb\u53d6\u5f53\u524d\u8bf7\u6c42\u6b63\u6587\u7684 HTTP \u8f93\u5165\u5bf9\u8c61
     * @param methodParameter \u5f53\u524d\u63a7\u5236\u5668\u53c2\u6570\u7684\u58f0\u660e
     * @param type \u76ee\u6807 Java \u7c7b\u578b\u6216\u58f0\u660e\u7684\u6cdb\u578b\u7c7b\u578b
     * @param aClass \u5f53\u524d\u6d88\u606f\u4f53\u6240\u4f7f\u7528\u7684\u6d88\u606f\u8f6c\u6362\u5668\u7c7b\u578b
     * @return \u7ee7\u7eed\u4ea4\u7ed9\u6d88\u606f\u8f6c\u6362\u5668\u7684\u8f93\u5165\u6d88\u606f
     * @throws IOException \u5f53\u524d\u6846\u67b6\u8bf7\u6c42\u5904\u7406\u3001\u6d88\u606f\u8bfb\u5199\u6216\u53c2\u6570\u8f6c\u6362\u5931\u8d25
     */
    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage httpInputMessage, MethodParameter methodParameter, Type type, Class<? extends HttpMessageConverter<?>> aClass) throws IOException {
        return httpInputMessage;
    }

    /**
     * \u5728\u8bf7\u6c42\u4f53\u8f6c\u6362\u5b8c\u6210\u540e\u5c06\u8bfb\u53d6\u7ed3\u679c\u4ea4\u7ed9\u63a7\u5236\u5668\u3002
     * @param o Spring \u5df2\u8bfb\u51fa\u7684\u6b63\u6587\u6216\u7a7a\u6b63\u6587\u5bf9\u8c61
     * @param httpInputMessage \u4ece\u4e2d\u8bfb\u53d6\u5f53\u524d\u8bf7\u6c42\u6b63\u6587\u7684 HTTP \u8f93\u5165\u5bf9\u8c61
     * @param methodParameter \u5f53\u524d\u63a7\u5236\u5668\u53c2\u6570\u7684\u58f0\u660e
     * @param type \u76ee\u6807 Java \u7c7b\u578b\u6216\u58f0\u660e\u7684\u6cdb\u578b\u7c7b\u578b
     * @param aClass \u5f53\u524d\u6d88\u606f\u4f53\u6240\u4f7f\u7528\u7684\u6d88\u606f\u8f6c\u6362\u5668\u7c7b\u578b
     * @return \u63d0\u4ea4\u7ed9\u63a7\u5236\u5668\u7684\u8bf7\u6c42\u4f53\u5bf9\u8c61
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
     * \u63d0\u4f9b\u7a7a\u8bf7\u6c42\u4f53\u573a\u666f\u7684\u5904\u7406\u56de\u8c03\u3002
     * @param o Spring \u5df2\u8bfb\u51fa\u7684\u6b63\u6587\u6216\u7a7a\u6b63\u6587\u5bf9\u8c61
     * @param httpInputMessage \u4ece\u4e2d\u8bfb\u53d6\u5f53\u524d\u8bf7\u6c42\u6b63\u6587\u7684 HTTP \u8f93\u5165\u5bf9\u8c61
     * @param methodParameter \u5f53\u524d\u63a7\u5236\u5668\u53c2\u6570\u7684\u58f0\u660e
     * @param type \u76ee\u6807 Java \u7c7b\u578b\u6216\u58f0\u660e\u7684\u6cdb\u578b\u7c7b\u578b
     * @param aClass \u5f53\u524d\u6d88\u606f\u4f53\u6240\u4f7f\u7528\u7684\u6d88\u606f\u8f6c\u6362\u5668\u7c7b\u578b
     * @return \u7a7a\u8bf7\u6c42\u4f53\u5bf9\u5e94\u7684\u63a7\u5236\u5668\u8f93\u5165\u503c
     */
    @Override
    public Object handleEmptyBody(Object o, HttpInputMessage httpInputMessage, MethodParameter methodParameter, Type type, Class<? extends HttpMessageConverter<?>> aClass) {
        return o;
    }
}
