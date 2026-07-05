package io.ddd4j.core.exception;

import java.io.Serial;

/**
 * 权限被拒绝异常（访问受保护资源但缺少必要权限）。
 *
 * <p>跨三鉴权框架统一抛出：
 * <ul>
 *   <li>SaToken：{@code NotPermissionException}</li>
 *   <li>Shiro：{@code UnauthorizedException}（permission 检查失败）</li>
 *   <li>Spring Security：{@code AccessDeniedException}</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public class PermissionDeniedException extends AuthorizationException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PermissionDeniedException(String message) {
        super(message);
    }

    public PermissionDeniedException(String message, Throwable cause) {
        super(message, cause);
    }
}