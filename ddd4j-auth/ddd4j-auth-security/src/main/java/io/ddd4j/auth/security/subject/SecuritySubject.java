/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import java.util.Arrays;
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

    /** {@inheritDoc} */
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
        if (auth.getPrincipal() instanceof AuthUserDetails) {
            AuthUserDetails userDetails = (AuthUserDetails) auth.getPrincipal();
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

    /** {@inheritDoc} */
    @Override
    public <T extends AuthPrincipal> T getPrincipalByLoginId(Object loginId) {
        // Spring Security 不直接支持按 loginId 反查，返回当前 principal
        return getPrincipal();
    }

    /** {@inheritDoc} */
    @Override
    public <T extends AuthPrincipal> T getPrincipalByToken(String tokenValue) {
        // Spring Security JWT 场景需业务层扩展 JwtDecoder
        return getPrincipal();
    }

    // ==================== 会话生命周期（委托 SecurityContextHolder）====================

    /** {@inheritDoc} */
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
                Arrays.asList()
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

    /** {@inheritDoc} */
    @Override
    public void logout() {
        SecurityContextHolder.clearContext();
    }

    /** {@inheritDoc} */
    @Override
    public void logout(Object loginId) {
        // Spring Security 无中心会话，JWT 场景需配合 Token 黑名单
        if (Objects.nonNull(loginId) && loginId.equals(getLoginId())) {
            logout();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void kickout(Object loginId) {
        logout(loginId);
    }

    /** {@inheritDoc} */
    @Override
    public String refresh() {
        // Spring Security 无状态，JWT 场景由 JwtIssuer 重签
        Authentication auth = getAuthentication();
        return Objects.nonNull(auth) ? auth.getName() : null;
    }

    /** {@inheritDoc} */
    @Override
    public <T extends AuthPrincipal> T verify(String token) {
        // JWT 场景委托 JwtParser，此处返回当前 principal
        return getPrincipal();
    }

    // ==================== 权限与角色（委托 SubjectDataProvider 数据源 SPI）====================

    /** {@inheritDoc} */
    @Override
    public boolean isPermitted(String permission) {
        AuthPrincipal principal = getPrincipal();
        if (Objects.isNull(principal)) {
            return false;
        }
        List<String> perms = SubjectKit.getDataProvider().getPermissionList(principal);
        return SubjectKit.getStrategy().hasElement.apply(perms, permission);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isPermitted(Object loginId, String permission) {
        AuthPrincipal principal = getPrincipalByLoginId(loginId);
        if (Objects.isNull(principal)) {
            return false;
        }
        List<String> perms = SubjectKit.getDataProvider().getPermissionList(principal);
        return SubjectKit.getStrategy().hasElement.apply(perms, permission);
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public boolean hasRole(String roleIdentifier) {
        AuthPrincipal principal = getPrincipal();
        if (Objects.isNull(principal)) {
            return false;
        }
        List<String> roles = SubjectKit.getDataProvider().getRoleList(principal);
        return SubjectKit.getStrategy().hasElement.apply(roles, roleIdentifier);
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasRole(Object loginId, String roleIdentifier) {
        AuthPrincipal principal = getPrincipalByLoginId(loginId);
        if (Objects.isNull(principal)) {
            return false;
        }
        List<String> roles = SubjectKit.getDataProvider().getRoleList(principal);
        return SubjectKit.getStrategy().hasElement.apply(roles, roleIdentifier);
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public boolean isAuthenticated() {
        Authentication auth = getAuthentication();
        return Objects.nonNull(auth)
                && !(auth instanceof AnonymousAuthenticationToken)
                && auth.isAuthenticated();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isAuthenticated(Object loginId) {
        return isAuthenticated();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isRemembered() {
        Authentication auth = getAuthentication();
        return Objects.nonNull(auth) && auth instanceof org.springframework.security.authentication.RememberMeAuthenticationToken;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isTrustDeviceId(String deviceId) {
        // Spring Security 不内置设备信任机制，委托给业务层
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isTrustDeviceId(Object userId, String deviceId) {
        return isTrustDeviceId(deviceId);
    }

    /** {@inheritDoc} */
    @Override
    public Object getLoginId() {
        Authentication auth = getAuthentication();
        if (Objects.isNull(auth) || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return auth.getName();
    }

    /** {@inheritDoc} */
    @Override
    public Object getUserId() {
        AuthPrincipal principal = getPrincipal();
        return Objects.nonNull(principal) ? principal.getUserId() : null;
    }

    /** {@inheritDoc} */
    @Override
    public Object getExtra(String tokenValue, String key) {
        AuthPrincipal principal = getPrincipalByToken(tokenValue);
        if (Objects.isNull(principal)) {
            return null;
        }
        return principal.getProfile().get(key);
    }

    // ==================== 封禁（委托业务层）====================

    /** {@inheritDoc} */
    @Override
    public void disable(Object loginId, long timeout) {
        // Spring Security 封禁通常通过 Token 黑名单或业务标记实现
    }

    /** {@inheritDoc} */
    @Override
    public boolean isDisabled(Object loginId) {
        return SubjectKit.getDataProvider().isDisabled(loginId, "default");
    }

    /** {@inheritDoc} */
    @Override
    public void untieDisable(Object loginId) {
        // Spring Security 解封委托业务层
    }

}
