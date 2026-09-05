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
package io.ddd4j.web.helidon;

import io.ddd4j.web.core.error.WebError;
import io.ddd4j.web.core.error.WebExceptionTranslator;
import io.ddd4j.web.error.BaseErrorConfiguration;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Helidon MP 全局异常映射。
 *
 * <p>翻译、5xx 日志与响应体构造复用 {@link BaseErrorConfiguration}，
 * 此处只保留 JAX-RS 的 {@code Response} 写出与 {@link WebApplicationException} 归一。
 */
@Provider
public final class Ddd4jHelidonExceptionMapper extends BaseErrorConfiguration implements ExceptionMapper<RuntimeException> {

    public Ddd4jHelidonExceptionMapper() {
        super();
    }

    public Ddd4jHelidonExceptionMapper(WebExceptionTranslator translator) {
        super(translator);
    }

    @Override
    protected String frameworkName() {
        return "Helidon";
    }

    /**
     * 先归一 JAX-RS {@link WebApplicationException}（保留容器给出的状态与消息），
     * 其余异常走 ddd4j 通用翻译。
     */
    @Override
    protected WebError doTranslate(Throwable exception) {
        if (exception instanceof WebApplicationException webException) {
            return httpStatusError(webException.getResponse().getStatus(),
                    webException.getResponse().getStatusInfo().getReasonPhrase(), webException.getMessage());
        }
        return super.doTranslate(exception);
    }

    @Override
    public Response toResponse(RuntimeException exception) {
        WebError error = translate(exception);
        logUnhandled(exception, error);
        return Response.status(error.status()).type(MediaType.APPLICATION_JSON_TYPE)
                .entity(toResponse(error)).build();
    }
}
