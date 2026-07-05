package io.ddd4j.core.exception;

import java.io.Serial;

/**
 * 账号过期异常（账号使用期限到期）。
 *
 * <p>跨三鉴权框架统一抛出：
 * <ul>
 *   <li>SaToken：业务侧通过 {@code SaTempKit.disable(...)} 触发</li>
 *   <li>Shiro：{@code ExpiredAccountException}</li>
 *   <li>Spring Security：{@code AccountExpiredException}</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public class AccountExpiredException extends AuthenticationException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AccountExpiredException(String message) {
        super(message);
    }

    public AccountExpiredException(String message, Throwable cause) {
        super(message, cause);
    }
}