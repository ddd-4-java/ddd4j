package io.ddd4j.core.exception;

import io.ddd4j.core.ApiCode;
import io.ddd4j.core.CustomApiCode;

public class CryptoException extends BizRuntimeException {

    public CryptoException(Integer code, String message) {
        super(code, message);
    }

    public CryptoException(Integer code, String i18nCode, String message) {
        super(code, i18nCode, message);
    }

    public CryptoException(Integer code, String i18nCode, Object[] args, String message) {
        super(code, i18nCode, args, message);
    }

    public CryptoException(String message) {
        super(message);
    }

    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }

    public CryptoException(ApiCode code, String i18nCode) {
        super(code, i18nCode);
    }

    public CryptoException(Integer code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public CryptoException(Integer code, String i18nCode, String defMsg, Throwable cause) {
        super(code, i18nCode, defMsg, cause);
    }

    public CryptoException(CustomApiCode code) {
        super(code);
    }

}
