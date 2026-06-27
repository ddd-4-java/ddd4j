/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 */
package io.ddd4j.core.exception;

import io.ddd4j.core.ApiCode;
import io.ddd4j.core.CustomApiCode;
import lombok.Getter;


public class BizRuntimeException extends RuntimeException {

    @Getter
    private Integer code;
    @Getter
    private String i18nCode;
    @Getter
    private Object[] args;

    public BizRuntimeException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public BizRuntimeException(Integer code, String i18nCode, String message) {
        super(message);
        this.code = code;
        this.i18nCode = i18nCode;
    }

    public BizRuntimeException(Integer code, String i18nCode, Object[] args, String message) {
        super(message);
        this.code = code;
        this.i18nCode = i18nCode;
        this.args = args;
    }

    public BizRuntimeException(String message) {
        super(message);
    }

    public BizRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }

    public BizRuntimeException(ApiCode code, String i18nCode) {
        super(code.getReason());
        this.code = code.getCode();
        this.i18nCode = i18nCode;
    }

    public BizRuntimeException(Integer code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public BizRuntimeException(Integer code, String i18nCode, String defMsg, Throwable cause) {
        super(defMsg, cause);
        this.code = code;
        this.i18nCode = i18nCode;
    }

    public BizRuntimeException(CustomApiCode code) {
        super(code.getReason());
        this.code = code.getCode();
    }

    public static BizRuntimeException e(String message) {
        return new BizRuntimeException(message);
    }

    public static BizRuntimeException e(String i18nCode, String message) {
        return new BizRuntimeException(500, i18nCode, message);
    }

}
