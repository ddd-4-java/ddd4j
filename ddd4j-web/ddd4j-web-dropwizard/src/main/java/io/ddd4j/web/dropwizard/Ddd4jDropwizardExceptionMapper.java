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
package io.ddd4j.web.dropwizard;

import io.ddd4j.web.core.error.WebError;
import io.ddd4j.web.core.error.WebExceptionTranslator;
import io.ddd4j.web.error.BaseErrorConfiguration;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

import java.util.List;
import java.util.Objects;

/**
 * Dropwizard Jersey 全局异常映射。
 *
 * <p>翻译、5xx 日志与响应体构造复用 {@link BaseErrorConfiguration}，
 * 此处只保留 JAX-RS 的 {@code Response} 写出与 {@link WebApplicationException} 归一。
 */
public final class Ddd4jDropwizardExceptionMapper extends BaseErrorConfiguration implements ExceptionMapper<Throwable> {

    /** 使用默认异常翻译器创建映射器。 */
    public Ddd4jDropwizardExceptionMapper() {
        super();
    }

    /**
     * 使用指定异常翻译器创建映射器。
     *
     * @param translator 异常翻译器
     */
    public Ddd4jDropwizardExceptionMapper(WebExceptionTranslator translator) {
        super(translator);
    }

    /**
     * 返回当前适配实现对应的运行框架名称。
     *
     * @return 框架标识
     */
    @Override
    protected String frameworkName() {
        return "Dropwizard";
    }

    /**
     * \u5148\u5f52\u4e00 JAX-RS {@link WebApplicationException}\uff08\u4fdd\u7559\u5bb9\u5668\u7ed9\u51fa\u7684\u72b6\u6001\u4e0e\u6d88\u606f\uff09\uff0c
     *  \u5176\u4f59\u5f02\u5e38\u8d70 ddd4j \u901a\u7528\u7ffb\u8bd1\u3002
     *
     * @param exception \u5f53\u524d\u8bf7\u6c42\u629b\u51fa\u7684\u5f02\u5e38
     * @return \u547d\u4e2d\u7684\u5f02\u5e38\u6620\u5c04\u7ed3\u679c
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
     * \u5c06\u6846\u67b6\u5f02\u5e38\u8f6c\u6362\u6210\u5e26 HTTP \u72b6\u6001\u7801\u548c JSON \u6b63\u6587\u7684\u5171\u7528\u9519\u8bef\u54cd\u5e94\u3002
     * @param exception \u5f53\u524d HTTP \u8bf7\u6c42\u629b\u51fa\u7684\u5f02\u5e38
     * @return \u63d0\u4ea4\u7ed9\u8fd0\u884c\u6846\u67b6\u7684\u5f02\u5e38\u54cd\u5e94
     */
    @Override
    public Response toResponse(Throwable exception) {
        WebError error = translate(exception);
        logUnhandled(exception, error);
        Response.ResponseBuilder response = Response.status(error.status())
                .type(MediaType.APPLICATION_JSON_TYPE).entity(toResponse(error));
        if (error.status() == Response.Status.METHOD_NOT_ALLOWED.getStatusCode()
                && exception instanceof WebApplicationException) {
            WebApplicationException webException = (WebApplicationException) exception;
            // 保留 Jersey 路由器给出的 Allow 响应头。
            List<Object> allowedMethods = webException.getResponse().getHeaders().get(HttpHeaders.ALLOW);
            if (Objects.nonNull(allowedMethods)) {
                allowedMethods.forEach(value -> response.header(HttpHeaders.ALLOW, value));
            }
        }
        return response.build();
    }
}
