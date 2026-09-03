package io.ddd4j.auth.shiro.subject;

import java.util.ArrayList;
import java.util.List;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;

import io.ddd4j.core.auth.AuthPrincipal;

/**
 * 基于 Apache Shiro 的 {@link Subject} 实现。
 *
 * <p>将 ddd4j 的 {@link io.ddd4j.core.subject.Subject} 契约委托给
 * Shiro 的 {@link org.apache.shiro.subject.Subject}（通过 {@link SecurityUtils#getSubject()} 获取）。
 *
 * <p>权限/角色校验直接委托给 Shiro 的 Realm 体系，登录态判断委托给 Shiro 的会话管理。
 *
 * @author wandl
 * @since 3.4.x
 */
public class ShiroSubject implements io.ddd4j.core.subject.Subject {

    /**
     * 获取当前线程绑定的 Shiro Subject。
     *
     * @return Shiro Subject（未登录时返回 null）
     */
    private Subject getShiroSubject() {
        try {
            return SecurityUtils.getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public <T extends AuthPrincipal> T getPrincipal() {
        Subject subject = getShiroSubject();
        if (subject == null) {
            return null;
        }
        Object principal = subject.getPrincipal();
        if (principal instanceof AuthPrincipal) {
            return (T) principal;
        }
        return null;
    }

    @Override
    public <T extends AuthPrincipal> T getPrincipalByLoginId(Object loginId) {
        // Shiro 不直接支持按 loginId 查询 Principal，需要业务层自行实现
        return getPrincipal();
    }

    @Override
    public <T extends AuthPrincipal> T getPrincipalByToken(String tokenValue) {
        // Shiro 不直接支持按 token 查询 Principal，需要业务层自行实现
        return getPrincipal();
    }

    @Override
    public boolean isPermitted(String permission) {
        Subject subject = getShiroSubject();
        return subject != null && subject.isPermitted(permission);
    }

    @Override
    public boolean isPermitted(Object loginId, String permission) {
        // Shiro 只能校验当前登录用户的权限，loginId 参数需业务层自行处理
        return isPermitted(permission);
    }

    @Override
    public boolean[] isPermitted(String... permissions) {
        Subject subject = getShiroSubject();
        if (subject == null) {
            return new boolean[permissions.length];
        }
        return subject.isPermitted(permissions);
    }

    @Override
    public boolean[] isPermitted(Object loginId, String... permissions) {
        return isPermitted(permissions);
    }

    @Override
    public boolean isPermittedAny(String... permissions) {
        Subject subject = getShiroSubject();
        if (subject == null) {
            return false;
        }
        for (String permission : permissions) {
            if (subject.isPermitted(permission)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isPermittedAny(Object loginId, String... permissions) {
        return isPermittedAny(permissions);
    }

    @Override
    public boolean isPermittedAll(String... permissions) {
        Subject subject = getShiroSubject();
        if (subject == null) {
            return false;
        }
        boolean[] results = subject.isPermitted(permissions);
        for (boolean result : results) {
            if (!result) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isPermittedAll(Object loginId, String... permissions) {
        return isPermittedAll(permissions);
    }

    @Override
    public boolean hasRole(String roleIdentifier) {
        Subject subject = getShiroSubject();
        return subject != null && subject.hasRole(roleIdentifier);
    }

    @Override
    public boolean hasRole(Object loginId, String roleIdentifier) {
        return hasRole(roleIdentifier);
    }

    @Override
    public boolean[] hasRoles(String... roleIdentifiers) {
        Subject subject = getShiroSubject();
        if (subject == null) {
            return new boolean[roleIdentifiers.length];
        }
        List<String> roleList = new ArrayList<>(java.util.Arrays.asList(roleIdentifiers));
        return subject.hasRoles(roleList);
    }

    @Override
    public boolean[] hasRoles(Object loginId, String... roleIdentifiers) {
        return hasRoles(roleIdentifiers);
    }

    @Override
    public boolean hasAnyRole(String... roleIdentifiers) {
        Subject subject = getShiroSubject();
        if (subject == null) {
            return false;
        }
        for (String role : roleIdentifiers) {
            if (subject.hasRole(role)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasAnyRole(Object loginId, String... roleIdentifiers) {
        return hasAnyRole(roleIdentifiers);
    }

    @Override
    public boolean hasAllRole(String... roleIdentifiers) {
        Subject subject = getShiroSubject();
        if (subject == null) {
            return false;
        }
        for (String role : roleIdentifiers) {
            if (!subject.hasRole(role)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean hasAllRole(Object loginId, String... roleIdentifiers) {
        return hasAllRole(roleIdentifiers);
    }

    @Override
    public boolean isAuthenticated() {
        Subject subject = getShiroSubject();
        return subject != null && subject.isAuthenticated();
    }

    @Override
    public boolean isAuthenticated(Object loginId) {
        return isAuthenticated();
    }

    @Override
    public boolean isRemembered() {
        Subject subject = getShiroSubject();
        return subject != null && subject.isRemembered();
    }

    @Override
    public boolean isTrustDeviceId(String deviceId) {
        // Shiro 不内置设备信任机制，委托给业务层
        return false;
    }

    @Override
    public boolean isTrustDeviceId(Object userId, String deviceId) {
        return isTrustDeviceId(deviceId);
    }

    @Override
    public Object getLoginId() {
        Subject subject = getShiroSubject();
        if (subject == null) {
            return null;
        }
        return subject.getPrincipal();
    }

    @Override
    public Object getUserId() {
        AuthPrincipal principal = getPrincipal();
        return principal != null ? principal.getUserId() : null;
    }

}
