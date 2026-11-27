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

    /**
     * \u5c06 HTTP \u72b6\u6001\u548c\u539f\u5f02\u5e38\u8f6c\u6362\u4e3a\u5171\u7528 Web \u9519\u8bef\u3002
     * @param status \u51c6\u5907\u8fd4\u56de\u7ed9 HTTP \u5ba2\u6237\u7aef\u7684\u72b6\u6001\u7801
     * @param throwable \u8bf7\u6c42\u5904\u7406\u65f6\u629b\u51fa\u7684\u5f02\u5e38
     * @return \u5e26\u72b6\u6001\u53ca\u5b89\u5168\u54cd\u5e94\u5185\u5bb9\u7684 Web \u9519\u8bef
     */
    private WebError error(int status, Throwable throwable) {
        return new WebError(status, status, message(throwable), null);
    }

    /**
     * \u4ece\u5f02\u5e38\u4e2d\u8bfb\u53d6\u54cd\u5e94\u53ef\u4f7f\u7528\u7684\u6d88\u606f\u6587\u672c\u3002
     * @param throwable \u8bf7\u6c42\u5904\u7406\u65f6\u629b\u51fa\u7684\u5f02\u5e38
     * @return \u7528\u4e8e\u5f53\u524d\u5f02\u5e38\u8f6c\u6362\u7684\u6d88\u606f
     */
    private String message(Throwable throwable) {
        return StrKit.isBlank(throwable.getMessage()) ? "Internal Server Error" : throwable.getMessage();
    }

    /**
     * \u4fdd\u7559\u5408\u6cd5\u7684 HTTP \u9519\u8bef\u72b6\u6001\uff0c\u5176\u4ed6\u503c\u5f52\u4e3a\u5185\u90e8\u670d\u52a1\u5668\u9519\u8bef\u3002
     * @param code \u5f53\u524d\u8981\u8f6c\u6362\u7684\u4e1a\u52a1\u9519\u8bef\u7801\u6216 HTTP \u72b6\u6001\u7801
     * @return \u8f6c\u6362\u540e\u7684 HTTP \u9519\u8bef\u72b6\u6001\u7801
     */
    private int normalizeStatus(Integer code) {
        if (Objects.nonNull(code) && code >= BAD_REQUEST && code <= 599) {
            return code;
        }
        return INTERNAL_SERVER_ERROR;
    }
}
