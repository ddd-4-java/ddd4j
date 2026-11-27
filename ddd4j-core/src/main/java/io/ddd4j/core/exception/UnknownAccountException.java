package io.ddd4j.core.exception;

import java.io.Serial;

/**
 * 账号不存在异常（登录时账号未注册）。
 *
 * <p>跨三鉴权框架统一抛出：
 * <ul>
 *   <li>SaToken：{@code NotLoginException}(code 10011) / {@code DisableServiceException}</li>
 *   <li>Shiro：{@code UnknownAccountException}</li>
 *   <li>Spring Security：{@code UsernameNotFoundException}</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public class UnknownAccountException extends AuthenticationException {

    /** Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002 */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * \u4f7f\u7528\u9519\u8bef\u6d88\u606f\u521b\u5efa\u672a\u77e5\u8d26\u53f7\u5f02\u5e38\u3002
     * @param message \u9519\u8bef\u6d88\u606f
     */
    public UnknownAccountException(String message) {
        super(message);
    }

    /**
     * \u4f7f\u7528\u9519\u8bef\u6d88\u606f\u521b\u5efa\u672a\u77e5\u8d26\u53f7\u5f02\u5e38\u3002
     * @param message \u9519\u8bef\u6d88\u606f
     * @param cause \u539f\u59cb\u5f02\u5e38
     */
    public UnknownAccountException(String message, Throwable cause) {
        super(message, cause);
    }
}