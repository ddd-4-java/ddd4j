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
     * 获取权限数据源（默认空实现兜底）。
     * @return 当前数据提供者
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
     * 获取策略集（默认单例兜底）。
     * @return 当前策略集
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
     * 读取当前会话的认证主体。
     *
     * @param <T> 认证主体类型
     * @return 认证主体，未获取到或类型不匹配时为 null
     */
    public static <T extends AuthPrincipal> T getPrincipal() {
        return getSubject().getPrincipal();
    }

    /**
     * 读取当前会话的认证主体。
     *
     * @param <T> 认证主体类型
     * @param clazz 要求的认证主体类型；类型不匹配时返回 null
     * @return 认证主体，未获取到或类型不匹配时为 null
     */
    public static <T extends AuthPrincipal> T getPrincipal(Class<T> clazz) {
        T principal = getSubject().getPrincipal();
        if (Objects.nonNull(principal) && clazz.isAssignableFrom(principal.getClass())) {
            return principal;
        }
        return null;
    }

    /**
     * 按登录标识读取认证主体。
     *
     * @param <T> 认证主体类型
     * @param loginId 登录标识
     * @return 认证主体，未获取到或类型不匹配时为 null
     */
    public static <T extends AuthPrincipal> T getPrincipalByLoginId(Object loginId) {
        return getSubject().getPrincipalByLoginId(loginId);
    }

    /**
     * 按登录标识读取认证主体。
     *
     * @param <T> 认证主体类型
     * @param loginId 登录标识
     * @param clazz 要求的认证主体类型；类型不匹配时返回 null
     * @return 认证主体，未获取到或类型不匹配时为 null
     */
    public static <T extends AuthPrincipal> T getPrincipalByLoginId(Object loginId, Class<T> clazz) {
        T principal = getSubject().getPrincipalByLoginId(loginId);
        if (Objects.nonNull(principal) && clazz.isAssignableFrom(principal.getClass())) {
            return principal;
        }
        return null;
    }

    /**
     * 按凭证读取认证主体。
     *
     * @param <T> 认证主体类型
     * @param tokenValue 会话凭证
     * @return 认证主体，未获取到或类型不匹配时为 null
     */
    public static <T extends AuthPrincipal> T getPrincipalByToken(String tokenValue) {
        return getSubject().getPrincipalByToken(tokenValue);
    }

    /**
     * 按凭证读取认证主体。
     *
     * @param <T> 认证主体类型
     * @param tokenValue 会话凭证
     * @param clazz 要求的认证主体类型；类型不匹配时返回 null
     * @return 认证主体，未获取到或类型不匹配时为 null
     */
    public static <T extends AuthPrincipal> T getPrincipalByToken(String tokenValue, Class<T> clazz) {
        T principal = getSubject().getPrincipalByToken(tokenValue);
        if (Objects.nonNull(principal) && clazz.isAssignableFrom(principal.getClass())) {
            return principal;
        }
        return null;
    }

    /**
     * 读取当前会话的登录标识。
     *
     * @return 登录标识
     */
    public static Object getLoginId() {
        return getSubject().getLoginId();
    }

    /**
     * 以字符串读取登录标识。
     *
     * @return 字符串登录标识
     */
    public static String getLoginIdAsString() {
        return getSubject().getLoginIdAsString();
    }

    /**
     * 以 Long 读取登录标识。
     *
     * @return Long 登录标识
     */
    public static Long getLoginIdAsLong() {
        return getSubject().getLoginIdAsLong();
    }

    /**
     * 以 Integer 读取登录标识。
     *
     * @return Integer 登录标识
     */
    public static Integer getLoginIdAsInteger() {
        return getSubject().getLoginIdAsInteger();
    }

    /**
     * 读取当前用户标识。
     *
     * @return 用户标识
     */
    public static Object getUserId() {
        return getSubject().getUserId();
    }

    /**
     * 以字符串读取用户标识。
     *
     * @return 字符串用户标识
     */
    public static String getUserIdAsString() {
        return getSubject().getUserIdAsString();
    }

    /**
     * 以 Long 读取用户标识。
     *
     * @return Long 用户标识
     */
    public static Long getUserIdAsLong() {
        return getSubject().getUserIdAsLong();
    }

    /**
     * 以 Integer 读取用户标识。
     *
     * @return Integer 用户标识
     */
    public static Integer getUserIdAsInteger() {
        return getSubject().getUserIdAsInteger();
    }

    /**
     * 判断当前会话是否已认证。
     *
     * @return 已认证时为 true
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
     * 校验凭证（仅校验，不建会话）。
     *
     * @param token 凭证
     * @return 认证主体，校验失败返回 null
     * @param <T> 认证主体类型
     */
    public static <T extends AuthPrincipal> T verify(String token) {
        return getSubject().verify(token);
    }

    // ==================== 权限与角色（便捷门面）====================

    /**
     * 判断当前会话是否拥有权限。
     *
     * @param permission 权限标识
     * @return 拥有权限时为 true
     */
    public static boolean hasPermission(String permission) {
        return getSubject().isPermitted(permission);
    }

    /**
     * 判断指定会话是否拥有权限。
     *
     * @param loginId 登录标识
     * @param permission 权限标识
     * @return 拥有权限时为 true
     */
    public static boolean hasPermission(Object loginId, String permission) {
        return getSubject().isPermitted(loginId, permission);
    }

    /**
     * 判断当前会话是否拥有角色。
     *
     * @param roleIdentifier 角色标识
     * @return 拥有角色时为 true
     */
    public static boolean hasRole(String roleIdentifier) {
        return getSubject().hasRole(roleIdentifier);
    }

    /**
     * 判断指定会话是否拥有角色。
     *
     * @param loginId 登录标识
     * @param roleIdentifier 角色标识
     * @return 拥有角色时为 true
     */
    public static boolean hasRole(Object loginId, String roleIdentifier) {
        return getSubject().hasRole(loginId, roleIdentifier);
    }

    /**
     * 判断当前会话是否拥有任一指定角色。
     *
     * @param roleIdentifiers 角色标识集合
     * @return 拥有任一角色时为 true
     */
    public static boolean hasAnyRole(String... roleIdentifiers) {
        return getSubject().hasAnyRole(roleIdentifiers);
    }

    /**
     * 判断当前会话是否拥有全部指定角色。
     *
     * @param roleIdentifiers 角色标识集合
     * @return 拥有全部角色时为 true
     */
    public static boolean hasAllRole(String... roleIdentifiers) {
        return getSubject().hasAllRole(roleIdentifiers);
    }

    // ==================== 会话数据（便捷门面）====================

    /**
     * 设置当前会话属性。
     *
     * @param key 会话属性键
     * @param value 会话属性值
     */
    public static void setAttribute(String key, Object value) {
        getSubject().setAttribute(key, value);
    }

    /**
     * 读取当前会话属性。
     *
     * @param <V> 会话属性值类型
     * @param key 会话属性键
     * @return 会话属性值
     */
    public static <V> V getAttribute(String key) {
        return getSubject().getAttribute(key);
    }

    // ==================== 封禁（便捷门面）====================

    /**
     * 按登录标识封禁账号。
     *
     * @param loginId 登录标识
     * @param timeout 封禁时长
     */
    public static void disable(Object loginId, long timeout) {
        getSubject().disable(loginId, timeout);
    }

    /**
     * 判断账号是否被封禁。
     *
     * @param loginId 登录标识
     * @return 已封禁时为 true
     */
    public static boolean isDisabled(Object loginId) {
        return getSubject().isDisabled(loginId);
    }

    /**
     * 解除账号封禁。
     *
     * @param loginId 登录标识
     */
    public static void untieDisable(Object loginId) {
        getSubject().untieDisable(loginId);
    }

}
