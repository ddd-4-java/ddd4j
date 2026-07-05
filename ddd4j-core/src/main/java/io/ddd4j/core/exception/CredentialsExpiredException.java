package io.ddd4j.core.exception;

import java.io.Serial;

/**
 * 凭证（密码）过期异常。
 *
 * <p>跨三鉴权框架统一抛出：
 * <ul>
 *   <li>SaToken：通过 {@code SaTempKit.updatePassword(...)} 检测</li>
 *   <li>Shiro：{@code ExpiredCredentialsException}</li>
 *   <li>Spring Security：{@code CredentialsExpiredException}</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public class CredentialsExpiredException extends AuthenticationException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CredentialsExpiredException(String message) {
        super(message);
    }

    public CredentialsExpiredException(String message, Throwable cause) {
        super(message, cause);
    }
}