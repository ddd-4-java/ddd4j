package io.ddd4j.auth.security.subject;

import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.auth.AuthRequest;
import io.ddd4j.core.exception.*;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.util.SubjectKit;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 基于 Spring Security 的 {@link Subject} 实现。
 *
 * <p>将 ddd4j 的 {@link Subject} 契约委托给 Spring Security 的
 * {@link SecurityContextHolder}。权限/角色校验统一委托 {@link SubjectKit#getDataProvider()} 数据源 SPI，
 * 保证三鉴权实现行为一致。
 *
 * <p>本模块<b>允许</b> Spring 依赖（Spring Security 本就是 Spring 生态组件）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@SuppressWarnings("unchecked")
public class SecuritySubject implements Subject {

    /**
     * 登录时存入 Authentication details 的 principal 键
     */
    public static final String PRINCIPAL_KEY = "principal";

    /**
     * 获取当前 Authentication。
     *
     * @return Authentication（未登录时返回 null）
     */
    private Authentication getAuthentication() {
        try {
            return SecurityContextHolder.getContext().getAuthentication();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * \u628a\u539f\u751f\u4e3b\u4f53\u8f6c\u6362\u4e3a DDD4J \u8ba4\u8bc1\u4e3b\u4f53\u3002
     * @param <T> \u8c03\u7528\u65b9\u671f\u671b\u7684\u8ba4\u8bc1\u4e3b\u4f53\u5b50\u7c7b\u578b
     * @param principal \u6846\u67b6\u63d0\u4f9b\u7684\u4e3b\u4f53\u5bf9\u8c61
     * @return \u8ba4\u8bc1\u4e3b\u4f53\uff0c\u5176\u4ed6\u5bf9\u8c61\u6216\u7a7a\u503c\u8fd4\u56de\u7a7a
     */
    private <T extends AuthPrincipal> T castPrincipal(Object principal) {
        if (principal instanceof AuthPrincipal) {
            return (T) principal;
        }
        return null;
    }

    // ==================== 身份与会话读取 ====================

    /**
     * 读取当前会话的认证主体。
     *
     * <p>此实现调用 <code>getAuthentication()</code>。</p>
     *
     * @param <T> AuthPrincipal 子类型
     * @return 当前会话绑定的主体
     */
    @Override
    public <T extends AuthPrincipal> T getPrincipal() {
        Authentication auth = getAuthentication();
        if (Objects.isNull(auth) || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated()) {
            return null;
        }
        T principal = castPrincipal(auth.getPrincipal());
        if (Objects.nonNull(principal)) {
            return principal;
        }
        if (auth.getPrincipal() instanceof AuthUserDetails userDetails) {
            return (T) userDetails.getAuthPrincipal();
        }
        List<AuthPrincipal.RolePair> roles = new ArrayList<>();
        auth.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .forEach(role -> roles.add(new AuthPrincipal.RolePair().setRoleCode(role)));
        AuthPrincipal adapted = new AuthPrincipal()
                .setLoginId(auth.getName())
                .setUserId(auth.getName())
                .setUserCode(auth.getName())
                .setRoleCode(roles.isEmpty() ? null : roles.get(0).getRoleCode())
                .setRoles(roles);
        return (T) adapted;
    }

    /**
     * 查询指定登录标识关联的认证主体。
     *
     * <p>此实现调用 <code>getPrincipal()</code>。</p>
     *
     * @param <T> AuthPrincipal 子类型
     * @param loginId 登录账号 ID
     * @return 给定登录标识关联的主体
     */
    @Override
    public <T extends AuthPrincipal> T getPrincipalByLoginId(Object loginId) {
        // Spring Security 不直接支持按 loginId 反查，返回当前 principal
        return getPrincipal();
    }

    /**
     * 查询指定令牌关联的认证主体。
     *
     * <p>此实现调用 <code>getPrincipal()</code>。</p>
     *
     * @param <T> AuthPrincipal 子类型
     * @param tokenValue 会话凭证（Token / SessionId）
     * @return 给定令牌关联的主体
     */
    @Override
    public <T extends AuthPrincipal> T getPrincipalByToken(String tokenValue) {
        // Spring Security JWT 场景需业务层扩展 JwtDecoder
        return getPrincipal();
    }

    // ==================== 会话生命周期（委托 SecurityContextHolder）====================

    /**
     * 以传入的凭据启动认证会话。
     *
     * <p>此实现调用 <code>request.getPrincipal()</code>。</p>
     *
     * @param request 登录请求（loginId + 密码 + 扩展信息 + 有效期）
     * @return 实际返回<code>request.getLoginIdAsString()</code>
     */
    @Override
    public String login(AuthRequest request) {
        // 构建 Spring Security Authentication
        // 注意：本类为 stub 实现——业务方通常通过 Spring Security AuthenticationManager 完成实际认证。
        // 这里仅设置 SecurityContext，不触发 AuthenticationProvider；
        // 真实场景应替换为 AuthenticationManager.authenticate(...)。
        // 我们把通用异常映射加在这里是为了与 Shiro/SaToken 行为对齐（三鉴权统一抛 ddd4j AuthException）。
        Object credentials = Objects.nonNull(request.getPrincipal()) ? request.getPrincipal() : "";
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                Objects.nonNull(request.getPrincipal()) ? request.getPrincipal() : request.getLoginId(),
                credentials,
                List.of()
        );
        auth.setDetails(request.getExtra());
        try {
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (UsernameNotFoundException e) {
            // Spring Security 账号不存在 → ddd4j 通用 UnknownAccount
            throw new UnknownAccountException(e.getMessage(), e);
        } catch (LockedException e) {
            throw new io.ddd4j.core.exception.AccountLockedException(e.getMessage(), e);
        } catch (DisabledException e) {
            throw new AccountDisabledException(e.getMessage(), e);
        } catch (AccountExpiredException e) {
            throw new AccountExpiredException(e.getMessage(), e);
        } catch (CredentialsExpiredException e) {
            throw new CredentialsExpiredException(e.getMessage(), e);
        } catch (BadCredentialsException e) {
            throw new BadCredentialsException(e.getMessage(), e);
        } catch (AuthenticationException e) {
            // Spring Security 其他认证异常：未登录 → ddd4j 通用 NotLoggedIn
            throw new NotLoggedInException(e.getMessage(), e);
        }
        // Spring Security 无状态，返回 loginId 作为凭证标识
        return request.getLoginIdAsString();
    }

    /**
     * 结束由当前调用指定的认证会话。
     *
     * <p>此实现调用 <code>SecurityContextHolder.clearContext()</code>。</p>
     */
    @Override
    public void logout() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 结束由当前调用指定的认证会话。
     *
     * <p>此实现调用 <code>loginId.equals(getLoginId())</code>。</p>
     *
     * @param loginId 账号 ID
     */
    @Override
    public void logout(Object loginId) {
        // Spring Security 无中心会话，JWT 场景需配合 Token 黑名单
        if (Objects.nonNull(loginId) && loginId.equals(getLoginId())) {
            logout();
        }
    }

    /**
     * 强制结束指定身份关联的登录会话。
     *
     * <p>此实现调用 <code>logout(loginId)</code>。</p>
     *
     * @param loginId 账号 ID
     */
    @Override
    public void kickout(Object loginId) {
        logout(loginId);
    }

    /**
     * 刷新当前实现所持有的数据或状态。
     *
     * <p>此实现调用 <code>getAuthentication()</code>。</p>
     *
     * @return 实际返回<code>Objects.nonNull(auth) ? auth.getName() : null</code>
     */
    @Override
    public String refresh() {
        // Spring Security 无状态，JWT 场景由 JwtIssuer 重签
        Authentication auth = getAuthentication();
        return Objects.nonNull(auth) ? auth.getName() : null;
    }

    /**
     * 校验当前调用传入的数据或凭据。
     *
     * <p>此实现调用 <code>getPrincipal()</code>。</p>
     *
     * @param <T> AuthPrincipal 子类型
     * @param token 会话凭证
     * @return 实际返回<code>getPrincipal()</code>
     */
    @Override
    public <T extends AuthPrincipal> T verify(String token) {
        // JWT 场景委托 JwtParser，此处返回当前 principal
        return getPrincipal();
    }

    // ==================== 权限与角色（委托 SubjectDataProvider 数据源 SPI）====================

    /**
     * 检查身份是否拥有指定权限。
     *
     * <p>此实现调用 <code>getPrincipal()</code>。</p>
     *
     * @param permission 权限标识（如 {@code "order:create"}）
     * @return 请求的权限被授予时为真
     */
    @Override
    public boolean isPermitted(String permission) {
        AuthPrincipal principal = getPrincipal();
        if (Objects.isNull(principal)) {
            return false;
        }
        List<String> perms = SubjectKit.getDataProvider().getPermissionList(principal);
        return SubjectKit.getStrategy().hasElement.apply(perms, permission);
    }

    /**
     * 检查身份是否拥有指定权限。
     *
     * <p>此实现调用 <code>getPrincipalByLoginId(loginId)</code>。</p>
     *
     * @param loginId 用户账号 ID
     * @param permission 权限标识
     * @return 请求的权限被授予时为真
     */
    @Override
    public boolean isPermitted(Object loginId, String permission) {
        AuthPrincipal principal = getPrincipalByLoginId(loginId);
        if (Objects.isNull(principal)) {
            return false;
        }
        List<String> perms = SubjectKit.getDataProvider().getPermissionList(principal);
        return SubjectKit.getStrategy().hasElement.apply(perms, permission);
    }

    /**
     * 检查身份是否拥有指定权限。
     *
     * <p>此实现调用 <code>getPrincipal()</code>。</p>
     *
     * @param permissions 权限标识列表
     * @return 请求的权限被授予时为真
     */
    @Override
    public boolean[] isPermitted(String... permissions) {
        AuthPrincipal principal = getPrincipal();
        if (Objects.isNull(principal) || Objects.isNull(permissions) || permissions.length == 0) {
            return new boolean[0];
        }
        List<String> perms = SubjectKit.getDataProvider().getPermissionList(principal);
        boolean[] result = new boolean[permissions.length];
        for (int i = 0; i < permissions.length; i++) {
            result[i] = SubjectKit.getStrategy().hasElement.apply(perms, permissions[i]);
        }
        return result;
    }

    /**
     * 检查身份是否拥有指定权限。
     *
     * <p>此实现调用 <code>getPrincipalByLoginId(loginId)</code>。</p>
     *
     * @param loginId 用户账号 ID
     * @param permissions 权限标识列表
     * @return 请求的权限被授予时为真
     */
    @Override
    public boolean[] isPermitted(Object loginId, String... permissions) {
        AuthPrincipal principal = getPrincipalByLoginId(loginId);
        if (Objects.isNull(principal) || Objects.isNull(permissions) || permissions.length == 0) {
            return new boolean[0];
        }
        List<String> perms = SubjectKit.getDataProvider().getPermissionList(principal);
        boolean[] result = new boolean[permissions.length];
        for (int i = 0; i < permissions.length; i++) {
            result[i] = SubjectKit.getStrategy().hasElement.apply(perms, permissions[i]);
        }
        return result;
    }

    /**
     * 检查身份是否具有给定权限中的任意一项。
     *
     * <p>此实现调用 <code>getPrincipal()</code>。</p>
     *
     * @param permissions 权限标识列表
     * @return 至少有一项请求权限被授予时为真
     */
    @Override
    public boolean isPermittedAny(String... permissions) {
        AuthPrincipal principal = getPrincipal();
        if (Objects.isNull(principal) || Objects.isNull(permissions) || permissions.length == 0) {
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

    /**
     * 检查身份是否具有给定权限中的任意一项。
     *
     * <p>此实现调用 <code>getPrincipalByLoginId(loginId)</code>。</p>
     *
     * @param loginId 用户账号 ID
     * @param permissions 权限标识列表
     * @return 至少有一项请求权限被授予时为真
     */
    @Override
    public boolean isPermittedAny(Object loginId, String... permissions) {
        AuthPrincipal principal = getPrincipalByLoginId(loginId);
        if (Objects.isNull(principal) || Objects.isNull(permissions) || permissions.length == 0) {
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

    /**
     * 检查身份是否具有全部给定权限。
     *
     * <p>此实现调用 <code>getPrincipal()</code>。</p>
     *
     * @param permissions 权限标识列表
     * @return 所有请求权限都被授予时为真
     */
    @Override
    public boolean isPermittedAll(String... permissions) {
        AuthPrincipal principal = getPrincipal();
        if (Objects.isNull(principal) || Objects.isNull(permissions) || permissions.length == 0) {
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

    /**
     * 检查身份是否具有全部给定权限。
     *
     * <p>此实现调用 <code>getPrincipalByLoginId(loginId)</code>。</p>
     *
     * @param loginId 用户账号 ID
     * @param permissions 权限标识列表
     * @return 所有请求权限都被授予时为真
     */
    @Override
    public boolean isPermittedAll(Object loginId, String... permissions) {
        AuthPrincipal principal = getPrincipalByLoginId(loginId);
        if (Objects.isNull(principal) || Objects.isNull(permissions) || permissions.length == 0) {
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

    /**
     * 检查身份是否具有指定角色。
     *
     * <p>此实现调用 <code>getPrincipal()</code>。</p>
     *
     * @param roleIdentifier 角色标识（角色 ID 或角色名）
     * @return 具有指定角色时为真
     */
    @Override
    public boolean hasRole(String roleIdentifier) {
        AuthPrincipal principal = getPrincipal();
        if (Objects.isNull(principal)) {
            return false;
        }
        List<String> roles = SubjectKit.getDataProvider().getRoleList(principal);
        return SubjectKit.getStrategy().hasElement.apply(roles, roleIdentifier);
    }

    /**
     * 检查身份是否具有指定角色。
     *
     * <p>此实现调用 <code>getPrincipalByLoginId(loginId)</code>。</p>
     *
     * @param loginId 用户账号 ID
     * @param roleIdentifier 角色标识
     * @return 具有指定角色时为真
     */
    @Override
    public boolean hasRole(Object loginId, String roleIdentifier) {
        AuthPrincipal principal = getPrincipalByLoginId(loginId);
        if (Objects.isNull(principal)) {
            return false;
        }
        List<String> roles = SubjectKit.getDataProvider().getRoleList(principal);
        return SubjectKit.getStrategy().hasElement.apply(roles, roleIdentifier);
    }

    /**
     * 按输入顺序逐项判断身份的角色归属。
     *
     * <p>此实现调用 <code>getPrincipal()</code>。</p>
     *
     * @param roleIdentifiers 角色标识列表
     * @return 实际返回<code>result</code>
     */
    @Override
    public boolean[] hasRoles(String... roleIdentifiers) {
        AuthPrincipal principal = getPrincipal();
        if (Objects.isNull(principal) || Objects.isNull(roleIdentifiers) || roleIdentifiers.length == 0) {
            return new boolean[0];
        }
        List<String> roles = SubjectKit.getDataProvider().getRoleList(principal);
        boolean[] result = new boolean[roleIdentifiers.length];
        for (int i = 0; i < roleIdentifiers.length; i++) {
            result[i] = SubjectKit.getStrategy().hasElement.apply(roles, roleIdentifiers[i]);
        }
        return result;
    }

    /**
     * 按输入顺序逐项判断身份的角色归属。
     *
     * <p>此实现调用 <code>getPrincipalByLoginId(loginId)</code>。</p>
     *
     * @param loginId 用户账号 ID
     * @param roleIdentifiers 角色标识列表
     * @return 实际返回<code>result</code>
     */
    @Override
    public boolean[] hasRoles(Object loginId, String... roleIdentifiers) {
        AuthPrincipal principal = getPrincipalByLoginId(loginId);
        if (Objects.isNull(principal) || Objects.isNull(roleIdentifiers) || roleIdentifiers.length == 0) {
            return new boolean[0];
        }
        List<String> roles = SubjectKit.getDataProvider().getRoleList(principal);
        boolean[] result = new boolean[roleIdentifiers.length];
        for (int i = 0; i < roleIdentifiers.length; i++) {
            result[i] = SubjectKit.getStrategy().hasElement.apply(roles, roleIdentifiers[i]);
        }
        return result;
    }

    /**
     * 检查身份是否具有给定角色中的任意一个。
     *
     * <p>此实现调用 <code>getPrincipal()</code>。</p>
     *
     * @param roleIdentifiers 角色标识列表
     * @return 至少具有一个指定角色时为真
     */
    @Override
    public boolean hasAnyRole(String... roleIdentifiers) {
        AuthPrincipal principal = getPrincipal();
        if (Objects.isNull(principal) || Objects.isNull(roleIdentifiers) || roleIdentifiers.length == 0) {
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

    /**
     * 检查身份是否具有给定角色中的任意一个。
     *
     * <p>此实现调用 <code>getPrincipalByLoginId(loginId)</code>。</p>
     *
     * @param loginId 用户账号 ID
     * @param roleIdentifiers 角色标识列表
     * @return 至少具有一个指定角色时为真
     */
    @Override
    public boolean hasAnyRole(Object loginId, String... roleIdentifiers) {
        AuthPrincipal principal = getPrincipalByLoginId(loginId);
        if (Objects.isNull(principal) || Objects.isNull(roleIdentifiers) || roleIdentifiers.length == 0) {
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

    /**
     * 检查身份是否具有全部给定角色。
     *
     * <p>此实现调用 <code>getPrincipal()</code>。</p>
     *
     * @param roleIdentifiers 角色标识列表
     * @return 具有全部指定角色时为真
     */
    @Override
    public boolean hasAllRole(String... roleIdentifiers) {
        AuthPrincipal principal = getPrincipal();
        if (Objects.isNull(principal) || Objects.isNull(roleIdentifiers) || roleIdentifiers.length == 0) {
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

    /**
     * 检查身份是否具有全部给定角色。
     *
     * <p>此实现调用 <code>getPrincipalByLoginId(loginId)</code>。</p>
     *
     * @param loginId 用户账号 ID
     * @param roleIdentifiers 角色标识列表
     * @return 具有全部指定角色时为真
     */
    @Override
    public boolean hasAllRole(Object loginId, String... roleIdentifiers) {
        AuthPrincipal principal = getPrincipalByLoginId(loginId);
        if (Objects.isNull(principal) || Objects.isNull(roleIdentifiers) || roleIdentifiers.length == 0) {
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

    // ==================== 状态判断 ====================

    /**
     * 检查当前会话是否已经认证。
     *
     * <p>此实现调用 <code>getAuthentication()</code>。</p>
     *
     * @return 当前会话通过身份认证时为真
     */
    @Override
    public boolean isAuthenticated() {
        Authentication auth = getAuthentication();
        return Objects.nonNull(auth)
                && !(auth instanceof AnonymousAuthenticationToken)
                && auth.isAuthenticated();
    }

    /**
     * 检查当前会话是否已经认证。
     *
     * <p>此实现调用 <code>isAuthenticated()</code>。</p>
     *
     * @param loginId 用户账号 ID
     * @return 当前会话通过身份认证时为真
     */
    @Override
    public boolean isAuthenticated(Object loginId) {
        return isAuthenticated();
    }

    /**
     * 判断当前身份是否由记住登录状态恢复。
     *
     * <p>此实现调用 <code>getAuthentication()</code>。</p>
     *
     * @return 被记住时 {@code true}
     */
    @Override
    public boolean isRemembered() {
        Authentication auth = getAuthentication();
        return Objects.nonNull(auth) && auth instanceof org.springframework.security.authentication.RememberMeAuthenticationToken;
    }

    /**
     * 检查当前认证实现是否将给定设备识别为受信设备。
     *
     * @param deviceId 设备标识
     * @return 此实现返回<code>false</code>
     */
    @Override
    public boolean isTrustDeviceId(String deviceId) {
        // Spring Security 不内置设备信任机制，委托给业务层
        return false;
    }

    /**
     * 检查当前认证实现是否将给定设备识别为受信设备。
     *
     * <p>此实现调用 <code>isTrustDeviceId(deviceId)</code>。</p>
     *
     * @param userId 用户 ID
     * @param deviceId 设备标识
     * @return 实际返回<code>isTrustDeviceId(deviceId)</code>
     */
    @Override
    public boolean isTrustDeviceId(Object userId, String deviceId) {
        return isTrustDeviceId(deviceId);
    }

    /**
     * 返回当前会话关联的登录标识。
     *
     * <p>此实现调用 <code>getAuthentication()</code>。</p>
     *
     * @return 根据执行路径返回<code>null</code>、<code>auth.getName()</code>
     */
    @Override
    public Object getLoginId() {
        Authentication auth = getAuthentication();
        if (Objects.isNull(auth) || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return auth.getName();
    }

    /**
     * 读取认证主体的用户标识。
     *
     * <p>此实现调用 <code>getPrincipal()</code>。</p>
     *
     * @return 实际返回<code>Objects.nonNull(principal) ? principal.getUserId() : null</code>
     */
    @Override
    public Object getUserId() {
        AuthPrincipal principal = getPrincipal();
        return Objects.nonNull(principal) ? principal.getUserId() : null;
    }

    /**
     * 从指定 Token 的会话中获取扩展属性。
     *
     * <p>此实现调用 <code>getPrincipalByToken(tokenValue)</code>。</p>
     *
     * @param tokenValue 会话凭证
     * @param key 扩展属性键
     * @return 根据执行路径返回<code>null</code>、<code>principal.getProfile().get(key)</code>
     */
    @Override
    public Object getExtra(String tokenValue, String key) {
        AuthPrincipal principal = getPrincipalByToken(tokenValue);
        if (Objects.isNull(principal)) {
            return null;
        }
        return principal.getProfile().get(key);
    }

    // ==================== 封禁（委托业务层）====================

    /**
     * 接收<code>disable</code>回调，当前实现无需在此步骤更新状态。
     *
     * @param loginId 账号 ID
     * @param timeout 封禁时长（秒），{@code -1} 代表永久封禁
     */
    @Override
    public void disable(Object loginId, long timeout) {
        // Spring Security 封禁通常通过 Token 黑名单或业务标记实现
    }

    /**
     * 判断当前限制是否禁止指定身份执行相应操作。
     *
     * <p>此实现调用 <code>SubjectKit.getDataProvider().isDisabled(loginId, "default")</code>。</p>
     *
     * @param loginId 账号 ID
     * @return 实际返回<code>SubjectKit.getDataProvider().isDisabled(loginId, "default")</code>
     */
    @Override
    public boolean isDisabled(Object loginId) {
        return SubjectKit.getDataProvider().isDisabled(loginId, "default");
    }

    /**
     * 接收<code>untieDisable</code>回调，当前实现无需在此步骤更新状态。
     *
     * @param loginId 账号 ID
     */
    @Override
    public void untieDisable(Object loginId) {
        // Spring Security 解封委托业务层
    }

}
