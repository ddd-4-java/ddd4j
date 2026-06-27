package io.ddd4j.core.api.contract.exception;

import lombok.Getter;

import java.util.Map;

/**
 * 校验异常，继承 ServiceException，携带校验错误详情
 *
 * @author Jensen
 * @公众号 架构师修行录
 */
@Getter
public class ValidateException extends ServiceException {

    private final Map<String, String> errorMap;

    public ValidateException(Map<String, String> errorMap) {
        super("参数校验失败");
        this.errorMap = errorMap;
    }

    public ValidateException(String message, Map<String, String> errorMap) {
        super(message);
        this.errorMap = errorMap;
    }
}
