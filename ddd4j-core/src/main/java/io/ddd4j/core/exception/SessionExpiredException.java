package io.ddd4j.core.exception;

import java.io.Serial;

/**
 * Session 过期异常（业务会话已超时）。
 *
 * <p>跨三鉴权框架统一抛出：
 * <ul>
 *   <li>SaToken：{@code NotLoginException}(code 10014 "token已过期")</li>
 *   <li>Shiro：{@code InvalidSessionException} 或 {@code ExpiredSessionException}</li>
 *   <li>Spring Security：业务侧由 {@code SessionManagementFilter} 触发</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public class SessionExpiredException extends AuthenticationException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SessionExpiredException(String message) {
        super(message);
    }

    public SessionExpiredException(String message, Throwable cause) {
        super(message, cause);
    }
}