package io.ddd4j.core.subject;

import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.auth.AuthRequest;
import io.ddd4j.core.util.SubjectKit;
import io.ddd4j.kit.lang.FunctionKit;
import io.ddd4j.kit.lang.JsonKit;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * 认证主体（Subject）—— 当前用户的安全操作抽象。
 *
 * <p>对齐 Shiro {@code Subject} 与 Sa-Token {@code StpLogic} 的能力边界，提供：
 * <ul>
 *   <li><b>身份获取</b>：{@link #getPrincipal()}、{@link #getLoginId()}、{@link #getUserId()} 等</li>
 *   <li><b>权限校验</b>：{@link #isPermitted(String)}、{@link #hasRole(String)} 等</li>
 *   <li><b>会话生命周期</b>：{@link #login(AuthRequest)}、{@link #logout()}、{@link #kickout(Object)}</li>
 *   <li><b>会话数据</b>：{@link #setAttribute(String, Object)}、{@link #getAttribute(String)}</li>
 *   <li><b>封禁管理</b>：{@link #disable(Object, long)}、{@link #isDisabled(Object)}</li>
 * </ul>
 *
 * <p>各鉴权框架（Sa-Token / Shiro / Spring Security）提供各自的实现，
 * 业务代码统一通过 {@link SubjectKit} 门面调用。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 获取当前登录用户 ID
 * Object loginId = SubjectKit.getLoginId();
 *
 * // 权限校验
 * if (!SubjectKit.isPermitted("order:create")) {
 *     throw new ServiceException("无权创建订单");
 * }
 *
 * // 角色校验
 * if (!SubjectKit.hasRole("admin")) {
 *     throw new ServiceException("需要管理员角色");
 * }
 *
 * // 会话属性
 * SubjectKit.setAttribute("lastAccessTime", Instant.now());
 * String clientIp = SubjectKit.getAttribute("clientIp");
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public interface Subject {

    // ==================== 身份获取 ====================

    /**
     * 获取当前会话的认证主体。
     *
     * @param <T> AuthPrincipal 子类型
     * @return 认证主体，未登录时返回 null
     */
    <T extends AuthPrincipal> T getPrincipal();

    /**
     * 按登录账号 ID 获取认证主体（跨会话查询）。
     *
     * @param loginId 登录账号 ID
     * @param <T>     AuthPrincipal 子类型
     * @return 认证主体，不存在时返回 null
     */
    <T extends AuthPrincipal> T getPrincipalByLoginId(Object loginId);

    /**
     * 按会话凭证获取认证主体。
     *
     * @param tokenValue 会话凭证（Token / SessionId）
     * @param <T>        AuthPrincipal 子类型
     * @return 认证主体，凭证无效时返回 null
     */
    <T extends AuthPrincipal> T getPrincipalByToken(String tokenValue);

    // ==================== 权限校验 ====================

    /**
     * 检查当前用户是否拥有指定权限。
     *
     * @param permission 权限标识（如 {@code "order:create"}）
     * @return 有权限时 {@code true}
     */
    boolean isPermitted(String permission);

    /**
     * 检查指定用户是否拥有指定权限。
     *
     * @param loginId    用户账号 ID
     * @param permission 权限标识
     * @return 有权限时 {@code true}
     */
    boolean isPermitted(Object loginId, String permission);

    /**
     * 批量检查当前用户的权限，返回与入参顺序一致的结果数组。
     *
     * @param permissions 权限标识列表
     * @return 权限匹配结果数组，索引与入参一一对应
     */
    boolean[] isPermitted(String... permissions);

    /**
     * 批量检查指定用户的权限。
     *
     * @param loginId     用户账号 ID
     * @param permissions 权限标识列表
     * @return 权限匹配结果数组
     */
    boolean[] isPermitted(Object loginId, String... permissions);

    /**
     * 检查当前用户是否拥有给定权限中的任意一个。
     *
     * @param permissions 权限标识列表
     * @return 拥有任一权限时 {@code true}
     */
    boolean isPermittedAny(String... permissions);

    /**
     * 检查指定用户是否拥有给定权限中的任意一个。
     *
     * @param loginId     用户账号 ID
     * @param permissions 权限标识列表
     * @return 拥有任一权限时 {@code true}
     */
    boolean isPermittedAny(Object loginId, String... permissions);

    /**
     * 检查当前用户是否拥有全部给定权限。
     *
     * @param permissions 权限标识列表
     * @return 全部拥有时 {@code true}
     */
    boolean isPermittedAll(String... permissions);

    /**
     * 检查指定用户是否拥有全部给定权限。
     *
     * @param loginId     用户账号 ID
     * @param permissions 权限标识列表
     * @return 全部拥有时 {@code true}
     */
    boolean isPermittedAll(Object loginId, String... permissions);

    // ==================== 角色校验 ====================

    /**
     * 检查当前用户是否拥有指定角色。
     *
     * @param roleIdentifier 角色标识（角色 ID 或角色名）
     * @return 有角色时 {@code true}
     */
    boolean hasRole(String roleIdentifier);

    /**
     * 检查指定用户是否拥有指定角色。
     *
     * @param loginId        用户账号 ID
     * @param roleIdentifier 角色标识
     * @return 有角色时 {@code true}
     */
    boolean hasRole(Object loginId, String roleIdentifier);

    /**
     * 批量检查当前用户的角色，返回与入参顺序一致的结果数组。
     *
     * @param roleIdentifiers 角色标识列表
     * @return 角色匹配结果数组
     */
    boolean[] hasRoles(String... roleIdentifiers);

    /**
     * 批量检查指定用户的角色。
     *
     * @param loginId         用户账号 ID
     * @param roleIdentifiers 角色标识列表
     * @return 角色匹配结果数组
     */
    boolean[] hasRoles(Object loginId, String... roleIdentifiers);

    /**
     * 检查当前用户是否拥有给定角色中的任意一个。
     *
     * @param roleIdentifiers 角色标识列表
     * @return 拥有任一角色时 {@code true}
     */
    boolean hasAnyRole(String... roleIdentifiers);

    /**
     * 检查指定用户是否拥有给定角色中的任意一个。
     *
     * @param loginId         用户账号 ID
     * @param roleIdentifiers 角色标识列表
     * @return 拥有任一角色时 {@code true}
     */
    boolean hasAnyRole(Object loginId, String... roleIdentifiers);

    /**
     * 检查当前用户是否拥有全部给定角色。
     *
     * @param roleIdentifiers 角色标识列表
     * @return 全部拥有时 {@code true}
     */
    boolean hasAllRole(String... roleIdentifiers);

    /**
     * 检查指定用户是否拥有全部给定角色。
     *
     * @param loginId         用户账号 ID
     * @param roleIdentifiers 角色标识列表
     * @return 全部拥有时 {@code true}
     */
    boolean hasAllRole(Object loginId, String... roleIdentifiers);

    // ==================== 认证状态 ====================

    /**
     * 当前用户是否已通过身份认证（本次会话中提交了有效凭证）。
     *
     * <p>注意：即使通过 remember-me 记住了身份，
     * 只要在本次会话中没有重新登录，此方法仍返回 {@code false}。
     *
     * @return 已认证时 {@code true}
     */
    boolean isAuthenticated();

    /**
     * 指定用户是否已通过身份认证。
     *
     * @param loginId 用户账号 ID
     * @return 已认证时 {@code true}
     */
    boolean isAuthenticated(Object loginId);

    /**
     * 当前用户是否通过 remember-me 记住身份（非本次会话登录）。
     *
     * @return 被记住时 {@code true}
     */
    boolean isRemembered();

    /**
     * 检查设备是否为当前用户的受信任设备。
     *
     * @param deviceId 设备标识
     * @return 受信任时 {@code true}
     */
    boolean isTrustDeviceId(String deviceId);

    /**
     * 检查设备是否为指定用户的受信任设备。
     *
     * @param userId   用户 ID
     * @param deviceId 设备标识
     * @return 受信任时 {@code true}
     */
    boolean isTrustDeviceId(Object userId, String deviceId);

    // ==================== 用户类型 ====================

    /**
     * 获取当前会话的用户类型（如 admin / tenant / member）。
     *
     * @return 用户类型，未登录时返回 null
     */
    default String getUserType() {
        AuthPrincipal principal = this.getPrincipal();
        if (Objects.isNull(principal)) {
            return null;
        }
        return principal.getUserType();
    }

    // ==================== 账号 ID（loginId） ====================

    /**
     * 获取当前会话的登录账号 ID。
     *
     * @return 账号 ID，未登录时返回 null
     */
    default Object getLoginId() {
        AuthPrincipal principal = this.getPrincipal();
        if (Objects.isNull(principal)) {
            return null;
        }
        return principal.getLoginId();
    }

    /**
     * 获取当前会话的登录账号 ID 并转为 String。
     *
     * @return 账号 ID 字符串
     */
    default String getLoginIdAsString() {
        return FunctionKit.TO_STRING.apply(getLoginId());
    }

    /**
     * 获取当前会话的登录账号 ID 并转为 Integer。
     *
     * @return 账号 ID
     */
    default Integer getLoginIdAsInteger() {
        return FunctionKit.TO_INTEGER.apply(getLoginId());
    }

    /**
     * 获取当前会话的登录账号 ID 并转为 Long。
     *
     * @return 账号 ID
     */
    default Long getLoginIdAsLong() {
        return FunctionKit.TO_LONG.apply(getLoginId());
    }

    // ==================== 用户 ID（userId） ====================

    /**
     * 获取当前会话的用户 ID（区别于 loginId：一个账号可关联多个用户）。
     *
     * @return 用户 ID，未登录时返回 null
     */
    default Object getUserId() {
        AuthPrincipal principal = this.getPrincipal();
        if (Objects.isNull(principal)) {
            return null;
        }
        return principal.getUserId();
    }

    /**
     * 获取当前会话的用户 ID 并转为 String。
     *
     * @return 用户 ID 字符串
     */
    default String getUserIdAsString() {
        return FunctionKit.TO_STRING.apply(getUserId());
    }

    /**
     * 获取当前会话的用户 ID 并转为 Integer。
     *
     * @return 用户 ID
     */
    default Integer getUserIdAsInteger() {
        return FunctionKit.TO_INTEGER.apply(getUserId());
    }

    /**
     * 获取当前会话的用户 ID 并转为 Long。
     *
     * @return 用户 ID
     */
    default Long getUserIdAsLong() {
        return FunctionKit.TO_LONG.apply(getUserId());
    }

    // ==================== 机构 ID（orgId） ====================

    /**
     * 获取当前会话用户所属机构 ID。
     *
     * @return 机构 ID，未登录时返回 null
     */
    default Object getOrgId() {
        AuthPrincipal principal = this.getPrincipal();
        if (Objects.isNull(principal)) {
            return null;
        }
        return principal.getOrgId();
    }

    /**
     * 获取当前会话用户所属机构 ID 并转为 String。
     *
     * @return 机构 ID 字符串
     */
    default String getOrgIdAsString() {
        return FunctionKit.TO_STRING.apply(getOrgId());
    }

    /**
     * 获取当前会话用户所属机构 ID 并转为 Integer。
     *
     * @return 机构 ID
     */
    default Integer getOrgIdAsInteger() {
        return FunctionKit.TO_INTEGER.apply(getOrgId());
    }

    /**
     * 获取当前会话用户所属机构 ID 并转为 Long。
     *
     * @return 机构 ID
     */
    default Long getOrgIdAsLong() {
        return FunctionKit.TO_LONG.apply(getOrgId());
    }

    // ==================== 角色 ID（roleId） ====================

    /**
     * 获取当前会话的角色 ID。
     *
     * @return 角色 ID，未登录时返回 null
     */
    default Object getRoleId() {
        AuthPrincipal principal = this.getPrincipal();
        if (Objects.isNull(principal)) {
            return null;
        }
        return principal.getRoleId();
    }

    /**
     * 获取当前会话的角色 ID 并转为 String。
     *
     * @return 角色 ID 字符串
     */
    default String getRoleIdAsString() {
        return FunctionKit.TO_STRING.apply(getRoleId());
    }

    /**
     * 获取当前会话的角色 ID 并转为 Integer。
     *
     * @return 角色 ID
     */
    default Integer getRoleIdAsInteger() {
        return FunctionKit.TO_INTEGER.apply(getRoleId());
    }

    /**
     * 获取当前会话的角色 ID 并转为 Long。
     *
     * @return 角色 ID
     */
    default Long getRoleIdAsLong() {
        return FunctionKit.TO_LONG.apply(getRoleId());
    }

    // ==================== 扩展属性（当前会话） ====================

    /**
     * 从当前会话的认证主体中获取扩展属性。
     *
     * @param key 扩展属性键
     * @return 属性值，不存在时返回 null
     */
    default Object getExtra(String key) {
        AuthPrincipal principal = this.getPrincipal();
        if (Objects.isNull(principal)) {
            return null;
        }
        Map<String, Object> profile = principal.getProfile();
        if (Objects.isNull(profile) || profile.isEmpty()) {
            return null;
        }
        return profile.get(key);
    }

    /**
     * 从当前会话获取扩展属性并转为 String。
     *
     * @param key 扩展属性键
     * @return 属性值字符串
     */
    default String getExtraAsString(String key) {
        return getExtraAs(key, FunctionKit.TO_STRING);
    }

    /**
     * 从当前会话获取扩展属性并转为 Integer。
     *
     * @param key 扩展属性键
     * @return 属性值
     */
    default Integer getExtraAsInteger(String key) {
        return getExtraAs(key, FunctionKit.TO_INTEGER);
    }

    /**
     * 从当前会话获取扩展属性并转为 Long。
     *
     * @param key 扩展属性键
     * @return 属性值
     */
    default Long getExtraAsLong(String key) {
        return getExtraAs(key, FunctionKit.TO_LONG);
    }

    /**
     * 从当前会话获取扩展属性，通过自定义函数转换类型。
     *
     * @param key    扩展属性键
     * @param mapper 类型转换函数
     * @param <T>    目标类型
     * @return 转换后的值
     */
    default <T> T getExtraAs(String key, Function<Object, T> mapper) {
        Object obj = getExtra(key);
        if (Objects.nonNull(obj)) {
            return mapper.apply(obj);
        }
        return null;
    }

    /**
     * 从当前会话获取扩展属性，通过 Jackson 转换为指定类型。
     *
     * @param key       扩展属性键
     * @param valueType 目标类型
     * @param <T>       目标类型
     * @return 转换后的值
     */
    default <T> T getExtraAs(String key, Class<T> valueType) {
        Object value = this.getExtra(key);
        return JsonKit.toType(value, valueType);
    }

    // ==================== 扩展属性（指定 Token） ====================

    /**
     * 从指定 Token 的会话中获取扩展属性。
     *
     * @param tokenValue 会话凭证
     * @param key        扩展属性键
     * @return 属性值
     */
    default Object getExtra(String tokenValue, String key) {
        AuthPrincipal principal = this.getPrincipalByToken(tokenValue);
        if (Objects.isNull(principal)) {
            return null;
        }
        Map<String, Object> profile = principal.getProfile();
        if (Objects.isNull(profile) || profile.isEmpty()) {
            return null;
        }
        return profile.get(key);
    }

    /**
     * 从指定 Token 的会话获取扩展属性并转为 String。
     *
     * @param tokenValue 会话凭证
     * @param key        扩展属性键
     * @return 属性值字符串
     */
    default String getExtraAsString(String tokenValue, String key) {
        return getExtraAs(tokenValue, key, FunctionKit.TO_STRING);
    }

    /**
     * 从指定 Token 的会话获取扩展属性并转为 Integer。
     *
     * @param tokenValue 会话凭证
     * @param key        扩展属性键
     * @return 属性值
     */
    default Integer getExtraAsInteger(String tokenValue, String key) {
        return getExtraAs(tokenValue, key, FunctionKit.TO_INTEGER);
    }

    /**
     * 从指定 Token 的会话获取扩展属性并转为 Long。
     *
     * @param tokenValue 会话凭证
     * @param key        扩展属性键
     * @return 属性值
     */
    default Long getExtraAsLong(String tokenValue, String key) {
        return getExtraAs(tokenValue, key, FunctionKit.TO_LONG);
    }

    /**
     * 从指定 Token 的会话获取扩展属性，通过自定义函数转换类型。
     *
     * @param tokenValue 会话凭证
     * @param key        扩展属性键
     * @param mapper     类型转换函数
     * @param <T>        目标类型
     * @return 转换后的值
     */
    default <T> T getExtraAs(String tokenValue, String key, Function<Object, T> mapper) {
        Object obj = getExtra(tokenValue, key);
        if (Objects.nonNull(obj)) {
            return mapper.apply(obj);
        }
        return null;
    }

    /**
     * 从指定 Token 的会话获取扩展属性，通过 Jackson 转换为指定类型。
     *
     * @param tokenValue 会话凭证
     * @param key        扩展属性键
     * @param valueType  目标类型
     * @param <T>        目标类型
     * @return 转换后的值
     */
    default <T> T getExtraAs(String tokenValue, String key, Class<T> valueType) {
        Object value = getExtra(tokenValue, key);
        return JsonKit.toType(value, valueType);
    }

    // ==================== 会话生命周期 ====================

    /**
     * 登录（建立会话）。
     *
     * @param request 登录请求（loginId + 密码 + 扩展信息 + 有效期）
     * @return 会话凭证（Token / SessionId，无状态模式返回 null）
     */
    String login(AuthRequest request);

    /**
     * 登出当前会话（销毁当前用户的 Token）。
     */
    void logout();

    /**
     * 按账号 ID 登出（可跨设备注销该账号的所有会话）。
     *
     * @param loginId 账号 ID
     */
    void logout(Object loginId);

    /**
     * 踢人下线（区别于 {@link #logout(Object)}：被踢方会收到踢出通知事件）。
     *
     * @param loginId 账号 ID
     */
    void kickout(Object loginId);

    /**
     * 刷新会话凭证（续期或换发新 Token）。
     *
     * @return 新的会话凭证
     */
    String refresh();

    /**
     * 校验凭证有效性（仅校验，不建立会话）。
     *
     * @param token 会话凭证
     * @param <T>   AuthPrincipal 子类型
     * @return 凭证对应的认证主体，校验失败返回 null
     */
    <T extends AuthPrincipal> T verify(String token);

    // ==================== 会话数据操作 ====================

    /**
     * 设置会话属性（存储在当前认证主体的 profile 中）。
     *
     * @param key   属性键
     * @param value 属性值
     */
    default void setAttribute(String key, Object value) {
        AuthPrincipal principal = getPrincipal();
        if (Objects.nonNull(principal)) {
            principal.getProfile().put(key, value);
        }
    }

    /**
     * 获取会话属性。
     *
     * @param key 属性键
     * @param <V> 属性值类型
     * @return 属性值，不存在时返回 null
     */
    default <V> V getAttribute(String key) {
        AuthPrincipal principal = getPrincipal();
        if (Objects.isNull(principal)) {
            return null;
        }
        return (V) principal.getProfile().get(key);
    }

    // ==================== 封禁管理 ====================

    /**
     * 封禁账号，被封禁的账号在封禁期内无法登录。
     *
     * @param loginId 账号 ID
     * @param timeout 封禁时长（秒），{@code -1} 代表永久封禁
     */
    void disable(Object loginId, long timeout);

    /**
     * 判断账号是否处于封禁状态。
     *
     * @param loginId 账号 ID
     * @return 封禁中时 {@code true}
     */
    boolean isDisabled(Object loginId);

    /**
     * 解除账号封禁。
     *
     * @param loginId 账号 ID
     */
    void untieDisable(Object loginId);

}
