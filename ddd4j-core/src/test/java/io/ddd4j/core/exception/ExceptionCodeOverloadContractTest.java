package io.ddd4j.core.exception;

import io.ddd4j.core.api.ApiCode;
import io.ddd4j.core.api.CustomApiCode;
import io.ddd4j.core.util.I18nKit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 公开异常构造器同时保留标准码和业务扩展码入口。
 */
class ExceptionCodeOverloadContractTest {

    /**
     * 构造本组测试输入时复用的固定键。
     */
    private static final String KEY = "ddd4j.exception.overload.contract";

    /**
     * 核对<code>exception.getCode()</code> 等于 <code>code.getCode()</code>，<code>exception.getI18nCode()</code> 等于 <code>KEY</code>。
     *
     * @param exception 测试中捕获或传播的异常
     * @param code      用于定位目标测试记录的业务编码
     */
    private static void assertRuntime(BizRuntimeException exception, CustomApiCode code) {
        assertEquals(code.getCode(), exception.getCode());
        assertEquals(KEY, exception.getI18nCode());
        assertEquals(I18nKit.get(KEY, code.getReason()), exception.getMessage());
    }

    /**
     * 验证<code>exceptionType.getConstructor(ApiCode.class, String.class)</code> 正常返回，<code>exceptionType.getConstructor(CustomApiCode.class, String.class)</code> 正常返回。
     */
    @Test
    void bothBinaryConstructorSignaturesArePublished() {
        Class<?>[] exceptionTypes = {
                BizCheckedException.class, BizRuntimeException.class, BizIOException.class,
                CryptoException.class, IdempotentException.class,
                MessagePushException.class, ParamException.class, PayException.class,
                RedisOperationException.class
        };
        for (Class<?> exceptionType : exceptionTypes) {
            assertDoesNotThrow(() -> exceptionType.getConstructor(ApiCode.class, String.class));
            assertDoesNotThrow(() -> exceptionType.getConstructor(CustomApiCode.class, String.class));
        }
    }

    /**
     * 验证<code>checked.getCode()</code> 等于 <code>code.getCode()</code>，<code>checked.getI18nCode()</code> 等于 <code>KEY</code>，<code>checked.getMessage()</code> 等于 <code>I18nKit.get(KEY, code.getReason())</code>。
     */
    @Test
    void standardApiCodeOverloadsPreserveCodeAndI18nKey() {
        ApiCode code = ApiCode.BAD_REQUEST;
        BizIOException checked = new BizIOException(code, KEY);
        assertEquals(code.getCode(), checked.getCode());
        assertEquals(KEY, checked.getI18nCode());
        assertEquals(I18nKit.get(KEY, code.getReason()), checked.getMessage());

        assertRuntime(new CryptoException(code, KEY), code);
        assertRuntime(new IdempotentException(code, KEY), code);
        assertRuntime(new MessagePushException(code, KEY), code);
        assertRuntime(new ParamException(code, KEY), code);
        assertRuntime(new PayException(code, KEY), code);
        assertRuntime(new RedisOperationException(code, KEY), code);
    }

    /**
     * 验证<code>checked.getCode()</code> 等于 <code>code.getCode()</code>，<code>checked.getI18nCode()</code> 等于 <code>KEY</code>，<code>checked.getMessage()</code> 等于 <code>I18nKit.get(KEY, code.getReason())</code>。
     */
    @Test
    void customApiCodeOverloadsPreserveCodeAndI18nKey() {
        CustomApiCode code = new CustomApiCode() {
            @Override
            public Integer getCode() {
                return 471;
            }

            @Override
            public String getReason() {
                return "custom failure";
            }
        };
        BizIOException checked = new BizIOException(code, KEY);
        assertEquals(code.getCode(), checked.getCode());
        assertEquals(KEY, checked.getI18nCode());
        assertEquals(I18nKit.get(KEY, code.getReason()), checked.getMessage());

        assertRuntime(new CryptoException(code, KEY), code);
        assertRuntime(new IdempotentException(code, KEY), code);
        assertRuntime(new MessagePushException(code, KEY), code);
        assertRuntime(new ParamException(code, KEY), code);
        assertRuntime(new PayException(code, KEY), code);
        assertRuntime(new RedisOperationException(code, KEY), code);
    }

    /**
     * 验证被测调用 抛出 <code>NullPointerException.class</code>。
     */
    @Test
    void typedNullCodeFailsWithoutChoosingAnotherOverload() {
        assertThrows(NullPointerException.class, () -> new BizIOException((ApiCode) null, KEY));
        assertThrows(NullPointerException.class, () -> new BizIOException((CustomApiCode) null, KEY));
        assertThrows(NullPointerException.class, () -> new CryptoException((ApiCode) null, KEY));
        assertThrows(NullPointerException.class, () -> new CryptoException((CustomApiCode) null, KEY));
        assertThrows(NullPointerException.class, () -> new IdempotentException((ApiCode) null, KEY));
        assertThrows(NullPointerException.class, () -> new IdempotentException((CustomApiCode) null, KEY));
        assertThrows(NullPointerException.class, () -> new MessagePushException((ApiCode) null, KEY));
        assertThrows(NullPointerException.class, () -> new MessagePushException((CustomApiCode) null, KEY));
        assertThrows(NullPointerException.class, () -> new ParamException((ApiCode) null, KEY));
        assertThrows(NullPointerException.class, () -> new ParamException((CustomApiCode) null, KEY));
        assertThrows(NullPointerException.class, () -> new PayException((ApiCode) null, KEY));
        assertThrows(NullPointerException.class, () -> new PayException((CustomApiCode) null, KEY));
        assertThrows(NullPointerException.class, () -> new RedisOperationException((ApiCode) null, KEY));
        assertThrows(NullPointerException.class, () -> new RedisOperationException((CustomApiCode) null, KEY));
    }
}
