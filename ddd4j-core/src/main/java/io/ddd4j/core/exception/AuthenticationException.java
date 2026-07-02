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

    @Serial
    private static final long serialVersionUID = 1L;

    public AuthenticationException(String message) {
        super(message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
