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
    /** \u5b57\u6bb5\u5230\u6821\u9a8c\u9519\u8bef\u6d88\u606f\u7684\u6620\u5c04\u3002 */
    private Map<String, String> errorMap;

    /**
     * \u4f7f\u7528\u5b57\u6bb5\u6821\u9a8c\u9519\u8bef\u6620\u5c04\u521b\u5efa\u5f02\u5e38\u3002
     * @param errorMap \u5b57\u6bb5\u5230\u9519\u8bef\u6d88\u606f\u7684\u6620\u5c04
     */
    public ValidateException(Map<String, String> errorMap) {
        super(ResultCode.PARAMETER_VALIDATION_FAILED.getCode(), ResultCode.PARAMETER_VALIDATION_FAILED.getDesc());
        this.errorMap = errorMap;
    }

    /**
     * \u4f7f\u7528\u9519\u8bef\u6d88\u606f\u521b\u5efa\u5f02\u5e38\u3002
     * @param errorMsg \u9519\u8bef\u6d88\u606f
     */
    public ValidateException(String errorMsg) {
        super(ResultCode.PARAMETER_VALIDATION_FAILED.getCode(), errorMsg);
    }

    /**
     * \u628a\u6821\u9a8c\u72b6\u6001\u3001\u9519\u8bef\u6d88\u606f\u548c\u5b57\u6bb5\u9519\u8bef\u6c47\u603b\u4e3a\u8bca\u65ad\u6587\u672c\u3002
     * @return \u5f53\u524d\u6821\u9a8c\u5f02\u5e38\u7684\u6587\u672c\u8868\u793a
     */
    public String toString() {
        return (new StringJoiner(", ", ValidateException.class.getSimpleName() + "[", "]")).add("code=" + this.getCode()).add("msg=" + this.getMessage()).add("errorMap=" + this.errorMap).toString();
    }

    /**
     * \u83b7\u53d6\u5b57\u6bb5\u6821\u9a8c\u9519\u8bef\u6620\u5c04\u3002
     * @return \u5b57\u6bb5\u5230\u9519\u8bef\u6d88\u606f\u7684\u6620\u5c04
     */
    public Map<String, String> getErrorMap() {
        return this.errorMap;
    }
}
