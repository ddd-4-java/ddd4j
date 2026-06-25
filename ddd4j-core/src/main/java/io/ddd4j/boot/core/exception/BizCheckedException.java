/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 */
package io.ddd4j.boot.core.exception;

import io.ddd4j.boot.core.ApiCode;
import io.ddd4j.boot.core.CustomApiCode;
import lombok.Getter;
import org.springframework.core.NestedCheckedException;

public class BizCheckedException extends NestedCheckedException {

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
