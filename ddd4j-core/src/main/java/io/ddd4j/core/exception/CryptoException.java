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
import io.ddd4j.core.api.CustomApiCode;
import io.ddd4j.core.api.ApiCode;


/**
 * 加解密操作异常。
 * <p>
 * 当加密、解密、签名验证等密码学操作失败时抛出。
 * 继承 {@link BizRuntimeException}，支持国际化消息和业务错误码。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class CryptoException extends BizRuntimeException {

    /**
     * 创建可国际化的加密处理异常。
     *
     * @param code 业务错误码
     * @param message 错误消息或国际化资源键
     */
    public CryptoException(Integer code, String message) {
        super(code, message);
    }

    /**
     * 按国际化资源键创建加密处理异常。
     *
     * @param code 业务错误码
     * @param i18nCode 国际化资源键
     * @param message 错误消息或国际化资源键
     */
    public CryptoException(Integer code, String i18nCode, String message) {
        super(code, i18nCode, message);
    }

    /**
     * 按国际化资源键创建加密处理异常。
     *
     * @param code 业务错误码
     * @param i18nCode 国际化资源键
     * @param args 消息格式化参数
     * @param message 错误消息或国际化资源键
     */
    public CryptoException(Integer code, String i18nCode, Object[] args, String message) {
        super(code, i18nCode, args, message);
    }

    /**
     * 创建可国际化的加密处理异常。
     *
     * @param message 错误消息或国际化资源键
     */
    public CryptoException(String message) {
        super(message);
    }

    /**
     * 创建可国际化的加密处理异常。
     *
     * @param message 错误消息或国际化资源键
     * @param cause 原始异常
     */
    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 按预定义业务码和国际化键创建加密处理异常。
     *
     * @param code 业务错误码
     * @param i18nCode 国际化资源键
     */
    public CryptoException(ApiCode code, String i18nCode) {
        this((CustomApiCode) code, i18nCode);
    }

    /**
     * 按业务扩展码和国际化键创建异常。
     *
     * @param code 业务扩展错误码
     * @param i18nCode 国际化资源键
     */
    public CryptoException(CustomApiCode code, String i18nCode) {
        super(code, i18nCode);
    }

    /**
     * 创建可国际化的加密处理异常。
     *
     * @param code 业务错误码
     * @param message 错误消息或国际化资源键
     * @param cause 原始异常
     */
    public CryptoException(Integer code, String message, Throwable cause) {
        super(code, message, cause);
    }

    /**
     * 按国际化资源键创建加密处理异常。
     *
     * @param code 业务错误码
     * @param i18nCode 国际化资源键
     * @param defMsg 资源缺失时的默认消息
     * @param cause 原始异常
     */
    public CryptoException(Integer code, String i18nCode, String defMsg, Throwable cause) {
        super(code, i18nCode, defMsg, cause);
    }

    /**
     * 按自定义业务码创建加密处理异常。
     *
     * @param code 业务错误码
     */
    public CryptoException(CustomApiCode code) {
        super(code);
    }

}
