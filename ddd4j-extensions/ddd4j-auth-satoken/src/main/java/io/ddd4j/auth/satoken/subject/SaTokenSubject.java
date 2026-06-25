package io.ddd4j.auth.satoken.subject;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.strategy.SaStrategy;
import io.ddd4j.auth.satoken.util.StpKit;
import io.ddd4j.core.subject.AuthPrincipal;
import io.ddd4j.core.subject.Subject;

import java.util.List;

public class SaTokenSubject implements Subject {

    @Override
    public <T extends AuthPrincipal> T getPrincipal() {
        return null;
    }

    @Override
    public <T extends AuthPrincipal> T getPrincipalByLoginId(Object loginId) {
        return null;
    }

    @Override
    public <T extends AuthPrincipal> T getPrincipalByToken(String tokenValue) {
        return null;
    }

    @Override
    public boolean isPermitted(String permission) {
        return StpUtil.hasPermission(permission);
    }

    @Override
    public boolean isPermitted(Object loginId, String permission) {
        return StpUtil.hasPermission(loginId, permission);
    }

    @Override
    public boolean[] isPermitted(String... permissions) {
        if (permissions == null || permissions.length == 0) {
            return new boolean[0];
        }
        boolean[] hasPermissions = new boolean[permissions.length];
        for (int i = 0; i < permissions.length; i++) {
            hasPermissions[i] = StpUtil.hasPermission(permissions[i]);
        }
        return hasPermissions;
    }

    @Override
    public boolean[] isPermitted(Object loginId, String... permissions) {
        if (permissions == null || permissions.length == 0) {
            return new boolean[0];
        }
        boolean[] hasPermissions = new boolean[permissions.length];
        for (int i = 0; i < permissions.length; i++) {
            hasPermissions[i] = StpUtil.hasPermission(loginId, permissions[i]);
        }
        return hasPermissions;
    }

    @Override
    public boolean isPermittedAny(String... permissions) {
        return StpUtil.hasPermissionOr(permissions);
    }

    @Override
    public boolean isPermittedAny(Object loginId, String... permissions) {
        // 如果没有指定权限，那么直接跳过
        if(permissions == null || permissions.length == 0) {
            return Boolean.FALSE;
        }
        // 开始校验
        List<String> permissionList = StpUtil.getPermissionList(loginId);
        for (String permission : permissions) {
            if(SaStrategy.instance.hasElement.apply(permissionList, permission)) {
                return Boolean.TRUE;
            }
        }
        return Boolean.FALSE;
    }

    @Override
    public boolean isPermittedAll(String... permissions) {
        return StpUtil.hasPermissionAnd(permissions);
    }

    @Override
    public boolean isPermittedAll(Object loginId, String... permissions) {
        // 如果没有指定权限，那么直接跳过
        if(permissions == null || permissions.length == 0) {
            return Boolean.FALSE;
        }
        // 开始校验
        List<String> permissionList = StpUtil.getPermissionList(loginId);
        for (String permission : permissions) {
            if(!SaStrategy.instance.hasElement.apply(permissionList, permission)) {
                return Boolean.FALSE;
            }
        }
        return Boolean.TRUE;
    }

    @Override
    public boolean hasRole(String roleIdentifier) {
        return StpUtil.hasRole(roleIdentifier);
    }

    @Override
    public boolean hasRole(Object loginId, String roleIdentifier) {
        return StpUtil.hasRole(loginId, roleIdentifier);
    }

    @Override
    public boolean[] hasRoles(String... roleIdentifiers) {
        if (roleIdentifiers == null || roleIdentifiers.length == 0) {
            return new boolean[0];
        }
        boolean[] hasRoles = new boolean[roleIdentifiers.length];
        for (int i = 0; i < roleIdentifiers.length; i++) {
            hasRoles[i] = StpUtil.hasRole(roleIdentifiers[i]);
        }
        return hasRoles;
    }

    @Override
    public boolean[] hasRoles(Object loginId, String... roleIdentifiers) {
        if (roleIdentifiers == null || roleIdentifiers.length == 0) {
            return new boolean[0];
        }
        boolean[] hasRoles = new boolean[roleIdentifiers.length];
        for (int i = 0; i < roleIdentifiers.length; i++) {
            hasRoles[i] = StpUtil.hasRole(loginId, roleIdentifiers[i]);
        }
        return hasRoles;
    }

    @Override
    public boolean hasAnyRole(String... roleIdentifiers) {
        return StpUtil.hasRoleOr(roleIdentifiers);
    }

    @Override
    public boolean hasAnyRole(Object loginId, String... roleIdentifiers) {
        // 如果没有指定权限，那么直接跳过
        if(roleIdentifiers == null || roleIdentifiers.length == 0) {
            return Boolean.FALSE;
        }
        // 开始校验
        List<String> roleList = StpUtil.getRoleList(loginId);
        for (String role : roleIdentifiers) {
            if(SaStrategy.instance.hasElement.apply(roleList, role)) {
                // 有的话提前退出
                return Boolean.TRUE;
            }
        }
        return Boolean.FALSE;
    }

    @Override
    public boolean hasAllRole(String... roleIdentifiers) {
        return StpUtil.hasRoleAnd(roleIdentifiers);
    }

    @Override
    public boolean hasAllRole(Object loginId, String... roleIdentifiers) {
        // 如果没有指定权限，那么直接跳过
        if(roleIdentifiers == null || roleIdentifiers.length == 0) {
            return Boolean.FALSE;
        }
        // 开始校验
        List<String> roleList = StpUtil.getRoleList(loginId);
        for (String role : roleIdentifiers) {
            if(!SaStrategy.instance.hasElement.apply(roleList, role)) {
                // 任意一个没有的话提前退出
                return Boolean.FALSE;
            }
        }
        return Boolean.TRUE;
    }

    @Override
    public boolean isAuthenticated() {
        return StpUtil.isLogin();
    }

    @Override
    public boolean isAuthenticated(Object loginId) {
        return StpUtil.isLogin(loginId);
    }

    @Override
    public boolean isRemembered() {
        return Boolean.FALSE;
    }

    @Override
    public boolean isTrustDeviceId(String deviceId) {
        return StpUtil.isTrustDeviceId(StpKit.getUserIdAsLong(), deviceId);
    }

    @Override
    public boolean isTrustDeviceId(Object userId, String deviceId) {
        return StpUtil.isTrustDeviceId(userId, deviceId);
    }

    /**
     * 复写默认实现，提高效率
     * @return 登录账号 Id
     */
    @Override
    public Object getLoginId() {
        return StpUtil.getLoginId();
    }

    /**
     * 复写默认实现，提高效率
     * @return 登录用户 Id
     */
    @Override
    public Object getUserId() {
        return StpKit.getUserId();
    }

    /**
     * 复写默认实现，提高效率
     * @return 所属组织 Id
     */
    @Override
    public Object getOrgId() {
        return StpKit.getOrgId();
    }

    /**
     * 复写默认实现，提高效率
     * @return 角色 Id
     */
    @Override
    public Object getRoleId() {
        return StpKit.getRoleId();
    }

    /**
     * 复写默认实现，提高效率
     * @param tokenValue 指定的 Token 值
     * @param key        键值
     * @return 对应的扩展数据
     */
    @Override
    public Object getExtra(String tokenValue, String key) {
        return StpUtil.getExtra(tokenValue, key);
    }

}
