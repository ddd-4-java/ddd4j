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

    /** Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002 */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * \u4f7f\u7528\u9519\u8bef\u6d88\u606f\u521b\u5efa\u6743\u9650\u4e0d\u8db3\u5f02\u5e38\u3002
     * @param message \u9519\u8bef\u6d88\u606f
     */
    public PermissionDeniedException(String message) {
        super(message);
    }

    /**
     * \u4f7f\u7528\u9519\u8bef\u6d88\u606f\u521b\u5efa\u6743\u9650\u4e0d\u8db3\u5f02\u5e38\u3002
     * @param message \u9519\u8bef\u6d88\u606f
     * @param cause \u539f\u59cb\u5f02\u5e38
     */
    public PermissionDeniedException(String message, Throwable cause) {
        super(message, cause);
    }
}