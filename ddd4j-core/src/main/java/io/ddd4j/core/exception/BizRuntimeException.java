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

import io.ddd4j.core.api.ApiCode;
import io.ddd4j.core.api.CustomApiCode;
import io.ddd4j.core.util.I18nKit;
import lombok.Getter;

import java.util.Objects;

/**
 * 业务运行时异常（ddd4j 核心异常基类）。
 * <p>
 * 合并了原 {@code ServiceException} 的 i18n 能力：所有 message 参数会先经过
 * {@link I18nKit#get(String, Object...)} 国际化处理，再传给父类。
 * <p>
 * 支持两种 message 风格：
 * <ul>
 *   <li><b>i18n key</b>：{@code new BizRuntimeException("user.not.found", userId)}
 *       —— 先查 i18n 资源，找不到则原样返回</li>
 *   <li><b>纯文本</b>：{@code new BizRuntimeException("用户不存在")}
 *       —— 无 i18n 资源匹配时原样使用</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class BizRuntimeException extends RuntimeException {

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
     * \u521b\u5efa\u53ef\u56fd\u9645\u5316\u7684\u4e1a\u52a1\u8fd0\u884c\u65f6\u5f02\u5e38\u3002
     * @param code \u4e1a\u52a1\u9519\u8bef\u7801
     * @param message \u9519\u8bef\u6d88\u606f\u6216\u56fd\u9645\u5316\u8d44\u6e90\u952e
     */
    public BizRuntimeException(Integer code, String message) {
        super(I18nKit.get(message));
        this.code = code;
    }

    /**
     * \u521b\u5efa\u53ef\u56fd\u9645\u5316\u7684\u4e1a\u52a1\u8fd0\u884c\u65f6\u5f02\u5e38\u3002
     * @param code \u4e1a\u52a1\u9519\u8bef\u7801
     * @param message \u9519\u8bef\u6d88\u606f\u6216\u56fd\u9645\u5316\u8d44\u6e90\u952e
     * @param args \u6d88\u606f\u683c\u5f0f\u5316\u53c2\u6570
     */
    public BizRuntimeException(Integer code, String message, Object... args) {
        super(I18nKit.get(message, args));
        this.code = code;
        this.args = args;
    }

    /**
     * \u6309\u4e1a\u52a1\u7801\u548c\u56fd\u9645\u5316\u8d44\u6e90\u952e\u521b\u5efa\u4e1a\u52a1\u8fd0\u884c\u65f6\u5f02\u5e38\u3002
     * @param code \u4e1a\u52a1\u9519\u8bef\u7801
     * @param i18nCode \u56fd\u9645\u5316\u8d44\u6e90\u952e
     * @param message \u9519\u8bef\u6d88\u606f\u6216\u56fd\u9645\u5316\u8d44\u6e90\u952e
     */
    public BizRuntimeException(Integer code, String i18nCode, String message) {
        super(I18nKit.get(i18nCode, message));
        this.code = code;
        this.i18nCode = i18nCode;
    }

    /**
     * \u6309\u4e1a\u52a1\u7801\u548c\u56fd\u9645\u5316\u8d44\u6e90\u952e\u521b\u5efa\u4e1a\u52a1\u8fd0\u884c\u65f6\u5f02\u5e38\u3002
     * @param code \u4e1a\u52a1\u9519\u8bef\u7801
     * @param i18nCode \u56fd\u9645\u5316\u8d44\u6e90\u952e
     * @param args \u6d88\u606f\u683c\u5f0f\u5316\u53c2\u6570
     * @param message \u9519\u8bef\u6d88\u606f\u6216\u56fd\u9645\u5316\u8d44\u6e90\u952e
     */
    public BizRuntimeException(Integer code, String i18nCode, Object[] args, String message) {
        super(I18nKit.get(i18nCode, Objects.nonNull(args) ? args : new Object[]{message}));
        this.code = code;
        this.i18nCode = i18nCode;
        this.args = args;
    }

    /**
     * \u521b\u5efa\u53ef\u56fd\u9645\u5316\u7684\u4e1a\u52a1\u8fd0\u884c\u65f6\u5f02\u5e38\u3002
     * @param message \u9519\u8bef\u6d88\u606f\u6216\u56fd\u9645\u5316\u8d44\u6e90\u952e
     */
    public BizRuntimeException(String message) {
        super(I18nKit.get(message));
    }

    /**
     * \u521b\u5efa\u53ef\u56fd\u9645\u5316\u7684\u4e1a\u52a1\u8fd0\u884c\u65f6\u5f02\u5e38\u3002
     * @param message \u9519\u8bef\u6d88\u606f\u6216\u56fd\u9645\u5316\u8d44\u6e90\u952e
     * @param args \u6d88\u606f\u683c\u5f0f\u5316\u53c2\u6570
     */
    public BizRuntimeException(String message, Object... args) {
        super(I18nKit.get(message, args));
        this.code = 500;
    }

    /**
     * \u521b\u5efa\u53ef\u56fd\u9645\u5316\u7684\u4e1a\u52a1\u8fd0\u884c\u65f6\u5f02\u5e38\u3002
     * @param message \u9519\u8bef\u6d88\u606f\u6216\u56fd\u9645\u5316\u8d44\u6e90\u952e
     * @param cause \u539f\u59cb\u5f02\u5e38
     */
    public BizRuntimeException(String message, Throwable cause) {
        super(I18nKit.get(message), cause);
    }

    /**
     * 按标准业务码和国际化键创建异常。
     *
     * @param code 标准业务错误码
     * @param i18nCode 国际化资源键
     */
    public BizRuntimeException(ApiCode code, String i18nCode) {
        this((CustomApiCode) code, i18nCode);
    }

    /**
     * \u6309\u4e1a\u52a1\u6269\u5c55\u7801\u548c\u56fd\u9645\u5316\u952e\u521b\u5efa\u5f02\u5e38\u3002
     * @param code \u4e1a\u52a1\u6269\u5c55\u9519\u8bef\u7801
     * @param i18nCode \u56fd\u9645\u5316\u8d44\u6e90\u952e
     */
    public BizRuntimeException(CustomApiCode code, String i18nCode) {
        super(I18nKit.get(i18nCode, code.getReason()));
        this.code = code.getCode();
        this.i18nCode = i18nCode;
    }

    /**
     * \u521b\u5efa\u53ef\u56fd\u9645\u5316\u7684\u4e1a\u52a1\u8fd0\u884c\u65f6\u5f02\u5e38\u3002
     * @param code \u4e1a\u52a1\u9519\u8bef\u7801
     * @param message \u9519\u8bef\u6d88\u606f\u6216\u56fd\u9645\u5316\u8d44\u6e90\u952e
     * @param cause \u539f\u59cb\u5f02\u5e38
     */
    public BizRuntimeException(Integer code, String message, Throwable cause) {
        super(I18nKit.get(message), cause);
        this.code = code;
    }

    /**
     * \u6309\u4e1a\u52a1\u7801\u548c\u56fd\u9645\u5316\u8d44\u6e90\u952e\u521b\u5efa\u4e1a\u52a1\u8fd0\u884c\u65f6\u5f02\u5e38\u3002
     * @param code \u4e1a\u52a1\u9519\u8bef\u7801
     * @param i18nCode \u56fd\u9645\u5316\u8d44\u6e90\u952e
     * @param defMsg \u8d44\u6e90\u7f3a\u5931\u65f6\u7684\u9ed8\u8ba4\u6d88\u606f
     * @param cause \u539f\u59cb\u5f02\u5e38
     */
    public BizRuntimeException(Integer code, String i18nCode, String defMsg, Throwable cause) {
        super(I18nKit.get(i18nCode, defMsg), cause);
        this.code = code;
        this.i18nCode = i18nCode;
    }

    /**
     * \u6309\u81ea\u5b9a\u4e49\u4e1a\u52a1\u7801\u521b\u5efa\u4e1a\u52a1\u8fd0\u884c\u65f6\u5f02\u5e38\u3002
     * @param code \u4e1a\u52a1\u9519\u8bef\u7801
     */
    public BizRuntimeException(CustomApiCode code) {
        super(I18nKit.get(code.getReason()));
        this.code = code.getCode();
    }

    /** \u521b\u5efa\u9ed8\u8ba4\u7684\u4e1a\u52a1\u8fd0\u884c\u65f6\u5f02\u5e38\u3002 */
    public BizRuntimeException() {
        this(500, "Internal Server Error");
    }

    /**
     * \u4ece\u539f\u5f02\u5e38\u521b\u5efa\u4e1a\u52a1\u8fd0\u884c\u65f6\u5f02\u5e38\u3002
     * @param e \u539f\u59cb\u5f02\u5e38
     */
    public BizRuntimeException(Throwable e) {
        this(e.getMessage(), e);
    }

    /**
     * \u521b\u5efa\u53ef\u56fd\u9645\u5316\u7684\u4e1a\u52a1\u8fd0\u884c\u65f6\u5f02\u5e38\u3002
     * @param message \u9519\u8bef\u6d88\u606f\u6216\u56fd\u9645\u5316\u8d44\u6e90\u952e
     * @return \u65b0\u5efa\u7684\u4e1a\u52a1\u8fd0\u884c\u65f6\u5f02\u5e38
     */
    public static BizRuntimeException e(String message) {
        return new BizRuntimeException(message);
    }

    /**
     * \u6309\u4e1a\u52a1\u7801\u548c\u56fd\u9645\u5316\u8d44\u6e90\u952e\u521b\u5efa\u4e1a\u52a1\u8fd0\u884c\u65f6\u5f02\u5e38\u3002
     * @param i18nCode \u56fd\u9645\u5316\u8d44\u6e90\u952e
     * @param message \u9519\u8bef\u6d88\u606f\u6216\u56fd\u9645\u5316\u8d44\u6e90\u952e
     * @return \u65b0\u5efa\u7684\u4e1a\u52a1\u8fd0\u884c\u65f6\u5f02\u5e38
     */
    public static BizRuntimeException e(String i18nCode, String message) {
        return new BizRuntimeException(500, i18nCode, message);
    }

}
