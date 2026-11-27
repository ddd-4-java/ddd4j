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
    /** \u4ee4\u724c\u4f1a\u8bdd\u4e2d\u5b58\u653e\u8ba4\u8bc1\u4e3b\u4f53\u548c\u6570\u636e\u8303\u56f4\u7684\u5c5e\u6027\u952e\u3002 */
    private static final String TOKEN_CONTEXT = "io.ddd4j.boot.auth.satoken.SaTokenDataSubjectProvider.context";

    /**
     * 创建采用独立令牌保存授权主体的提供者。
     */
    public SaTokenDataSubjectProvider() {
    }

    /**
     * \u5728\u4ee4\u724c\u6709\u6548\u4e14\u4f1a\u8bdd\u542b\u8ba4\u8bc1\u4e3b\u4f53\u65f6\u8bfb\u53d6\u5f53\u524d\u6570\u636e\u8303\u56f4\u3002
     * @param logic \u5bf9\u5e94\u8d26\u53f7\u57df\u7684\u539f\u751f\u767b\u5f55\u903b\u8f91
     * @param token \u5f85\u68c0\u67e5\u6216\u8bfb\u53d6\u4e0a\u4e0b\u6587\u7684\u8bbf\u95ee\u4ee4\u724c
     * @return \u4ee4\u724c\u6240\u5c5e\u7684\u6570\u636e\u8eab\u4efd\uff0c\u9a8c\u8bc1\u6216\u8bfb\u53d6\u5931\u8d25\u65f6\u4e3a\u7a7a
     */
    static SaTokenDataIdentity identityForToken(StpLogic logic, String token) {
        Map<?, ?> context = verifiedTokenContext(logic, token);
        return Objects.nonNull(tokenPrincipal(context)) ? SaTokenDataIdentity.fromSession(context) : null;
    }

    /**
     * \u4ece\u4ee4\u724c\u4f1a\u8bdd\u7684\u4e0a\u4e0b\u6587\u4e2d\u8bfb\u53d6\u8ba4\u8bc1\u4e3b\u4f53\u3002
     * @param <T> \u8c03\u7528\u65b9\u8981\u6c42\u7684\u8ba4\u8bc1\u4e3b\u4f53\u7c7b\u578b
     * @param context \u4ee4\u724c\u4f1a\u8bdd\u4e2d\u4fdd\u5b58\u7684\u8ba4\u8bc1\u4e0a\u4e0b\u6587
     * @return \u5b58\u653e\u7684\u8ba4\u8bc1\u4e3b\u4f53\uff0c\u7c7b\u578b\u4e0d\u5339\u914d\u65f6\u4e3a\u7a7a
     */
    @SuppressWarnings("unchecked")
    private static <T extends AuthPrincipal> T tokenPrincipal(Map<?, ?> context) {
        Object principal = Objects.nonNull(context) ? context.get("principal") : null;
        return principal instanceof AuthPrincipal ? (T) principal : null;
    }

    /**
     * \u5148\u6821\u9a8c\u4ee4\u724c\uff0c\u518d\u8bfb\u53d6\u4f1a\u8bdd\u4e2d\u7684\u8eab\u4efd\u4e0a\u4e0b\u6587\u3002
     * @param logic \u5bf9\u5e94\u8d26\u53f7\u57df\u7684\u539f\u751f\u767b\u5f55\u903b\u8f91
     * @param token \u5f85\u68c0\u67e5\u6216\u8bfb\u53d6\u4e0a\u4e0b\u6587\u7684\u8bbf\u95ee\u4ee4\u724c
     * @return \u6709\u6548\u4ee4\u724c\u7684\u4f1a\u8bdd\u4e0a\u4e0b\u6587\uff0c\u65e0\u6709\u6548\u8bb0\u5f55\u65f6\u4e3a\u7a7a
     */
    private static Map<?, ?> verifiedTokenContext(StpLogic logic, String token) {
        return isVerifiedToken(logic, token) ? tokenContext(logic, token) : null;
    }

    /**
     * \u4f9d\u6b21\u68c0\u67e5\u4ee4\u724c\u6709\u6548\u6027\u3001\u4f1a\u8bdd\u6d3b\u52a8\u72b6\u6001\u548c\u5f53\u524d\u8ba4\u8bc1\u57df\u8ba4\u53ef\u7684\u4e3b\u4f53\u3002
     * @param logic \u5bf9\u5e94\u8d26\u53f7\u57df\u7684\u539f\u751f\u767b\u5f55\u903b\u8f91
     * @param token \u5f85\u68c0\u67e5\u6216\u8bfb\u53d6\u4e0a\u4e0b\u6587\u7684\u8bbf\u95ee\u4ee4\u724c
     * @return \u4ee4\u724c\u4ecd\u6709\u6548\u5e76\u5173\u8054\u53ef\u7528\u8d26\u53f7\u65f6\u8fd4\u56de true
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
     * \u8bfb\u53d6\u5df2\u7ecf\u5b58\u5728\u7684\u4ee4\u724c\u4f1a\u8bdd\u53ca\u5176\u4e0a\u4e0b\u6587\uff0c\u4e0d\u4e3a\u67e5\u627e\u521b\u5efa\u4f1a\u8bdd\u3002
     * @param logic \u5bf9\u5e94\u8d26\u53f7\u57df\u7684\u539f\u751f\u767b\u5f55\u903b\u8f91
     * @param token \u5f85\u68c0\u67e5\u6216\u8bfb\u53d6\u4e0a\u4e0b\u6587\u7684\u8bbf\u95ee\u4ee4\u724c
     * @return \u4f1a\u8bdd\u4e2d\u7684\u8ba4\u8bc1\u4e0a\u4e0b\u6587\uff0c\u7f3a\u5c11\u65f6\u8fd4\u56de\u7a7a
     */
    private static Map<?, ?> tokenContext(StpLogic logic, String token) {
        SaSession tokenSession = logic.getTokenSessionByToken(token, false);
        return Objects.nonNull(tokenSession) && tokenSession.get(TOKEN_CONTEXT) instanceof Map<?, ?> context
                ? context : null;
    }

    /**
     * \u628a\u8ba4\u8bc1\u4e3b\u4f53\u548c\u5b8c\u6574\u7684\u6570\u636e\u8303\u56f4\u8bb0\u5f55\u5199\u5165\u4ee4\u724c\u4f1a\u8bdd\u3002
     * @param logic \u5bf9\u5e94\u8d26\u53f7\u57df\u7684\u539f\u751f\u767b\u5f55\u903b\u8f91
     * @param token \u5f85\u68c0\u67e5\u6216\u8bfb\u53d6\u4e0a\u4e0b\u6587\u7684\u8bbf\u95ee\u4ee4\u724c
     * @param principal \u539f\u751f\u4f1a\u8bdd\u5173\u8054\u7684\u8ba4\u8bc1\u4e3b\u4f53
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

    /** {@inheritDoc} */
    @Override
    public Subject getSubject() {
        return new TokenSubject(null);
    }

    /** {@inheritDoc} */
    @Override
    public Subject getSubject(String realm) {
        return new TokenSubject(realm);
    }

    /** \u5728\u6307\u5b9a\u8ba4\u8bc1\u57df\u7684 Sa-Token \u4e3b\u4f53\u4e0a\u63a5\u5165\u6570\u636e\u8eab\u4efd\u3002 */
    private static final class TokenSubject extends SaTokenSubject {

        /** \u5f53\u524d\u8ba4\u8bc1\u903b\u8f91\u6216\u8ba4\u8bc1\u4e3b\u4f53\u4f7f\u7528\u7684\u8ba4\u8bc1\u57df\u3002 */
        private final String realm;

        /**
         * \u628a\u4e3b\u4f53\u5173\u8054\u5230\u6307\u5b9a Sa-Token \u8ba4\u8bc1\u57df\u3002
         * @param realm \u5f53\u524d\u8ba4\u8bc1\u57df\u540d\u79f0
         */
        private TokenSubject(String realm) {
            super(realm);
            this.realm = realm;
        }

        /** {@inheritDoc} */
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

        /** {@inheritDoc} */
        @Override
        public <T extends AuthPrincipal> T verify(String token) {
            try {
                return tokenPrincipal(verifiedTokenContext(stpLogic(realm), token));
            } catch (RuntimeException exception) {
                log.warn("Sa-Token data subject verification failed closed: {}", exception.getClass().getSimpleName());
                return null;
            }
        }

        /** {@inheritDoc} */
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

        /** {@inheritDoc} */
        @Override
        public boolean isAuthenticated() {
            return Objects.nonNull(getPrincipal());
        }

        /** {@inheritDoc} */
        @Override
        public boolean isAuthenticated(Object loginId) {
            return Boolean.TRUE.equals(resolveAccount(loginId, super::isAuthenticated, principal -> Boolean.TRUE));
        }

        /** {@inheritDoc} */
        @Override
        @SuppressWarnings("unchecked")
        public <T extends AuthPrincipal> T getPrincipalByLoginId(Object loginId) {
            return resolveAccount(loginId, super::getPrincipalByLoginId, principal -> (T) principal);
        }

        /**
         * \u5f53\u8bf7\u6c42\u542b\u51ed\u636e\u65f6\u4f7f\u7528\u6821\u9a8c\u540e\u7684\u4ee4\u724c\u4e3b\u4f53\uff0c\u5426\u5219\u6309\u6307\u5b9a\u8d26\u53f7\u67e5\u8be2\u3002
         * @param <T> \u8d26\u53f7\u76f8\u5173\u67e5\u8be2\u7684\u7ed3\u679c\u7c7b\u578b
         * @param loginId \u5f53\u524d\u8ba4\u8bc1\u57df\u5185\u7684\u8d26\u53f7\u6807\u8bc6
         * @param accountLookup \u4ec5\u4f7f\u7528\u8d26\u53f7\u6807\u8bc6\u67e5\u8be2\u6743\u9650\u6216\u89d2\u8272\u7684\u56de\u8c03
         * @param tokenLookup \u4f7f\u7528\u4ee4\u724c\u4f1a\u8bdd\u8ba4\u8bc1\u4e3b\u4f53\u67e5\u8be2\u6743\u9650\u6216\u89d2\u8272\u7684\u56de\u8c03
         * @return \u5f53\u524d\u8d26\u53f7\u7684\u67e5\u8be2\u7ed3\u679c\uff0c\u8ba4\u8bc1\u6216\u67e5\u627e\u5931\u8d25\u65f6\u4e3a\u7a7a
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
