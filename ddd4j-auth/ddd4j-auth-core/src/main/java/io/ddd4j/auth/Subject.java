package io.ddd4j.auth;

import java.io.Serializable;
import java.util.Set;

/**
 * Subject 抽象（纯 Java）
 * <p>
 * 代表当前认证主体。各框架适配层（Sa-Token/Spring Security/Shiro）提供具体实现。
 *
 * @author Loong Wan
 * @since 3.4.x
 */
public interface Subject extends Serializable {

    /** 唯一标识（用户ID/登录名） */
    String getId();

    /** 显示名称 */
    String getName();

    /** 是否已认证 */
    boolean isAuthenticated();

    /** 租户ID（多租户场景） */
    String getTenantId();

    /** 角色集合 */
    Set<String> getRoles();

    /** 权限集合 */
    Set<String> getPermissions();

    /** 角色检查 */
    default boolean hasRole(String role) {
        return getRoles() != null && getRoles().contains(role);
    }

    /** 权限检查 */
    default boolean hasPermission(String permission) {
        return getPermissions() != null && getPermissions().contains(permission);
    }
}
