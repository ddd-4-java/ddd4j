package io.ddd4j.core.exception;

import java.io.Serial;

/**
 * 认证异常抽象（纯 Java）
 * <p>
 * 框架无关的认证失败异常基类。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class AuthenticationException extends RuntimeException {

    /** Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002 */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * \u4f7f\u7528\u9519\u8bef\u6d88\u606f\u521b\u5efa\u8ba4\u8bc1\u5f02\u5e38\u3002
     * @param message \u9519\u8bef\u6d88\u606f
     */
    public AuthenticationException(String message) {
        super(message);
    }

    /**
     * \u4f7f\u7528\u9519\u8bef\u6d88\u606f\u521b\u5efa\u8ba4\u8bc1\u5f02\u5e38\u3002
     * @param message \u9519\u8bef\u6d88\u606f
     * @param cause \u539f\u59cb\u5f02\u5e38
     */
    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
