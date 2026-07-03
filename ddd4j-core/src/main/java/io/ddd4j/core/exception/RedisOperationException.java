package io.ddd4j.core.exception;

import io.ddd4j.core.ApiCode;
import io.ddd4j.core.CustomApiCode;

/**
 * Redis 操作异常。
 * <p>
 * 当 Redis 缓存操作（连接、读写、超时等）失败时抛出。
 * 继承 {@link BizRuntimeException}，支持国际化消息和业务错误码。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
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
