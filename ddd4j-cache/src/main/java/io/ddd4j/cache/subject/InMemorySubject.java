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
package io.ddd4j.cache.subject;

import io.ddd4j.cache.CacheKit;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.auth.AuthRequest;
import io.ddd4j.core.auth.event.AuthFailedEvent;
import io.ddd4j.core.auth.event.AuthSucceededEvent;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.kit.lang.StrKit;

import java.time.Instant;
import java.util.*;

/**
 * 基于 {@link CacheKit} 的内存版 {@link Subject} 实现（单进程、非持久化）。
 *
 * <p><b>用途</b>：本地开发、单元测试、示例工程的 Subject 实现。
 * 生产环境应替换为基于 sa-token / shiro / spring-security 的实现。
 *
 * <p>本实现只负责默认本地运行态，具体缓存后端由 {@link CacheKit} 承接。
 * 业务方如需替换存储，可在构造本类前向 {@link CacheKit} 注册同名业务缓存。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
@SuppressWarnings("unchecked")
public class InMemorySubject implements Subject {

    /**
     * Token 到身份主体的缓存业务名。
     */
    public static final String PRINCIPALS_BY_TOKEN_CACHE = "ddd4j:subject:principals-by-token";
    /**
     * 登录标识到身份主体的缓存业务名。
     */
    public static final String PRINCIPALS_BY_LOGIN_ID_CACHE = "ddd4j:subject:principals-by-login-id";
    /**
     * 登录标识到 Token 的缓存业务名。
     */
    public static final String TOKEN_BY_LOGIN_ID_CACHE = "ddd4j:subject:token-by-login-id";
    /**
     * 账号禁用截止时间的缓存业务名。
     */
    public static final String DISABLED_UNTIL_CACHE = "ddd4j:subject:disabled-until";
    /**
     * 当前 Token 的缓存业务名。
     */
    public static final String CURRENT_TOKEN_CACHE = "ddd4j:subject:current-token";
    /**
     * 在禁用缓存中表示账号永久禁用的标记值。
     */
    private static final long PERMANENT_DISABLE = -1L;
    /**
     * 创建登录状态缓存时使用的永不过期配置。
     */
    private static final long NO_EXPIRE_SECONDS = 0L;
    /**
     * 消费登录、退出和会话变更等认证事件的回调。
     */
    private final java.util.function.Consumer<Object> eventPublisher;

    /**
     * 构造基于 {@link CacheKit} 的内存版 Subject。
     *
     * @param eventPublisher ddd4j 通用事件发布者 SPI（接受任意事件对象）
     */
    public InMemorySubject(java.util.function.Consumer<Object> eventPublisher) {
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
        ensureCaches();
    }

    /**
     * 绑定会话凭证（典型用法：HTTP filter 解析 Bearer Token 后调用）。
     *
     * @param token 会话凭证
     */
    public void bind(String token) {
        if (Objects.nonNull(principalByToken(token))) {
            setCurrentToken(token);
        }
    }

    /**
     * 清理当前线程绑定的凭证（典型用法：HTTP filter 在 finally 块调用）。
     */
    public void clear() {
        clearCurrentToken();
    }

    /**
     * 读取当前会话的认证主体。
     *
     * <p>此实现调用 <code>currentToken()</code>。</p>
     *
     * @param <T> AuthPrincipal 子类型
     * @return 当前会话绑定的主体
     */
    @Override
    public <T extends AuthPrincipal> T getPrincipal() {
        String token = currentToken();
        if (Objects.isNull(token)) {
            return null;
        }
        return (T) principalByToken(token);
    }

    /**
     * 查询指定登录标识关联的认证主体。
     *
     * <p>此实现调用 <code>principalByLoginId(loginId)</code>。</p>
     *
     * @param <T>     AuthPrincipal 子类型
     * @param loginId 登录账号 ID
     * @return 给定登录标识关联的主体
     */
    @Override
    public <T extends AuthPrincipal> T getPrincipalByLoginId(Object loginId) {
        return (T) principalByLoginId(loginId);
    }

    /**
     * 查询指定令牌关联的认证主体。
     *
     * <p>此实现调用 <code>principalByToken(tokenValue)</code>。</p>
     *
     * @param <T>        AuthPrincipal 子类型
     * @param tokenValue 会话凭证（Token / SessionId）
     * @return 给定令牌关联的主体
     */
    @Override
    public <T extends AuthPrincipal> T getPrincipalByToken(String tokenValue) {
        return (T) principalByToken(tokenValue);
    }

    /**
     * 以传入的凭据启动认证会话。
     *
     * <p>此实现调用 <code>validate(request)</code>。</p>
     *
     * @param request 登录请求（loginId + 密码 + 扩展信息 + 有效期）
     * @return 实际返回<code>token</code>
     */
    @Override
    public String login(AuthRequest request) {
        try {
            validate(request);
            AuthPrincipal principal = normalizePrincipal(request);
            String token = createToken(request);
            CacheKit.put(PRINCIPALS_BY_TOKEN_CACHE, token, principal);
            CacheKit.put(PRINCIPALS_BY_LOGIN_ID_CACHE, cacheKey(request.getLoginId()), principal);
            CacheKit.put(TOKEN_BY_LOGIN_ID_CACHE, cacheKey(request.getLoginId()), token);
            setCurrentToken(token);
            eventPublisher.accept(new AuthSucceededEvent(request, principal, token, Instant.now()));
            return token;
        } catch (RuntimeException exception) {
            eventPublisher.accept(new AuthFailedEvent(request, exception.getMessage(), Instant.now()));
            throw exception;
        }
    }

    /**
     * 结束由当前调用指定的认证会话。
     *
     * <p>此实现调用 <code>currentToken()</code>。</p>
     */
    @Override
    public void logout() {
        String token = currentToken();
        if (Objects.isNull(token)) {
            return;
        }
        AuthPrincipal principal = principalByToken(token);
        CacheKit.invalidate(PRINCIPALS_BY_TOKEN_CACHE, token);
        if (Objects.nonNull(principal)) {
            CacheKit.invalidate(PRINCIPALS_BY_LOGIN_ID_CACHE, cacheKey(principal.getLoginId()));
            CacheKit.invalidate(TOKEN_BY_LOGIN_ID_CACHE, cacheKey(principal.getLoginId()));
        }
        clearCurrentToken();
    }

    /**
     * 结束由当前调用指定的认证会话。
     *
     * <p>此实现调用 <code>cacheKey(loginId)</code>。</p>
     *
     * @param loginId 账号 ID
     */
    @Override
    public void logout(Object loginId) {
        String loginIdKey = cacheKey(loginId);
        String token = CacheKit.get(TOKEN_BY_LOGIN_ID_CACHE, loginIdKey);
        if (Objects.nonNull(token)) {
            CacheKit.invalidate(PRINCIPALS_BY_TOKEN_CACHE, token);
        }
        CacheKit.invalidate(PRINCIPALS_BY_LOGIN_ID_CACHE, loginIdKey);
        CacheKit.invalidate(TOKEN_BY_LOGIN_ID_CACHE, loginIdKey);
        if (Objects.equals(token, currentToken())) {
            clearCurrentToken();
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
     * <p>此实现调用 <code>getPrincipal()</code>。</p>
     *
     * @return 根据执行路径返回<code>null</code>、<code>newToken</code>
     */
    @Override
    public String refresh() {
        AuthPrincipal principal = getPrincipal();
        if (Objects.isNull(principal)) {
            return null;
        }
        String oldToken = currentToken();
        String newToken = "refresh:" + UUID.randomUUID();
        CacheKit.invalidate(PRINCIPALS_BY_TOKEN_CACHE, oldToken);
        CacheKit.put(PRINCIPALS_BY_TOKEN_CACHE, newToken, principal);
        CacheKit.put(TOKEN_BY_LOGIN_ID_CACHE, cacheKey(principal.getLoginId()), newToken);
        setCurrentToken(newToken);
        return newToken;
    }

    /**
     * 校验当前调用传入的数据或凭据。
     *
     * <p>此实现调用 <code>getPrincipalByToken(token)</code>。</p>
     *
     * @param <T>   AuthPrincipal 子类型
     * @param token 会话凭证
     * @return 实际返回<code>getPrincipalByToken(token)</code>
     */
    @Override
    public <T extends AuthPrincipal> T verify(String token) {
        return getPrincipalByToken(token);
    }

    /**
     * 检查身份是否拥有指定权限。
     *
     * <p>此实现调用 <code>hasPermission(getPrincipal(), permission)</code>。</p>
     *
     * @param permission 权限标识（如 {@code "order:create"}）
     * @return 请求的权限被授予时为真
     */
    @Override
    public boolean isPermitted(String permission) {
        return hasPermission(getPrincipal(), permission);
    }

    /**
     * 检查身份是否拥有指定权限。
     *
     * <p>此实现调用 <code>hasPermission(getPrincipalByLoginId(loginId), permission)</code>。</p>
     *
     * @param loginId    用户账号 ID
     * @param permission 权限标识
     * @return 请求的权限被授予时为真
     */
    @Override
    public boolean isPermitted(Object loginId, String permission) {
        return hasPermission(getPrincipalByLoginId(loginId), permission);
    }

    /**
     * 检查身份是否拥有指定权限。
     *
     * <p>此实现调用 <code>permissionChecks(getPrincipal(), permissions)</code>。</p>
     *
     * @param permissions 权限标识列表
     * @return 请求的权限被授予时为真
     */
    @Override
    public boolean[] isPermitted(String... permissions) {
        return permissionChecks(getPrincipal(), permissions);
    }

    /**
     * 检查身份是否拥有指定权限。
     *
     * <p>此实现调用 <code>permissionChecks(getPrincipalByLoginId(loginId), permissions)</code>。</p>
     *
     * @param loginId     用户账号 ID
     * @param permissions 权限标识列表
     * @return 请求的权限被授予时为真
     */
    @Override
    public boolean[] isPermitted(Object loginId, String... permissions) {
        return permissionChecks(getPrincipalByLoginId(loginId), permissions);
    }

    /**
     * 检查身份是否具有给定权限中的任意一项。
     *
     * <p>此实现调用 <code>isPermitted(permissions)</code>。</p>
     *
     * @param permissions 权限标识列表
     * @return 至少有一项请求权限被授予时为真
     */
    @Override
    public boolean isPermittedAny(String... permissions) {
        for (boolean permitted : isPermitted(permissions)) {
            if (permitted) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查身份是否具有给定权限中的任意一项。
     *
     * <p>此实现调用 <code>isPermitted(loginId, permissions)</code>。</p>
     *
     * @param loginId     用户账号 ID
     * @param permissions 权限标识列表
     * @return 至少有一项请求权限被授予时为真
     */
    @Override
    public boolean isPermittedAny(Object loginId, String... permissions) {
        for (boolean permitted : isPermitted(loginId, permissions)) {
            if (permitted) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查身份是否具有全部给定权限。
     *
     * <p>此实现调用 <code>allTrue(isPermitted(permissions))</code>。</p>
     *
     * @param permissions 权限标识列表
     * @return 所有请求权限都被授予时为真
     */
    @Override
    public boolean isPermittedAll(String... permissions) {
        return allTrue(isPermitted(permissions));
    }

    /**
     * 检查身份是否具有全部给定权限。
     *
     * <p>此实现调用 <code>allTrue(isPermitted(loginId, permissions))</code>。</p>
     *
     * @param loginId     用户账号 ID
     * @param permissions 权限标识列表
     * @return 所有请求权限都被授予时为真
     */
    @Override
    public boolean isPermittedAll(Object loginId, String... permissions) {
        return allTrue(isPermitted(loginId, permissions));
    }

    /**
     * 检查给定标识是否匹配主体的角色编码或编号。
     *
     * @param roleIdentifier 待检查的角色标识
     * @return 存在匹配角色时返回 true
     */
    @Override
    public boolean hasRole(String roleIdentifier) {
        return hasRole(getPrincipal(), roleIdentifier);
    }

    /**
     * 检查给定标识是否匹配主体的角色编码或编号。
     *
     * @param loginId        登录标识
     * @param roleIdentifier 待检查的角色标识
     * @return 存在匹配角色时返回 true
     */
    @Override
    public boolean hasRole(Object loginId, String roleIdentifier) {
        return hasRole(getPrincipalByLoginId(loginId), roleIdentifier);
    }

    /**
     * 按输入顺序逐项判断身份的角色归属。
     *
     * <p>此实现调用 <code>roleChecks(getPrincipal(), roleIdentifiers)</code>。</p>
     *
     * @param roleIdentifiers 角色标识列表
     * @return 实际返回<code>roleChecks(getPrincipal(), roleIdentifiers)</code>
     */
    @Override
    public boolean[] hasRoles(String... roleIdentifiers) {
        return roleChecks(getPrincipal(), roleIdentifiers);
    }

    /**
     * 按输入顺序逐项判断身份的角色归属。
     *
     * <p>此实现调用 <code>roleChecks(getPrincipalByLoginId(loginId), roleIdentifiers)</code>。</p>
     *
     * @param loginId         用户账号 ID
     * @param roleIdentifiers 角色标识列表
     * @return 实际返回<code>roleChecks(getPrincipalByLoginId(loginId), roleIdentifiers)</code>
     */
    @Override
    public boolean[] hasRoles(Object loginId, String... roleIdentifiers) {
        return roleChecks(getPrincipalByLoginId(loginId), roleIdentifiers);
    }

    /**
     * 检查身份是否具有给定角色中的任意一个。
     *
     * <p>此实现调用 <code>hasRoles(roleIdentifiers)</code>。</p>
     *
     * @param roleIdentifiers 角色标识列表
     * @return 至少具有一个指定角色时为真
     */
    @Override
    public boolean hasAnyRole(String... roleIdentifiers) {
        for (boolean hasRole : hasRoles(roleIdentifiers)) {
            if (hasRole) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查身份是否具有给定角色中的任意一个。
     *
     * <p>此实现调用 <code>hasRoles(loginId, roleIdentifiers)</code>。</p>
     *
     * @param loginId         用户账号 ID
     * @param roleIdentifiers 角色标识列表
     * @return 至少具有一个指定角色时为真
     */
    @Override
    public boolean hasAnyRole(Object loginId, String... roleIdentifiers) {
        for (boolean hasRole : hasRoles(loginId, roleIdentifiers)) {
            if (hasRole) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查身份是否具有全部给定角色。
     *
     * <p>此实现调用 <code>allTrue(hasRoles(roleIdentifiers))</code>。</p>
     *
     * @param roleIdentifiers 角色标识列表
     * @return 具有全部指定角色时为真
     */
    @Override
    public boolean hasAllRole(String... roleIdentifiers) {
        return allTrue(hasRoles(roleIdentifiers));
    }

    /**
     * 检查身份是否具有全部给定角色。
     *
     * <p>此实现调用 <code>allTrue(hasRoles(loginId, roleIdentifiers))</code>。</p>
     *
     * @param loginId         用户账号 ID
     * @param roleIdentifiers 角色标识列表
     * @return 具有全部指定角色时为真
     */
    @Override
    public boolean hasAllRole(Object loginId, String... roleIdentifiers) {
        return allTrue(hasRoles(loginId, roleIdentifiers));
    }

    /**
     * 检查当前会话是否已经认证。
     *
     * <p>此实现调用 <code>getPrincipal()</code>。</p>
     *
     * @return 当前会话通过身份认证时为真
     */
    @Override
    public boolean isAuthenticated() {
        return Objects.nonNull(getPrincipal());
    }

    /**
     * 检查当前会话是否已经认证。
     *
     * <p>此实现调用 <code>principalByLoginId(loginId)</code>。</p>
     *
     * @param loginId 用户账号 ID
     * @return 当前会话通过身份认证时为真
     */
    @Override
    public boolean isAuthenticated(Object loginId) {
        return Objects.nonNull(principalByLoginId(loginId));
    }

    /**
     * 判断当前身份是否由记住登录状态恢复。
     *
     * @return 此实现返回<code>false</code>
     */
    @Override
    public boolean isRemembered() {
        return false;
    }

    /**
     * 检查当前认证实现是否将给定设备识别为受信设备。
     *
     * <p>此实现调用 <code>getPrincipal()</code>。</p>
     *
     * @param deviceId 设备标识
     * @return 实际返回<code>Objects.nonNull(principal) &amp;&amp; Objects.equals(principal.getDeviceId(), deviceId)</code>
     */
    @Override
    public boolean isTrustDeviceId(String deviceId) {
        AuthPrincipal principal = getPrincipal();
        return Objects.nonNull(principal) && Objects.equals(principal.getDeviceId(), deviceId);
    }

    /**
     * 检查当前认证实现是否将给定设备识别为受信设备。
     *
     * <p>此实现调用 <code>getPrincipalByLoginId(userId)</code>。</p>
     *
     * @param userId   用户 ID
     * @param deviceId 设备标识
     * @return 实际返回<code>Objects.nonNull(principal) &amp;&amp; Objects.equals(principal.getDeviceId(), deviceId)</code>
     */
    @Override
    public boolean isTrustDeviceId(Object userId, String deviceId) {
        AuthPrincipal principal = getPrincipalByLoginId(userId);
        return Objects.nonNull(principal) && Objects.equals(principal.getDeviceId(), deviceId);
    }

    /**
     * 封禁账号，被封禁的账号在封禁期内无法登录。
     *
     * <p>此实现调用 <code>Instant.now().plusSeconds(timeout).getEpochSecond()</code>。</p>
     *
     * @param loginId 账号 ID
     * @param timeout 封禁时长（秒），{@code -1} 代表永久封禁
     */
    @Override
    public void disable(Object loginId, long timeout) {
        long disabledUntil = timeout < 0 ? PERMANENT_DISABLE : Instant.now().plusSeconds(timeout).getEpochSecond();
        CacheKit.put(DISABLED_UNTIL_CACHE, cacheKey(loginId), disabledUntil);
    }

    /**
     * 判断当前限制是否禁止指定身份执行相应操作。
     *
     * <p>此实现调用 <code>CacheKit.get(DISABLED_UNTIL_CACHE, cacheKey(loginId))</code>。</p>
     *
     * @param loginId 账号 ID
     * @return 根据执行路径返回<code>false</code>、<code>true</code>、<code>disabled</code>
     */
    @Override
    public boolean isDisabled(Object loginId) {
        Long disabledUntil = CacheKit.get(DISABLED_UNTIL_CACHE, cacheKey(loginId));
        if (Objects.isNull(disabledUntil)) {
            return false;
        }
        if (Objects.equals(disabledUntil, PERMANENT_DISABLE)) {
            return true;
        }
        boolean disabled = disabledUntil > Instant.now().getEpochSecond();
        if (!disabled) {
            CacheKit.invalidate(DISABLED_UNTIL_CACHE, cacheKey(loginId));
        }
        return disabled;
    }

    /**
     * 解除指定身份的禁用限制。
     *
     * <p>此实现调用 <code>CacheKit.invalidate(DISABLED_UNTIL_CACHE, cacheKey(loginId))</code>。</p>
     *
     * @param loginId 账号 ID
     */
    @Override
    public void untieDisable(Object loginId) {
        CacheKit.invalidate(DISABLED_UNTIL_CACHE, cacheKey(loginId));
    }

    /**
     * 确保主体、令牌、账号禁用状态和当前线程会话使用的缓存均已注册。
     */
    private void ensureCaches() {
        ensureCache(PRINCIPALS_BY_TOKEN_CACHE);
        ensureCache(PRINCIPALS_BY_LOGIN_ID_CACHE);
        ensureCache(TOKEN_BY_LOGIN_ID_CACHE);
        ensureCache(DISABLED_UNTIL_CACHE);
        ensureCache(CURRENT_TOKEN_CACHE);
    }

    /**
     * 当指定缓存尚未注册时创建一个永不过期的本地缓存。
     *
     * @param cacheName 缓存注册名称
     */
    private void ensureCache(String cacheName) {
        if (Objects.isNull(CacheKit.getCache(cacheName))) {
            CacheKit.build(cacheName, NO_EXPIRE_SECONDS);
        }
    }

    /**
     * 按令牌读取主体，空白令牌直接视为缺失。
     *
     * @param token 登录令牌
     * @return 认证主体，找不到时返回空
     */
    private AuthPrincipal principalByToken(String token) {
        if (StrKit.isBlank(token)) {
            return null;
        }
        return CacheKit.get(PRINCIPALS_BY_TOKEN_CACHE, token);
    }

    /**
     * 按登录标识读取已缓存的认证主体。
     *
     * @param loginId 登录标识
     * @return 认证主体，标识为空或查不到时返回空
     */
    private AuthPrincipal principalByLoginId(Object loginId) {
        if (Objects.isNull(loginId)) {
            return null;
        }
        return CacheKit.get(PRINCIPALS_BY_LOGIN_ID_CACHE, cacheKey(loginId));
    }

    /**
     * 将非空登录标识转换为缓存键。
     *
     * @param key 缓存键
     * @return 登录标识的字符串形式
     * @throws NullPointerException 登录标识为空
     */
    private String cacheKey(Object key) {
        return String.valueOf(Objects.requireNonNull(key, "cache key must not be null"));
    }

    /**
     * 读取与当前线程绑定的登录令牌。
     *
     * @return 当前线程的令牌，没有绑定时返回空
     */
    private String currentToken() {
        return CacheKit.get(CURRENT_TOKEN_CACHE, currentTokenKey());
    }

    /**
     * 在当前线程对应的会话键下保存登录令牌。
     *
     * @param token 登录令牌
     */
    private void setCurrentToken(String token) {
        CacheKit.put(CURRENT_TOKEN_CACHE, currentTokenKey(), token);
    }

    /**
     * 清除当前线程对应的令牌绑定。
     */
    private void clearCurrentToken() {
        CacheKit.invalidate(CURRENT_TOKEN_CACHE, currentTokenKey());
    }

    /**
     * 用线程编号生成当前线程的令牌缓存键。
     *
     * @return 带线程前缀的令牌缓存键
     */
    private String currentTokenKey() {
        return "thread:" + Thread.currentThread().getId();
    }

    /**
     * 检查登录标识、账号禁用状态和示例登录场景的必填信息。
     *
     * @param request 登录请求
     * @throws IllegalArgumentException 请求缺少登录标识、账号被禁用或登录场景校验失败
     */
    private void validate(AuthRequest request) {
        if (Objects.isNull(request) || Objects.isNull(request.getLoginId())) {
            throw new IllegalArgumentException("loginId must not be null");
        }
        if (isDisabled(request.getLoginId())) {
            throw new IllegalArgumentException("account is disabled");
        }
        String scene = String.valueOf(request.getExtra().get("loginScene"));
        if ("phone".equals(scene) && !"123456".equals(request.getExtra().get("verificationCode"))) {
            throw new IllegalArgumentException("invalid phone verification code");
        }
        if ("third-party".equals(scene) && StrKit.isBlank(String.valueOf(request.getExtra().get("openId")))) {
            throw new IllegalArgumentException("third-party openId must not be blank");
        }
    }

    /**
     * 补齐主体标识和设备类型，并将登录附加信息写入主体资料。
     *
     * @param request 登录请求
     * @return 已补齐信息的认证主体
     */
    private AuthPrincipal normalizePrincipal(AuthRequest request) {
        AuthPrincipal principal = request.getPrincipal();
        if (Objects.isNull(principal)) {
            principal = new AuthPrincipal().setLoginId(request.getLoginId()).setUserId(request.getLoginId());
        }
        if (Objects.isNull(principal.getLoginId())) {
            principal.setLoginId(request.getLoginId());
        }
        if (Objects.isNull(principal.getUserId())) {
            principal.setUserId(request.getLoginId());
        }
        if (Objects.isNull(principal.getDeviceType())) {
            principal.setDeviceType(request.getDeviceType());
        }
        principal.getProfile().putAll(request.getExtra());
        principal.getProfile().put("realm", request.getRealm());
        return principal;
    }

    /**
     * 为本次登录生成包含认证域和随机 UUID 的令牌。
     *
     * @param request 登录请求
     * @return 新登录令牌
     */
    private String createToken(AuthRequest request) {
        String realm = StrKit.isBlank(request.getRealm()) ? "default" : request.getRealm();
        return realm + ":" + UUID.randomUUID();
    }

    /**
     * 检查主体包含给定的非空白权限标识。
     *
     * @param principal  当前认证主体
     * @param permission 待检查的权限标识
     * @return 存在该权限时返回 true
     */
    private boolean hasPermission(AuthPrincipal principal, String permission) {
        if (Objects.isNull(principal) || StrKit.isBlank(permission)) {
            return false;
        }
        Set<String> permissions = principal.getPerms();
        return Objects.nonNull(permissions) && permissions.contains(permission);
    }

    /**
     * 逐个检查权限，保持输入顺序。
     *
     * @param principal   当前认证主体
     * @param permissions 待检查的权限标识序列
     * @return 权限检查结果，未提供权限时返回空数组
     */
    private boolean[] permissionChecks(AuthPrincipal principal, String... permissions) {
        if (Objects.isNull(permissions) || permissions.length == 0) {
            return new boolean[0];
        }
        boolean[] result = new boolean[permissions.length];
        for (int i = 0; i < permissions.length; i++) {
            result[i] = hasPermission(principal, permissions[i]);
        }
        return result;
    }

    /**
     * 检查给定标识是否匹配主体的角色编码或编号。
     *
     * @param principal      当前认证主体
     * @param roleIdentifier 待检查的角色标识
     * @return 存在匹配角色时返回 true
     */
    private boolean hasRole(AuthPrincipal principal, String roleIdentifier) {
        if (Objects.isNull(principal) || StrKit.isBlank(roleIdentifier)) {
            return false;
        }
        return roleIdentifiers(principal).contains(roleIdentifier);
    }

    /**
     * 逐个检查角色，保持输入顺序。
     *
     * @param principal       当前认证主体
     * @param roleIdentifiers 待检查的角色标识序列
     * @return 角色检查结果，未提供角色时返回空数组
     */
    private boolean[] roleChecks(AuthPrincipal principal, String... roleIdentifiers) {
        if (Objects.isNull(roleIdentifiers) || roleIdentifiers.length == 0) {
            return new boolean[0];
        }
        boolean[] result = new boolean[roleIdentifiers.length];
        for (int i = 0; i < roleIdentifiers.length; i++) {
            result[i] = hasRole(principal, roleIdentifiers[i]);
        }
        return result;
    }

    /**
     * 汇总主体的主角色和其他角色的编码与编号。
     *
     * @param principal 当前认证主体
     * @return 用于角色匹配的字符串标识列表
     */
    private List<String> roleIdentifiers(AuthPrincipal principal) {
        if (Objects.isNull(principal)) {
            return Collections.emptyList();
        }
        List<String> roles = new ArrayList<>();
        if (StrKit.isNotBlank(principal.getRoleCode())) {
            roles.add(principal.getRoleCode());
        }
        if (Objects.nonNull(principal.getRoleId())) {
            roles.add(String.valueOf(principal.getRoleId()));
        }
        if (Objects.nonNull(principal.getRoles())) {
            for (AuthPrincipal.RolePair role : principal.getRoles()) {
                if (Objects.isNull(role)) {
                    continue;
                }
                if (StrKit.isNotBlank(role.getRoleCode())) {
                    roles.add(role.getRoleCode());
                }
                if (Objects.nonNull(role.getRoleId())) {
                    roles.add(String.valueOf(role.getRoleId()));
                }
            }
        }
        return roles;
    }

    /**
     * 要求至少存在一个检查结果且所有检查通过。
     *
     * @param values 按输入顺序排列的检查结果
     * @return 结果非空且全为 true 时返回 true
     */
    private boolean allTrue(boolean[] values) {
        if (values.length == 0) {
            return false;
        }
        for (boolean value : values) {
            if (!value) {
                return false;
            }
        }
        return true;
    }
}
