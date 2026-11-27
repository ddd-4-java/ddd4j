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

    /** \u4e1a\u52a1\u9519\u8bef\u7801\u3002 */
    @Getter
    private Integer code;
    /** \u56fd\u9645\u5316\u8d44\u6e90\u952e\u3002 */
    @Getter
    private String i18nCode;
    /** \u6d88\u606f\u683c\u5f0f\u5316\u53c2\u6570\u3002 */
    @Getter
    private Object[] args;

    /**
     * \u521b\u5efa\u53ef\u56fd\u9645\u5316\u7684\u4e1a\u52a1\u53d7\u68c0\u5f02\u5e38\u3002
     * @param code \u4e1a\u52a1\u9519\u8bef\u7801
     * @param message \u9519\u8bef\u6d88\u606f\u6216\u56fd\u9645\u5316\u8d44\u6e90\u952e
     */
    public BizCheckedException(Integer code, String message) {
        super(I18nKit.get(message));
        this.code = code;
    }

    /**
     * \u521b\u5efa\u53ef\u56fd\u9645\u5316\u7684\u4e1a\u52a1\u53d7\u68c0\u5f02\u5e38\u3002
     * @param code \u4e1a\u52a1\u9519\u8bef\u7801
     * @param message \u9519\u8bef\u6d88\u606f\u6216\u56fd\u9645\u5316\u8d44\u6e90\u952e
     * @param args \u6d88\u606f\u683c\u5f0f\u5316\u53c2\u6570
     */
    public BizCheckedException(Integer code, String message, Object... args) {
        super(I18nKit.get(message, args));
        this.code = code;
        this.args = args;
    }

    /**
     * \u6309\u4e1a\u52a1\u7801\u548c\u56fd\u9645\u5316\u8d44\u6e90\u952e\u521b\u5efa\u4e1a\u52a1\u53d7\u68c0\u5f02\u5e38\u3002
     * @param code \u4e1a\u52a1\u9519\u8bef\u7801
     * @param i18nCode \u56fd\u9645\u5316\u8d44\u6e90\u952e
     * @param message \u9519\u8bef\u6d88\u606f\u6216\u56fd\u9645\u5316\u8d44\u6e90\u952e
     */
    public BizCheckedException(Integer code, String i18nCode, String message) {
        super(I18nKit.get(i18nCode, message));
        this.code = code;
        this.i18nCode = i18nCode;
    }

    /**
     * \u6309\u4e1a\u52a1\u7801\u548c\u56fd\u9645\u5316\u8d44\u6e90\u952e\u521b\u5efa\u4e1a\u52a1\u53d7\u68c0\u5f02\u5e38\u3002
     * @param code \u4e1a\u52a1\u9519\u8bef\u7801
     * @param i18nCode \u56fd\u9645\u5316\u8d44\u6e90\u952e
     * @param args \u6d88\u606f\u683c\u5f0f\u5316\u53c2\u6570
     * @param message \u9519\u8bef\u6d88\u606f\u6216\u56fd\u9645\u5316\u8d44\u6e90\u952e
     */
    public BizCheckedException(Integer code, String i18nCode, Object[] args, String message) {
        super(I18nKit.get(i18nCode, Objects.nonNull(args) ? args : new Object[]{message}));
        this.code = code;
        this.i18nCode = i18nCode;
        this.args = args;
    }

    /**
     * \u521b\u5efa\u53ef\u56fd\u9645\u5316\u7684\u4e1a\u52a1\u53d7\u68c0\u5f02\u5e38\u3002
     * @param message \u9519\u8bef\u6d88\u606f\u6216\u56fd\u9645\u5316\u8d44\u6e90\u952e
     */
    public BizCheckedException(String message) {
        super(I18nKit.get(message));
    }

    /**
     * \u521b\u5efa\u53ef\u56fd\u9645\u5316\u7684\u4e1a\u52a1\u53d7\u68c0\u5f02\u5e38\u3002
     * @param message \u9519\u8bef\u6d88\u606f\u6216\u56fd\u9645\u5316\u8d44\u6e90\u952e
     * @param args \u6d88\u606f\u683c\u5f0f\u5316\u53c2\u6570
     */
    public BizCheckedException(String message, Object... args) {
        super(I18nKit.get(message, args));
        this.code = 500;
    }

    /**
     * \u521b\u5efa\u53ef\u56fd\u9645\u5316\u7684\u4e1a\u52a1\u53d7\u68c0\u5f02\u5e38\u3002
     * @param message \u9519\u8bef\u6d88\u606f\u6216\u56fd\u9645\u5316\u8d44\u6e90\u952e
     * @param cause \u539f\u59cb\u5f02\u5e38
     */
    public BizCheckedException(String message, Throwable cause) {
        super(I18nKit.get(message), cause);
    }

    /**
     * \u6309\u9884\u5b9a\u4e49\u4e1a\u52a1\u7801\u548c\u56fd\u9645\u5316\u952e\u521b\u5efa\u4e1a\u52a1\u53d7\u68c0\u5f02\u5e38\u3002
     * @param code \u4e1a\u52a1\u9519\u8bef\u7801
     * @param i18nCode \u56fd\u9645\u5316\u8d44\u6e90\u952e
     */
    public BizCheckedException(ApiCode code, String i18nCode) {
        this((CustomApiCode) code, i18nCode);
    }

    /**
     * \u6309\u4e1a\u52a1\u6269\u5c55\u7801\u548c\u56fd\u9645\u5316\u952e\u521b\u5efa\u5f02\u5e38\u3002
     * @param code \u4e1a\u52a1\u6269\u5c55\u9519\u8bef\u7801
     * @param i18nCode \u56fd\u9645\u5316\u8d44\u6e90\u952e
     */
    public BizCheckedException(CustomApiCode code, String i18nCode) {
        super(I18nKit.get(i18nCode, code.getReason()));
        this.code = code.getCode();
        this.i18nCode = i18nCode;
    }

    /**
     * \u521b\u5efa\u53ef\u56fd\u9645\u5316\u7684\u4e1a\u52a1\u53d7\u68c0\u5f02\u5e38\u3002
     * @param code \u4e1a\u52a1\u9519\u8bef\u7801
     * @param message \u9519\u8bef\u6d88\u606f\u6216\u56fd\u9645\u5316\u8d44\u6e90\u952e
     * @param cause \u539f\u59cb\u5f02\u5e38
     */
    public BizCheckedException(Integer code, String message, Throwable cause) {
        super(I18nKit.get(message), cause);
        this.code = code;
    }

    /**
     * \u6309\u4e1a\u52a1\u7801\u548c\u56fd\u9645\u5316\u8d44\u6e90\u952e\u521b\u5efa\u4e1a\u52a1\u53d7\u68c0\u5f02\u5e38\u3002
     * @param code \u4e1a\u52a1\u9519\u8bef\u7801
     * @param i18nCode \u56fd\u9645\u5316\u8d44\u6e90\u952e
     * @param defMsg \u8d44\u6e90\u7f3a\u5931\u65f6\u7684\u9ed8\u8ba4\u6d88\u606f
     * @param cause \u539f\u59cb\u5f02\u5e38
     */
    public BizCheckedException(Integer code, String i18nCode, String defMsg, Throwable cause) {
        super(I18nKit.get(i18nCode, defMsg), cause);
        this.code = code;
        this.i18nCode = i18nCode;
    }

    /**
     * \u6309\u81ea\u5b9a\u4e49\u4e1a\u52a1\u7801\u521b\u5efa\u4e1a\u52a1\u53d7\u68c0\u5f02\u5e38\u3002
     * @param code \u4e1a\u52a1\u9519\u8bef\u7801
     */
    public BizCheckedException(CustomApiCode code) {
        super(I18nKit.get(code.getReason()));
        this.code = code.getCode();
    }

    /** \u521b\u5efa\u9ed8\u8ba4\u7684\u4e1a\u52a1\u53d7\u68c0\u5f02\u5e38\u3002 */
    public BizCheckedException() {
        this(500, "Internal Server Error");
    }

    /**
     * \u4ece\u539f\u5f02\u5e38\u521b\u5efa\u4e1a\u52a1\u53d7\u68c0\u5f02\u5e38\u3002
     * @param e \u539f\u59cb\u5f02\u5e38
     */
    public BizCheckedException(Throwable e) {
        this(e.getMessage(), e);
    }

    /**
     * e() 方法。
     * @param message 异常消息。
     * @return 当前对象自身，支持链式调用。
     */
    public static BizCheckedException e(String message) {
        return new BizCheckedException(message);
    }

    /**
     * e() 方法。
     * @param i18nCode 字符串参数。
     * @param message 异常消息。
     * @return 当前对象自身，支持链式调用。
     */
    public static BizCheckedException e(String i18nCode, String message) {
        return new BizCheckedException(500, i18nCode, message);
    }

}
