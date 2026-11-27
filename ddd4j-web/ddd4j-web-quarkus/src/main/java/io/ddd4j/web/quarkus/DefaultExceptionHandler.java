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
package io.ddd4j.web.quarkus;

import io.ddd4j.web.core.error.WebError;
import io.ddd4j.web.core.error.WebExceptionTranslator;
import io.ddd4j.web.error.BaseErrorConfiguration;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Maps Quarkus request failures to the shared ddd4j HTTP error contract.
 */
@Provider
public class DefaultExceptionHandler extends BaseErrorConfiguration implements ExceptionMapper<RuntimeException> {

    /**
     * 使用默认异常翻译器创建处理器。
     */
    public DefaultExceptionHandler() {
        super();
    }

    /**
     * 使用指定异常翻译器创建处理器。
     *
     * @param translator 异常翻译器
     */
    public DefaultExceptionHandler(WebExceptionTranslator translator) {
        super(translator);
    }

    /**
     * 返回当前适配实现对应的运行框架名称。
     *
     * @return 框架标识
     */
    @Override
    protected String frameworkName() {
        return "Quarkus";
    }

    /**
     * 先归一 JAX-RS {@link WebApplicationException}（保留容器给出的状态与消息），
     * 其余异常走 ddd4j 通用翻译。
     *
     * @param exception 当前请求抛出的异常
     * @return 共用异常映射返回的响应
     */
    @Override
    protected WebError doTranslate(Throwable exception) {
        if (exception instanceof WebApplicationException) {
            WebApplicationException webException = (WebApplicationException) exception;
            return httpStatusError(webException.getResponse().getStatus(),
                    webException.getResponse().getStatusInfo().getReasonPhrase(), webException.getMessage());
        }
        return super.doTranslate(exception);
    }

    /**
     * 将运行时异常映射为 Quarkus 可返回的 HTTP 错误。
     *
     * @param exception 当前请求处理时抛出的异常
     * @return 框架响应中的状态和错误正文
     */
    @Override
    public Response toResponse(RuntimeException exception) {
        WebError error = translate(exception);
        logUnhandled(exception, error);
        return Response.status(error.status())
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(toResponse(error))
                .build();
    }
}
