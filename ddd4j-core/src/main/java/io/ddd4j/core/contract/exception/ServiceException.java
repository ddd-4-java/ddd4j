package io.ddd4j.core.contract.exception;

import io.ddd4j.core.context.I18nKit;
import io.ddd4j.core.contract.enums.IEnum;
import io.ddd4j.core.contract.enums.ResultCode;
import lombok.Data;

/**
 * 服务异常，可用于控制业务异常流程，抛出后由统一异常增强类捕获，返回友好提示
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
public class ServiceException extends RuntimeException {
    protected Integer code;

    public ServiceException(Integer code, String message) {
        super(I18nKit.get(message));
        this.code = code;
    }

    public ServiceException(Integer code, String message, Object... args) {
        super(I18nKit.get(message, args));
        this.code = code;
    }

    public ServiceException(String message, Object... args) {
        super(I18nKit.get(message, args));
        this.code = ResultCode.FAIL.getCode();
    }

    public ServiceException() {
        this(ResultCode.FAIL.getCode(), ResultCode.FAIL.getDesc());
    }

    public ServiceException(String message) {
        this(ResultCode.FAIL.getCode(), message);
    }

    public ServiceException(Throwable e) {
        this(e.getMessage());
    }

    public ServiceException(IEnum<Integer> resultCode) {
        this(resultCode.getCode(), resultCode.getDesc());
    }

}