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
    private static final String TOKEN_CONTEXT = "io.ddd4j.boot.auth.satoken.SaTokenDataSubjectProvider.context";

    /**
     * 创建采用独立令牌保存授权主体的提供者。
     */
    public SaTokenDataSubjectProvider() {
    }

    static SaTokenDataIdentity identityForToken(StpLogic logic, String token) {
        Map<?, ?> context = verifiedTokenContext(logic, token);
        return Objects.nonNull(tokenPrincipal(context)) ? SaTokenDataIdentity.fromSession(context) : null;
    }

    @SuppressWarnings("unchecked")
    private static <T extends AuthPrincipal> T tokenPrincipal(Map<?, ?> context) {
        Object principal = Objects.nonNull(context) ? context.get("principal") : null;
        return principal instanceof AuthPrincipal ? (T) principal : null;
    }

    private static Map<?, ?> verifiedTokenContext(StpLogic logic, String token) {
        return isVerifiedToken(logic, token) ? tokenContext(logic, token) : null;
    }

    private static boolean isVerifiedToken(StpLogic logic, String token) {
        if (!StrKit.hasText(token) || !logic.isValidToken(token)
                || logic.getTokenActiveTimeoutByToken(token) == SaTokenDao.NOT_VALUE_EXPIRE) {
            return false;
        }
        // 复用 P0 验证以核对 JWT 声明、签名、账号体系和服务端映射。
        return Objects.nonNull(new SaTokenSubject(logic.getLoginType()).verify(token));
    }

    private static Map<?, ?> tokenContext(StpLogic logic, String token) {
        SaSession tokenSession = logic.getTokenSessionByToken(token, false);
        return Objects.nonNull(tokenSession) && tokenSession.get(TOKEN_CONTEXT) instanceof Map<?, ?> context
                ? context : null;
    }

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

    @Override
    public Subject getSubject() {
        return new TokenSubject(null);
    }

    @Override
    public Subject getSubject(String realm) {
        return new TokenSubject(realm);
    }

    private static final class TokenSubject extends SaTokenSubject {

        private final String realm;

        private TokenSubject(String realm) {
            super(realm);
            this.realm = realm;
        }

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

        @Override
        public <T extends AuthPrincipal> T verify(String token) {
            try {
                return tokenPrincipal(verifiedTokenContext(stpLogic(realm), token));
            } catch (RuntimeException exception) {
                log.warn("Sa-Token data subject verification failed closed: {}", exception.getClass().getSimpleName());
                return null;
            }
        }

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

        @Override
        public boolean isAuthenticated() {
            return Objects.nonNull(getPrincipal());
        }

        @Override
        public boolean isAuthenticated(Object loginId) {
            return Boolean.TRUE.equals(resolveAccount(loginId, super::isAuthenticated, principal -> Boolean.TRUE));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends AuthPrincipal> T getPrincipalByLoginId(Object loginId) {
            return resolveAccount(loginId, super::getPrincipalByLoginId, principal -> (T) principal);
        }

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
