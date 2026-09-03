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

    public static <T extends AuthPrincipal> T getPrincipal() {
        return getSubject().getPrincipal();
    }

    public static <T extends AuthPrincipal> T getPrincipal(Class<T> clazz) {
        T principal = getSubject().getPrincipal();
        if (Objects.nonNull(principal) && clazz.isAssignableFrom(principal.getClass())) {
            return principal;
        }
        return null;
    }

    public static <T extends AuthPrincipal> T getPrincipalByLoginId(Object loginId) {
        return getSubject().getPrincipalByLoginId(loginId);
    }

    public static <T extends AuthPrincipal> T getPrincipalByLoginId(Object loginId, Class<T> clazz) {
        T principal = getSubject().getPrincipalByLoginId(loginId);
        if (Objects.nonNull(principal) && clazz.isAssignableFrom(principal.getClass())) {
            return principal;
        }
        return null;
    }

    public static <T extends AuthPrincipal> T getPrincipalByToken(String tokenValue) {
        return getSubject().getPrincipalByToken(tokenValue);
    }

    public static <T extends AuthPrincipal> T getPrincipalByToken(String tokenValue, Class<T> clazz) {
        T principal = getSubject().getPrincipalByToken(tokenValue);
        if (Objects.nonNull(principal) && clazz.isAssignableFrom(principal.getClass())) {
            return principal;
        }
        return null;
    }

    public static Object getLoginId() {
        return getSubject().getLoginId();
    }

    public static String getLoginIdAsString() {
        return getSubject().getLoginIdAsString();
    }

    public static Long getLoginIdAsLong() {
        return getSubject().getLoginIdAsLong();
    }

    public static Integer getLoginIdAsInteger() {
        return getSubject().getLoginIdAsInteger();
    }

    public static Object getUserId() {
        return getSubject().getUserId();
    }

    public static String getUserIdAsString() {
        return getSubject().getUserIdAsString();
    }

    public static Long getUserIdAsLong() {
        return getSubject().getUserIdAsLong();
    }

    public static Integer getUserIdAsInteger() {
        return getSubject().getUserIdAsInteger();
    }

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
     */
    public static <T extends AuthPrincipal> T verify(String token) {
        return getSubject().verify(token);
    }

    // ==================== 权限与角色（便捷门面）====================

    public static boolean hasPermission(String permission) {
        return getSubject().isPermitted(permission);
    }

    public static boolean hasPermission(Object loginId, String permission) {
        return getSubject().isPermitted(loginId, permission);
    }

    public static boolean hasRole(String roleIdentifier) {
        return getSubject().hasRole(roleIdentifier);
    }

    public static boolean hasRole(Object loginId, String roleIdentifier) {
        return getSubject().hasRole(loginId, roleIdentifier);
    }

    public static boolean hasAnyRole(String... roleIdentifiers) {
        return getSubject().hasAnyRole(roleIdentifiers);
    }

    public static boolean hasAllRole(String... roleIdentifiers) {
        return getSubject().hasAllRole(roleIdentifiers);
    }

    // ==================== 会话数据（便捷门面）====================

    public static void setAttribute(String key, Object value) {
        getSubject().setAttribute(key, value);
    }

    public static <V> V getAttribute(String key) {
        return getSubject().getAttribute(key);
    }

    // ==================== 封禁（便捷门面）====================

    public static void disable(Object loginId, long timeout) {
        getSubject().disable(loginId, timeout);
    }

    public static boolean isDisabled(Object loginId) {
        return getSubject().isDisabled(loginId);
    }

    public static void untieDisable(Object loginId) {
        getSubject().untieDisable(loginId);
    }

}
