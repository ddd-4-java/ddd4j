package io.ddd4j.core.exception;

import io.ddd4j.core.api.ApiCode;
import io.ddd4j.core.api.CustomApiCode;
import io.ddd4j.core.util.I18nKit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 公开异常构造器同时保留标准码和业务扩展码入口。
 */
class ExceptionCodeOverloadContractTest {

    /** \u6784\u9020\u672c\u7ec4\u6d4b\u8bd5\u8f93\u5165\u65f6\u590d\u7528\u7684\u56fa\u5b9a\u952e\u3002 */
    private static final String KEY = "ddd4j.exception.overload.contract";

    /** \u9a8c\u8bc1<code>exceptionType.getConstructor(ApiCode.class, String.class)</code> \u6b63\u5e38\u8fd4\u56de\uff0c<code>exceptionType.getConstructor(CustomApiCode.class, String.class)</code> \u6b63\u5e38\u8fd4\u56de\u3002 */
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

    /** \u9a8c\u8bc1<code>checked.getCode()</code> \u7b49\u4e8e <code>code.getCode()</code>\uff0c<code>checked.getI18nCode()</code> \u7b49\u4e8e <code>KEY</code>\uff0c<code>checked.getMessage()</code> \u7b49\u4e8e <code>I18nKit.get(KEY, code.getReason())</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>checked.getCode()</code> \u7b49\u4e8e <code>code.getCode()</code>\uff0c<code>checked.getI18nCode()</code> \u7b49\u4e8e <code>KEY</code>\uff0c<code>checked.getMessage()</code> \u7b49\u4e8e <code>I18nKit.get(KEY, code.getReason())</code>\u3002 */
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

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
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

    /**
     * \u6838\u5bf9<code>exception.getCode()</code> \u7b49\u4e8e <code>code.getCode()</code>\uff0c<code>exception.getI18nCode()</code> \u7b49\u4e8e <code>KEY</code>\u3002
     * @param exception \u6d4b\u8bd5\u4e2d\u6355\u83b7\u6216\u4f20\u64ad\u7684\u5f02\u5e38
     * @param code \u7528\u4e8e\u5b9a\u4f4d\u76ee\u6807\u6d4b\u8bd5\u8bb0\u5f55\u7684\u4e1a\u52a1\u7f16\u7801
     */
    private static void assertRuntime(BizRuntimeException exception, CustomApiCode code) {
        assertEquals(code.getCode(), exception.getCode());
        assertEquals(KEY, exception.getI18nCode());
        assertEquals(I18nKit.get(KEY, code.getReason()), exception.getMessage());
    }
}
