package io.ddd4j.core.exception;

import java.io.Serial;

/**
 * Token 无效异常（格式错误/签名错误/伪造）。
 *
 * <p>跨三鉴权框架统一抛出：
 * <ul>
 *   <li>SaToken：{@code NotLoginException}(code 10012, "无效的Token")</li>
 *   <li>Shiro：{@code IncorrectCredentialsException} 或 {@code CorruptTokenException}</li>
 *   <li>Spring Security：{@code BadCredentialsException} 或 {@code InvalidBearerTokenException}</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public class TokenInvalidException extends AuthenticationException {

    /** Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002 */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * \u4f7f\u7528\u9519\u8bef\u6d88\u606f\u521b\u5efa\u4ee4\u724c\u65e0\u6548\u5f02\u5e38\u3002
     * @param message \u9519\u8bef\u6d88\u606f
     */
    public TokenInvalidException(String message) {
        super(message);
    }

    /**
     * \u4f7f\u7528\u9519\u8bef\u6d88\u606f\u521b\u5efa\u4ee4\u724c\u65e0\u6548\u5f02\u5e38\u3002
     * @param message \u9519\u8bef\u6d88\u606f
     * @param cause \u539f\u59cb\u5f02\u5e38
     */
    public TokenInvalidException(String message, Throwable cause) {
        super(message, cause);
    }
}