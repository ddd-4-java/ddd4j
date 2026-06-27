/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
package io.ddd4j.core.exception;

import io.ddd4j.core.ApiCode;
import io.ddd4j.core.CustomApiCode;
import lombok.Getter;

public class BizCheckedException extends Exception {

    @Getter
    private Integer code;
    @Getter
    private String i18nCode;
    @Getter
    private Object[] args;

    public BizCheckedException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public BizCheckedException(Integer code, String i18nCode, String message) {
        super(message);
        this.code = code;
        this.i18nCode = i18nCode;
    }

    public BizCheckedException(Integer code, String i18nCode, Object[] args, String message) {
        super(message);
        this.code = code;
        this.i18nCode = i18nCode;
        this.args = args;
    }

    public BizCheckedException(String message) {
        super(message);
    }

    public BizCheckedException(String message, Throwable cause) {
        super(message, cause);
    }
 
    public BizCheckedException(ApiCode code, String i18nCode) {
        super(code.getReason());
        this.code = code.getCode();
        this.i18nCode = i18nCode;
    }

    public BizCheckedException(Integer code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public BizCheckedException(Integer code, String i18nCode, String defMsg, Throwable cause) {
        super(defMsg, cause);
        this.code = code;
        this.i18nCode = i18nCode;
    }

    public BizCheckedException(CustomApiCode code) {
        super(code.getReason());
        this.code = code.getCode();
    }

    public static BizCheckedException e(String message) {
        return new BizCheckedException(message);
    }

    public static BizCheckedException e(String i18nCode, String message) {
        return new BizCheckedException(500, i18nCode, message);
    }

}
