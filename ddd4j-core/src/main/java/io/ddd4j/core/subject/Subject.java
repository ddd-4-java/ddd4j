package io.ddd4j.core.subject;

import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.util.Functions;
import io.ddd4j.core.util.JacksonKit;
import org.apache.commons.collections.MapUtils;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

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

    /**
     * Returns {@code true} if this Subject has the specified role, {@code false} otherwise.
     *
     * @param roleIdentifier the application-specific role identifier (usually a role id or role name).
     * @return {@code true} if this Subject has the specified role, {@code false} otherwise.
     */
    boolean hasRole(String roleIdentifier);
    boolean hasRole(Object loginId, String roleIdentifier);

    /**
     * Checks if this Subject has the specified roles, returning a boolean array indicating
     * which roles are associated.
     * <p/>
     * This is primarily a performance-enhancing method to help reduce the number of
     * {@link #hasRole} invocations over the wire in client/server systems.
     *
     * @param roleIdentifiers the application-specific role identifiers to check (usually role ids or role names).
     * @return a boolean array where indices correspond to the index of the
     * roles in the given identifiers.  A true value indicates this Subject has the
     * role at that index.  False indicates this Subject does not have the role at that index.
     */
    boolean[] hasRoles(String... roleIdentifiers);
    boolean[] hasRoles(Object loginId, String... roleIdentifiers);

    /**
     * Returns {@code true} if this Subject has any of the specified roles, {@code false} otherwise.
     *
     * @param roleIdentifiers the application-specific role identifiers to check (usually role ids or role names).
     * @return true if this Subject has any the roles, false otherwise.
     */
    boolean hasAnyRole(String... roleIdentifiers);
    boolean hasAnyRole(Object loginId, String... roleIdentifiers);

    /**
     * Returns {@code true} if this Subject has all of the specified roles, {@code false} otherwise.
     *
     * @param roleIdentifiers the application-specific role identifiers to check (usually role ids or role names).
     * @return true if this Subject has all the roles, false otherwise.
     */
    boolean hasAllRole(String... roleIdentifiers);
    boolean hasAllRole(Object loginId, String... roleIdentifiers);

    /**
     * Returns {@code true} if this Subject/user proved their identity <em>during their current session</em>
     * by providing valid credentials matching those known to the system, {@code false} otherwise.
     * <p/>
     * Note that even if this Subject's identity has been remembered via 'remember me' services, this method will
     * still return {@code false} unless the user has actually logged in with proper credentials <em>during their
     * current session</em>.  See the {@link #isRemembered() isRemembered()} method JavaDoc for more.
     *
     * @return {@code true} if this Subject proved their identity during their current session
     * by providing valid credentials matching those known to the system, {@code false} otherwise.
     * @since 0.9
     */
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

    /**
     * 获取当前会话账号id，默认从 principal 中获取，建议复写该方法，提供更高效
     * @return 账号id
     */
    default Object getLoginId(){
        AuthPrincipal principal = this.getPrincipal();
        if (Objects.isNull(principal)) {
            return null;
        }
        return principal.getLoginId();
    }

    /**
     * 获取当前会话账号id, 并转换为 String 类型
     * @return 账号id
     */
    default String getLoginIdAsString(){
        return Functions.TO_STRING.apply(getLoginId());
    }

    /**
     * 获取当前会话账号id, 并转换为 Integer 类型
     *
     * @return 账号id
     */
    default Integer getLoginIdAsInteger(){
        return Functions.TO_INTEGER.apply(getLoginId());
    }

    /**
     * 获取当前会话账号id, 并转换为 Long 类型
     * @return 账号id
     */
    default Long getLoginIdAsLong(){
        return Functions.TO_LONG.apply(getLoginId());
    }

    /**
     * 获取当前会话用户id，默认从 principal 中获取，建议复写该方法，提供更高效
     * @return 用户id
     */
    default Object getUserId(){
        AuthPrincipal principal = this.getPrincipal();
        if (Objects.isNull(principal)) {
            return null;
        }
        return principal.getUserId();
    }

    default String getUserIdAsString() {
        return Functions.TO_STRING.apply(getLoginId());
    }

    default Integer getUserIdAsInteger() {
        return Functions.TO_INTEGER.apply(getLoginId());
    }
    
    default Long getUserIdAsLong() {
        return Functions.TO_LONG.apply(getLoginId());
    }

    /**
     * 获取当前会话用户所属机构id，默认从 principal 中获取，建议复写该方法，提供更高效
     * @return 用户所属机构id
     */
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

    /**
     * 获取当前会话角色id，默认从 principal 中获取，建议复写该方法，提供更高效
     * @return 角色id
     */
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

    /**
     * 获取当前 Token 的扩展信息（此函数只在jwt模式下生效）
     *
     * @param key 键值
     * @return 对应的扩展数据
     */
    default Object getExtra(String key) {
        Map<String, Object> profile = this.getPrincipal().getProfile();
        if (MapUtils.isEmpty(profile)) {
            return null;
        }
        return MapUtils.getObject(profile, key);
    }

    /**
     * 获取当前 Token 的扩展信息, 并转换为 String 类型
     * @return 账号id
     */
    default String getExtraAsString(String key) {
        return getExtraAs(key, Functions.TO_STRING);
    }

    /**
     * 获取当前 Token 的扩展信息,并转换为 int 类型
     *
     * @return 账号id
     */
    default Integer getExtraAsInteger(String key) {
        return getExtraAs(key, Functions.TO_INTEGER);
    }

    /**
     * 获取当前 Token 的扩展信息, 并转换为 long 类型
     *
     * @return 账号id
     */
    default Long getExtraAsLong(String key) {
        return getExtraAs(key, Functions.TO_LONG);
    }

    /**
     * 获取当前 Token 的扩展信息, 并通过自定义函数转换为指定类型
     *
     * @param key    缓存key
     * @param mapper 对象转换函数
     * @param <T>    指定的类型
     * @return 转换函数转换后的对象
     */
    default <T> T getExtraAs(String key, Function<Object, T> mapper) {
        Object obj = getExtra(key);
        if (Objects.nonNull(obj)) {
            return mapper.apply(obj);
        }
        return null;
    }

    /**
     * 获取当前 Token 的扩展信息, 并通过 Jackson 转换为指定类型
     *
     * @param key       键值
     * @param valueType 转换类型
     * @return 对应的扩展数据
     */
    default <T> T getExtraAs(String key, Class<T> valueType) {
        Object value = this.getExtra(key);
        return JacksonKit.toType(value, valueType);
    }

    /**
     * 获取当前 Token 的扩展信息
     *
     * @param tokenValue 指定的 Token 值
     * @param key        键值
     * @return 对应的扩展数据
     */
    default Object getExtra(String tokenValue, String key){
        Map<String, Object> profile = this.getPrincipalByToken(tokenValue).getProfile();
        if (MapUtils.isEmpty(profile)) {
            return null;
        }
        return MapUtils.getObject(profile, key);
    }

    /**
     * 获取当前 Token 的扩展信息, 并转换为 String 类型
     * @param tokenValue 指定的 Token 值
     * @param key        键值
     * @return 账号id
     */
    default String getExtraAsString(String tokenValue, String key) {
        return getExtraAs(tokenValue, key, Functions.TO_STRING);
    }

    /**
     * 获取当前 Token 的扩展信息,并转换为 int 类型
     *
     * @param tokenValue 指定的 Token 值
     * @param key        键值
     * @return 账号id
     */
    default Integer getExtraAsInteger(String tokenValue, String key) {
        return getExtraAs(tokenValue, key, Functions.TO_INTEGER);
    }

    /**
     * 获取当前 Token 的扩展信息, 并转换为 long 类型
     *
     * @param tokenValue 指定的 Token 值
     * @param key        键值
     * @return 账号id
     */
    default Long getExtraAsLong(String tokenValue, String key) {
        return getExtraAs(tokenValue, key, Functions.TO_LONG);
    }

    /**
     * 获取当前 Token 的扩展信息, 并通过自定义函数转换为指定类型
     *
     * @param tokenValue 指定的 Token 值
     * @param key        键值
     * @param mapper 对象转换函数
     * @param <T>    指定的类型
     * @return 转换函数转换后的对象
     */
    default <T> T getExtraAs(String tokenValue, String key, Function<Object, T> mapper){
        Object obj = getExtra(tokenValue, key);
        if (Objects.nonNull(obj)) {
            return mapper.apply(obj);
        }
        return null;
    }

    /**
     * 获取当前 Token 的扩展信息, 并通过 Jackson 转换为指定类型
     *
     * @param tokenValue 指定的 Token 值
     * @param key        键值
     * @param valueType  转换类型
     * @return 对应的扩展数据
     */
    default <T> T getExtraAs(String tokenValue, String key, Class<T> valueType) {
        Object value = getExtra(tokenValue, key);
        return JacksonKit.toType(value, valueType);
    }

}
