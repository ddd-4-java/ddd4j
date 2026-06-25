package io.ddd4j.boot.core.exception;

import io.ddd4j.boot.core.ApiCode;
import io.ddd4j.boot.core.CustomApiCode;

public class PayException extends BizRuntimeException {

    public PayException(Integer code, String message) {
        super(code, message);
    }

    public PayException(Integer code, String i18nCode, String message) {
        super(code, i18nCode, message);
    }

    public PayException(Integer code, String i18nCode, Object[] args, String message) {
        super(code, i18nCode, args, message);
    }

    public PayException(String message) {
        super(message);
    }

    public PayException(String message, Throwable cause) {
        super(message, cause);
    }

    public PayException(ApiCode code, String i18nCode) {
        super(code, i18nCode);
    }

    public PayException(Integer code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public PayException(Integer code, String i18nCode, String defMsg, Throwable cause) {
        super(code, i18nCode, defMsg, cause);
    }

    public PayException(CustomApiCode code) {
        super(code);
    }

}
