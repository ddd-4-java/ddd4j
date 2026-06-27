package io.ddd4j.core.api.subject;

import io.ddd4j.core.api.util.Functions;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * 认证授权主体接口（纯 Java，无框架依赖）
 *
 * @author Jensen
 * @公众号 架构师修行录
 */
public interface Subject {

    <T extends AuthPrincipal> T getPrincipal();
    <T extends AuthPrincipal> T getPrincipalByLoginId(Object loginId);
    <T extends AuthPrincipal> T getPrincipalByToken(String tokenValue);

    boolean isPermitted(String permission);
    boolean isPermitted(Object loginId, String permission);

    boolean[] isPermitted(String... permissions);
    boolean[] isPermitted(Object loginId, String... permissions);

    boolean isPermittedAny(String... permissions);
    boolean isPermittedAny(Object loginId, String... permissions);

    boolean isPermittedAll(String... permissions);
    boolean isPermittedAll(Object loginId, String... permissions);

    boolean hasRole(String roleIdentifier);
    boolean hasRole(Object loginId, String roleIdentifier);

    boolean[] hasRoles(String... roleIdentifiers);
    boolean[] hasRoles(Object loginId, String... roleIdentifiers);

    boolean hasAnyRole(String... roleIdentifiers);
    boolean hasAnyRole(Object loginId, String... roleIdentifiers);

    boolean hasAllRole(String... roleIdentifiers);
    boolean hasAllRole(Object loginId, String... roleIdentifiers);

    boolean isAuthenticated();
    boolean isAuthenticated(Object loginId);

    boolean isRemembered();

    boolean isTrustDeviceId(String deviceId);
    boolean isTrustDeviceId(Object userId, String deviceId);

    default String getUserType(){
        AuthPrincipal principal = this.getPrincipal();
        if (Objects.isNull(principal)) {
            return null;
        }
        return principal.getUserType();
    }

    default Object getLoginId(){
        AuthPrincipal principal = this.getPrincipal();
        if (Objects.isNull(principal)) {
            return null;
        }
        return principal.getLoginId();
    }

    default String getLoginIdAsString(){
        return Functions.TO_STRING.apply(getLoginId());
    }

    default Integer getLoginIdAsInteger(){
        return Functions.TO_INTEGER.apply(getLoginId());
    }

    default Long getLoginIdAsLong(){
        return Functions.TO_LONG.apply(getLoginId());
    }

    default Object getUserId(){
        AuthPrincipal principal = this.getPrincipal();
        if (Objects.isNull(principal)) {
            return null;
        }
        return principal.getUserId();
    }

    default String getUserIdAsString() {
        return Functions.TO_STRING.apply(getUserId());
    }

    default Integer getUserIdAsInteger() {
        return Functions.TO_INTEGER.apply(getUserId());
    }

    default Long getUserIdAsLong() {
        return Functions.TO_LONG.apply(getUserId());
    }

    default Object getOrgId() {
        AuthPrincipal principal = this.getPrincipal();
        if (Objects.isNull(principal)) {
            return null;
        }
        return principal.getOrgId();
    }

    default String getOrgIdAsString() {
        return Functions.TO_STRING.apply(getOrgId());
    }

    default Integer getOrgIdAsInteger() {
        return Functions.TO_INTEGER.apply(getOrgId());
    }

    default Long getOrgIdAsLong() {
        return Functions.TO_LONG.apply(getOrgId());
    }

    default Object getRoleId() {
        AuthPrincipal principal = this.getPrincipal();
        if (Objects.isNull(principal)) {
            return null;
        }
        return principal.getRoleId();
    }

    default String getRoleIdAsString() {
        return Functions.TO_STRING.apply(getRoleId());
    }

    default Integer getRoleIdAsInteger() {
        return Functions.TO_INTEGER.apply(getRoleId());
    }

    default Long getRoleIdAsLong() {
        return Functions.TO_LONG.apply(getRoleId());
    }

    default Object getExtra(String key) {
        Map<String, Object> profile = this.getPrincipal().getProfile();
        if (profile == null || profile.isEmpty()) {
            return null;
        }
        return profile.get(key);
    }

    default String getExtraAsString(String key) {
        return getExtraAs(key, Functions.TO_STRING);
    }

    default Integer getExtraAsInteger(String key) {
        return getExtraAs(key, Functions.TO_INTEGER);
    }

    default Long getExtraAsLong(String key) {
        return getExtraAs(key, Functions.TO_LONG);
    }

    default <T> T getExtraAs(String key, Function<Object, T> mapper) {
        Object obj = getExtra(key);
        if (Objects.nonNull(obj)) {
            return mapper.apply(obj);
        }
        return null;
    }

    default Object getExtra(String tokenValue, String key){
        Map<String, Object> profile = this.getPrincipalByToken(tokenValue).getProfile();
        if (profile == null || profile.isEmpty()) {
            return null;
        }
        return profile.get(key);
    }

    default String getExtraAsString(String tokenValue, String key) {
        return getExtraAs(tokenValue, key, Functions.TO_STRING);
    }

    default Integer getExtraAsInteger(String tokenValue, String key) {
        return getExtraAs(tokenValue, key, Functions.TO_INTEGER);
    }

    default Long getExtraAsLong(String tokenValue, String key) {
        return getExtraAs(tokenValue, key, Functions.TO_LONG);
    }

    default <T> T getExtraAs(String tokenValue, String key, Function<Object, T> mapper){
        Object obj = getExtra(tokenValue, key);
        if (Objects.nonNull(obj)) {
            return mapper.apply(obj);
        }
        return null;
    }

}
