/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
package io.ddd4j.core.exception;

import hitool.core.lang3.exception.NestedIOException;
import io.ddd4j.core.ApiCode;
import io.ddd4j.core.CustomApiCode;
import lombok.Getter;

public class BizIOException extends NestedIOException {

    @Getter
    private Integer code;
    @Getter
    private String i18nCode;
    @Getter
    private Object[] args;

    public BizIOException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public BizIOException(Integer code, String i18nCode, String message) {
        super(message);
        this.code = code;
        this.i18nCode = i18nCode;
    }

    public BizIOException(Integer code, String i18nCode, Object[] args, String message) {
        super(message);
        this.code = code;
        this.i18nCode = i18nCode;
        this.args = args;
    }

    public BizIOException(String message) {
        super(message);
    }

    public BizIOException(String message, Throwable cause) {
        super(message, cause);
    }

    public BizIOException(ApiCode code, String i18nCode) {
        super(code.getReason());
        this.code = code.getCode();
        this.i18nCode = i18nCode;
    }

    public BizIOException(Integer code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public BizIOException(Integer code, String i18nCode, String defMsg, Throwable cause) {
        super(defMsg, cause);
        this.code = code;
        this.i18nCode = i18nCode;
    }

    public BizIOException(CustomApiCode code) {
        super(code.getReason());
        this.code = code.getCode();
    }

    public static BizIOException e(String message) {
        return new BizIOException(message);
    }

    public static BizIOException e(String i18nCode, String message) {
        return new BizIOException(500, i18nCode, message);
    }

}
