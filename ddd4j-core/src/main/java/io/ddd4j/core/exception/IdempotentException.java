package io.ddd4j.core.exception;

import io.ddd4j.core.ApiCode;
import io.ddd4j.core.CustomApiCode;


/**
 * 幂等性校验异常。
 * <p>
 * 当请求重复提交或违反幂等约束时抛出，用于防止重复处理。
 * 继承 {@link BizRuntimeException}，支持国际化消息和业务错误码。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
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
