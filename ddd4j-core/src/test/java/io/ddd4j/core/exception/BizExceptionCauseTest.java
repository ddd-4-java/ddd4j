package io.ddd4j.core.exception;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 验证业务异常包装外部失败时保留完整诊断原因。
 */
class BizExceptionCauseTest {

    /**
     * 验证业务运行时异常保留构造时提供的 IOException 原因。
     */
    @Test
    void runtimeWrapperMustPreserveOriginalCause() {
        IOException cause = new IOException("upstream failure");
        assertSame(cause, new BizRuntimeException(cause).getCause());
    }

    /**
     * 验证业务受检异常保留构造时提供的 IOException 原因。
     */
    @Test
    void checkedWrapperMustPreserveOriginalCause() {
        IOException cause = new IOException("upstream failure");
        assertSame(cause, new BizCheckedException(cause).getCause());
    }
}
