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

    @Serial
    private static final long serialVersionUID = 1L;

    public NotLoggedInException(String message) {
        super(message);
    }

    public NotLoggedInException(String message, Throwable cause) {
        super(message, cause);
    }
}