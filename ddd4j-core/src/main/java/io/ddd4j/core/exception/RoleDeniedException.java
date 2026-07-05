package io.ddd4j.core.exception;

import java.io.Serial;

/**
 * 角色被拒绝异常（访问受保护资源但缺少必要角色）。
 *
 * <p>跨三鉴权框架统一抛出：
 * <ul>
 *   <li>SaToken：{@code NotRoleException}</li>
 *   <li>Shiro：{@code UnauthorizedException}（role 检查失败）</li>
 *   <li>Spring Security：{@code AccessDeniedException}</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public class RoleDeniedException extends AuthorizationException {

    @Serial
    private static final long serialVersionUID = 1L;

    public RoleDeniedException(String message) {
        super(message);
    }

    public RoleDeniedException(String message, Throwable cause) {
        super(message, cause);
    }
}