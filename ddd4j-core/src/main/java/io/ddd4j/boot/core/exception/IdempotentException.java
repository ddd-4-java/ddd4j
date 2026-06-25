package io.ddd4j.boot.core.exception;

import io.ddd4j.boot.core.ApiCode;
import io.ddd4j.boot.core.CustomApiCode;

public class IdempotentException extends BizRuntimeException {

    public IdempotentException(Integer code, String message) {
        super(code, message);
    }

    public IdempotentException(Integer code, String i18nCode, String message) {
        super(code, i18nCode, message);
    }

    public IdempotentException(Integer code, String i18nCode, Object[] args, String message) {
        super(code, i18nCode, args, message);
    }

    public IdempotentException(String message) {
        super(message);
    }

    public IdempotentException(String message, Throwable cause) {
        super(message, cause);
    }

    public IdempotentException(ApiCode code, String i18nCode) {
        super(code, i18nCode);
    }

    public IdempotentException(Integer code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public IdempotentException(Integer code, String i18nCode, String defMsg, Throwable cause) {
        super(code, i18nCode, defMsg, cause);
    }

    public IdempotentException(CustomApiCode code) {
        super(code);
    }
}
