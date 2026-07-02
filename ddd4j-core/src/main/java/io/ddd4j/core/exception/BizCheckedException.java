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

    @Getter
    private Integer code;
    @Getter
    private String i18nCode;
    @Getter
    private Object[] args;

    public BizCheckedException(Integer code, String message) {
        super(I18nKit.get(message));
        this.code = code;
    }

    public BizCheckedException(Integer code, String message, Object... args) {
        super(I18nKit.get(message, args));
        this.code = code;
        this.args = args;
    }

    public BizCheckedException(Integer code, String i18nCode, String message) {
        super(I18nKit.get(i18nCode, message));
        this.code = code;
        this.i18nCode = i18nCode;
    }

    public BizCheckedException(Integer code, String i18nCode, Object[] args, String message) {
        super(I18nKit.get(i18nCode, args != null ? args : new Object[]{message}));
        this.code = code;
        this.i18nCode = i18nCode;
        this.args = args;
    }

    public BizCheckedException(String message) {
        super(I18nKit.get(message));
    }

    public BizCheckedException(String message, Object... args) {
        super(I18nKit.get(message, args));
        this.code = 500;
    }

    public BizCheckedException(String message, Throwable cause) {
        super(I18nKit.get(message), cause);
    }

    public BizCheckedException(ApiCode code, String i18nCode) {
        super(I18nKit.get(i18nCode, code.getReason()));
        this.code = code.getCode();
        this.i18nCode = i18nCode;
    }

    public BizCheckedException(Integer code, String message, Throwable cause) {
        super(I18nKit.get(message), cause);
        this.code = code;
    }

    public BizCheckedException(Integer code, String i18nCode, String defMsg, Throwable cause) {
        super(I18nKit.get(i18nCode, defMsg), cause);
        this.code = code;
        this.i18nCode = i18nCode;
    }

    public BizCheckedException(CustomApiCode code) {
        super(I18nKit.get(code.getReason()));
        this.code = code.getCode();
    }

    public BizCheckedException() {
        this(500, "Internal Server Error");
    }

    public BizCheckedException(Throwable e) {
        this(e.getMessage());
    }

    public static BizCheckedException e(String message) {
        return new BizCheckedException(message);
    }

    public static BizCheckedException e(String i18nCode, String message) {
        return new BizCheckedException(500, i18nCode, message);
    }

}
