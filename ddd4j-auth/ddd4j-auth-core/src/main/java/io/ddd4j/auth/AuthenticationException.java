package io.ddd4j.auth;

/**
 * 认证异常抽象（纯 Java）
 * <p>
 * 框架无关的认证失败异常基类。
 *
 * @author wandl
 * @since 3.4.x
 */
public class AuthenticationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AuthenticationException(String message) {
        super(message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
