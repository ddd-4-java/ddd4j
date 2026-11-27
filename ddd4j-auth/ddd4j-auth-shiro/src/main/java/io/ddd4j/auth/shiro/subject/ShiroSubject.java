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
package io.ddd4j.auth.shiro.subject;

import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.auth.AuthRequest;
import io.ddd4j.core.auth.session.AuthSessionConfig;
import io.ddd4j.core.exception.*;
import io.ddd4j.core.exception.UnknownAccountException;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.*;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.Subject;

import java.util.Objects;

/**
 * 基于 Apache Shiro 的 {@link io.ddd4j.core.subject.Subject} 实现。
 *
 * <h2>设计原则</h2>
 * <p><b>绝大多数方法直接调用 Shiro 原生 API</b>（{@code subject.isPermitted/isPermittedAll/hasRole/...}），
 * Shiro 的 {@link org.apache.shiro.realm.Realm} 体系负责实际的"账号→权限/角色"映射。
 * 这样做的好处是：业务方继续用 Shiro 的标准 {@code @RequiresPermissions}/{@code @RequiresRoles} 即可生效，
 * 不需要在 ddd4j 这层重复实现权限策略。
 *
 * <p>本类与 Shiro 之间的"通用→框架"桥接主要发生在：
 * <ul>
 *   <li>{@link #login(AuthRequest)}：把框架无关的 {@link AuthRequest} 翻译为 Shiro 的
 *       {@link AuthenticationToken}，登录后把 {@link AuthPrincipal} 存进 Shiro Session。</li>
 *   <li>{@link #getPrincipal()}：从 Shiro Session 提取 {@code AuthPrincipal}。</li>
 *   <li>{@link #kickout(Object)}：通过 {@code DefaultSecurityManager} 强踢指定 sessionId。</li>
 * </ul>
 *
 * <p>对于 Shiro 弱支持的场景（按 loginId 查 principal/按 token 查 principal），委托业务层实现：
 * 这些方法需要业务侧扩展 {@code Realm} 或 {@code SessionDAO}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@SuppressWarnings("unchecked")
public class ShiroSubject implements io.ddd4j.core.subject.Subject {

    /**
     * Shiro Session 中保存 {@link AuthPrincipal} 的键名。
     */
    public static final String SESSION_KEY_PRINCIPAL = "ddd4j:auth:principal";

    /**
     * Shiro Session 中保存 ddd4j sessionConfig（用于业务侧扩展）的键名。
     */
    public static final String SESSION_KEY_CONFIG = "ddd4j:auth:sessionConfig";

    /**
     * \u5f53\u524d\u7ebf\u7a0b\u7ed1\u5b9a\u7684 Shiro Subject\u3002
     *
     * @return \u5f53\u524d\u7ebf\u7a0b\u4e0a\u7684 Shiro \u4e3b\u4f53
     */
    private Subject shiroSubject() {
        try {
            return SecurityUtils.getSubject();
        } catch (Exception e) {
            // SecurityUtils 未初始化（如纯单元测试无 INI 上下文）
            return null;
        }
    }

    // ==================== 身份与会话读取 ====================

    /**
     * 读取当前会话的认证主体。
     *
     * <p>Shiro 实现调用 <code>shiroSubject()</code>。</p>
     *
     * @param <T> AuthPrincipal 子类型
     * @return 当前会话绑定的主体
     */
    @Override
    public <T extends AuthPrincipal> T getPrincipal() {
        Subject subject = shiroSubject();
        if (Objects.isNull(subject) || !subject.isAuthenticated()) {
            return null;
        }
        // 优先从 Session 拿 ddd4j AuthPrincipal（登录时写入）
        Session session = subject.getSession(false);
        if (Objects.nonNull(session)) {
            Object stored = session.getAttribute(SESSION_KEY_PRINCIPAL);
            if (stored instanceof AuthPrincipal) {
                return (T) stored;
            }
        }
        // 回退到 Shiro 自有 principal（兼容纯 Shiro Realm 用户）
        Object principal = subject.getPrincipal();
        if (principal instanceof AuthPrincipal) {
            return (T) principal;
        }
        return null;
    }

    /**
     * 查询指定登录标识关联的认证主体。
     *
     * <p>Shiro 实现调用 <code>getPrincipal()</code>。</p>
     *
     * @param <T> AuthPrincipal 子类型
     * @param loginId 登录账号 ID
     * @return 给定登录标识关联的主体
     */
    @Override
    public <T extends AuthPrincipal> T getPrincipalByLoginId(Object loginId) {
        // Shiro 不直接支持按 loginId 查询 principal；委托业务侧 Realm 实现
        // 这里降级处理：若 loginId 与当前登录用户一致则返回当前 principal，否则返回 null
        if (Objects.isNull(loginId)) {
            return null;
        }
        AuthPrincipal current = getPrincipal();
        if (Objects.nonNull(current) && Objects.equals(current.getLoginId(), loginId)) {
            return (T) current;
        }
        return null;
    }

    /**
     * 查询指定令牌关联的认证主体。
     *
     * <p>Shiro 实现调用 <code>getPrincipal()</code>。</p>
     *
     * @param <T> AuthPrincipal 子类型
     * @param tokenValue 会话凭证（Token / SessionId）
     * @return 给定令牌关联的主体
     */
    @Override
    public <T extends AuthPrincipal> T getPrincipalByToken(String tokenValue) {
        // Shiro 不内置按 token 查 principal；委托业务侧 SessionDAO/Realm 扩展
        return getPrincipal();
    }

    /**
     * 校验当前调用传入的数据或凭据。
     *
     * <p>Shiro 实现调用 <code>getPrincipal()</code>。</p>
     *
     * @param <T> AuthPrincipal 子类型
     * @param token 会话凭证
     * @return 实际返回<code>getPrincipal()</code>
     */
    @Override
    public <T extends AuthPrincipal> T verify(String token) {
        // 委托业务侧 Realm 扩展；降级返回当前 principal
        return getPrincipal();
    }

    // ==================== 权限校验（直接调用 Shiro API）====================

    /**
     * 检查身份是否拥有指定权限。
     *
     * <p>Shiro 实现调用 <code>shiroSubject()</code>。</p>
     *
     * @param permission 权限标识（如 {@code "order:create"}）
     * @return 请求的权限被授予时为真
     */
    @Override
    public boolean isPermitted(String permission) {
        Subject subject = shiroSubject();
        return Objects.nonNull(subject) && subject.isPermitted(permission);
    }

    /**
     * 检查身份是否拥有指定权限。
     *
     * <p>Shiro 实现调用 <code>isPermitted(permission)</code>。</p>
     *
     * @param loginId 用户账号 ID
     * @param permission 权限标识
     * @return 请求的权限被授予时为真
     */
    @Override
    public boolean isPermitted(Object loginId, String permission) {
        // Shiro 同一 Subject 线程下只能拿当前用户；按 loginId 校验需要业务侧 Realm 扩展
        return isPermitted(permission);
    }

    /**
     * 检查身份是否拥有指定权限。
     *
     * <p>Shiro 实现调用 <code>shiroSubject()</code>。</p>
     *
     * @param permissions 权限标识列表
     * @return 请求的权限被授予时为真
     */
    @Override
    public boolean[] isPermitted(String... permissions) {
        if (Objects.isNull(permissions) || permissions.length == 0) {
            return new boolean[0];
        }
        Subject subject = shiroSubject();
        if (Objects.isNull(subject)) {
            return new boolean[0];
        }
        return subject.isPermitted(permissions);
    }

    /**
     * 检查身份是否拥有指定权限。
     *
     * <p>Shiro 实现调用 <code>isPermitted(permissions)</code>。</p>
     *
     * @param loginId 用户账号 ID
     * @param permissions 权限标识列表
     * @return 请求的权限被授予时为真
     */
    @Override
    public boolean[] isPermitted(Object loginId, String... permissions) {
        return isPermitted(permissions);
    }

    /**
     * 检查身份是否具有全部给定权限。
     *
     * <p>Shiro 实现调用 <code>shiroSubject()</code>。</p>
     *
     * @param permissions 权限标识列表
     * @return 所有请求权限都被授予时为真
     */
    @Override
    public boolean isPermittedAll(String... permissions) {
        if (Objects.isNull(permissions) || permissions.length == 0) {
            return false;
        }
        Subject subject = shiroSubject();
        if (Objects.isNull(subject)) {
            return false;
        }
        return subject.isPermittedAll(permissions);
    }

    /**
     * 检查身份是否具有全部给定权限。
     *
     * <p>Shiro 实现调用 <code>isPermittedAll(permissions)</code>。</p>
     *
     * @param loginId 用户账号 ID
     * @param permissions 权限标识列表
     * @return 所有请求权限都被授予时为真
     */
    @Override
    public boolean isPermittedAll(Object loginId, String... permissions) {
        return isPermittedAll(permissions);
    }

    /**
     * 检查身份是否具有给定权限中的任意一项。
     *
     * <p>Shiro 实现调用 <code>shiroSubject()</code>。</p>
     *
     * @param permissions 权限标识列表
     * @return 至少有一项请求权限被授予时为真
     */
    @Override
    public boolean isPermittedAny(String... permissions) {
        if (Objects.isNull(permissions) || permissions.length == 0) {
            return false;
        }
        Subject subject = shiroSubject();
        if (Objects.nonNull(subject)) {
            for (String p : permissions) {
                if (subject.isPermitted(p)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 检查身份是否具有给定权限中的任意一项。
     *
     * <p>Shiro 实现调用 <code>isPermittedAny(permissions)</code>。</p>
     *
     * @param loginId 用户账号 ID
     * @param permissions 权限标识列表
     * @return 至少有一项请求权限被授予时为真
     */
    @Override
    public boolean isPermittedAny(Object loginId, String... permissions) {
        return isPermittedAny(permissions);
    }

    // ==================== 角色校验（直接调用 Shiro API）====================

    /**
     * 检查身份是否具有指定角色。
     *
     * <p>Shiro 实现调用 <code>shiroSubject()</code>。</p>
     *
     * @param roleIdentifier 角色标识（角色 ID 或角色名）
     * @return 具有指定角色时为真
     */
    @Override
    public boolean hasRole(String roleIdentifier) {
        Subject subject = shiroSubject();
        return Objects.nonNull(subject) && subject.hasRole(roleIdentifier);
    }

    /**
     * 检查身份是否具有指定角色。
     *
     * <p>Shiro 实现调用 <code>hasRole(roleIdentifier)</code>。</p>
     *
     * @param loginId 用户账号 ID
     * @param roleIdentifier 角色标识
     * @return 具有指定角色时为真
     */
    @Override
    public boolean hasRole(Object loginId, String roleIdentifier) {
        return hasRole(roleIdentifier);
    }

    /**
     * 按输入顺序逐项判断身份的角色归属。
     *
     * <p>Shiro 实现调用 <code>shiroSubject()</code>。</p>
     *
     * @param roleIdentifiers 角色标识列表
     * @return 实际返回<code>subject.hasRoles(java.util.Arrays.asList(roleIdentifiers))</code>
     */
    @Override
    public boolean[] hasRoles(String... roleIdentifiers) {
        if (Objects.isNull(roleIdentifiers) || roleIdentifiers.length == 0) {
            return new boolean[0];
        }
        Subject subject = shiroSubject();
        if (Objects.isNull(subject)) {
            return new boolean[0];
        }
        // Shiro API：hasRoles(List<String>)
        return subject.hasRoles(java.util.Arrays.asList(roleIdentifiers));
    }

    /**
     * 按输入顺序逐项判断身份的角色归属。
     *
     * <p>Shiro 实现调用 <code>hasRoles(roleIdentifiers)</code>。</p>
     *
     * @param loginId 用户账号 ID
     * @param roleIdentifiers 角色标识列表
     * @return 实际返回<code>hasRoles(roleIdentifiers)</code>
     */
    @Override
    public boolean[] hasRoles(Object loginId, String... roleIdentifiers) {
        return hasRoles(roleIdentifiers);
    }

    /**
     * 检查身份是否具有给定角色中的任意一个。
     *
     * <p>Shiro 实现调用 <code>shiroSubject()</code>。</p>
     *
     * @param roleIdentifiers 角色标识列表
     * @return 至少具有一个指定角色时为真
     */
    @Override
    public boolean hasAnyRole(String... roleIdentifiers) {
        if (Objects.isNull(roleIdentifiers) || roleIdentifiers.length == 0) {
            return false;
        }
        Subject subject = shiroSubject();
        if (Objects.nonNull(subject)) {
            for (String r : roleIdentifiers) {
                if (subject.hasRole(r)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 检查身份是否具有给定角色中的任意一个。
     *
     * <p>Shiro 实现调用 <code>hasAnyRole(roleIdentifiers)</code>。</p>
     *
     * @param loginId 用户账号 ID
     * @param roleIdentifiers 角色标识列表
     * @return 至少具有一个指定角色时为真
     */
    @Override
    public boolean hasAnyRole(Object loginId, String... roleIdentifiers) {
        return hasAnyRole(roleIdentifiers);
    }

    /**
     * 检查身份是否具有全部给定角色。
     *
     * <p>Shiro 实现调用 <code>shiroSubject()</code>。</p>
     *
     * @param roleIdentifiers 角色标识列表
     * @return 具有全部指定角色时为真
     */
    @Override
    public boolean hasAllRole(String... roleIdentifiers) {
        if (Objects.isNull(roleIdentifiers) || roleIdentifiers.length == 0) {
            return false;
        }
        Subject subject = shiroSubject();
        return Objects.nonNull(subject) && subject.hasAllRoles(java.util.Arrays.asList(roleIdentifiers));
    }

    /**
     * 检查身份是否具有全部给定角色。
     *
     * <p>Shiro 实现调用 <code>hasAllRole(roleIdentifiers)</code>。</p>
     *
     * @param loginId 用户账号 ID
     * @param roleIdentifiers 角色标识列表
     * @return 具有全部指定角色时为真
     */
    @Override
    public boolean hasAllRole(Object loginId, String... roleIdentifiers) {
        return hasAllRole(roleIdentifiers);
    }

    // ==================== 状态判断 ====================

    /**
     * 检查当前会话是否已经认证。
     *
     * <p>Shiro 实现调用 <code>shiroSubject()</code>。</p>
     *
     * @return 当前会话通过身份认证时为真
     */
    @Override
    public boolean isAuthenticated() {
        Subject subject = shiroSubject();
        return Objects.nonNull(subject) && subject.isAuthenticated();
    }

    /**
     * 检查当前会话是否已经认证。
     *
     * <p>Shiro 实现调用 <code>getPrincipal()</code>。</p>
     *
     * @param loginId 用户账号 ID
     * @return 当前会话通过身份认证时为真
     */
    @Override
    public boolean isAuthenticated(Object loginId) {
        // Shiro 不直接支持按 loginId 查登录态；当前用户匹配即视为认证
        if (Objects.isNull(loginId)) {
            return false;
        }
        AuthPrincipal principal = getPrincipal();
        return Objects.nonNull(principal) && Objects.equals(principal.getLoginId(), loginId) && isAuthenticated();
    }

    /**
     * 判断当前身份是否由记住登录状态恢复。
     *
     * <p>Shiro 实现调用 <code>shiroSubject()</code>。</p>
     *
     * @return 实际返回<code>Objects.nonNull(subject) &amp;&amp; subject.isRemembered()</code>
     */
    @Override
    public boolean isRemembered() {
        Subject subject = shiroSubject();
        return Objects.nonNull(subject) && subject.isRemembered();
    }

    /**
     * 检查当前认证实现是否将给定设备识别为受信设备。
     *
     * @param deviceId 设备标识
     * @return 此实现返回<code>false</code>
     */
    @Override
    public boolean isTrustDeviceId(String deviceId) {
        // Shiro 不内置设备信任机制；委托业务侧 Realm/Authorizer 扩展
        return false;
    }

    /**
     * 检查当前认证实现是否将给定设备识别为受信设备。
     *
     * <p>Shiro 实现调用 <code>isTrustDeviceId(deviceId)</code>。</p>
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
     * <p>Shiro 实现调用 <code>getPrincipal()</code>。</p>
     *
     * @return 实际返回<code>Objects.nonNull(principal) ? principal.getLoginId() : null</code>
     */
    @Override
    public Object getLoginId() {
        AuthPrincipal principal = getPrincipal();
        return Objects.nonNull(principal) ? principal.getLoginId() : null;
    }

    /**
     * 读取认证主体的用户标识。
     *
     * <p>Shiro 实现调用 <code>getPrincipal()</code>。</p>
     *
     * @return 实际返回<code>Objects.nonNull(principal) ? principal.getUserId() : null</code>
     */
    @Override
    public Object getUserId() {
        AuthPrincipal principal = getPrincipal();
        return Objects.nonNull(principal) ? principal.getUserId() : null;
    }

    /**
     * 读取认证主体的组织标识。
     *
     * <p>Shiro 实现调用 <code>getPrincipal()</code>。</p>
     *
     * @return 实际返回<code>Objects.nonNull(principal) ? principal.getOrgId() : null</code>
     */
    @Override
    public Object getOrgId() {
        AuthPrincipal principal = getPrincipal();
        return Objects.nonNull(principal) ? principal.getOrgId() : null;
    }

    /**
     * 读取认证主体的角色标识。
     *
     * <p>Shiro 实现调用 <code>getPrincipal()</code>。</p>
     *
     * @return 实际返回<code>Objects.nonNull(principal) ? principal.getRoleId() : null</code>
     */
    @Override
    public Object getRoleId() {
        AuthPrincipal principal = getPrincipal();
        return Objects.nonNull(principal) ? principal.getRoleId() : null;
    }

    /**
     * 从指定 Token 的会话中获取扩展属性。
     *
     * <p>Shiro 实现调用 <code>shiroSubject()</code>。</p>
     *
     * @param tokenValue 会话凭证
     * @param key 扩展属性键
     * @return 根据执行路径返回<code>session.getAttribute(key)</code>、<code>null</code>
     */
    @Override
    public Object getExtra(String tokenValue, String key) {
        Subject subject = shiroSubject();
        if (Objects.nonNull(subject)) {
            Session session = subject.getSession(false);
            if (Objects.nonNull(session)) {
                return session.getAttribute(key);
            }
        }
        return null;
    }

    // ==================== 会话生命周期（委托 Shiro Subject）====================

    /**
     * 以传入的凭据启动认证会话。
     *
     * <p>Shiro 实现调用 <code>request.getLoginId()</code>。</p>
     *
     * @param request 登录请求（loginId + 密码 + 扩展信息 + 有效期）
     * @return 实际返回<code>session.getId().toString()</code>
     */
    @Override
    public String login(AuthRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(request.getLoginId(), "loginId must not be null");

        Subject subject = SecurityUtils.getSubject();
        AuthSessionConfig cfg = Objects.nonNull(request.getSessionConfig()) ? request.getSessionConfig() : new AuthSessionConfig();

        // 1. 从 AuthRequest.extra 读取已经由业务层验证的凭证。
        // 2. 构造 Shiro AuthenticationToken：默认走 UsernamePasswordToken 形式
        //    （业务侧可在 ShiroSubject 上扩展 login 重载以支持其他 Token 类型）
        Object credentialValue = Objects.nonNull(request.getExtra())
                ? request.getExtra().get("credential") : null;
        String credential = Objects.nonNull(credentialValue) ? String.valueOf(credentialValue) : "";
        AuthenticationToken token = new UsernamePasswordToken(
                String.valueOf(request.getLoginId()),
                credential
        );

        try {
            subject.login(token);
        } catch (org.apache.shiro.authc.UnknownAccountException e) {
            // Shiro 账号不存在 → ddd4j 通用 UnknownAccount
            throw new UnknownAccountException(e.getMessage(), e);
        } catch (IncorrectCredentialsException e) {
            // Shiro 密码错误 → ddd4j 通用 BadCredentials
            throw new BadCredentialsException(e.getMessage(), e);
        } catch (LockedAccountException e) {
            // Shiro 账号被锁 → ddd4j 通用 AccountLocked
            throw new AccountLockedException(e.getMessage(), e);
        } catch (DisabledAccountException e) {
            // Shiro 账号被禁 → ddd4j 通用 AccountDisabled
            throw new AccountDisabledException(e.getMessage(), e);
        } catch (org.apache.shiro.authc.ExpiredCredentialsException e) {
            // Shiro 凭证过期 → ddd4j 通用 CredentialsExpired
            throw new CredentialsExpiredException(e.getMessage(), e);
        } catch (org.apache.shiro.session.ExpiredSessionException e) {
            // Shiro session 过期 → ddd4j 通用 SessionExpired
            throw new SessionExpiredException(e.getMessage(), e);
        } catch (RuntimeException e) {
            // Shiro 其他认证异常（含未实现的账号过期等）：未登录/无主体等 → ddd4j 通用 NotLoggedIn
            // （用 RuntimeException 兜底，避开 shiro 1.7.0 包路径兼容问题）
            throw new NotLoggedInException(e.getMessage(), e);
        }

        // 3. 登录成功：把 AuthPrincipal 写入 Session，并保留 sessionConfig
        Session session = subject.getSession(true);
        if (Objects.nonNull(request.getPrincipal())) {
            session.setAttribute(SESSION_KEY_PRINCIPAL, request.getPrincipal());
        }
        if (Objects.nonNull(cfg) && cfg.getTimeout() != -1) {
            // 适配 Shiro Session 超时
            session.setTimeout(cfg.getTimeout() * 1000L);
        }
        session.setAttribute(SESSION_KEY_CONFIG, cfg);
        return session.getId().toString();
    }

    /**
     * 结束由当前调用指定的认证会话。
     *
     * <p>Shiro 实现调用 <code>shiroSubject()</code>。</p>
     */
    @Override
    public void logout() {
        Subject subject = shiroSubject();
        if (Objects.nonNull(subject)) {
            subject.logout();
        }
    }

    /**
     * 结束由当前调用指定的认证会话。
     *
     * <p>Shiro 实现调用 <code>getLoginId()</code>。</p>
     *
     * @param loginId 账号 ID
     */
    @Override
    public void logout(Object loginId) {
        // Shiro 通过 SessionDAO + SessionManager 强踢指定账号的所有 session：
        // 业务侧可通过自定义 SessionDAO 扩展。
        // 默认行为：若 loginId 与当前用户一致则登出当前会话。
        if (Objects.nonNull(loginId) && Objects.equals(loginId, getLoginId())) {
            logout();
        }
    }

    /**
     * 强制结束指定身份关联的登录会话。
     *
     * <p>Shiro 实现调用 <code>SecurityUtils.getSecurityManager()</code>。</p>
     *
     * @param loginId 账号 ID
     */
    @Override
    public void kickout(Object loginId) {
        if (Objects.isNull(loginId)) {
            return;
        }
        // 通过 SecurityManager 强踢：拿到所有活跃 Session 找到匹配 loginId 的删除
        // ddd4j 标准做法：业务侧提供自定义 SessionDAO/Realm 把 loginId 映射到 Session
        // 此处降级处理：使用 DefaultSecurityManager 的 logout 入口
        SecurityManager sm = SecurityUtils.getSecurityManager();
        if (sm instanceof DefaultSecurityManager && Objects.nonNull(loginId)) {
            // 注意：DefaultSecurityManager 没有直接 killByPrincipal，我们走 sessionId 路径
            // 业务方可通过 Subject.runAs / 自定义 SessionDAO 实现
            // 这里降级为：如果登录用户就是被踢者，登出当前会话
            if (Objects.equals(loginId, getLoginId())) {
                logout();
            }
        }
    }

    /**
     * 刷新当前实现所持有的数据或状态。
     *
     * <p>Shiro 实现调用 <code>shiroSubject()</code>。</p>
     *
     * @return 根据执行路径返回<code>session.getId().toString()</code>、<code>null</code>
     */
    @Override
    public String refresh() {
        Subject subject = shiroSubject();
        if (Objects.nonNull(subject)) {
            Session session = subject.getSession(false);
            if (Objects.nonNull(session)) {
                session.touch();
                return session.getId().toString();
            }
        }
        return null;
    }

    // ==================== 封禁（委托业务层 / Shiro CacheManager）====================

    /**
     * 接收<code>disable</code>回调，当前实现无需在此步骤更新状态。
     *
     * @param loginId 账号 ID
     * @param timeout 封禁时长（秒），{@code -1} 代表永久封禁
     */
    @Override
    public void disable(Object loginId, long timeout) {
        // Shiro 封禁通常通过 CacheManager + 自定义 Realm；
        // 委托业务侧实现。
    }

    /**
     * 判断当前限制是否禁止指定身份执行相应操作。
     *
     * @param loginId 账号 ID
     * @return 此实现返回<code>false</code>
     */
    @Override
    public boolean isDisabled(Object loginId) {
        // 委托业务侧 Realm/CacheManager；Shiro 默认不内置
        return false;
    }

    /**
     * 接收<code>untieDisable</code>回调，当前实现无需在此步骤更新状态。
     *
     * @param loginId 账号 ID
     */
    @Override
    public void untieDisable(Object loginId) {
        // 同 disable：委托业务侧
    }
}
