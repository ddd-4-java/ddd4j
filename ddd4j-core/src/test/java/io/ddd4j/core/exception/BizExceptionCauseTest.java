package io.ddd4j.core.exception;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 验证业务异常包装外部失败时保留完整诊断原因。
 */
class BizExceptionCauseTest {

    /** \u9a8c\u8bc1\u4e1a\u52a1\u8fd0\u884c\u65f6\u5f02\u5e38\u4fdd\u7559\u6784\u9020\u65f6\u63d0\u4f9b\u7684 IOException \u539f\u56e0\u3002 */
    @Test
    void runtimeWrapperMustPreserveOriginalCause() {
        IOException cause = new IOException("upstream failure");
        assertSame(cause, new BizRuntimeException(cause).getCause());
    }

    /** \u9a8c\u8bc1\u4e1a\u52a1\u53d7\u68c0\u5f02\u5e38\u4fdd\u7559\u6784\u9020\u65f6\u63d0\u4f9b\u7684 IOException \u539f\u56e0\u3002 */
    @Test
    void checkedWrapperMustPreserveOriginalCause() {
        IOException cause = new IOException("upstream failure");
        assertSame(cause, new BizCheckedException(cause).getCause());
    }
}
