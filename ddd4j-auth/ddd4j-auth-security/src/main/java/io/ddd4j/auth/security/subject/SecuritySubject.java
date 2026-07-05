package io.ddd4j.auth.security.subject;

import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.auth.AuthRequest;
import io.ddd4j.core.exception.AccountDisabledException;
import io.ddd4j.core.exception.AccountExpiredException;
import io.ddd4j.core.exception.BadCredentialsException;
import io.ddd4j.core.exception.CredentialsExpiredException;
import io.ddd4j.core.exception.NotLoggedInException;
import io.ddd4j.core.exception.SessionExpiredException;
import io.ddd4j.core.exception.UnknownAccountException;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.util.SubjectKit;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

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

    private <T extends AuthPrincipal> T castPrincipal(Object principal) {
        if (principal instanceof AuthPrincipal) {
            return (T) principal;
        }
        return null;
    }

    // ==================== 身份与会话读取 ====================

    @Override
    public <T extends AuthPrincipal> T getPrincipal() {
        Authentication auth = getAuthentication();
        if (Objects.isNull(auth)) {
            return null;
        }
        return castPrincipal(auth.getPrincipal());
    }

    @Override
    public <T extends AuthPrincipal> T getPrincipalByLoginId(Object loginId) {
        // Spring Security 不直接支持按 loginId 反查，返回当前 principal
        return getPrincipal();
    }

    @Override
    public <T extends AuthPrincipal> T getPrincipalByToken(String tokenValue) {
        // Spring Security JWT 场景需业务层扩展 JwtDecoder
        return getPrincipal();
    }

    // ==================== 会话生命周期（委托 SecurityContextHolder）====================

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
                credentials
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

    @Override
    public void logout() {
        SecurityContextHolder.clearContext();
    }

    @Override
    public void logout(Object loginId) {
        // Spring Security 无中心会话，JWT 场景需配合 Token 黑名单
        if (Objects.nonNull(loginId) && loginId.equals(getLoginId())) {
            logout();
        }
    }

    @Override
    public void kickout(Object loginId) {
        logout(loginId);
    }

    @Override
    public String refresh() {
        // Spring Security 无状态，JWT 场景由 JwtIssuer 重签
        Authentication auth = getAuthentication();
        return Objects.nonNull(auth) ? auth.getName() : null;
    }

    @Override
    public <T extends AuthPrincipal> T verify(String token) {
        // JWT 场景委托 JwtParser，此处返回当前 principal
        return getPrincipal();
    }

    // ==================== 权限与角色（委托 SubjectDataProvider 数据源 SPI）====================

    @Override
    public boolean isPermitted(String permission) {
        AuthPrincipal principal = getPrincipal();
        if (Objects.isNull(principal)) {
            return false;
        }
        List<String> perms = SubjectKit.getDataProvider().getPermissionList(principal);
        return SubjectKit.getStrategy().hasElement.apply(perms, permission);
    }

    @Override
    public boolean isPermitted(Object loginId, String permission) {
        AuthPrincipal principal = getPrincipalByLoginId(loginId);
        if (Objects.isNull(principal)) {
            return false;
        }
        List<String> perms = SubjectKit.getDataProvider().getPermissionList(principal);
        return SubjectKit.getStrategy().hasElement.apply(perms, permission);
    }

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

    @Override
    public boolean hasRole(String roleIdentifier) {
        AuthPrincipal principal = getPrincipal();
        if (Objects.isNull(principal)) {
            return false;
        }
        List<String> roles = SubjectKit.getDataProvider().getRoleList(principal);
        return SubjectKit.getStrategy().hasElement.apply(roles, roleIdentifier);
    }

    @Override
    public boolean hasRole(Object loginId, String roleIdentifier) {
        AuthPrincipal principal = getPrincipalByLoginId(loginId);
        if (Objects.isNull(principal)) {
            return false;
        }
        List<String> roles = SubjectKit.getDataProvider().getRoleList(principal);
        return SubjectKit.getStrategy().hasElement.apply(roles, roleIdentifier);
    }

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

    @Override
    public boolean isAuthenticated() {
        Authentication auth = getAuthentication();
        return Objects.nonNull(auth) && auth.isAuthenticated();
    }

    @Override
    public boolean isAuthenticated(Object loginId) {
        return isAuthenticated();
    }

    @Override
    public boolean isRemembered() {
        Authentication auth = getAuthentication();
        return Objects.nonNull(auth) && auth instanceof org.springframework.security.authentication.RememberMeAuthenticationToken;
    }

    @Override
    public boolean isTrustDeviceId(String deviceId) {
        // Spring Security 不内置设备信任机制，委托给业务层
        return false;
    }

    @Override
    public boolean isTrustDeviceId(Object userId, String deviceId) {
        return isTrustDeviceId(deviceId);
    }

    @Override
    public Object getLoginId() {
        Authentication auth = getAuthentication();
        if (Objects.isNull(auth)) {
            return null;
        }
        return auth.getName();
    }

    @Override
    public Object getUserId() {
        AuthPrincipal principal = getPrincipal();
        return Objects.nonNull(principal) ? principal.getUserId() : null;
    }

    @Override
    public Object getExtra(String tokenValue, String key) {
        AuthPrincipal principal = getPrincipalByToken(tokenValue);
        if (Objects.isNull(principal)) {
            return null;
        }
        return principal.getProfile().get(key);
    }

    // ==================== 封禁（委托业务层）====================

    @Override
    public void disable(Object loginId, long timeout) {
        // Spring Security 封禁通常通过 Token 黑名单或业务标记实现
    }

    @Override
    public boolean isDisabled(Object loginId) {
        return SubjectKit.getDataProvider().isDisabled(loginId, "default");
    }

    @Override
    public void untieDisable(Object loginId) {
        // Spring Security 解封委托业务层
    }

}
