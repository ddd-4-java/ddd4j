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

    private static final String KEY = "ddd4j.exception.overload.contract";

    @Test
    void bothBinaryConstructorSignaturesArePublished() {
        Class<?>[] exceptionTypes = {
                BizIOException.class, CryptoException.class, IdempotentException.class,
                MessagePushException.class, ParamException.class, PayException.class,
                RedisOperationException.class
        };
        for (Class<?> exceptionType : exceptionTypes) {
            assertDoesNotThrow(() -> exceptionType.getConstructor(ApiCode.class, String.class));
            assertDoesNotThrow(() -> exceptionType.getConstructor(CustomApiCode.class, String.class));
        }
    }

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

    private static void assertRuntime(BizRuntimeException exception, CustomApiCode code) {
        assertEquals(code.getCode(), exception.getCode());
        assertEquals(KEY, exception.getI18nCode());
        assertEquals(I18nKit.get(KEY, code.getReason()), exception.getMessage());
    }
}
