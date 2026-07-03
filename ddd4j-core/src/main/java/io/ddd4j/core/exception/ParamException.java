package io.ddd4j.core.exception;

import io.ddd4j.core.ApiCode;
import io.ddd4j.core.CustomApiCode;

/**
 * 参数校验异常。
 * <p>
 * 当请求参数不符合预期时抛出，由统一异常处理层捕获后返回友好提示。
 * 继承 {@link BizRuntimeException}，支持国际化消息和业务错误码。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class ParamException extends BizRuntimeException {

    public ParamException(Integer code, String message) {
        super(code, message);
    }

    public ParamException(Integer code, String i18nCode, String message) {
        super(code, i18nCode, message);
    }

    public ParamException(Integer code, String i18nCode, Object[] args, String message) {
        super(code, i18nCode, args, message);
    }

    public ParamException(String message) {
        super(message);
    }

    public ParamException(String message, Throwable cause) {
        super(message, cause);
    }

    public ParamException(ApiCode code, String i18nCode) {
        super(code, i18nCode);
    }

    public ParamException(Integer code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public ParamException(Integer code, String i18nCode, String defMsg, Throwable cause) {
        super(code, i18nCode, defMsg, cause);
    }

    public ParamException(CustomApiCode code) {
        super(code);
    }

}
