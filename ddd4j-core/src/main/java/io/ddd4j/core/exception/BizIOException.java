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

    @Getter
    private Integer code;
    @Getter
    private String i18nCode;
    @Getter
    private Object[] args;

    public BizIOException(Integer code, String message) {
        super(I18nKit.get(message));
        this.code = code;
    }

    public BizIOException(Integer code, String message, Object... args) {
        super(I18nKit.get(message, args));
        this.code = code;
        this.args = args;
    }

    public BizIOException(Integer code, String i18nCode, String message) {
        super(I18nKit.get(i18nCode, message));
        this.code = code;
        this.i18nCode = i18nCode;
    }

    public BizIOException(Integer code, String i18nCode, Object[] args, String message) {
        super(I18nKit.get(i18nCode, Objects.nonNull(args) ? args : new Object[]{message}));
        this.code = code;
        this.i18nCode = i18nCode;
        this.args = args;
    }

    public BizIOException(String message) {
        super(I18nKit.get(message));
    }

    public BizIOException(String message, Object... args) {
        super(I18nKit.get(message, args));
        this.code = 500;
    }

    public BizIOException(String message, Throwable cause) {
        super(I18nKit.get(message), cause);
    }

    public BizIOException(ApiCode code, String i18nCode) {
        super(I18nKit.get(i18nCode, code.getReason()));
        this.code = code.getCode();
        this.i18nCode = i18nCode;
    }

    public BizIOException(Integer code, String message, Throwable cause) {
        super(I18nKit.get(message), cause);
        this.code = code;
    }

    public BizIOException(Integer code, String i18nCode, String defMsg, Throwable cause) {
        super(I18nKit.get(i18nCode, defMsg), cause);
        this.code = code;
        this.i18nCode = i18nCode;
    }

    public BizIOException(CustomApiCode code) {
        super(I18nKit.get(code.getReason()));
        this.code = code.getCode();
    }

    public BizIOException() {
        this(500, "Internal Server Error");
    }

    public BizIOException(Throwable e) {
        this(e.getMessage());
    }

    public static BizIOException e(String message) {
        return new BizIOException(message);
    }

    public static BizIOException e(String i18nCode, String message) {
        return new BizIOException(500, i18nCode, message);
    }

}
