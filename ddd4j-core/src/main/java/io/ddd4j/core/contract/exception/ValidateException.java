package io.ddd4j.core.contract.exception;

import io.ddd4j.core.contract.enums.ResultCode;

import java.util.Map;
import java.util.StringJoiner;

/**
 * 校验异常，可用于控制业务异常流程，抛出后由统一异常增强类捕获，返回友好提示
 *
 * @author Jensen
 * @公众号 架构师修行录
 */
/**
 * @deprecated 1.0.x 契约归位：与 3.0.x 对齐后，本类语义已由 {@code io.ddd4j.core.exception.ValidateException} 承接。
 * 本类仅为保持 1.0.x 现有消费方兼容而保留，新代码请使用新包路径。
 */
@Deprecated
public class ValidateException extends ServiceException {
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
