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
import java.util.Collections;

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

    public static final String PRINCIPALS_BY_TOKEN_CACHE = "ddd4j:subject:principals-by-token";
    public static final String PRINCIPALS_BY_LOGIN_ID_CACHE = "ddd4j:subject:principals-by-login-id";
    public static final String TOKEN_BY_LOGIN_ID_CACHE = "ddd4j:subject:token-by-login-id";
    public static final String DISABLED_UNTIL_CACHE = "ddd4j:subject:disabled-until";
    public static final String CURRENT_TOKEN_CACHE = "ddd4j:subject:current-token";
    private static final long PERMANENT_DISABLE = -1L;
    private static final long NO_EXPIRE_SECONDS = 0L;
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

    @Override
    public <T extends AuthPrincipal> T getPrincipal() {
        String token = currentToken();
        if (Objects.isNull(token)) {
            return null;
        }
        return (T) principalByToken(token);
    }

    @Override
    public <T extends AuthPrincipal> T getPrincipalByLoginId(Object loginId) {
        return (T) principalByLoginId(loginId);
    }

    @Override
    public <T extends AuthPrincipal> T getPrincipalByToken(String tokenValue) {
        return (T) principalByToken(tokenValue);
    }

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

    public void kickout(Object loginId) {
        logout(loginId);
    }

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

    public <T extends AuthPrincipal> T verify(String token) {
        return getPrincipalByToken(token);
    }

    @Override
    public boolean isPermitted(String permission) {
        return hasPermission(getPrincipal(), permission);
    }

    @Override
    public boolean isPermitted(Object loginId, String permission) {
        return hasPermission(getPrincipalByLoginId(loginId), permission);
    }

    @Override
    public boolean[] isPermitted(String... permissions) {
        return permissionChecks(getPrincipal(), permissions);
    }

    @Override
    public boolean[] isPermitted(Object loginId, String... permissions) {
        return permissionChecks(getPrincipalByLoginId(loginId), permissions);
    }

    @Override
    public boolean isPermittedAny(String... permissions) {
        for (boolean permitted : isPermitted(permissions)) {
            if (permitted) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isPermittedAny(Object loginId, String... permissions) {
        for (boolean permitted : isPermitted(loginId, permissions)) {
            if (permitted) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isPermittedAll(String... permissions) {
        return allTrue(isPermitted(permissions));
    }

    @Override
    public boolean isPermittedAll(Object loginId, String... permissions) {
        return allTrue(isPermitted(loginId, permissions));
    }

    @Override
    public boolean hasRole(String roleIdentifier) {
        return hasRole(getPrincipal(), roleIdentifier);
    }

    @Override
    public boolean hasRole(Object loginId, String roleIdentifier) {
        return hasRole(getPrincipalByLoginId(loginId), roleIdentifier);
    }

    @Override
    public boolean[] hasRoles(String... roleIdentifiers) {
        return roleChecks(getPrincipal(), roleIdentifiers);
    }

    @Override
    public boolean[] hasRoles(Object loginId, String... roleIdentifiers) {
        return roleChecks(getPrincipalByLoginId(loginId), roleIdentifiers);
    }

    @Override
    public boolean hasAnyRole(String... roleIdentifiers) {
        for (boolean hasRole : hasRoles(roleIdentifiers)) {
            if (hasRole) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasAnyRole(Object loginId, String... roleIdentifiers) {
        for (boolean hasRole : hasRoles(loginId, roleIdentifiers)) {
            if (hasRole) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasAllRole(String... roleIdentifiers) {
        return allTrue(hasRoles(roleIdentifiers));
    }

    @Override
    public boolean hasAllRole(Object loginId, String... roleIdentifiers) {
        return allTrue(hasRoles(loginId, roleIdentifiers));
    }

    @Override
    public boolean isAuthenticated() {
        return Objects.nonNull(getPrincipal());
    }

    @Override
    public boolean isAuthenticated(Object loginId) {
        return Objects.nonNull(principalByLoginId(loginId));
    }

    @Override
    public boolean isRemembered() {
        return false;
    }

    @Override
    public boolean isTrustDeviceId(String deviceId) {
        AuthPrincipal principal = getPrincipal();
        return Objects.nonNull(principal) && Objects.equals(principal.getDeviceId(), deviceId);
    }

    @Override
    public boolean isTrustDeviceId(Object userId, String deviceId) {
        AuthPrincipal principal = getPrincipalByLoginId(userId);
        return Objects.nonNull(principal) && Objects.equals(principal.getDeviceId(), deviceId);
    }

    public void disable(Object loginId, long timeout) {
        long disabledUntil = timeout < 0 ? PERMANENT_DISABLE : Instant.now().plusSeconds(timeout).getEpochSecond();
        CacheKit.put(DISABLED_UNTIL_CACHE, cacheKey(loginId), disabledUntil);
    }

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

    public void untieDisable(Object loginId) {
        CacheKit.invalidate(DISABLED_UNTIL_CACHE, cacheKey(loginId));
    }

    private void ensureCaches() {
        ensureCache(PRINCIPALS_BY_TOKEN_CACHE);
        ensureCache(PRINCIPALS_BY_LOGIN_ID_CACHE);
        ensureCache(TOKEN_BY_LOGIN_ID_CACHE);
        ensureCache(DISABLED_UNTIL_CACHE);
        ensureCache(CURRENT_TOKEN_CACHE);
    }

    private void ensureCache(String cacheName) {
        if (Objects.isNull(CacheKit.getCache(cacheName))) {
            CacheKit.build(cacheName, NO_EXPIRE_SECONDS);
        }
    }

    private AuthPrincipal principalByToken(String token) {
        if (StrKit.isBlank(token)) {
            return null;
        }
        return CacheKit.get(PRINCIPALS_BY_TOKEN_CACHE, token);
    }

    private AuthPrincipal principalByLoginId(Object loginId) {
        if (Objects.isNull(loginId)) {
            return null;
        }
        return CacheKit.get(PRINCIPALS_BY_LOGIN_ID_CACHE, cacheKey(loginId));
    }

    private String cacheKey(Object key) {
        return String.valueOf(Objects.requireNonNull(key, "cache key must not be null"));
    }

    private String currentToken() {
        return CacheKit.get(CURRENT_TOKEN_CACHE, currentTokenKey());
    }

    private void setCurrentToken(String token) {
        CacheKit.put(CURRENT_TOKEN_CACHE, currentTokenKey(), token);
    }

    private void clearCurrentToken() {
        CacheKit.invalidate(CURRENT_TOKEN_CACHE, currentTokenKey());
    }

    private String currentTokenKey() {
        return "thread:" + Thread.currentThread().getId();
    }

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

    private String createToken(AuthRequest request) {
        String realm = StrKit.isBlank(request.getRealm()) ? "default" : request.getRealm();
        return realm + ":" + UUID.randomUUID();
    }

    private boolean hasPermission(AuthPrincipal principal, String permission) {
        if (Objects.isNull(principal) || StrKit.isBlank(permission)) {
            return false;
        }
        Set<String> permissions = principal.getPerms();
        return Objects.nonNull(permissions) && permissions.contains(permission);
    }

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

    private boolean hasRole(AuthPrincipal principal, String roleIdentifier) {
        if (Objects.isNull(principal) || StrKit.isBlank(roleIdentifier)) {
            return false;
        }
        return roleIdentifiers(principal).contains(roleIdentifier);
    }

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
