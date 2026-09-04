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
package io.ddd4j.core.exception;

import io.ddd4j.core.ApiCode;
import io.ddd4j.core.CustomApiCode;

/**
 * Redis 操作异常。
 * <p>
 * 当 Redis 缓存操作（连接、读写、超时等）失败时抛出。
 * 继承 {@link BizRuntimeException}，支持国际化消息和业务错误码。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class RedisOperationException extends BizRuntimeException {

    public RedisOperationException(Integer code, String message) {
        super(code, message);
    }

    public RedisOperationException(Integer code, String i18nCode, String message) {
        super(code, i18nCode, message);
    }

    public RedisOperationException(Integer code, String i18nCode, Object[] args, String message) {
        super(code, i18nCode, args, message);
    }

    public RedisOperationException(String message) {
        super(message);
    }

    public RedisOperationException(String message, Throwable cause) {
        super(message, cause);
    }

    public RedisOperationException(ApiCode code, String i18nCode) {
        super(code, i18nCode);
    }

    public RedisOperationException(Integer code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public RedisOperationException(Integer code, String i18nCode, String defMsg, Throwable cause) {
        super(code, i18nCode, defMsg, cause);
    }

    public RedisOperationException(CustomApiCode code) {
        super(code);
    }

}
