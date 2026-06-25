package io.ddd4j.boot.core.exception;

import io.ddd4j.boot.core.ApiCode;
import io.ddd4j.boot.core.CustomApiCode;

public class RedisOperationException extends BizRuntimeException {

    public RedisOperationException(Integer code, String message) {
        super(code, message);
    }

    public RedisOperationException(Integer code, String i18nCode, String message) {
        super(code, i18nCode, message);
    }

    public RedisOperationException(Integer code, String i18nCode, Object[] args, String message) {
        super(code, i18nCode, args, message);
    }

    public RedisOperationException(String message) {
        super(message);
    }

    public RedisOperationException(String message, Throwable cause) {
        super(message, cause);
    }

    public RedisOperationException(ApiCode code, String i18nCode) {
        super(code, i18nCode);
    }

    public RedisOperationException(Integer code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public RedisOperationException(Integer code, String i18nCode, String defMsg, Throwable cause) {
        super(code, i18nCode, defMsg, cause);
    }

    public RedisOperationException(CustomApiCode code) {
        super(code);
    }

}
