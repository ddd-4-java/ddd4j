/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
package io.ddd4j.core.exception;

import io.ddd4j.core.ApiCode;
import io.ddd4j.core.CustomApiCode;
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

    @Getter
    private Integer code;
    @Getter
    private String i18nCode;
    @Getter
    private Object[] args;

    public BizRuntimeException(Integer code, String message) {
        super(I18nKit.get(message));
        this.code = code;
    }

    public BizRuntimeException(Integer code, String message, Object... args) {
        super(I18nKit.get(message, args));
        this.code = code;
        this.args = args;
    }

    public BizRuntimeException(Integer code, String i18nCode, String message) {
        super(I18nKit.get(i18nCode, message));
        this.code = code;
        this.i18nCode = i18nCode;
    }

    public BizRuntimeException(Integer code, String i18nCode, Object[] args, String message) {
        super(I18nKit.get(i18nCode, Objects.nonNull(args) ? args : new Object[]{message}));
        this.code = code;
        this.i18nCode = i18nCode;
        this.args = args;
    }

    public BizRuntimeException(String message) {
        super(I18nKit.get(message));
    }

    public BizRuntimeException(String message, Object... args) {
        super(I18nKit.get(message, args));
        this.code = 500;
    }

    public BizRuntimeException(String message, Throwable cause) {
        super(I18nKit.get(message), cause);
    }

    public BizRuntimeException(ApiCode code, String i18nCode) {
        super(I18nKit.get(i18nCode, code.getReason()));
        this.code = code.getCode();
        this.i18nCode = i18nCode;
    }

    public BizRuntimeException(Integer code, String message, Throwable cause) {
        super(I18nKit.get(message), cause);
        this.code = code;
    }

    public BizRuntimeException(Integer code, String i18nCode, String defMsg, Throwable cause) {
        super(I18nKit.get(i18nCode, defMsg), cause);
        this.code = code;
        this.i18nCode = i18nCode;
    }

    public BizRuntimeException(CustomApiCode code) {
        super(I18nKit.get(code.getReason()));
        this.code = code.getCode();
    }

    public BizRuntimeException() {
        this(500, "Internal Server Error");
    }

    public BizRuntimeException(Throwable e) {
        this(e.getMessage());
    }

    public static BizRuntimeException e(String message) {
        return new BizRuntimeException(message);
    }

    public static BizRuntimeException e(String i18nCode, String message) {
        return new BizRuntimeException(500, i18nCode, message);
    }

}
