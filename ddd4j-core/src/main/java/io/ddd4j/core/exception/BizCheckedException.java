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

import io.ddd4j.core.util.I18nKit;
import lombok.Getter;

import java.util.Objects;

/**
 * 业务受检异常（ddd4j 核心异常基类）。
 * <p>
 * 合并了原 {@code ServiceException} 的 i18n 能力：所有 message 参数会先经过
 * {@link I18nKit#get(String, Object...)} 国际化处理，再传给父类。
 * <p>
 * 与 {@link BizRuntimeException} 的区别：本类继承 {@link Exception}（受检异常），
 * 调用方必须 try-catch 或 throws 声明。适用于 IO/外部调用等必须显式处理的场景。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class BizCheckedException extends Exception {

    /**
     * 业务错误码。
     */
    @Getter
    private Integer code;
    /**
     * 国际化资源键。
     */
    @Getter
    private String i18nCode;
    /**
     * 消息格式化参数。
     */
    @Getter
    private Object[] args;

    /**
     * 创建可国际化的业务受检异常。
     *
     * @param code 业务错误码
     * @param message 错误消息或国际化资源键
     */
    public BizCheckedException(Integer code, String message) {
        super(I18nKit.get(message));
        this.code = code;
    }

    /**
     * 创建可国际化的业务受检异常。
     *
     * @param code 业务错误码
     * @param message 错误消息或国际化资源键
     * @param args 消息格式化参数
     */
    public BizCheckedException(Integer code, String message, Object... args) {
        super(I18nKit.get(message, args));
        this.code = code;
        this.args = args;
    }

    /**
     * 按业务码和国际化资源键创建业务受检异常。
     *
     * @param code 业务错误码
     * @param i18nCode 国际化资源键
     * @param message 错误消息或国际化资源键
     */
    public BizCheckedException(Integer code, String i18nCode, String message) {
        super(I18nKit.get(i18nCode, message));
        this.code = code;
        this.i18nCode = i18nCode;
    }

    /**
     * 按业务码和国际化资源键创建业务受检异常。
     *
     * @param code 业务错误码
     * @param i18nCode 国际化资源键
     * @param args 消息格式化参数
     * @param message 错误消息或国际化资源键
     */
    public BizCheckedException(Integer code, String i18nCode, Object[] args, String message) {
        super(I18nKit.get(i18nCode, Objects.nonNull(args) ? args : new Object[]{message}));
        this.code = code;
        this.i18nCode = i18nCode;
        this.args = args;
    }

    /**
     * 创建可国际化的业务受检异常。
     *
     * @param message 错误消息或国际化资源键
     */
    public BizCheckedException(String message) {
        super(I18nKit.get(message));
    }

    /**
     * 创建可国际化的业务受检异常。
     *
     * @param message 错误消息或国际化资源键
     * @param args 消息格式化参数
     */
    public BizCheckedException(String message, Object... args) {
        super(I18nKit.get(message, args));
        this.code = 500;
    }

    /**
     * 创建可国际化的业务受检异常。
     *
     * @param message 错误消息或国际化资源键
     * @param cause 原始异常
     */
    public BizCheckedException(String message, Throwable cause) {
        super(I18nKit.get(message), cause);
    }

    /**
     * 按预定义业务码和国际化键创建业务受检异常。
     *
     * @param code 业务错误码
     * @param i18nCode 国际化资源键
     */
    public BizCheckedException(ApiCode code, String i18nCode) {
        super(I18nKit.get(i18nCode, code.getReason()));
        this.code = code.getCode();
        this.i18nCode = i18nCode;
    }

    /**
     * 创建可国际化的业务受检异常。
     *
     * @param code 业务错误码
     * @param message 错误消息或国际化资源键
     * @param cause 原始异常
     */
    public BizCheckedException(Integer code, String message, Throwable cause) {
        super(I18nKit.get(message), cause);
        this.code = code;
    }

    /**
     * 按业务码和国际化资源键创建业务受检异常。
     *
     * @param code 业务错误码
     * @param i18nCode 国际化资源键
     * @param defMsg 资源缺失时的默认消息
     * @param cause 原始异常
     */
    public BizCheckedException(Integer code, String i18nCode, String defMsg, Throwable cause) {
        super(I18nKit.get(i18nCode, defMsg), cause);
        this.code = code;
        this.i18nCode = i18nCode;
    }

    /**
     * 按自定义业务码创建业务受检异常。
     *
     * @param code 业务错误码
     */
    public BizCheckedException(CustomApiCode code) {
        super(I18nKit.get(code.getReason()));
        this.code = code.getCode();
    }

    /**
     * 创建默认的业务受检异常。
     */
    public BizCheckedException() {
        this(500, "Internal Server Error");
    }

    /**
     * 从原异常创建业务受检异常。
     *
     * @param e 原始异常
     */
    public BizCheckedException(Throwable e) {
        this(e.getMessage(), e);
    }

    /**
     * 创建可国际化的业务受检异常。
     *
     * @param message 错误消息或国际化资源键
     * @return 新建的业务受检异常
     */
    public static BizCheckedException e(String message) {
        return new BizCheckedException(message);
    }

    /**
     * 按业务码和国际化资源键创建业务受检异常。
     *
     * @param i18nCode 国际化资源键
     * @param message 错误消息或国际化资源键
     * @return 新建的业务受检异常
     */
    public static BizCheckedException e(String i18nCode, String message) {
        return new BizCheckedException(500, i18nCode, message);
    }

}
