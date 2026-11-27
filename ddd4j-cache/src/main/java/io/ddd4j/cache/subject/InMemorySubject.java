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

    /** Token \u5230\u8eab\u4efd\u4e3b\u4f53\u7684\u7f13\u5b58\u4e1a\u52a1\u540d\u3002 */
    public static final String PRINCIPALS_BY_TOKEN_CACHE = "ddd4j:subject:principals-by-token";
    /** \u767b\u5f55\u6807\u8bc6\u5230\u8eab\u4efd\u4e3b\u4f53\u7684\u7f13\u5b58\u4e1a\u52a1\u540d\u3002 */
    public static final String PRINCIPALS_BY_LOGIN_ID_CACHE = "ddd4j:subject:principals-by-login-id";
    /** \u767b\u5f55\u6807\u8bc6\u5230 Token \u7684\u7f13\u5b58\u4e1a\u52a1\u540d\u3002 */
    public static final String TOKEN_BY_LOGIN_ID_CACHE = "ddd4j:subject:token-by-login-id";
    /** \u8d26\u53f7\u7981\u7528\u622a\u6b62\u65f6\u95f4\u7684\u7f13\u5b58\u4e1a\u52a1\u540d\u3002 */
    public static final String DISABLED_UNTIL_CACHE = "ddd4j:subject:disabled-until";
    /** \u5f53\u524d Token \u7684\u7f13\u5b58\u4e1a\u52a1\u540d\u3002 */
    public static final String CURRENT_TOKEN_CACHE = "ddd4j:subject:current-token";
    /** \u5728\u7981\u7528\u7f13\u5b58\u4e2d\u8868\u793a\u8d26\u53f7\u6c38\u4e45\u7981\u7528\u7684\u6807\u8bb0\u503c\u3002 */
    private static final long PERMANENT_DISABLE = -1L;
    /** \u521b\u5efa\u767b\u5f55\u72b6\u6001\u7f13\u5b58\u65f6\u4f7f\u7528\u7684\u6c38\u4e0d\u8fc7\u671f\u914d\u7f6e\u3002 */
    private static final long NO_EXPIRE_SECONDS = 0L;
    /** \u6d88\u8d39\u767b\u5f55\u3001\u9000\u51fa\u548c\u4f1a\u8bdd\u53d8\u66f4\u7b49\u8ba4\u8bc1\u4e8b\u4ef6\u7684\u56de\u8c03\u3002 */
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

    /** {@inheritDoc} */
    @Override
    public <T extends AuthPrincipal> T getPrincipal() {
        String token = currentToken();
        if (Objects.isNull(token)) {
            return null;
        }
        return (T) principalByToken(token);
    }

    /** {@inheritDoc} */
    @Override
    public <T extends AuthPrincipal> T getPrincipalByLoginId(Object loginId) {
        return (T) principalByLoginId(loginId);
    }

    /** {@inheritDoc} */
    @Override
    public <T extends AuthPrincipal> T getPrincipalByToken(String tokenValue) {
        return (T) principalByToken(tokenValue);
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public void kickout(Object loginId) {
        logout(loginId);
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public <T extends AuthPrincipal> T verify(String token) {
        return getPrincipalByToken(token);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isPermitted(String permission) {
        return hasPermission(getPrincipal(), permission);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isPermitted(Object loginId, String permission) {
        return hasPermission(getPrincipalByLoginId(loginId), permission);
    }

    /** {@inheritDoc} */
    @Override
    public boolean[] isPermitted(String... permissions) {
        return permissionChecks(getPrincipal(), permissions);
    }

    /** {@inheritDoc} */
    @Override
    public boolean[] isPermitted(Object loginId, String... permissions) {
        return permissionChecks(getPrincipalByLoginId(loginId), permissions);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isPermittedAny(String... permissions) {
        for (boolean permitted : isPermitted(permissions)) {
            if (permitted) {
                return true;
            }
        }
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isPermittedAny(Object loginId, String... permissions) {
        for (boolean permitted : isPermitted(loginId, permissions)) {
            if (permitted) {
                return true;
            }
        }
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isPermittedAll(String... permissions) {
        return allTrue(isPermitted(permissions));
    }

    /** {@inheritDoc} */
    @Override
    public boolean isPermittedAll(Object loginId, String... permissions) {
        return allTrue(isPermitted(loginId, permissions));
    }

    /**
     * \u68c0\u67e5\u7ed9\u5b9a\u6807\u8bc6\u662f\u5426\u5339\u914d\u4e3b\u4f53\u7684\u89d2\u8272\u7f16\u7801\u6216\u7f16\u53f7\u3002
     * @param roleIdentifier \u5f85\u68c0\u67e5\u7684\u89d2\u8272\u6807\u8bc6
     * @return \u5b58\u5728\u5339\u914d\u89d2\u8272\u65f6\u8fd4\u56de true
     */
    @Override
    public boolean hasRole(String roleIdentifier) {
        return hasRole(getPrincipal(), roleIdentifier);
    }

    /**
     * \u68c0\u67e5\u7ed9\u5b9a\u6807\u8bc6\u662f\u5426\u5339\u914d\u4e3b\u4f53\u7684\u89d2\u8272\u7f16\u7801\u6216\u7f16\u53f7\u3002
     * @param loginId \u767b\u5f55\u6807\u8bc6
     * @param roleIdentifier \u5f85\u68c0\u67e5\u7684\u89d2\u8272\u6807\u8bc6
     * @return \u5b58\u5728\u5339\u914d\u89d2\u8272\u65f6\u8fd4\u56de true
     */
    @Override
    public boolean hasRole(Object loginId, String roleIdentifier) {
        return hasRole(getPrincipalByLoginId(loginId), roleIdentifier);
    }

    /** {@inheritDoc} */
    @Override
    public boolean[] hasRoles(String... roleIdentifiers) {
        return roleChecks(getPrincipal(), roleIdentifiers);
    }

    /** {@inheritDoc} */
    @Override
    public boolean[] hasRoles(Object loginId, String... roleIdentifiers) {
        return roleChecks(getPrincipalByLoginId(loginId), roleIdentifiers);
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasAnyRole(String... roleIdentifiers) {
        for (boolean hasRole : hasRoles(roleIdentifiers)) {
            if (hasRole) {
                return true;
            }
        }
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasAnyRole(Object loginId, String... roleIdentifiers) {
        for (boolean hasRole : hasRoles(loginId, roleIdentifiers)) {
            if (hasRole) {
                return true;
            }
        }
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasAllRole(String... roleIdentifiers) {
        return allTrue(hasRoles(roleIdentifiers));
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasAllRole(Object loginId, String... roleIdentifiers) {
        return allTrue(hasRoles(loginId, roleIdentifiers));
    }

    /** {@inheritDoc} */
    @Override
    public boolean isAuthenticated() {
        return Objects.nonNull(getPrincipal());
    }

    /** {@inheritDoc} */
    @Override
    public boolean isAuthenticated(Object loginId) {
        return Objects.nonNull(principalByLoginId(loginId));
    }

    /** {@inheritDoc} */
    @Override
    public boolean isRemembered() {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isTrustDeviceId(String deviceId) {
        AuthPrincipal principal = getPrincipal();
        return Objects.nonNull(principal) && Objects.equals(principal.getDeviceId(), deviceId);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isTrustDeviceId(Object userId, String deviceId) {
        AuthPrincipal principal = getPrincipalByLoginId(userId);
        return Objects.nonNull(principal) && Objects.equals(principal.getDeviceId(), deviceId);
    }

    /** {@inheritDoc} */
    @Override
    public void disable(Object loginId, long timeout) {
        long disabledUntil = timeout < 0 ? PERMANENT_DISABLE : Instant.now().plusSeconds(timeout).getEpochSecond();
        CacheKit.put(DISABLED_UNTIL_CACHE, cacheKey(loginId), disabledUntil);
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public void untieDisable(Object loginId) {
        CacheKit.invalidate(DISABLED_UNTIL_CACHE, cacheKey(loginId));
    }

    /** \u786e\u4fdd\u4e3b\u4f53\u3001\u4ee4\u724c\u3001\u8d26\u53f7\u7981\u7528\u72b6\u6001\u548c\u5f53\u524d\u7ebf\u7a0b\u4f1a\u8bdd\u4f7f\u7528\u7684\u7f13\u5b58\u5747\u5df2\u6ce8\u518c\u3002 */
    private void ensureCaches() {
        ensureCache(PRINCIPALS_BY_TOKEN_CACHE);
        ensureCache(PRINCIPALS_BY_LOGIN_ID_CACHE);
        ensureCache(TOKEN_BY_LOGIN_ID_CACHE);
        ensureCache(DISABLED_UNTIL_CACHE);
        ensureCache(CURRENT_TOKEN_CACHE);
    }

    /**
     * \u5f53\u6307\u5b9a\u7f13\u5b58\u5c1a\u672a\u6ce8\u518c\u65f6\u521b\u5efa\u4e00\u4e2a\u6c38\u4e0d\u8fc7\u671f\u7684\u672c\u5730\u7f13\u5b58\u3002
     * @param cacheName \u7f13\u5b58\u6ce8\u518c\u540d\u79f0
     */
    private void ensureCache(String cacheName) {
        if (Objects.isNull(CacheKit.getCache(cacheName))) {
            CacheKit.build(cacheName, NO_EXPIRE_SECONDS);
        }
    }

    /**
     * \u6309\u4ee4\u724c\u8bfb\u53d6\u4e3b\u4f53\uff0c\u7a7a\u767d\u4ee4\u724c\u76f4\u63a5\u89c6\u4e3a\u7f3a\u5931\u3002
     * @param token \u767b\u5f55\u4ee4\u724c
     * @return \u8ba4\u8bc1\u4e3b\u4f53\uff0c\u627e\u4e0d\u5230\u65f6\u8fd4\u56de\u7a7a
     */
    private AuthPrincipal principalByToken(String token) {
        if (StrKit.isBlank(token)) {
            return null;
        }
        return CacheKit.get(PRINCIPALS_BY_TOKEN_CACHE, token);
    }

    /**
     * \u6309\u767b\u5f55\u6807\u8bc6\u8bfb\u53d6\u5df2\u7f13\u5b58\u7684\u8ba4\u8bc1\u4e3b\u4f53\u3002
     * @param loginId \u767b\u5f55\u6807\u8bc6
     * @return \u8ba4\u8bc1\u4e3b\u4f53\uff0c\u6807\u8bc6\u4e3a\u7a7a\u6216\u67e5\u4e0d\u5230\u65f6\u8fd4\u56de\u7a7a
     */
    private AuthPrincipal principalByLoginId(Object loginId) {
        if (Objects.isNull(loginId)) {
            return null;
        }
        return CacheKit.get(PRINCIPALS_BY_LOGIN_ID_CACHE, cacheKey(loginId));
    }

    /**
     * \u5c06\u975e\u7a7a\u767b\u5f55\u6807\u8bc6\u8f6c\u6362\u4e3a\u7f13\u5b58\u952e\u3002
     * @param key \u7f13\u5b58\u952e
     * @return \u767b\u5f55\u6807\u8bc6\u7684\u5b57\u7b26\u4e32\u5f62\u5f0f
     * @throws NullPointerException \u767b\u5f55\u6807\u8bc6\u4e3a\u7a7a
     */
    private String cacheKey(Object key) {
        return String.valueOf(Objects.requireNonNull(key, "cache key must not be null"));
    }

    /**
     * \u8bfb\u53d6\u4e0e\u5f53\u524d\u7ebf\u7a0b\u7ed1\u5b9a\u7684\u767b\u5f55\u4ee4\u724c\u3002
     * @return \u5f53\u524d\u7ebf\u7a0b\u7684\u4ee4\u724c\uff0c\u6ca1\u6709\u7ed1\u5b9a\u65f6\u8fd4\u56de\u7a7a
     */
    private String currentToken() {
        return CacheKit.get(CURRENT_TOKEN_CACHE, currentTokenKey());
    }

    /**
     * \u5728\u5f53\u524d\u7ebf\u7a0b\u5bf9\u5e94\u7684\u4f1a\u8bdd\u952e\u4e0b\u4fdd\u5b58\u767b\u5f55\u4ee4\u724c\u3002
     * @param token \u767b\u5f55\u4ee4\u724c
     */
    private void setCurrentToken(String token) {
        CacheKit.put(CURRENT_TOKEN_CACHE, currentTokenKey(), token);
    }

    /** \u6e05\u9664\u5f53\u524d\u7ebf\u7a0b\u5bf9\u5e94\u7684\u4ee4\u724c\u7ed1\u5b9a\u3002 */
    private void clearCurrentToken() {
        CacheKit.invalidate(CURRENT_TOKEN_CACHE, currentTokenKey());
    }

    /**
     * \u7528\u7ebf\u7a0b\u7f16\u53f7\u751f\u6210\u5f53\u524d\u7ebf\u7a0b\u7684\u4ee4\u724c\u7f13\u5b58\u952e\u3002
     * @return \u5e26\u7ebf\u7a0b\u524d\u7f00\u7684\u4ee4\u724c\u7f13\u5b58\u952e
     */
    private String currentTokenKey() {
        return "thread:" + Thread.currentThread().getId();
    }

    /**
     * \u68c0\u67e5\u767b\u5f55\u6807\u8bc6\u3001\u8d26\u53f7\u7981\u7528\u72b6\u6001\u548c\u793a\u4f8b\u767b\u5f55\u573a\u666f\u7684\u5fc5\u586b\u4fe1\u606f\u3002
     * @param request \u767b\u5f55\u8bf7\u6c42
     * @throws IllegalArgumentException \u8bf7\u6c42\u7f3a\u5c11\u767b\u5f55\u6807\u8bc6\u3001\u8d26\u53f7\u88ab\u7981\u7528\u6216\u767b\u5f55\u573a\u666f\u6821\u9a8c\u5931\u8d25
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
     * \u8865\u9f50\u4e3b\u4f53\u6807\u8bc6\u548c\u8bbe\u5907\u7c7b\u578b\uff0c\u5e76\u5c06\u767b\u5f55\u9644\u52a0\u4fe1\u606f\u5199\u5165\u4e3b\u4f53\u8d44\u6599\u3002
     * @param request \u767b\u5f55\u8bf7\u6c42
     * @return \u5df2\u8865\u9f50\u4fe1\u606f\u7684\u8ba4\u8bc1\u4e3b\u4f53
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
     * \u4e3a\u672c\u6b21\u767b\u5f55\u751f\u6210\u5305\u542b\u8ba4\u8bc1\u57df\u548c\u968f\u673a UUID \u7684\u4ee4\u724c\u3002
     * @param request \u767b\u5f55\u8bf7\u6c42
     * @return \u65b0\u767b\u5f55\u4ee4\u724c
     */
    private String createToken(AuthRequest request) {
        String realm = StrKit.isBlank(request.getRealm()) ? "default" : request.getRealm();
        return realm + ":" + UUID.randomUUID();
    }

    /**
     * \u68c0\u67e5\u4e3b\u4f53\u5305\u542b\u7ed9\u5b9a\u7684\u975e\u7a7a\u767d\u6743\u9650\u6807\u8bc6\u3002
     * @param principal \u5f53\u524d\u8ba4\u8bc1\u4e3b\u4f53
     * @param permission \u5f85\u68c0\u67e5\u7684\u6743\u9650\u6807\u8bc6
     * @return \u5b58\u5728\u8be5\u6743\u9650\u65f6\u8fd4\u56de true
     */
    private boolean hasPermission(AuthPrincipal principal, String permission) {
        if (Objects.isNull(principal) || StrKit.isBlank(permission)) {
            return false;
        }
        Set<String> permissions = principal.getPerms();
        return Objects.nonNull(permissions) && permissions.contains(permission);
    }

    /**
     * \u9010\u4e2a\u68c0\u67e5\u6743\u9650\uff0c\u4fdd\u6301\u8f93\u5165\u987a\u5e8f\u3002
     * @param principal \u5f53\u524d\u8ba4\u8bc1\u4e3b\u4f53
     * @param permissions \u5f85\u68c0\u67e5\u7684\u6743\u9650\u6807\u8bc6\u5e8f\u5217
     * @return \u6743\u9650\u68c0\u67e5\u7ed3\u679c\uff0c\u672a\u63d0\u4f9b\u6743\u9650\u65f6\u8fd4\u56de\u7a7a\u6570\u7ec4
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
     * \u68c0\u67e5\u7ed9\u5b9a\u6807\u8bc6\u662f\u5426\u5339\u914d\u4e3b\u4f53\u7684\u89d2\u8272\u7f16\u7801\u6216\u7f16\u53f7\u3002
     * @param principal \u5f53\u524d\u8ba4\u8bc1\u4e3b\u4f53
     * @param roleIdentifier \u5f85\u68c0\u67e5\u7684\u89d2\u8272\u6807\u8bc6
     * @return \u5b58\u5728\u5339\u914d\u89d2\u8272\u65f6\u8fd4\u56de true
     */
    private boolean hasRole(AuthPrincipal principal, String roleIdentifier) {
        if (Objects.isNull(principal) || StrKit.isBlank(roleIdentifier)) {
            return false;
        }
        return roleIdentifiers(principal).contains(roleIdentifier);
    }

    /**
     * \u9010\u4e2a\u68c0\u67e5\u89d2\u8272\uff0c\u4fdd\u6301\u8f93\u5165\u987a\u5e8f\u3002
     * @param principal \u5f53\u524d\u8ba4\u8bc1\u4e3b\u4f53
     * @param roleIdentifiers \u5f85\u68c0\u67e5\u7684\u89d2\u8272\u6807\u8bc6\u5e8f\u5217
     * @return \u89d2\u8272\u68c0\u67e5\u7ed3\u679c\uff0c\u672a\u63d0\u4f9b\u89d2\u8272\u65f6\u8fd4\u56de\u7a7a\u6570\u7ec4
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
     * \u6c47\u603b\u4e3b\u4f53\u7684\u4e3b\u89d2\u8272\u548c\u5176\u4ed6\u89d2\u8272\u7684\u7f16\u7801\u4e0e\u7f16\u53f7\u3002
     * @param principal \u5f53\u524d\u8ba4\u8bc1\u4e3b\u4f53
     * @return \u7528\u4e8e\u89d2\u8272\u5339\u914d\u7684\u5b57\u7b26\u4e32\u6807\u8bc6\u5217\u8868
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
     * \u8981\u6c42\u81f3\u5c11\u5b58\u5728\u4e00\u4e2a\u68c0\u67e5\u7ed3\u679c\u4e14\u6240\u6709\u68c0\u67e5\u901a\u8fc7\u3002
     * @param values \u6309\u8f93\u5165\u987a\u5e8f\u6392\u5217\u7684\u68c0\u67e5\u7ed3\u679c
     * @return \u7ed3\u679c\u975e\u7a7a\u4e14\u5168\u4e3a true \u65f6\u8fd4\u56de true
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
