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
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpLogic;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.auth.AuthRequest;
import io.ddd4j.core.auth.session.AuthSessionConfig;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.kit.lang.StrKit;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * 为每次登录保留令牌级主体，避免同一账号的其他登录改变当前会话身份。
 *
 * <p>当前请求和显式令牌只读取本令牌的主体。没有请求凭证时可按账号查询。
 * 有请求凭证时，只有验证通过且明确查询其他账号才读取其账号会话。
 */
@Slf4j
public final class SaTokenDataSubjectProvider extends SaTokenSubjectProvider {

    // 与 Boot 3.3.x 使用的 Token-Session 格式一致。
    /**
     * 令牌会话中存放认证主体和数据范围的属性键。
     */
    private static final String TOKEN_CONTEXT = "io.ddd4j.boot.auth.satoken.SaTokenDataSubjectProvider.context";

    /**
     * 创建采用独立令牌保存授权主体的提供者。
     */
    public SaTokenDataSubjectProvider() {
    }

    /**
     * 在令牌有效且会话含认证主体时读取当前数据范围。
     *
     * @param logic 对应账号域的原生登录逻辑
     * @param token 待检查或读取上下文的访问令牌
     * @return 令牌所属的数据身份，验证或读取失败时为空
     */
    static SaTokenDataIdentity identityForToken(StpLogic logic, String token) {
        Map<?, ?> context = verifiedTokenContext(logic, token);
        return Objects.nonNull(tokenPrincipal(context)) ? SaTokenDataIdentity.fromSession(context) : null;
    }

    /**
     * 从令牌会话的上下文中读取认证主体。
     *
     * @param <T>     调用方要求的认证主体类型
     * @param context 令牌会话中保存的认证上下文
     * @return 存放的认证主体，类型不匹配时为空
     */
    @SuppressWarnings("unchecked")
    private static <T extends AuthPrincipal> T tokenPrincipal(Map<?, ?> context) {
        Object principal = Objects.nonNull(context) ? context.get("principal") : null;
        return principal instanceof AuthPrincipal ? (T) principal : null;
    }

    /**
     * 先校验令牌，再读取会话中的身份上下文。
     *
     * @param logic 对应账号域的原生登录逻辑
     * @param token 待检查或读取上下文的访问令牌
     * @return 有效令牌的会话上下文，无有效记录时为空
     */
    private static Map<?, ?> verifiedTokenContext(StpLogic logic, String token) {
        return isVerifiedToken(logic, token) ? tokenContext(logic, token) : null;
    }

    /**
     * 依次检查令牌有效性、会话活动状态和当前认证域认可的主体。
     *
     * @param logic 对应账号域的原生登录逻辑
     * @param token 待检查或读取上下文的访问令牌
     * @return 令牌仍有效并关联可用账号时返回 true
     */
    private static boolean isVerifiedToken(StpLogic logic, String token) {
        if (!StrKit.hasText(token) || !logic.isValidToken(token)
                || logic.getTokenActiveTimeoutByToken(token) == SaTokenDao.NOT_VALUE_EXPIRE) {
            return false;
        }
        // 复用 P0 验证以核对 JWT 声明、签名、账号体系和服务端映射。
        return Objects.nonNull(new SaTokenSubject(logic.getLoginType()).verify(token));
    }

    /**
     * 读取已经存在的令牌会话及其上下文，不为查找创建会话。
     *
     * @param logic 对应账号域的原生登录逻辑
     * @param token 待检查或读取上下文的访问令牌
     * @return 会话中的认证上下文，缺少时返回空
     */
    private static Map<?, ?> tokenContext(StpLogic logic, String token) {
        SaSession tokenSession = logic.getTokenSessionByToken(token, false);
        return Objects.nonNull(tokenSession) && tokenSession.get(TOKEN_CONTEXT) instanceof Map<?, ?> context
                ? context : null;
    }

    /**
     * 把认证主体和完整的数据范围记录写入令牌会话。
     *
     * @param logic     对应账号域的原生登录逻辑
     * @param token     待检查或读取上下文的访问令牌
     * @param principal 原生会话关联的认证主体
     */
    private static void bindTokenContext(StpLogic logic, String token, AuthPrincipal principal) {
        Map<String, Object> context = new HashMap<>();
        context.put("principal", principal);
        SaTokenDataIdentity identity = SaTokenDataIdentity.fromPrincipal(principal);
        if (Objects.nonNull(identity)) {
            context.put("tenantId", identity.getTenantId());
            context.put("appId", identity.getAppId());
            context.put("userId", identity.getUserId());
        }
        try {
            logic.getTokenSessionByToken(token, true).set(TOKEN_CONTEXT, context);
        } catch (RuntimeException failure) {
            try {
                logic.logoutByTokenValue(token);
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    /**
     * 取得当前请求使用的认证主体接口。
     *
     * @return 本次调用使用的认证主体接口
     */
    @Override
    public Subject getSubject() {
        return new TokenSubject(null);
    }

    /**
     * 取得当前请求使用的认证主体接口。
     *
     * @param realm 要取得主体的认证域
     * @return 本次调用使用的认证主体接口
     */
    @Override
    public Subject getSubject(String realm) {
        return new TokenSubject(realm);
    }

    /**
     * 在指定认证域的 Sa-Token 主体上接入数据身份。
     */
    private static final class TokenSubject extends SaTokenSubject {

        /**
         * 当前认证逻辑或认证主体使用的认证域。
         */
        private final String realm;

        /**
         * 把主体关联到指定 Sa-Token 认证域。
         *
         * @param realm 当前认证域名称
         */
        private TokenSubject(String realm) {
            super(realm);
            this.realm = realm;
        }

        /**
         * 以传入的凭据启动认证会话。
         *
         * <p>此实现调用 <code>request.getSessionConfig()</code>。</p>
         *
         * @param request 框架传入的当前请求
         * @return 实际返回<code>token</code>
         */
        @Override
        public String login(AuthRequest request) {
            AuthSessionConfig original = request.getSessionConfig();
            AuthSessionConfig session = new AuthSessionConfig();
            if (Objects.nonNull(original)) {
                if (StrKit.hasText(original.getPresetToken())) {
                    throw new IllegalArgumentException("Data context requires a new server-generated token");
                }
                session.setDeviceType(original.getDeviceType())
                        .setDeviceId(original.getDeviceId())
                        .setCookie(original.getCookie())
                        .setActiveTimeout(original.getActiveTimeout())
                        .setCreateTokenSessionNow(original.isCreateTokenSessionNow())
                        .setConcurrent(original.isConcurrent())
                        .setMaxLoginCount(original.getMaxLoginCount())
                        .setReplacedLoginExitMode(original.getReplacedLoginExitMode())
                        .setReplacedRange(original.getReplacedRange())
                        .setOverflowLogoutMode(original.getOverflowLogoutMode())
                        .setWriteTokenToHeader(original.isWriteTokenToHeader());
                if (original.hasTimeoutOverride()) {
                    session.setTimeout(original.getTimeout());
                }
            }
            session.setShare(false);
            AuthRequest login = AuthRequest.of(request.getLoginId()).setRealm(request.getRealm())
                    .setPrincipal(request.getPrincipal()).setExtra(request.getExtra()).setSessionConfig(session);
            String token = super.login(login);
            if (Objects.nonNull(login.getPrincipal())) {
                String loginRealm = StrKit.hasText(login.getRealm()) ? login.getRealm() : realm;
                bindTokenContext(stpLogic(loginRealm), token, login.getPrincipal());
            }
            return token;
        }

        /**
         * 校验当前调用传入的数据或凭据。
         *
         * <p>此实现调用 <code>tokenPrincipal(verifiedTokenContext(stpLogic(realm), token))</code>。</p>
         *
         * @param <T>   此操作处理的值或结果的 Java 类型
         * @param token 待校验或用于定位会话的令牌
         * @return 根据执行路径返回<code>tokenPrincipal(verifiedTokenContext(stpLogic(realm), token))</code>、<code>null</code>
         */
        @Override
        public <T extends AuthPrincipal> T verify(String token) {
            try {
                return tokenPrincipal(verifiedTokenContext(stpLogic(realm), token));
            } catch (RuntimeException exception) {
                log.warn("Sa-Token data subject verification failed closed: {}", exception.getClass().getSimpleName());
                return null;
            }
        }

        /**
         * 读取当前会话的认证主体。
         *
         * <p>此实现调用 <code>SaManager.getSaTokenContext().isValid()</code>。</p>
         *
         * @param <T> 此操作处理的值或结果的 Java 类型
         * @return 当前会话绑定的主体
         */
        @Override
        public <T extends AuthPrincipal> T getPrincipal() {
            if (!SaManager.getSaTokenContext().isValid()) {
                return null;
            }
            try {
                StpLogic logic = stpLogic(realm);
                return tokenPrincipal(verifiedTokenContext(logic, logic.getTokenValue()));
            } catch (RuntimeException exception) {
                log.warn("Sa-Token data subject verification failed closed: {}", exception.getClass().getSimpleName());
                return null;
            }
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
         * @param loginId 用来定位身份或登录会话的登录标识
         * @return 当前会话通过身份认证时为真
         */
        @Override
        public boolean isAuthenticated(Object loginId) {
            return Boolean.TRUE.equals(resolveAccount(loginId, super::isAuthenticated, principal -> Boolean.TRUE));
        }

        /**
         * 查询指定登录标识关联的认证主体。
         *
         * @param <T>     此操作处理的值或结果的 Java 类型
         * @param loginId 用来定位身份或登录会话的登录标识
         * @return 给定登录标识关联的主体
         */
        @Override
        @SuppressWarnings("unchecked")
        public <T extends AuthPrincipal> T getPrincipalByLoginId(Object loginId) {
            return resolveAccount(loginId, super::getPrincipalByLoginId, principal -> (T) principal);
        }

        /**
         * 当请求含凭据时使用校验后的令牌主体，否则按指定账号查询。
         *
         * @param <T>           账号相关查询的结果类型
         * @param loginId       当前认证域内的账号标识
         * @param accountLookup 仅使用账号标识查询权限或角色的回调
         * @param tokenLookup   使用令牌会话认证主体查询权限或角色的回调
         * @return 当前账号的查询结果，认证或查找失败时为空
         */
        private <T> T resolveAccount(Object loginId, Function<Object, T> accountLookup,
                                     Function<AuthPrincipal, T> tokenLookup) {
            if (Objects.isNull(loginId)) {
                return null;
            }
            try {
                if (SaManager.getSaTokenContext().isValid()) {
                    StpLogic logic = stpLogic(realm);
                    // 严格解析前缀，已提交但格式错误的凭证不能被视作无凭证。
                    String token = logic.getTokenValue(true);
                    if (Objects.nonNull(token)) {
                        AuthPrincipal principal = tokenPrincipal(verifiedTokenContext(logic, token));
                        if (Objects.isNull(principal)) {
                            return null;
                        }
                        Object currentLoginId = logic.getLoginIdByToken(token);
                        if (!logic.isValidLoginId(currentLoginId)) {
                            return null;
                        }
                        if (Objects.equals(String.valueOf(loginId), String.valueOf(currentLoginId))) {
                            return tokenLookup.apply(principal);
                        }
                    }
                }
            } catch (NotLoginException exception) {
                return null;
            } catch (RuntimeException exception) {
                log.warn("Sa-Token data subject verification failed closed: {}", exception.getClass().getSimpleName());
                return null;
            }
            return accountLookup.apply(loginId);
        }
    }
}
