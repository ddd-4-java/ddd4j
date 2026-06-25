package io.ddd4j.boot.cmpt.security.subject;

import io.ddd4j.boot.core.subject.AuthPrincipal;
import io.ddd4j.boot.core.subject.Subject;

public class SecuritySubject implements Subject {

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
        return false;
    }

    @Override
    public boolean isPermitted(Object loginId, String permission) {
        return false;
    }

    @Override
    public boolean[] isPermitted(String... permissions) {
        return new boolean[0];
    }

    @Override
    public boolean[] isPermitted(Object loginId, String... permissions) {
        return new boolean[0];
    }

    @Override
    public boolean isPermittedAny(String... permissions) {
        return false;
    }

    @Override
    public boolean isPermittedAny(Object loginId, String... permissions) {
        return false;
    }

    @Override
    public boolean isPermittedAll(String... permissions) {
        return false;
    }

    @Override
    public boolean isPermittedAll(Object loginId, String... permissions) {
        return false;
    }

    @Override
    public boolean hasRole(String roleIdentifier) {
        return false;
    }

    @Override
    public boolean hasRole(Object loginId, String roleIdentifier) {
        return false;
    }

    @Override
    public boolean[] hasRoles(String... roleIdentifiers) {
        return new boolean[0];
    }

    @Override
    public boolean[] hasRoles(Object loginId, String... roleIdentifiers) {
        return new boolean[0];
    }

    @Override
    public boolean hasAnyRole(String... roleIdentifiers) {
        return false;
    }

    @Override
    public boolean hasAnyRole(Object loginId, String... roleIdentifiers) {
        return false;
    }

    @Override
    public boolean hasAllRole(String... roleIdentifiers) {
        return false;
    }

    @Override
    public boolean hasAllRole(Object loginId, String... roleIdentifiers) {
        return false;
    }

    @Override
    public boolean isAuthenticated() {
        return false;
    }

    @Override
    public boolean isAuthenticated(Object loginId) {
        return false;
    }

    @Override
    public boolean isRemembered() {
        return false;
    }

    @Override
    public boolean isTrustDeviceId(String deviceId) {
        return false;
    }

    @Override
    public boolean isTrustDeviceId(Object userId, String deviceId) {
        return false;
    }

    /**
     * 复写默认实现，提高效率
     * @return 登录账号 Id
     */
    @Override
    public Object getLoginId() {
        return null;
    }

    /**
     * 复写默认实现，提高效率
     * @return 登录用户 Id
     */
    @Override
    public Object getUserId() {
        return null;
    }

    /**
     * 复写默认实现，提高效率
     * @return 所属组织 Id
     */
    @Override
    public Object getOrgId() {
        return null;
    }

    /**
     * 复写默认实现，提高效率
     * @return 角色 Id
     */
    @Override
    public Object getRoleId() {
        return null;
    }

    /**
     * 复写默认实现，提高效率
     * @param tokenValue 指定的 Token 值
     * @param key        键值
     * @return 对应的扩展数据
     */
    @Override
    public Object getExtra(String tokenValue, String key) {
        return null;
    }
}
