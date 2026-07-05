package io.ddd4j.core.exception;

import java.io.Serial;

/**
 * 授权/访问控制异常抽象（鉴权通过后无权限/无角色）。
 *
 * <p>框架无关的授权失败异常基类。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public class AuthorizationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AuthorizationException(String message) {
        super(message);
    }

    public AuthorizationException(String message, Throwable cause) {
        super(message, cause);
    }
}