package io.ddd4j.core.exception;

import java.io.Serial;

/**
 * Token 过期异常（业务会话生命周期到点）。
 *
 * <p>跨三鉴权框架统一抛出：
 * <ul>
 *   <li>SaToken：{@code NotLoginException}(code 10013/10014)</li>
 *   <li>Shiro：{@code ExpiredCredentialsException} 或 {@code InvalidSessionException}</li>
 *   <li>Spring Security：{@code InvalidBearerTokenException}(token_expired)</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public class TokenExpiredException extends AuthenticationException {

    @Serial
    private static final long serialVersionUID = 1L;

    public TokenExpiredException(String message) {
        super(message);
    }

    public TokenExpiredException(String message, Throwable cause) {
        super(message, cause);
    }
}