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

import java.io.IOException;
import java.util.Objects;

/**
 * 业务 IO 异常（ddd4j 核心异常基类）。
 * <p>
 * 合并了原 {@code BizRuntimeException} 的 i18n 能力：所有 message 参数会先经过
 * {@link I18nKit#get(String, Object...)} 国际化处理，再传给父类。
 * <p>
 * 与 {@link BizRuntimeException} 的区别：本类继承 {@link IOException}（受检异常），
 * 适用于文件/网络/数据库等 IO 操作失败场景。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class BizIOException extends IOException {

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
     * 创建可国际化的业务 IO 异常。
     *
     * @param code 业务错误码
     * @param message 错误消息或国际化资源键
     */
    public BizIOException(Integer code, String message) {
        super(I18nKit.get(message));
        this.code = code;
    }

    /**
     * 创建可国际化的业务 IO 异常。
     *
     * @param code 业务错误码
     * @param message 错误消息或国际化资源键
     * @param args 消息格式化参数
     */
    public BizIOException(Integer code, String message, Object... args) {
        super(I18nKit.get(message, args));
        this.code = code;
        this.args = args;
    }

    /**
     * 按业务码和国际化资源键创建业务 IO 异常。
     *
     * @param code 业务错误码
     * @param i18nCode 国际化资源键
     * @param message 错误消息或国际化资源键
     */
    public BizIOException(Integer code, String i18nCode, String message) {
        super(I18nKit.get(i18nCode, message));
        this.code = code;
        this.i18nCode = i18nCode;
    }

    /**
     * 按业务码和国际化资源键创建业务 IO 异常。
     *
     * @param code 业务错误码
     * @param i18nCode 国际化资源键
     * @param args 消息格式化参数
     * @param message 错误消息或国际化资源键
     */
    public BizIOException(Integer code, String i18nCode, Object[] args, String message) {
        super(I18nKit.get(i18nCode, Objects.nonNull(args) ? args : new Object[]{message}));
        this.code = code;
        this.i18nCode = i18nCode;
        this.args = args;
    }

    /**
     * 创建可国际化的业务 IO 异常。
     *
     * @param message 错误消息或国际化资源键
     */
    public BizIOException(String message) {
        super(I18nKit.get(message));
    }

    /**
     * 创建可国际化的业务 IO 异常。
     *
     * @param message 错误消息或国际化资源键
     * @param args 消息格式化参数
     */
    public BizIOException(String message, Object... args) {
        super(I18nKit.get(message, args));
        this.code = 500;
    }

    /**
     * 创建可国际化的业务 IO 异常。
     *
     * @param message 错误消息或国际化资源键
     * @param cause 原始异常
     */
    public BizIOException(String message, Throwable cause) {
        super(I18nKit.get(message), cause);
    }

    /**
     * 按预定义业务码和国际化键创建业务 IO 异常。
     *
     * @param code 业务错误码
     * @param i18nCode 国际化资源键
     */
    public BizIOException(ApiCode code, String i18nCode) {
        this((CustomApiCode) code, i18nCode);
    }

    /**
     * 按业务扩展码和国际化键创建异常。
     *
     * @param code 业务扩展错误码
     * @param i18nCode 国际化资源键
     */
    public BizIOException(CustomApiCode code, String i18nCode) {
        super(I18nKit.get(i18nCode, code.getReason()));
        this.code = code.getCode();
        this.i18nCode = i18nCode;
    }

    /**
     * 创建可国际化的业务 IO 异常。
     *
     * @param code 业务错误码
     * @param message 错误消息或国际化资源键
     * @param cause 原始异常
     */
    public BizIOException(Integer code, String message, Throwable cause) {
        super(I18nKit.get(message), cause);
        this.code = code;
    }

    /**
     * 按业务码和国际化资源键创建业务 IO 异常。
     *
     * @param code 业务错误码
     * @param i18nCode 国际化资源键
     * @param defMsg 资源缺失时的默认消息
     * @param cause 原始异常
     */
    public BizIOException(Integer code, String i18nCode, String defMsg, Throwable cause) {
        super(I18nKit.get(i18nCode, defMsg), cause);
        this.code = code;
        this.i18nCode = i18nCode;
    }

    /**
     * 按自定义业务码创建业务 IO 异常。
     *
     * @param code 业务错误码
     */
    public BizIOException(CustomApiCode code) {
        super(I18nKit.get(code.getReason()));
        this.code = code.getCode();
    }

    /**
     * 创建默认的业务 IO 异常。
     */
    public BizIOException() {
        this(500, "Internal Server Error");
    }

    /**
     * 从原异常创建业务 IO 异常。
     *
     * @param e 原始异常
     */
    public BizIOException(Throwable e) {
        this(e.getMessage());
    }

    /**
     * 创建可国际化的业务 IO 异常。
     *
     * @param message 错误消息或国际化资源键
     * @return 新建的业务 IO 异常
     */
    public static BizIOException e(String message) {
        return new BizIOException(message);
    }

    /**
     * 按业务码和国际化资源键创建业务 IO 异常。
     *
     * @param i18nCode 国际化资源键
     * @param message 错误消息或国际化资源键
     * @return 新建的业务 IO 异常
     */
    public static BizIOException e(String i18nCode, String message) {
        return new BizIOException(500, i18nCode, message);
    }

}
