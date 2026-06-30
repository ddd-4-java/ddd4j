package io.ddd4j.auth.shiro.subject;

import io.ddd4j.core.subject.AuthPrincipal;
import io.ddd4j.core.subject.AuthRequest;
import io.ddd4j.core.util.SubjectKit;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;

import java.util.List;

/**
 * 基于 Apache Shiro 的 {@link Subject} 实现。
 *
 * <p>将 ddd4j 的 {@link io.ddd4j.core.subject.Subject} 契约委托给
 * Shiro 的 {@link org.apache.shiro.subject.Subject}（通过 {@link SecurityUtils#getSubject()} 获取）。
 *
 * <p>权限/角色校验直接委托给 Shiro 的 Realm 体系，登录态判断委托给 Shiro 的会话管理。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
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
        if (java.util.Objects.isNull(subject)) {
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
        AuthPrincipal principal = getPrincipal();
        if (java.util.Objects.isNull(principal)) {
            return false;
        }
        // 统一委托数据源 SPI（对齐 SaTokenSubject 行为）
        List<String> perms = SubjectKit.getDataProvider().getPermissionList(principal);
        return SubjectKit.getStrategy().hasElement.apply(perms, permission);
    }

    @Override
    public boolean isPermitted(Object loginId, String permission) {
        return isPermitted(permission);
    }

    @Override
    public boolean[] isPermitted(String... permissions) {
        AuthPrincipal principal = getPrincipal();
        if (java.util.Objects.isNull(principal) || java.util.Objects.isNull(permissions) || permissions.length == 0) {
            return new boolean[0];
        }
        List<String> perms = SubjectKit.getDataProvider().getPermissionList(principal);
        boolean[] result = new boolean[permissions.length];
        for (int i = 0; i < permissions.length; i++) {
            result[i] = SubjectKit.getStrategy().hasElement.apply(perms, permissions[i]);
        }
        return result;
    }

    @Override
    public boolean[] isPermitted(Object loginId, String... permissions) {
        return isPermitted(permissions);
    }

    @Override
    public boolean isPermittedAny(String... permissions) {
        AuthPrincipal principal = getPrincipal();
        if (java.util.Objects.isNull(principal) || java.util.Objects.isNull(permissions) || permissions.length == 0) {
            return false;
        }
        List<String> perms = SubjectKit.getDataProvider().getPermissionList(principal);
        for (String permission : permissions) {
            if (SubjectKit.getStrategy().hasElement.apply(perms, permission)) {
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
        AuthPrincipal principal = getPrincipal();
        if (java.util.Objects.isNull(principal) || java.util.Objects.isNull(permissions) || permissions.length == 0) {
            return false;
        }
        List<String> perms = SubjectKit.getDataProvider().getPermissionList(principal);
        for (String permission : permissions) {
            if (!SubjectKit.getStrategy().hasElement.apply(perms, permission)) {
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
        AuthPrincipal principal = getPrincipal();
        if (java.util.Objects.isNull(principal)) {
            return false;
        }
        List<String> roles = SubjectKit.getDataProvider().getRoleList(principal);
        return SubjectKit.getStrategy().hasElement.apply(roles, roleIdentifier);
    }

    @Override
    public boolean hasRole(Object loginId, String roleIdentifier) {
        return hasRole(roleIdentifier);
    }

    @Override
    public boolean[] hasRoles(String... roleIdentifiers) {
        AuthPrincipal principal = getPrincipal();
        if (java.util.Objects.isNull(principal) || java.util.Objects.isNull(roleIdentifiers) || roleIdentifiers.length == 0) {
            return new boolean[0];
        }
        List<String> roles = SubjectKit.getDataProvider().getRoleList(principal);
        boolean[] result = new boolean[roleIdentifiers.length];
        for (int i = 0; i < roleIdentifiers.length; i++) {
            result[i] = SubjectKit.getStrategy().hasElement.apply(roles, roleIdentifiers[i]);
        }
        return result;
    }

    @Override
    public boolean[] hasRoles(Object loginId, String... roleIdentifiers) {
        return hasRoles(roleIdentifiers);
    }

    @Override
    public boolean hasAnyRole(String... roleIdentifiers) {
        AuthPrincipal principal = getPrincipal();
        if (java.util.Objects.isNull(principal) || java.util.Objects.isNull(roleIdentifiers) || roleIdentifiers.length == 0) {
            return false;
        }
        List<String> roles = SubjectKit.getDataProvider().getRoleList(principal);
        for (String role : roleIdentifiers) {
            if (SubjectKit.getStrategy().hasElement.apply(roles, role)) {
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
        AuthPrincipal principal = getPrincipal();
        if (java.util.Objects.isNull(principal) || java.util.Objects.isNull(roleIdentifiers) || roleIdentifiers.length == 0) {
            return false;
        }
        List<String> roles = SubjectKit.getDataProvider().getRoleList(principal);
        for (String role : roleIdentifiers) {
            if (!SubjectKit.getStrategy().hasElement.apply(roles, role)) {
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
        return java.util.Objects.nonNull(subject) && subject.isAuthenticated();
    }

    @Override
    public boolean isAuthenticated(Object loginId) {
        return isAuthenticated();
    }

    @Override
    public boolean isRemembered() {
        Subject subject = getShiroSubject();
        return java.util.Objects.nonNull(subject) && subject.isRemembered();
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
        if (java.util.Objects.isNull(subject)) {
            return null;
        }
        return subject.getPrincipal();
    }

    @Override
    public Object getUserId() {
        AuthPrincipal principal = getPrincipal();
        return java.util.Objects.nonNull(principal) ? principal.getUserId() : null;
    }

    // ==================== 会话生命周期（委托 Shiro Subject）====================

    @Override
    public String login(AuthRequest request) {
        Subject subject = SecurityUtils.getSubject();
        // Shiro 登录需要 AuthenticationToken，业务侧需提供 Realm 解析 loginId
        AuthenticationToken token = new UsernamePasswordToken(
                String.valueOf(request.getLoginId()),
                java.util.Objects.nonNull(request.getPrincipal()) ? String.valueOf(request.getPrincipal()) : ""
        );
        subject.login(token);
        // 登录后将 principal 存入 Shiro Session
        if (java.util.Objects.nonNull(request.getPrincipal()) && java.util.Objects.nonNull(subject.getSession())) {
            subject.getSession().setAttribute("principal", request.getPrincipal());
        }
        return java.util.Objects.nonNull(subject.getSession()) ? subject.getSession().getId().toString() : null;
    }

    @Override
    public void logout() {
        Subject subject = getShiroSubject();
        if (java.util.Objects.nonNull(subject)) {
            subject.logout();
        }
    }

    @Override
    public void logout(Object loginId) {
        // Shiro 通过 SessionDAO 删除指定账号的会话，具体实现委托业务层（不同 Shiro 版本 API 差异）
        // 默认行为：若指定 loginId 为当前登录用户则登出当前会话
        Object currentLoginId = getLoginId();
        if (java.util.Objects.nonNull(loginId) && loginId.equals(currentLoginId)) {
            logout();
        }
    }

    @Override
    public void kickout(Object loginId) {
        // Shiro 踢人下线等价于删除其会话
        logout(loginId);
    }

    @Override
    public String refresh() {
        Subject subject = getShiroSubject();
        if (java.util.Objects.nonNull(subject) && java.util.Objects.nonNull(subject.getSession())) {
            subject.getSession().touch();
            return subject.getSession().getId().toString();
        }
        return null;
    }

    @Override
    public <T extends AuthPrincipal> T verify(String token) {
        // Shiro 不直接支持按 token 反查 principal，需业务层扩展 Realm
        return getPrincipal();
    }

    // ==================== 封禁（委托业务层 / Shiro CacheManager）====================

    @Override
    public void disable(Object loginId, long timeout) {
        // Shiro 封禁通常通过 CacheManager 或自定义 Realm 实现，此处委托业务层
    }

    @Override
    public boolean isDisabled(Object loginId) {
        return SubjectKit.getDataProvider().isDisabled(loginId, "default");
    }

    @Override
    public void untieDisable(Object loginId) {
        // Shiro 解封委托业务层
    }

}
