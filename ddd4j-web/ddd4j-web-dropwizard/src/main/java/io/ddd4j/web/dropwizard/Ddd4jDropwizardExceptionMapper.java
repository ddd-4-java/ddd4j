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

import io.ddd4j.web.core.error.DefaultWebExceptionTranslator;
import io.ddd4j.web.core.error.WebError;
import io.ddd4j.web.core.error.WebExceptionTranslator;
import io.ddd4j.kit.lang.StrKit;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * Dropwizard Jersey 全局异常映射。
 */
@Slf4j
public final class Ddd4jDropwizardExceptionMapper implements ExceptionMapper<Throwable> {

    private final WebExceptionTranslator translator;

    public Ddd4jDropwizardExceptionMapper() {
        this(new DefaultWebExceptionTranslator());
    }

    public Ddd4jDropwizardExceptionMapper(WebExceptionTranslator translator) {
        this.translator = Objects.requireNonNull(translator, "translator must not be null");
    }

    @Override
    public Response toResponse(Throwable exception) {
        WebError error = translate(exception);
        if (error.status() >= 500) {
            log.error("Unhandled Dropwizard HTTP request failure", exception);
        }
        return Response.status(error.status()).type(MediaType.APPLICATION_JSON_TYPE)
                .entity(error.toResponse()).build();
    }

    private WebError translate(Throwable exception) {
        if (exception instanceof WebApplicationException webException) {
            int status = webException.getResponse().getStatus();
            String message = StrKit.isBlank(webException.getMessage())
                    ? webException.getResponse().getStatusInfo().getReasonPhrase() : webException.getMessage();
            return new WebError(status, status, message, null);
        }
        return translator.translate(exception);
    }
}
