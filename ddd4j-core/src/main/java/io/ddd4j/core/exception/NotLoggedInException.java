package io.ddd4j.core.exception;

import java.io.Serial;

/**
 * 未登录异常（访问受保护资源前未登录）。
 *
 * <p>跨三鉴权框架统一抛出：
 * <ul>
 *   <li>SaToken：{@code NotLoginException}</li>
 *   <li>Shiro：{@code UnauthenticatedException}（{@code subject.isAuthenticated() == false}）</li>
 *   <li>Spring Security：{@code AuthenticationCredentialsNotFoundException}</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public class NotLoggedInException extends AuthenticationException {

    /** Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002 */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * \u4f7f\u7528\u9519\u8bef\u6d88\u606f\u521b\u5efa\u672a\u767b\u5f55\u5f02\u5e38\u3002
     * @param message \u9519\u8bef\u6d88\u606f
     */
    public NotLoggedInException(String message) {
        super(message);
    }

    /**
     * \u4f7f\u7528\u9519\u8bef\u6d88\u606f\u521b\u5efa\u672a\u767b\u5f55\u5f02\u5e38\u3002
     * @param message \u9519\u8bef\u6d88\u606f
     * @param cause \u539f\u59cb\u5f02\u5e38
     */
    public NotLoggedInException(String message, Throwable cause) {
        super(message, cause);
    }
}