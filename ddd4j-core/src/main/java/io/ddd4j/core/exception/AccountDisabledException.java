package io.ddd4j.core.exception;

import java.io.Serial;

/**
 * 账号被禁用异常（业务侧封禁）。
 *
 * <p>跨三鉴权框架统一抛出：
 * <ul>
 *   <li>SaToken：{@code DisableServiceException}</li>
 *   <li>Shiro：{@code DisabledAccountException}</li>
 *   <li>Spring Security：{@code DisabledException}</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public class AccountDisabledException extends AuthenticationException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AccountDisabledException(String message) {
        super(message);
    }

    public AccountDisabledException(String message, Throwable cause) {
        super(message, cause);
    }
}