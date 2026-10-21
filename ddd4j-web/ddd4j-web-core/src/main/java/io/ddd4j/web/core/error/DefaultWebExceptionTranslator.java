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
package io.ddd4j.web.core.error;

import io.ddd4j.core.exception.BizRuntimeException;
import io.ddd4j.core.exception.IdempotentException;
import io.ddd4j.core.exception.ParamException;
import io.ddd4j.core.exception.ValidateException;
import io.ddd4j.kit.lang.StrKit;

import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * ddd4j 默认异常翻译策略。
 */
public final class DefaultWebExceptionTranslator implements WebExceptionTranslator {

    /** 表示请求参数或格式非法的状态码。 */
    public static final int BAD_REQUEST = 400;
    /** 表示缺少或无效授权凭证的状态码。 */
    public static final int UNAUTHORIZED = 401;
    /** 表示已认证但无权访问的状态码。 */
    public static final int FORBIDDEN = 403;
    /** 表示目标资源不存在的状态码。 */
    public static final int NOT_FOUND = 404;
    /** 表示请求与当前资源状态冲突（如重复提交）的状态码。 */
    public static final int CONFLICT = 409;
    /** 表示服务端发生未预期错误的状态码。 */
    public static final int INTERNAL_SERVER_ERROR = 500;

    /**
     * 将已知业务异常转换为稳定的 HTTP 错误响应。
     *
     * @param throwable 待翻译的异常
     * @return 包含状态码、错误码和消息的响应
     */
    @Override
    public WebError translate(Throwable throwable) {
        Throwable cause = Objects.requireNonNull(throwable, "throwable must not be null");
        if (cause instanceof WebStatusException exception) {
            return new WebError(exception.getStatus(), exception.getCode(), message(exception), exception.getData());
        }
        if (cause instanceof IdempotentException) {
            return error(CONFLICT, cause);
        }
        if (cause instanceof ParamException || cause instanceof ValidateException
                || cause instanceof IllegalArgumentException) {
            return error(BAD_REQUEST, cause);
        }
        if (cause instanceof IllegalStateException) {
            return error(CONFLICT, cause);
        }
        if (cause instanceof NoSuchElementException) {
            return error(NOT_FOUND, cause);
        }
        if (cause instanceof SecurityException) {
            return error(FORBIDDEN, cause);
        }
        if (cause instanceof BizRuntimeException exception) {
            int status = normalizeStatus(exception.getCode());
            return new WebError(status, Objects.nonNull(exception.getCode()) ? exception.getCode() : status,
                    message(exception), null);
        }
        return error(INTERNAL_SERVER_ERROR, cause);
    }

    private WebError error(int status, Throwable throwable) {
        return new WebError(status, status, message(throwable), null);
    }

    private String message(Throwable throwable) {
        return StrKit.isBlank(throwable.getMessage()) ? "Internal Server Error" : throwable.getMessage();
    }

    private int normalizeStatus(Integer code) {
        if (Objects.nonNull(code) && code >= BAD_REQUEST && code <= 599) {
            return code;
        }
        return INTERNAL_SERVER_ERROR;
    }
}
