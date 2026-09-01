package io.ddd4j.core.contract.exception;

import io.ddd4j.core.contract.enums.IEnum;
import io.ddd4j.core.contract.enums.ResultCode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ServiceException} 构造行为单元测试。
 */
class ServiceExceptionTest {

    @Test
    void ctorCodeAndMessage_shouldExposeBoth() {
        ServiceException ex = new ServiceException(4001, "msg");
        assertEquals(4001, ex.getCode());
        assertEquals("msg", ex.getMessage());
    }

    @Test
    void ctorVarargs_shouldFormatPlaceholders() {
        ServiceException ex = new ServiceException(500, "err.{}", "alice");
        assertEquals(500, ex.getCode());
        assertEquals("err.alice", ex.getMessage());
    }

    @Test
    void ctorIEnum_shouldMapCodeAndDesc() {
        IEnum<Integer> custom = new IEnum<Integer>() {
            @Override
            public Integer getCode() {
                return 7001;
            }

            @Override
            public String getDesc() {
                return "自定义错误";
            }
        };
        ServiceException ex = new ServiceException(custom);
        assertEquals(7001, ex.getCode());
        assertEquals("自定义错误", ex.getMessage());
    }

    @Test
    void ctorThrowable_shouldWrapMessage() {
        ServiceException ex = new ServiceException(new IllegalStateException("boom"));
        assertEquals("boom", ex.getMessage());
        assertEquals(ResultCode.FAIL.getCode(), ex.getCode());
    }

    @Test
    void ctorDefault_shouldUseFailCode() {
        ServiceException ex = new ServiceException();
        assertEquals(ResultCode.FAIL.getCode(), ex.getCode());
        assertEquals(ResultCode.FAIL.getDesc(), ex.getMessage());
    }

    @Test
    void ctorString_shouldUseMessageKey() {
        ServiceException ex = new ServiceException("key");
        assertEquals(ResultCode.FAIL.getCode(), ex.getCode());
        assertEquals("key", ex.getMessage());
    }

    @Test
    void subclassValidateException_shouldPreserveErrorMap() {
        Map<String, String> errors = new HashMap<>();
        errors.put("name", "不能为空");
        ValidateException ex = new ValidateException(errors);
        assertEquals(ResultCode.PARAMETER_VALIDATION_FAILED.getCode(), ex.getCode());
        assertSame(errors, ex.getErrorMap());
        assertEquals("不能为空", ex.getErrorMap().get("name"));
    }

    @Test
    void validateException_toString_includesErrorMap() {
        Map<String, String> errors = new HashMap<>();
        errors.put("age", "必须大于0");
        ValidateException ex = new ValidateException(errors);
        String text = ex.toString();
        assertTrue(text.contains("errorMap="), "toString 应包含 errorMap 字段: " + text);
        assertTrue(text.contains("age"), "toString 应包含 errorMap 内容: " + text);
        assertTrue(text.contains("必须大于0"), "toString 应包含 errorMap 内容: " + text);
    }
}
