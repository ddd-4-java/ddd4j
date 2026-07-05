package io.ddd4j.core.auth;

/**
 * 登录数超过 {@code maxLoginCount} 时，超限账号的下线方式。
 *
 * <p>对应 Sa-Token 的 {@code SaLogoutMode}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public enum AuthLogoutMode {

    /**
     * 注销下线（默认）。
     */
    LOGOUT,

    /**
     * 踢人下线（强制）。
     */
    KICKOUT,

    /**
     * 顶人下线（替换会话）。
     */
    REPLACED
}