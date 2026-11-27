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
package io.ddd4j.core.util;

import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.auth.AuthRequest;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectDataProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.core.subject.SubjectStrategy;

import java.util.Objects;

/**
 * 鉴权静态门面 + 全局注册中心（对齐 Sa-Token 的 {@code StpUtil} + {@code SaManager}）。
 *
 * <p>核心职责：
 * <ul>
 *   <li>注册 SPI：{@code register(SubjectProvider)} / {@code setDataProvider(SubjectDataProvider)}</li>
 *   <li>获取当前 Subject：{@link #getSubject()}（业务统一入口）</li>
 *   <li>便捷门面：{@code login} / {@code logout} / {@code getPrincipal} / {@code hasPermission} 等</li>
 * </ul>
 *
 * <p>各框架适配层在启动时调用 {@link #register(SubjectProvider)} 注入实现，
 * 业务代码统一通过 {@code SubjectKit.xxx()} 调用。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class SubjectKit {

    /**
     * Subject 工厂（volatile + 双重检查锁，对齐 SaManager）
     */
    public static volatile SubjectProvider subjectProvider;
    /**
     * 权限数据源（对齐 SaManager.stpInterface）
     */
    public static volatile SubjectDataProvider dataProvider;
    /**
     * 核心行为策略集（对齐 SaStrategy）
     */
    public static volatile SubjectStrategy strategy;

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>SubjectKit</code> \u5b9e\u4f8b\u3002 */
    private SubjectKit() {
    }

    /**
     * 注册 Subject 工厂（由框架适配层调用，如 Spring 桥接的 SubjectRegistrar）。
     *
     * @param provider SubjectProvider 实现
     */
    public static void register(SubjectProvider provider) {
        subjectProvider = provider;
    }

    /**
     * 获取当前 Subject（业务统一入口）。
     *
     * @return 当前 Subject
     * @throws IllegalStateException 未注册 SubjectProvider 时抛出
     */
    public static Subject getSubject() {
        if (Objects.isNull(subjectProvider)) {
            synchronized (SubjectKit.class) {
                if (Objects.isNull(subjectProvider)) {
                    throw new IllegalStateException(
                            "SubjectProvider not registered. Call SubjectKit.register() or use framework adapter.");
                }
            }
        }
        return subjectProvider.getSubject();
    }

    /**
     * 按账号体系获取 Subject（对齐 Sa-Token {@code StpUtil.stpLogic(loginType)}）。
     *
     * @param realm 账号体系标识（如 "admin"/"user"）
     * @return 对应 realm 的 Subject
     */
    public static Subject getSubject(String realm) {
        if (Objects.isNull(subjectProvider)) {
            throw new IllegalStateException("SubjectProvider not registered.");
        }
        return subjectProvider.getSubject(realm);
    }

    /**
     * \u83b7\u53d6\u6743\u9650\u6570\u636e\u6e90\uff08\u9ed8\u8ba4\u7a7a\u5b9e\u73b0\u515c\u5e95\uff09\u3002
     *
     * @return \u5f53\u524d\u6570\u636e\u63d0\u4f9b\u8005
     */
    public static SubjectDataProvider getDataProvider() {
        if (Objects.isNull(dataProvider)) {
            synchronized (SubjectKit.class) {
                if (Objects.isNull(dataProvider)) {
                    dataProvider = SubjectDataProvider.DEFAULT;
                }
            }
        }
        return dataProvider;
    }

    /**
     * 注册权限数据源（业务调用，对齐 Sa-Token {@code SaManager.setStpInterface}）。
     *
     * @param provider 权限/角色数据源实现
     */
    public static void setDataProvider(SubjectDataProvider provider) {
        dataProvider = provider;
    }

    /**
     * \u83b7\u53d6\u7b56\u7565\u96c6\uff08\u9ed8\u8ba4\u5355\u4f8b\u515c\u5e95\uff09\u3002
     *
     * @return \u5f53\u524d\u7b56\u7565\u96c6
     */
    public static SubjectStrategy getStrategy() {
        if (Objects.isNull(strategy)) {
            synchronized (SubjectKit.class) {
                if (Objects.isNull(strategy)) {
                    strategy = SubjectStrategy.instance;
                }
            }
        }
        return strategy;
    }

    /**
     * 注册策略集。
     *
     * @param strategy 策略实现
     */
    public static void setStrategy(SubjectStrategy strategy) {
        SubjectKit.strategy = strategy;
    }

    // ==================== 身份与会话读取（便捷门面）====================

    /**
     * \u8bfb\u53d6\u5f53\u524d\u4f1a\u8bdd\u7684\u8ba4\u8bc1\u4e3b\u4f53\u3002
     * @param <T> \u8ba4\u8bc1\u4e3b\u4f53\u7c7b\u578b
     * @return \u8ba4\u8bc1\u4e3b\u4f53\uff0c\u672a\u83b7\u53d6\u5230\u6216\u7c7b\u578b\u4e0d\u5339\u914d\u65f6\u4e3a null
     */
    public static <T extends AuthPrincipal> T getPrincipal() {
        return getSubject().getPrincipal();
    }

    /**
     * \u8bfb\u53d6\u5f53\u524d\u4f1a\u8bdd\u7684\u8ba4\u8bc1\u4e3b\u4f53\u3002
     * @param <T> \u8ba4\u8bc1\u4e3b\u4f53\u7c7b\u578b
     * @param clazz \u8981\u6c42\u7684\u8ba4\u8bc1\u4e3b\u4f53\u7c7b\u578b\uff1b\u7c7b\u578b\u4e0d\u5339\u914d\u65f6\u8fd4\u56de null
     * @return \u8ba4\u8bc1\u4e3b\u4f53\uff0c\u672a\u83b7\u53d6\u5230\u6216\u7c7b\u578b\u4e0d\u5339\u914d\u65f6\u4e3a null
     */
    public static <T extends AuthPrincipal> T getPrincipal(Class<T> clazz) {
        T principal = getSubject().getPrincipal();
        if (Objects.nonNull(principal) && clazz.isAssignableFrom(principal.getClass())) {
            return principal;
        }
        return null;
    }

    /**
     * \u6309\u767b\u5f55\u6807\u8bc6\u8bfb\u53d6\u8ba4\u8bc1\u4e3b\u4f53\u3002
     * @param <T> \u8ba4\u8bc1\u4e3b\u4f53\u7c7b\u578b
     * @param loginId \u767b\u5f55\u6807\u8bc6
     * @return \u8ba4\u8bc1\u4e3b\u4f53\uff0c\u672a\u83b7\u53d6\u5230\u6216\u7c7b\u578b\u4e0d\u5339\u914d\u65f6\u4e3a null
     */
    public static <T extends AuthPrincipal> T getPrincipalByLoginId(Object loginId) {
        return getSubject().getPrincipalByLoginId(loginId);
    }

    /**
     * \u6309\u767b\u5f55\u6807\u8bc6\u8bfb\u53d6\u8ba4\u8bc1\u4e3b\u4f53\u3002
     * @param <T> \u8ba4\u8bc1\u4e3b\u4f53\u7c7b\u578b
     * @param loginId \u767b\u5f55\u6807\u8bc6
     * @param clazz \u8981\u6c42\u7684\u8ba4\u8bc1\u4e3b\u4f53\u7c7b\u578b\uff1b\u7c7b\u578b\u4e0d\u5339\u914d\u65f6\u8fd4\u56de null
     * @return \u8ba4\u8bc1\u4e3b\u4f53\uff0c\u672a\u83b7\u53d6\u5230\u6216\u7c7b\u578b\u4e0d\u5339\u914d\u65f6\u4e3a null
     */
    public static <T extends AuthPrincipal> T getPrincipalByLoginId(Object loginId, Class<T> clazz) {
        T principal = getSubject().getPrincipalByLoginId(loginId);
        if (Objects.nonNull(principal) && clazz.isAssignableFrom(principal.getClass())) {
            return principal;
        }
        return null;
    }

    /**
     * \u6309\u51ed\u8bc1\u8bfb\u53d6\u8ba4\u8bc1\u4e3b\u4f53\u3002
     * @param <T> \u8ba4\u8bc1\u4e3b\u4f53\u7c7b\u578b
     * @param tokenValue \u4f1a\u8bdd\u51ed\u8bc1
     * @return \u8ba4\u8bc1\u4e3b\u4f53\uff0c\u672a\u83b7\u53d6\u5230\u6216\u7c7b\u578b\u4e0d\u5339\u914d\u65f6\u4e3a null
     */
    public static <T extends AuthPrincipal> T getPrincipalByToken(String tokenValue) {
        return getSubject().getPrincipalByToken(tokenValue);
    }

    /**
     * \u6309\u51ed\u8bc1\u8bfb\u53d6\u8ba4\u8bc1\u4e3b\u4f53\u3002
     * @param <T> \u8ba4\u8bc1\u4e3b\u4f53\u7c7b\u578b
     * @param tokenValue \u4f1a\u8bdd\u51ed\u8bc1
     * @param clazz \u8981\u6c42\u7684\u8ba4\u8bc1\u4e3b\u4f53\u7c7b\u578b\uff1b\u7c7b\u578b\u4e0d\u5339\u914d\u65f6\u8fd4\u56de null
     * @return \u8ba4\u8bc1\u4e3b\u4f53\uff0c\u672a\u83b7\u53d6\u5230\u6216\u7c7b\u578b\u4e0d\u5339\u914d\u65f6\u4e3a null
     */
    public static <T extends AuthPrincipal> T getPrincipalByToken(String tokenValue, Class<T> clazz) {
        T principal = getSubject().getPrincipalByToken(tokenValue);
        if (Objects.nonNull(principal) && clazz.isAssignableFrom(principal.getClass())) {
            return principal;
        }
        return null;
    }

    /**
     * \u8bfb\u53d6\u5f53\u524d\u4f1a\u8bdd\u7684\u767b\u5f55\u6807\u8bc6\u3002
     * @return \u767b\u5f55\u6807\u8bc6
     */
    public static Object getLoginId() {
        return getSubject().getLoginId();
    }

    /**
     * \u4ee5\u5b57\u7b26\u4e32\u8bfb\u53d6\u767b\u5f55\u6807\u8bc6\u3002
     * @return \u5b57\u7b26\u4e32\u767b\u5f55\u6807\u8bc6
     */
    public static String getLoginIdAsString() {
        return getSubject().getLoginIdAsString();
    }

    /**
     * \u4ee5 Long \u8bfb\u53d6\u767b\u5f55\u6807\u8bc6\u3002
     * @return Long \u767b\u5f55\u6807\u8bc6
     */
    public static Long getLoginIdAsLong() {
        return getSubject().getLoginIdAsLong();
    }

    /**
     * \u4ee5 Integer \u8bfb\u53d6\u767b\u5f55\u6807\u8bc6\u3002
     * @return Integer \u767b\u5f55\u6807\u8bc6
     */
    public static Integer getLoginIdAsInteger() {
        return getSubject().getLoginIdAsInteger();
    }

    /**
     * \u8bfb\u53d6\u5f53\u524d\u7528\u6237\u6807\u8bc6\u3002
     * @return \u7528\u6237\u6807\u8bc6
     */
    public static Object getUserId() {
        return getSubject().getUserId();
    }

    /**
     * \u4ee5\u5b57\u7b26\u4e32\u8bfb\u53d6\u7528\u6237\u6807\u8bc6\u3002
     * @return \u5b57\u7b26\u4e32\u7528\u6237\u6807\u8bc6
     */
    public static String getUserIdAsString() {
        return getSubject().getUserIdAsString();
    }

    /**
     * \u4ee5 Long \u8bfb\u53d6\u7528\u6237\u6807\u8bc6\u3002
     * @return Long \u7528\u6237\u6807\u8bc6
     */
    public static Long getUserIdAsLong() {
        return getSubject().getUserIdAsLong();
    }

    /**
     * \u4ee5 Integer \u8bfb\u53d6\u7528\u6237\u6807\u8bc6\u3002
     * @return Integer \u7528\u6237\u6807\u8bc6
     */
    public static Integer getUserIdAsInteger() {
        return getSubject().getUserIdAsInteger();
    }

    /**
     * \u5224\u65ad\u5f53\u524d\u4f1a\u8bdd\u662f\u5426\u5df2\u8ba4\u8bc1\u3002
     * @return \u5df2\u8ba4\u8bc1\u65f6\u4e3a true
     */
    public static boolean isLogin() {
        return getSubject().isAuthenticated();
    }

    // ==================== 会话生命周期（便捷门面，对齐 StpUtil.login/logout）====================

    /**
     * 登录（建立会话）。
     *
     * @param request 登录请求
     * @return 会话凭证
     */
    public static String login(AuthRequest request) {
        return getSubject().login(request);
    }

    /**
     * 登出当前会话。
     */
    public static void logout() {
        getSubject().logout();
    }

    /**
     * 按 loginId 登出。
     *
     * @param loginId 账号 ID
     */
    public static void logout(Object loginId) {
        getSubject().logout(loginId);
    }

    /**
     * 踢人下线。
     *
     * @param loginId 账号 ID
     */
    public static void kickout(Object loginId) {
        getSubject().kickout(loginId);
    }

    /**
     * 刷新会话凭证。
     *
     * @return 新凭证
     */
    public static String refresh() {
        return getSubject().refresh();
    }

    /**
     * \u6821\u9a8c\u51ed\u8bc1\uff08\u4ec5\u6821\u9a8c\uff0c\u4e0d\u5efa\u4f1a\u8bdd\uff09\u3002
     * @param token \u51ed\u8bc1
     * @return \u8ba4\u8bc1\u4e3b\u4f53\uff0c\u6821\u9a8c\u5931\u8d25\u8fd4\u56de null
     *
     * @param <T> \u8ba4\u8bc1\u4e3b\u4f53\u7c7b\u578b
     */
    public static <T extends AuthPrincipal> T verify(String token) {
        return getSubject().verify(token);
    }

    // ==================== 权限与角色（便捷门面）====================

    /**
     * \u5224\u65ad\u5f53\u524d\u4f1a\u8bdd\u662f\u5426\u62e5\u6709\u6743\u9650\u3002
     * @param permission \u6743\u9650\u6807\u8bc6
     * @return \u62e5\u6709\u6743\u9650\u65f6\u4e3a true
     */
    public static boolean hasPermission(String permission) {
        return getSubject().isPermitted(permission);
    }

    /**
     * \u5224\u65ad\u6307\u5b9a\u4f1a\u8bdd\u662f\u5426\u62e5\u6709\u6743\u9650\u3002
     * @param loginId \u767b\u5f55\u6807\u8bc6
     * @param permission \u6743\u9650\u6807\u8bc6
     * @return \u62e5\u6709\u6743\u9650\u65f6\u4e3a true
     */
    public static boolean hasPermission(Object loginId, String permission) {
        return getSubject().isPermitted(loginId, permission);
    }

    /**
     * \u5224\u65ad\u5f53\u524d\u4f1a\u8bdd\u662f\u5426\u62e5\u6709\u89d2\u8272\u3002
     * @param roleIdentifier \u89d2\u8272\u6807\u8bc6
     * @return \u62e5\u6709\u89d2\u8272\u65f6\u4e3a true
     */
    public static boolean hasRole(String roleIdentifier) {
        return getSubject().hasRole(roleIdentifier);
    }

    /**
     * \u5224\u65ad\u6307\u5b9a\u4f1a\u8bdd\u662f\u5426\u62e5\u6709\u89d2\u8272\u3002
     * @param loginId \u767b\u5f55\u6807\u8bc6
     * @param roleIdentifier \u89d2\u8272\u6807\u8bc6
     * @return \u62e5\u6709\u89d2\u8272\u65f6\u4e3a true
     */
    public static boolean hasRole(Object loginId, String roleIdentifier) {
        return getSubject().hasRole(loginId, roleIdentifier);
    }

    /**
     * \u5224\u65ad\u5f53\u524d\u4f1a\u8bdd\u662f\u5426\u62e5\u6709\u4efb\u4e00\u6307\u5b9a\u89d2\u8272\u3002
     * @param roleIdentifiers \u89d2\u8272\u6807\u8bc6\u96c6\u5408
     * @return \u62e5\u6709\u4efb\u4e00\u89d2\u8272\u65f6\u4e3a true
     */
    public static boolean hasAnyRole(String... roleIdentifiers) {
        return getSubject().hasAnyRole(roleIdentifiers);
    }

    /**
     * \u5224\u65ad\u5f53\u524d\u4f1a\u8bdd\u662f\u5426\u62e5\u6709\u5168\u90e8\u6307\u5b9a\u89d2\u8272\u3002
     * @param roleIdentifiers \u89d2\u8272\u6807\u8bc6\u96c6\u5408
     * @return \u62e5\u6709\u5168\u90e8\u89d2\u8272\u65f6\u4e3a true
     */
    public static boolean hasAllRole(String... roleIdentifiers) {
        return getSubject().hasAllRole(roleIdentifiers);
    }

    // ==================== 会话数据（便捷门面）====================

    /**
     * \u8bbe\u7f6e\u5f53\u524d\u4f1a\u8bdd\u5c5e\u6027\u3002
     * @param key \u4f1a\u8bdd\u5c5e\u6027\u952e
     * @param value \u4f1a\u8bdd\u5c5e\u6027\u503c
     */
    public static void setAttribute(String key, Object value) {
        getSubject().setAttribute(key, value);
    }

    /**
     * \u8bfb\u53d6\u5f53\u524d\u4f1a\u8bdd\u5c5e\u6027\u3002
     * @param <V> \u4f1a\u8bdd\u5c5e\u6027\u503c\u7c7b\u578b
     * @param key \u4f1a\u8bdd\u5c5e\u6027\u952e
     * @return \u4f1a\u8bdd\u5c5e\u6027\u503c
     */
    public static <V> V getAttribute(String key) {
        return getSubject().getAttribute(key);
    }

    // ==================== 封禁（便捷门面）====================

    /**
     * \u6309\u767b\u5f55\u6807\u8bc6\u5c01\u7981\u8d26\u53f7\u3002
     * @param loginId \u767b\u5f55\u6807\u8bc6
     * @param timeout \u5c01\u7981\u65f6\u957f
     */
    public static void disable(Object loginId, long timeout) {
        getSubject().disable(loginId, timeout);
    }

    /**
     * \u5224\u65ad\u8d26\u53f7\u662f\u5426\u88ab\u5c01\u7981\u3002
     * @param loginId \u767b\u5f55\u6807\u8bc6
     * @return \u5df2\u5c01\u7981\u65f6\u4e3a true
     */
    public static boolean isDisabled(Object loginId) {
        return getSubject().isDisabled(loginId);
    }

    /**
     * \u89e3\u9664\u8d26\u53f7\u5c01\u7981\u3002
     * @param loginId \u767b\u5f55\u6807\u8bc6
     */
    public static void untieDisable(Object loginId) {
        getSubject().untieDisable(loginId);
    }

}
