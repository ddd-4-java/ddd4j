package io.ddd4j.core.exception;

import io.ddd4j.core.api.ResultCode;

import java.util.Map;
import java.util.StringJoiner;

/**
 * 校验异常，可用于控制业务异常流程，抛出后由统一异常增强类捕获，返回友好提示
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class ValidateException extends BizRuntimeException {
    private Map<String, String> errorMap;

    public ValidateException(Map<String, String> errorMap) {
        super(ResultCode.PARAMETER_VALIDATION_FAILED.getCode(), ResultCode.PARAMETER_VALIDATION_FAILED.getDesc());
        this.errorMap = errorMap;
    }

    public ValidateException(String errorMsg) {
        super(ResultCode.PARAMETER_VALIDATION_FAILED.getCode(), errorMsg);
    }

    public String toString() {
        return (new StringJoiner(", ", ValidateException.class.getSimpleName() + "[", "]")).add("code=" + this.getCode()).add("msg=" + this.getMessage()).add("errorMap=" + this.errorMap).toString();
    }

    public Map<String, String> getErrorMap() {
        return this.errorMap;
    }
}
