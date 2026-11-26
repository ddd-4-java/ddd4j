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
package io.ddd4j.auth.satoken.subject;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaCookieConfig;
import cn.dev33.satoken.exception.DisableServiceException;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.SaTokenException;
import cn.dev33.satoken.jwt.SaJwtUtil;
import cn.dev33.satoken.jwt.StpLogicJwtForSimple;
import cn.dev33.satoken.jwt.exception.SaJwtException;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.dev33.satoken.stp.parameter.enums.SaLogoutMode;
import cn.dev33.satoken.stp.parameter.enums.SaReplacedLoginExitMode;
import cn.dev33.satoken.stp.parameter.enums.SaReplacedRange;
import io.ddd4j.auth.satoken.config.Ddd4jStpLogicJwtForSimple;
import io.ddd4j.core.auth.*;
import io.ddd4j.core.auth.session.AuthCookieConfig;
import io.ddd4j.core.auth.session.AuthSessionConfig;
import io.ddd4j.core.exception.AccountDisabledException;
import io.ddd4j.core.exception.NotLoggedInException;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.util.SubjectKit;
import io.ddd4j.kit.lang.StrKit;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;

/**
 * 基于 Sa-Token 的 {@link Subject} 实现（纯 Java，零 Spring 依赖）。
 *
 * <p>将 ddd4j 的 {@link Subject} 契约委托给 Sa-Token 的 {@link StpUtil}。
 * 会话生命周期（login/logout/kickout/refresh）直接委托 Sa-Token。
 * 权限/角色校验统一委托 {@link SubjectKit#getDataProvider()} 数据源 SPI，
 * 保证三鉴权实现行为一致。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j
public class SaTokenSubject implements Subject {

    /**
     * 登录时存入 SaSession 的 principal 键
     */
    public static final String PRINCIPAL_KEY = "principal";

    private final String realm;

    /**
     * 创建使用默认账号体系的主体。
     */
    public SaTokenSubject() {
        this(null);
    }

    /**
     * 创建绑定到指定账号体系的主体。
     *
     * @param realm 账号体系，空值表示使用默认账号体系
     */
    public SaTokenSubject(String realm) {
        this.realm = realm;
    }

    /**
     * 框架无关枚举映射：{@link AuthLogoutMode} -> sa-token 的 {@code SaLogoutMode}。
     */
    private static SaLogoutMode toSaLogoutMode(AuthLogoutMode mode) {
        if (Objects.isNull(mode)) {
            return SaLogoutMode.LOGOUT;
        }
        switch (mode) {
            case KICKOUT:
                return SaLogoutMode.KICKOUT;
            case REPLACED:
                return SaLogoutMode.REPLACED;
            default:
                return SaLogoutMode.LOGOUT;
        }
    }

    private static SaCookieConfig toSaCookieConfig(AuthCookieConfig auth) {
        SaCookieConfig sa = new SaCookieConfig();
        if (Objects.nonNull(auth.getDomain())) {
            sa.setDomain(auth.getDomain());
        }
        if (Objects.nonNull(auth.getPath())) {
            sa.setPath(auth.getPath());
        }
        if (Objects.nonNull(auth.isSecure())) {
            sa.setSecure(auth.isSecure());
        }
        if (Objects.nonNull(auth.isHttpOnly())) {
            sa.setHttpOnly(auth.isHttpOnly());
        }
        if (Objects.nonNull(auth.getSameSite())) {
            sa.setSameSite(auth.getSameSite());
        }
        return sa;
    }

    // ==================== 身份与会话读取 ====================

    private StpLogic currentLogic() {
        return stpLogic(realm);
    }

    /**
     * 根据账号体系返回对应的 StpLogic（对齐多账号体系）。
     *
     * @param realm 账号体系标识，null/空返回默认
     * @return StpLogic
     */
    protected StpLogic stpLogic(String realm) {
        if (StrKit.isBlank(realm)) {
            return StpUtil.stpLogic;
        }
        return SaManager.getStpLogic(realm, true);
    }

    @Override
    public <T extends AuthPrincipal> T getPrincipal() {
        StpLogic logic = currentLogic();
        if (!logic.isLogin()) {
            return null;
        }
        SaSession session = logic.getSession(false);
        if (Objects.isNull(session)) {
            return null;
        }
        return (T) session.get(PRINCIPAL_KEY);
    }

    // ==================== 会话生命周期（委托 StpUtil）====================

    @Override
    public <T extends AuthPrincipal> T getPrincipalByLoginId(Object loginId) {
        SaSession session = currentLogic().getSessionByLoginId(loginId, false);
        if (Objects.isNull(session)) {
            return null;
        }
        return (T) session.get(PRINCIPAL_KEY);
    }

    @Override
    public <T extends AuthPrincipal> T getPrincipalByToken(String tokenValue) {
        return verify(tokenValue);
    }

    @Override
    public String login(AuthRequest request) {
        String requestedRealm = request.getRealm();
        if (StrKit.isNotBlank(realm) && StrKit.isNotBlank(requestedRealm)
                && !Objects.equals(realm, requestedRealm)) {
            throw new IllegalArgumentException("Login realm does not match the bound subject");
        }
        StpLogic logic = StrKit.isBlank(requestedRealm) ? currentLogic() : stpLogic(requestedRealm);
        AuthSessionConfig cfg = Objects.requireNonNullElseGet(request.getSessionConfig(), AuthSessionConfig::new);

        SaLoginParameter param = logic.createSaLoginParameter();
        if (cfg.hasTimeoutOverride()) {
            param.setTimeout(cfg.getTimeout());
        }
        if (Objects.nonNull(cfg.getDeviceType())) {
            param.setDeviceType(cfg.getDeviceType());
        }
        param.setDeviceId(cfg.getDeviceId());
        param.setToken(cfg.getPresetToken());
        param.setRightNowCreateTokenSession(cfg.isCreateTokenSessionNow());
        if (Objects.nonNull(request.getExtra()) && !request.getExtra().isEmpty()) {
            param.setExtraData(request.getExtra());
        }
        if (Objects.nonNull(cfg.getActiveTimeout())) {
            param.setActiveTimeout(cfg.getActiveTimeout());
        }
        param.setIsConcurrent(cfg.isConcurrent());
        param.setIsShare(cfg.isShare());
        param.setMaxLoginCount(cfg.getMaxLoginCount());
        param.setOverflowLogoutMode(toSaLogoutMode(cfg.getOverflowLogoutMode()));
        if (Objects.nonNull(cfg.getReplacedLoginExitMode())) {
            // Ddd4j 命名的是保留端，Sa-Token 命名的是退出端。
            param.setReplacedLoginExitMode(cfg.getReplacedLoginExitMode() == AuthReplacedLoginExitMode.NEW_DEVICE
                    ? SaReplacedLoginExitMode.OLD_DEVICE : SaReplacedLoginExitMode.NEW_DEVICE);
        }
        if (Objects.nonNull(cfg.getReplacedRange())) {
            param.setReplacedRange(cfg.getReplacedRange() == AuthReplacedRange.ALL_DEVICE_TYPE
                    ? SaReplacedRange.ALL_DEVICE_TYPE : SaReplacedRange.CURR_DEVICE_TYPE);
        }
        param.setIsWriteHeader(cfg.isWriteTokenToHeader());
        if (Objects.nonNull(cfg.getCookie())) {
            param.setCookie(toSaCookieConfig(cfg.getCookie()));
        }

        // 调用 Sa-Token 登录，建立会话（异常映射到 ddd4j 通用 AuthException）
        try {
            logic.login(request.getLoginIdAsString(), param);
        } catch (NotLoginException e) {
            // sa-token 登录失败（含"账号无效/凭证错误"）：code 10011 -> NotLoggedInException
            // （业务侧通过 SaErrorCode 区分具体原因，本类做通用映射）
            throw new NotLoggedInException("Sa-Token login failed: " + e.getMessage(), e);
        } catch (DisableServiceException e) {
            // sa-token 账号被禁用：code 10011 -> AccountDisabled
            throw new AccountDisabledException("Sa-Token account disabled: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            // sa-token 其他异常：兜底
            throw new NotLoggedInException("Sa-Token login error: " + e.getMessage(), e);
        }

        String token = logic.getTokenValue();

        // 登录后将 principal 存入 SaSession
        if (Objects.nonNull(request.getPrincipal())) {
            SaSession session = logic.getSessionByLoginId(request.getLoginId(), true);
            session.set(PRINCIPAL_KEY, request.getPrincipal());
        }
        return token;
    }

    @Override
    public void logout() {
        StpLogic logic = currentLogic();
        logic.logout(logic.createSaLogoutParameter().setIsKeepFreezeOps(true));
    }

    @Override
    public void logout(Object loginId) {
        currentLogic().logout(loginId);
    }

    @Override
    public void kickout(Object loginId) {
        currentLogic().kickout(loginId);
    }

    @Override
    public String refresh() {
        StpLogic logic = currentLogic();
        try {
            String token = logic.getTokenValue();
            Object loginId = resolveVerifiedLoginId(token, logic);
            if (Objects.isNull(loginId)) {
                throw new NotLoggedInException("Sa-Token session is invalid or inactive");
            }
            SaSession session = logic.getSessionByLoginId(loginId, false);
            if (Objects.isNull(session) || Objects.isNull(session.getTerminal(token))) {
                throw new NotLoggedInException("Sa-Token session has no matching terminal");
            }
            logic.renewTimeout(logic.getConfigOrGlobal().getTimeout());
            if (Objects.isNull(logic.getLoginIdByToken(token))) {
                throw new NotLoggedInException("Sa-Token session is no longer active");
            }
            return token;
        } catch (SaTokenException e) {
            throw new NotLoggedInException("Sa-Token session renewal failed", e);
        }
    }

    @Override
    public <T extends AuthPrincipal> T verify(String token) {
        try {
            Object loginId = resolveVerifiedLoginId(token, currentLogic());
            if (Objects.isNull(loginId)) {
                return null;
            }
            return getPrincipalByLoginId(loginId);
        } catch (RuntimeException exception) {
            // 认证依赖不可用时必须拒绝，而不是从当前线程主体回退。
            log.warn("Sa-Token verification failed closed: {}", exception.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 解析并验证凭证对应的登录账号。
     *
     * <p>Session 模式通过服务端令牌映射校验有效性与撤销。JWT Simple
     * 同时校验签名、账号体系、签发方、受众与服务端映射，映射 TTL 决定会话到期。
     *
     * <p>无状态 JWT 不在此实现的支持范围：它不保存 {@link AuthPrincipal}，而 {@link Subject}
     * 必须返回完整主体。应用如需无状态 JWT，应提供可按 loginId 加载主体的 Subject 实现。
     *
     * @param token 请求携带的原始 token
     * @param logic 当前账号体系的逻辑对象
     * @return 已验证的登录账号 ID；校验失败或凭证为空时返回 {@code null}
     */
    private Object resolveVerifiedLoginId(String token, StpLogic logic) {
        if (StrKit.isBlank(token)) {
            return null;
        }
        if (logic instanceof StpLogicJwtForSimple) {
            StpLogicJwtForSimple jwtLogic = (StpLogicJwtForSimple) logic;
            // JWT Simple 必须经过 ddd4j 配置器，以固定 issuer/audience 与撤销语义。
            if (!(jwtLogic instanceof Ddd4jStpLogicJwtForSimple)) {
                return null;
            }
            Object jwtLoginId = jwtLoginId(token, jwtLogic);
            if (Objects.isNull(jwtLoginId)) {
                return null;
            }
            Object sessionLoginId = jwtLogic.getLoginIdByToken(token);
            if (Objects.isNull(sessionLoginId)
                    || !Objects.equals(String.valueOf(jwtLoginId), String.valueOf(sessionLoginId))) {
                return null;
            }
            return sessionLoginId;
        }
        return logic.getLoginIdByToken(token);
    }

    private Object jwtLoginId(String token, StpLogicJwtForSimple logic) {
        try {
            if (logic instanceof Ddd4jStpLogicJwtForSimple) {
                Ddd4jStpLogicJwtForSimple secureLogic = (Ddd4jStpLogicJwtForSimple) logic;
                if (!secureLogic.hasExpectedClaims(token)) {
                    return null;
                }
            }
            // JWT Simple 的有效期由 Sa-Token 服务端映射管理，不能误用会要求 eff 声明的 getPayloads(...).
            return SaJwtUtil.getPayloadsNotCheck(token, logic.getLoginType(), logic.jwtSecretKey())
                    .get(SaJwtUtil.LOGIN_ID);
        } catch (SaJwtException | IllegalArgumentException exception) {
            return null;
        }
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
        return currentLogic().isLogin();
    }

    @Override
    public boolean isAuthenticated(Object loginId) {
        return currentLogic().isLogin(loginId);
    }

    @Override
    public boolean isRemembered() {
        return false;
    }

    @Override
    public boolean isTrustDeviceId(String deviceId) {
        StpLogic logic = currentLogic();
        return logic.isTrustDeviceId(logic.getLoginId(), deviceId);
    }

    @Override
    public boolean isTrustDeviceId(Object userId, String deviceId) {
        return currentLogic().isTrustDeviceId(userId, deviceId);
    }

    @Override
    public Object getLoginId() {
        return currentLogic().getLoginId();
    }

    @Override
    public Object getUserId() {
        AuthPrincipal principal = getPrincipal();
        return Objects.nonNull(principal) ? principal.getUserId() : null;
    }

    @Override
    public Object getOrgId() {
        AuthPrincipal principal = getPrincipal();
        return Objects.nonNull(principal) ? principal.getOrgId() : null;
    }

    @Override
    public Object getRoleId() {
        AuthPrincipal principal = getPrincipal();
        return Objects.nonNull(principal) ? principal.getRoleId() : null;
    }

    @Override
    public Object getExtra(String tokenValue, String key) {
        return currentLogic().getExtra(tokenValue, key);
    }

    // ==================== 封禁（委托 Sa-Token）====================

    @Override
    public void disable(Object loginId, long timeout) {
        currentLogic().disable(loginId, timeout);
    }

    @Override
    public boolean isDisabled(Object loginId) {
        return currentLogic().isDisable(loginId);
    }

    @Override
    public void untieDisable(Object loginId) {
        currentLogic().untieDisable(loginId);
    }

}
