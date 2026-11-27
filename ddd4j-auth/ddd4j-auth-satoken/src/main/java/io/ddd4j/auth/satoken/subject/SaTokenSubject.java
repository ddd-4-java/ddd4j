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

import cn.dev33.satoken.exception.SaTokenException;
import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaCookieConfig;
import cn.dev33.satoken.exception.DisableServiceException;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.jwt.SaJwtUtil;
import cn.dev33.satoken.jwt.StpLogicJwtForSimple;
import cn.dev33.satoken.jwt.exception.SaJwtException;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import io.ddd4j.auth.satoken.config.Ddd4jStpLogicJwtForSimple;
import io.ddd4j.auth.satoken.util.StpKit;
import io.ddd4j.core.auth.AuthLogoutMode;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.auth.AuthRequest;
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

    /** 当前主体使用的认证域，空值对应默认域。 */
    private final String realm;

    /**
     * \u6846\u67b6\u65e0\u5173\u679a\u4e3e\u6620\u5c04\uff1a{@link AuthLogoutMode} -> sa-token \u7684 {@code SaLogoutMode}\u3002
     *  <p>sa-token 1.42 \u5e76\u4e0d\u66b4\u9732 {@code ReplacedLoginExitMode}/{@code ReplacedRange} \u679a\u4e3e\u7c7b\uff0c
     *  \u8fd9\u4e24\u4e2a\u5b57\u6bb5\u6211\u4eec\u4ecd\u5199\u5165 {@link AuthSessionConfig}\uff0c\u4f46\u5177\u4f53\u751f\u6548\u9700\u8981\u5728\u66f4\u65b0\u7248 sa-token \u4e0b\u8865\u5168\u3002
     *
     * @param mode DDD4J \u6ce8\u9500\u6a21\u5f0f
     * @return \u5bf9\u5e94\u7684 Sa-Token \u6ce8\u9500\u6a21\u5f0f
     */
    private static cn.dev33.satoken.stp.parameter.enums.SaLogoutMode toSaLogoutMode(AuthLogoutMode mode) {
        if (Objects.isNull(mode)) {
            return cn.dev33.satoken.stp.parameter.enums.SaLogoutMode.LOGOUT;
        }
        switch (mode) {
            case KICKOUT:
                return cn.dev33.satoken.stp.parameter.enums.SaLogoutMode.KICKOUT;
            case REPLACED:
                return cn.dev33.satoken.stp.parameter.enums.SaLogoutMode.REPLACED;
            default:
                return cn.dev33.satoken.stp.parameter.enums.SaLogoutMode.LOGOUT;
        }
    }

    // ==================== 身份与会话读取 ====================

    /**
     * \u628a\u660e\u786e\u8bbe\u7f6e\u7684 DDD4J Cookie \u5c5e\u6027\u8f6c\u6362\u4e3a Sa-Token \u539f\u751f\u914d\u7f6e\u3002
     * @param auth DDD4J \u767b\u5f55\u4f7f\u7528\u7684 Cookie \u914d\u7f6e
     * @return \u76f4\u63a5\u7528\u4e8e\u539f\u751f\u767b\u5f55\u7684 Cookie \u914d\u7f6e
     */
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

    /**
     * 读取当前会话的认证主体。
     *
     * <p>Sa-Token 实现调用 <code>StpUtil.isLogin()</code>。</p>
     *
     * @param <T> AuthPrincipal 子类型
     * @return 当前会话绑定的主体
     */
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

    /**
     * 查询指定登录标识关联的认证主体。
     *
     * <p>Sa-Token 实现调用 <code>StpUtil.getSessionByLoginId(loginId, false)</code>。</p>
     *
     * @param <T> AuthPrincipal 子类型
     * @param loginId 登录账号 ID
     * @return 给定登录标识关联的主体
     */
    @Override
    public <T extends AuthPrincipal> T getPrincipalByLoginId(Object loginId) {
        SaSession session = currentLogic().getSessionByLoginId(loginId, false);
        if (Objects.isNull(session)) {
            return null;
        }
        return (T) session.get(PRINCIPAL_KEY);
    }

    /**
     * 查询指定令牌关联的认证主体。
     *
     * <p>Sa-Token 实现调用 <code>verify(tokenValue)</code>。</p>
     *
     * @param <T> AuthPrincipal 子类型
     * @param tokenValue 会话凭证（Token / SessionId）
     * @return 给定令牌关联的主体
     */
    @Override
    public <T extends AuthPrincipal> T getPrincipalByToken(String tokenValue) {
        return verify(tokenValue);
    }

    /**
     * 以传入的凭据启动认证会话。
     *
     * <p>Sa-Token 实现调用 <code>stpLogic(request.getRealm())</code>。</p>
     *
     * @param request 登录请求（loginId + 密码 + 扩展信息 + 有效期）
     * @return 实际返回<code>token</code>
     */
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

    /**
     * 结束由当前调用指定的认证会话。
     *
     * <p>Sa-Token 实现调用 <code>StpUtil.stpLogic.logout()</code>。</p>
     */
    @Override
    public void logout() {
        StpLogic logic = currentLogic();
        logic.logout(logic.createSaLogoutParameter().setIsKeepFreezeOps(true));
    }

    /**
     * 结束由当前调用指定的认证会话。
     *
     * <p>Sa-Token 实现调用 <code>StpUtil.stpLogic.logout(loginId)</code>。</p>
     *
     * @param loginId 账号 ID
     */
    @Override
    public void logout(Object loginId) {
        currentLogic().logout(loginId);
    }

    /**
     * 强制结束指定身份关联的登录会话。
     *
     * <p>Sa-Token 实现调用 <code>StpUtil.stpLogic.kickout(loginId)</code>。</p>
     *
     * @param loginId 账号 ID
     */
    @Override
    public void kickout(Object loginId) {
        currentLogic().kickout(loginId);
    }

    /**
     * 刷新当前实现所持有的数据或状态。
     *
     * <p>Sa-Token 实现调用 <code>StpUtil.stpLogic.renewTimeout(StpUtil.stpLogic.getConfigOrGlobal().getTimeout())</code>。</p>
     *
     * @return 实际返回<code>StpUtil.stpLogic.getTokenValue()</code>
     */
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

    /**
     * 校验当前调用传入的数据或凭据。
     *
     * <p>Sa-Token 实现调用 <code>resolveVerifiedLoginId(token)</code>。</p>
     *
     * @param <T> AuthPrincipal 子类型
     * @param token 会话凭证
     * @return 根据执行路径返回<code>null</code>、<code>getPrincipalByLoginId(loginId)</code>
     */
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
     * <p>普通 Session 模式由 Sa-Token 的 token-to-loginId 映射完成有效性与撤销校验。JWT Simple
     * 模式额外通过 {@link SaJwtUtil} 校验签名和账号体系，再检查服务端映射。Sa-Token 1.45 的 JWT
     * Simple 模式不把过期时间写入 JWT，而是由该映射的 TTL 负责过期和撤销，因此两项校验都不可省略。
     *
     * <p>无状态 JWT 不在此实现的支持范围：它不保存 {@link AuthPrincipal}，而 {@link Subject}
     * 必须返回完整主体。应用如需无状态 JWT，应提供可按 loginId 加载主体的 Subject 实现。
     *
     * @param token 请求携带的原始 token
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

    /**
     * \u7528\u5f53\u524d\u767b\u5f55\u903b\u8f91\u68c0\u67e5 JWT \u540e\u8bfb\u53d6\u767b\u5f55\u6807\u8bc6\u3002
     * @param token \u5f85\u68c0\u67e5\u6216\u8bfb\u53d6\u4e0a\u4e0b\u6587\u7684\u8bbf\u95ee\u4ee4\u724c
     * @param logic \u5bf9\u5e94\u8d26\u53f7\u57df\u7684\u539f\u751f\u767b\u5f55\u903b\u8f91
     * @return \u6709\u6548\u4ee4\u724c\u4e2d\u7684\u767b\u5f55\u6807\u8bc6\uff0c\u65e0\u6548\u65f6\u4e3a\u7a7a
     */
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

    /**
     * 检查身份是否拥有指定权限。
     *
     * <p>Sa-Token 实现调用 <code>getPrincipal()</code>。</p>
     *
     * @param permission 权限标识（如 {@code "order:create"}）
     * @return 请求的权限被授予时为真
     */
    @Override
    public boolean isPermitted(String permission) {
        AuthPrincipal principal = getPrincipal();
        if (Objects.isNull(principal)) {
            return false;
        }
        List<String> perms = SubjectKit.getDataProvider().getPermissionList(principal);
        return SubjectKit.getStrategy().hasElement.apply(perms, permission);
    }

    /**
     * 检查身份是否拥有指定权限。
     *
     * <p>Sa-Token 实现调用 <code>getPrincipalByLoginId(loginId)</code>。</p>
     *
     * @param loginId 用户账号 ID
     * @param permission 权限标识
     * @return 请求的权限被授予时为真
     */
    @Override
    public boolean isPermitted(Object loginId, String permission) {
        AuthPrincipal principal = getPrincipalByLoginId(loginId);
        if (Objects.isNull(principal)) {
            return false;
        }
        List<String> perms = SubjectKit.getDataProvider().getPermissionList(principal);
        return SubjectKit.getStrategy().hasElement.apply(perms, permission);
    }

    /**
     * 检查身份是否拥有指定权限。
     *
     * <p>Sa-Token 实现调用 <code>getPrincipal()</code>。</p>
     *
     * @param permissions 权限标识列表
     * @return 请求的权限被授予时为真
     */
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

    /**
     * 检查身份是否拥有指定权限。
     *
     * <p>Sa-Token 实现调用 <code>getPrincipalByLoginId(loginId)</code>。</p>
     *
     * @param loginId 用户账号 ID
     * @param permissions 权限标识列表
     * @return 请求的权限被授予时为真
     */
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

    /**
     * 检查身份是否具有给定权限中的任意一项。
     *
     * <p>Sa-Token 实现调用 <code>getPrincipal()</code>。</p>
     *
     * @param permissions 权限标识列表
     * @return 至少有一项请求权限被授予时为真
     */
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

    /**
     * 检查身份是否具有给定权限中的任意一项。
     *
     * <p>Sa-Token 实现调用 <code>getPrincipalByLoginId(loginId)</code>。</p>
     *
     * @param loginId 用户账号 ID
     * @param permissions 权限标识列表
     * @return 至少有一项请求权限被授予时为真
     */
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

    /**
     * 检查身份是否具有全部给定权限。
     *
     * <p>Sa-Token 实现调用 <code>getPrincipal()</code>。</p>
     *
     * @param permissions 权限标识列表
     * @return 所有请求权限都被授予时为真
     */
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

    /**
     * 检查身份是否具有全部给定权限。
     *
     * <p>Sa-Token 实现调用 <code>getPrincipalByLoginId(loginId)</code>。</p>
     *
     * @param loginId 用户账号 ID
     * @param permissions 权限标识列表
     * @return 所有请求权限都被授予时为真
     */
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

    /**
     * 检查身份是否具有指定角色。
     *
     * <p>Sa-Token 实现调用 <code>getPrincipal()</code>。</p>
     *
     * @param roleIdentifier 角色标识（角色 ID 或角色名）
     * @return 具有指定角色时为真
     */
    @Override
    public boolean hasRole(String roleIdentifier) {
        AuthPrincipal principal = getPrincipal();
        if (Objects.isNull(principal)) {
            return false;
        }
        List<String> roles = SubjectKit.getDataProvider().getRoleList(principal);
        return SubjectKit.getStrategy().hasElement.apply(roles, roleIdentifier);
    }

    /**
     * 检查身份是否具有指定角色。
     *
     * <p>Sa-Token 实现调用 <code>getPrincipalByLoginId(loginId)</code>。</p>
     *
     * @param loginId 用户账号 ID
     * @param roleIdentifier 角色标识
     * @return 具有指定角色时为真
     */
    @Override
    public boolean hasRole(Object loginId, String roleIdentifier) {
        AuthPrincipal principal = getPrincipalByLoginId(loginId);
        if (Objects.isNull(principal)) {
            return false;
        }
        List<String> roles = SubjectKit.getDataProvider().getRoleList(principal);
        return SubjectKit.getStrategy().hasElement.apply(roles, roleIdentifier);
    }

    /**
     * 按输入顺序逐项判断身份的角色归属。
     *
     * <p>Sa-Token 实现调用 <code>getPrincipal()</code>。</p>
     *
     * @param roleIdentifiers 角色标识列表
     * @return 实际返回<code>result</code>
     */
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

    /**
     * 按输入顺序逐项判断身份的角色归属。
     *
     * <p>Sa-Token 实现调用 <code>getPrincipalByLoginId(loginId)</code>。</p>
     *
     * @param loginId 用户账号 ID
     * @param roleIdentifiers 角色标识列表
     * @return 实际返回<code>result</code>
     */
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

    /**
     * 检查身份是否具有给定角色中的任意一个。
     *
     * <p>Sa-Token 实现调用 <code>getPrincipal()</code>。</p>
     *
     * @param roleIdentifiers 角色标识列表
     * @return 至少具有一个指定角色时为真
     */
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

    /**
     * 检查身份是否具有给定角色中的任意一个。
     *
     * <p>Sa-Token 实现调用 <code>getPrincipalByLoginId(loginId)</code>。</p>
     *
     * @param loginId 用户账号 ID
     * @param roleIdentifiers 角色标识列表
     * @return 至少具有一个指定角色时为真
     */
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

    /**
     * 检查身份是否具有全部给定角色。
     *
     * <p>Sa-Token 实现调用 <code>getPrincipal()</code>。</p>
     *
     * @param roleIdentifiers 角色标识列表
     * @return 具有全部指定角色时为真
     */
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

    /**
     * 检查身份是否具有全部给定角色。
     *
     * <p>Sa-Token 实现调用 <code>getPrincipalByLoginId(loginId)</code>。</p>
     *
     * @param loginId 用户账号 ID
     * @param roleIdentifiers 角色标识列表
     * @return 具有全部指定角色时为真
     */
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

    /**
     * 检查当前会话是否已经认证。
     *
     * <p>Sa-Token 实现调用 <code>StpUtil.isLogin()</code>。</p>
     *
     * @return 当前会话通过身份认证时为真
     */
    @Override
    public boolean isAuthenticated() {
        return currentLogic().isLogin();
    }

    /**
     * 检查当前会话是否已经认证。
     *
     * <p>Sa-Token 实现调用 <code>StpUtil.isLogin(loginId)</code>。</p>
     *
     * @param loginId 用户账号 ID
     * @return 当前会话通过身份认证时为真
     */
    @Override
    public boolean isAuthenticated(Object loginId) {
        return currentLogic().isLogin(loginId);
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
     * <p>Sa-Token 实现调用 <code>StpUtil.isTrustDeviceId(StpKit.getUserIdAsLong(), deviceId)</code>。</p>
     *
     * @param deviceId 设备标识
     * @return 实际返回<code>StpUtil.isTrustDeviceId(StpKit.getUserIdAsLong(), deviceId)</code>
     */
    @Override
    public boolean isTrustDeviceId(String deviceId) {
        StpLogic logic = currentLogic();
        return logic.isTrustDeviceId(logic.getLoginId(), deviceId);
    }

    /**
     * 检查当前认证实现是否将给定设备识别为受信设备。
     *
     * <p>Sa-Token 实现调用 <code>StpUtil.isTrustDeviceId(userId, deviceId)</code>。</p>
     *
     * @param userId 用户 ID
     * @param deviceId 设备标识
     * @return 实际返回<code>StpUtil.isTrustDeviceId(userId, deviceId)</code>
     */
    @Override
    public boolean isTrustDeviceId(Object userId, String deviceId) {
        return currentLogic().isTrustDeviceId(userId, deviceId);
    }

    /**
     * 返回当前会话关联的登录标识。
     *
     * <p>Sa-Token 实现调用 <code>StpUtil.getLoginId()</code>。</p>
     *
     * @return 实际返回<code>StpUtil.getLoginId()</code>
     */
    @Override
    public Object getLoginId() {
        return currentLogic().getLoginId();
    }

    /**
     * 读取认证主体的用户标识。
     *
     * <p>Sa-Token 实现调用 <code>getPrincipal()</code>。</p>
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
     * <p>Sa-Token 实现调用 <code>getPrincipal()</code>。</p>
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
     * <p>Sa-Token 实现调用 <code>getPrincipal()</code>。</p>
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
     * <p>Sa-Token 实现调用 <code>StpUtil.getExtra(tokenValue, key)</code>。</p>
     *
     * @param tokenValue 会话凭证
     * @param key 扩展属性键
     * @return 实际返回<code>StpUtil.getExtra(tokenValue, key)</code>
     */
    @Override
    public Object getExtra(String tokenValue, String key) {
        return currentLogic().getExtra(tokenValue, key);
    }

    // ==================== 封禁（委托 Sa-Token）====================

    /**
     * 封禁账号，被封禁的账号在封禁期内无法登录。
     *
     * <p>Sa-Token 实现调用 <code>StpUtil.stpLogic.disable(loginId, timeout)</code>。</p>
     *
     * @param loginId 账号 ID
     * @param timeout 封禁时长（秒），{@code -1} 代表永久封禁
     */
    @Override
    public void disable(Object loginId, long timeout) {
        currentLogic().disable(loginId, timeout);
    }

    /**
     * 判断当前限制是否禁止指定身份执行相应操作。
     *
     * <p>Sa-Token 实现调用 <code>StpUtil.stpLogic.isDisable(loginId)</code>。</p>
     *
     * @param loginId 账号 ID
     * @return 实际返回<code>StpUtil.stpLogic.isDisable(loginId)</code>
     */
    @Override
    public boolean isDisabled(Object loginId) {
        return currentLogic().isDisable(loginId);
    }

    /**
     * 解除指定身份的禁用限制。
     *
     * <p>Sa-Token 实现调用 <code>StpUtil.stpLogic.untieDisable(loginId)</code>。</p>
     *
     * @param loginId 账号 ID
     */
    @Override
    public void untieDisable(Object loginId) {
        currentLogic().untieDisable(loginId);
    }

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
     * 解析当前主体认证域使用的登录逻辑。
     *
     * @return 当前认证域对应的原生登录逻辑
     */
    private StpLogic currentLogic() {
        return stpLogic(realm);
    }

}
