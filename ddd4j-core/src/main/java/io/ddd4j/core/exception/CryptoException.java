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
 * 加解密操作异常。
 * <p>
 * 当加密、解密、签名验证等密码学操作失败时抛出。
 * 继承 {@link BizRuntimeException}，支持国际化消息和业务错误码。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class CryptoException extends BizRuntimeException {

    public CryptoException(Integer code, String message) {
        super(code, message);
    }

    public CryptoException(Integer code, String i18nCode, String message) {
        super(code, i18nCode, message);
    }

    public CryptoException(Integer code, String i18nCode, Object[] args, String message) {
        super(code, i18nCode, args, message);
    }

    public CryptoException(String message) {
        super(message);
    }

    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }

    public CryptoException(ApiCode code, String i18nCode) {
        super(code, i18nCode);
    }

    public CryptoException(Integer code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public CryptoException(Integer code, String i18nCode, String defMsg, Throwable cause) {
        super(code, i18nCode, defMsg, cause);
    }

    public CryptoException(CustomApiCode code) {
        super(code);
    }

}
