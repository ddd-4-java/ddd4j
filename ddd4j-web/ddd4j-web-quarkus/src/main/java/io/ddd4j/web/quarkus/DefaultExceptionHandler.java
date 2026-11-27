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

    /** {@inheritDoc} */
    @Override
    protected String frameworkName() {
        return "Quarkus";
    }

    /**
     * \u5148\u5f52\u4e00 JAX-RS {@link WebApplicationException}\uff08\u4fdd\u7559\u5bb9\u5668\u7ed9\u51fa\u7684\u72b6\u6001\u4e0e\u6d88\u606f\uff09\uff0c
     *  \u5176\u4f59\u5f02\u5e38\u8d70 ddd4j \u901a\u7528\u7ffb\u8bd1\u3002
     *
     * @param exception \u5f53\u524d\u8bf7\u6c42\u629b\u51fa\u7684\u5f02\u5e38
     * @return \u5171\u7528\u5f02\u5e38\u6620\u5c04\u8fd4\u56de\u7684\u54cd\u5e94
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
     * \u5c06\u8fd0\u884c\u65f6\u5f02\u5e38\u6620\u5c04\u4e3a Quarkus \u53ef\u8fd4\u56de\u7684 HTTP \u9519\u8bef\u3002
     * @param exception \u5f53\u524d\u8bf7\u6c42\u5904\u7406\u65f6\u629b\u51fa\u7684\u5f02\u5e38
     * @return \u6846\u67b6\u54cd\u5e94\u4e2d\u7684\u72b6\u6001\u548c\u9519\u8bef\u6b63\u6587
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
