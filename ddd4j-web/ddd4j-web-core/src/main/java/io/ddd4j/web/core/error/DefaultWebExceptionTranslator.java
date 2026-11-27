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

import io.ddd4j.core.api.ApiCode;
import io.ddd4j.core.exception.*;
import io.ddd4j.kit.lang.StrKit;

import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * ddd4j 默认异常翻译策略。
 */
public final class DefaultWebExceptionTranslator implements WebExceptionTranslator {

    /**
     * 表示请求参数或格式非法的状态码。
     */
    public static final int BAD_REQUEST = 400;
    /**
     * 表示缺少或无效授权凭证的状态码。
     */
    public static final int UNAUTHORIZED = 401;
    /**
     * 表示已认证但无权访问的状态码。
     */
    public static final int FORBIDDEN = 403;
    /**
     * 表示目标资源不存在的状态码。
     */
    public static final int NOT_FOUND = 404;
    /**
     * 表示请求与当前资源状态冲突（如重复提交）的状态码。
     */
    public static final int CONFLICT = 409;
    /**
     * 表示服务端发生未预期错误的状态码。
     */
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
        if (cause instanceof WebStatusException) {
            WebStatusException exception = (WebStatusException) cause;
            return new WebError(exception.getStatus(), exception.getCode(), message(exception), exception.getData());
        }
        if (cause instanceof AuthenticationException) {
            return authError(ApiCode.UNAUTHORIZED, cause);
        }
        if (cause instanceof AuthorizationException) {
            return authError(ApiCode.FORBIDDEN, cause);
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
        if (cause instanceof BizRuntimeException) {
            BizRuntimeException exception = (BizRuntimeException) cause;
            int status = normalizeStatus(exception.getCode());
            return new WebError(status, Objects.nonNull(exception.getCode()) ? exception.getCode() : status,
                    message(exception), null);
        }
        return error(INTERNAL_SERVER_ERROR, cause);
    }

    /**
     * 将 HTTP 状态和原异常转换为共用 Web 错误。
     *
     * @param status    准备返回给 HTTP 客户端的状态码
     * @param throwable 请求处理时抛出的异常
     * @return 带状态及安全响应内容的 Web 错误
     */
    private WebError error(int status, Throwable throwable) {
        return new WebError(status, status, message(throwable), null);
    }

    /**
     * 将认证或授权失败转换为共用 Web 错误。
     *
     * @param code    当前要转换的业务错误码或 HTTP 状态码
     * @param failure 当前认证过程的异常
     * @return 向调用方报告的认证错误结果
     */
    private WebError authError(ApiCode code, Throwable failure) {
        int status = code.getCode();
        String message = failure.getMessage();
        return new WebError(status, status, StrKit.isBlank(message) ? code.getReason() : message, null);
    }

    /**
     * 从异常中读取响应可使用的消息文本。
     *
     * @param throwable 请求处理时抛出的异常
     * @return 用于当前异常转换的消息
     */
    private String message(Throwable throwable) {
        return StrKit.isBlank(throwable.getMessage()) ? "Internal Server Error" : throwable.getMessage();
    }

    /**
     * 保留合法的 HTTP 错误状态，其他值归为内部服务器错误。
     *
     * @param code 当前要转换的业务错误码或 HTTP 状态码
     * @return 转换后的 HTTP 错误状态码
     */
    private int normalizeStatus(Integer code) {
        if (Objects.nonNull(code) && code >= BAD_REQUEST && code <= 599) {
            return code;
        }
        return INTERNAL_SERVER_ERROR;
    }
}
