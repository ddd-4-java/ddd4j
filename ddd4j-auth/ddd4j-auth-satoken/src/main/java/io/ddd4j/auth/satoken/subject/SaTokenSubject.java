package io.ddd4j.auth.satoken.subject;

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

    /**
     * 框架无关枚举映射：{@link AuthLogoutMode} -> sa-token 的 {@code SaLogoutMode}。
     * <p>sa-token 1.42 并不暴露 {@code ReplacedLoginExitMode}/{@code ReplacedRange} 枚举类，
     * 这两个字段我们仍写入 {@link AuthSessionConfig}，但具体生效需要在更新版 sa-token 下补全。
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
        if (Objects.isNull(realm) || !io.ddd4j.kit.lang.StrKit.isNotEmpty(realm)) {
            return StpUtil.stpLogic;
        }
        return SaManager.getStpLogic(realm, true);
    }

    @Override
    public <T extends AuthPrincipal> T getPrincipal() {
        if (!StpUtil.isLogin()) {
            return null;
        }
        SaSession session = StpUtil.getSession(false);
        if (Objects.isNull(session)) {
            return null;
        }
        return (T) session.get(PRINCIPAL_KEY);
    }

    // ==================== 会话生命周期（委托 StpUtil）====================

    @Override
    public <T extends AuthPrincipal> T getPrincipalByLoginId(Object loginId) {
        SaSession session = StpUtil.getSessionByLoginId(loginId, false);
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
        StpLogic logic = stpLogic(request.getRealm());
        AuthSessionConfig cfg = Objects.nonNull(request.getSessionConfig()) ? request.getSessionConfig() : new AuthSessionConfig();

        // 构建 SaLoginParameter（框架无关 -> sa-token 桥接）
        SaLoginParameter param = new SaLoginParameter();
        param.setTimeout(cfg.getTimeout());
        if (Objects.nonNull(cfg.getDeviceType())) {
            param.setDeviceType(cfg.getDeviceType());
        }
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
            if (Objects.nonNull(cfg.getPresetToken())) {
                // 已预定 token：先 set token 再 login
                logic.setTokenValue(cfg.getPresetToken());
            }
            // sa-token 1.42 的 login(...) 返回 void；完成后通过 getTokenValue() 拿到 token
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

        // 登录后立即创建 TokenSession（如果配置需要）
        if (cfg.isCreateTokenSessionNow()) {
            logic.getSession(true);
        }

        // 登录后将 principal 存入 SaSession
        if (Objects.nonNull(request.getPrincipal())) {
            SaSession session = logic.getSessionByLoginId(request.getLoginId(), true);
            session.set(PRINCIPAL_KEY, request.getPrincipal());
        }
        return token;
    }

    @Override
    public void logout() {
        StpUtil.stpLogic.logout();
    }

    @Override
    public void logout(Object loginId) {
        StpUtil.stpLogic.logout(loginId);
    }

    @Override
    public void kickout(Object loginId) {
        StpUtil.stpLogic.kickout(loginId);
    }

    @Override
    public String refresh() {
        StpUtil.stpLogic.renewTimeout(StpUtil.stpLogic.getConfigOrGlobal().getTimeout());
        return StpUtil.stpLogic.getTokenValue();
    }

    @Override
    public <T extends AuthPrincipal> T verify(String token) {
        try {
            Object loginId = resolveVerifiedLoginId(token);
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
    private Object resolveVerifiedLoginId(String token) {
        if (StrKit.isBlank(token)) {
            return null;
        }
        StpLogic logic = StpUtil.stpLogic;
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
        return StpUtil.isLogin();
    }

    @Override
    public boolean isAuthenticated(Object loginId) {
        return StpUtil.isLogin(loginId);
    }

    @Override
    public boolean isRemembered() {
        return false;
    }

    @Override
    public boolean isTrustDeviceId(String deviceId) {
        return StpUtil.isTrustDeviceId(StpKit.getUserIdAsLong(), deviceId);
    }

    @Override
    public boolean isTrustDeviceId(Object userId, String deviceId) {
        return StpUtil.isTrustDeviceId(userId, deviceId);
    }

    @Override
    public Object getLoginId() {
        return StpUtil.getLoginId();
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
        return StpUtil.getExtra(tokenValue, key);
    }

    // ==================== 封禁（委托 Sa-Token）====================

    @Override
    public void disable(Object loginId, long timeout) {
        StpUtil.stpLogic.disable(loginId, timeout);
    }

    @Override
    public boolean isDisabled(Object loginId) {
        return StpUtil.stpLogic.isDisable(loginId);
    }

    @Override
    public void untieDisable(Object loginId) {
        StpUtil.stpLogic.untieDisable(loginId);
    }

}
