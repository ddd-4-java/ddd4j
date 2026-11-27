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

    /** Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002 */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * \u4f7f\u7528\u9519\u8bef\u6d88\u606f\u521b\u5efa\u4ee4\u724c\u8fc7\u671f\u5f02\u5e38\u3002
     * @param message \u9519\u8bef\u6d88\u606f
     */
    public TokenExpiredException(String message) {
        super(message);
    }

    /**
     * \u4f7f\u7528\u9519\u8bef\u6d88\u606f\u521b\u5efa\u4ee4\u724c\u8fc7\u671f\u5f02\u5e38\u3002
     * @param message \u9519\u8bef\u6d88\u606f
     * @param cause \u539f\u59cb\u5f02\u5e38
     */
    public TokenExpiredException(String message, Throwable cause) {
        super(message, cause);
    }
}