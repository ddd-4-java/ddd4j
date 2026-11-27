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
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.context.model.SaStorage;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import io.ddd4j.core.auth.AuthPrincipal;
import io.ddd4j.core.constant.AuthConstants;
import io.ddd4j.core.constant.XHeaders;
import io.ddd4j.core.exception.AuthorizationException;
import io.ddd4j.kit.lang.StrKit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * 令牌级的可信数据身份，只包含授权后的租户、应用、用户标识。
 */
@Getter
@Slf4j
public final class SaTokenDataIdentity {

    /** \u7528\u4e8e\u5728\u5f53\u524d\u8bf7\u6c42\u4e2d\u7ed1\u5b9a\u5df2\u9a8c\u8bc1\u6570\u636e\u8eab\u4efd\u7684\u952e\u3002 */
    private static final String REQUEST_KEY = SaTokenDataIdentity.class.getName();
    /** \u5f53\u524d\u4e1a\u52a1\u6570\u636e\u6240\u5c5e\u7684\u79df\u6237\u6807\u8bc6\u3002 */
    private final String tenantId;
    /** \u5df2\u9a8c\u8bc1\u8eab\u4efd\u6240\u5c5e\u7684\u5e94\u7528\u6807\u8bc6\u3002 */
    private final String appId;
    /** \u5df2\u9a8c\u8bc1\u8eab\u4efd\u5bf9\u5e94\u7684\u7528\u6237\u6807\u8bc6\u3002 */
    private final String userId;

    /**
     * \u7528\u7ed9\u5b9a\u503c\u521d\u59cb\u5316 <code>SaTokenDataIdentity</code> \u5b9e\u4f8b\u3002
     * @param tenantId \u5f53\u524d\u4e1a\u52a1\u6570\u636e\u6240\u5c5e\u7684\u79df\u6237\u6807\u8bc6
     * @param appId <code>appId</code> \u5b57\u6bb5
     * @param userId <code>userId</code> \u5b57\u6bb5
     */
    private SaTokenDataIdentity(String tenantId, String appId, String userId) {
        this.tenantId = tenantId;
        this.appId = appId;
        this.userId = userId;
    }

    /**
     * 验证默认账号体系的当前令牌，并在 Sa-Token 的请求存储中固定数据身份。
     *
     * @return 完整的可信身份，凭证无效或缺少标识时返回 null
     */
    public static SaTokenDataIdentity bindCurrentRequest() {
        if (!SaManager.getSaTokenContext().isValid()) {
            return null;
        }
        SaStorage storage = SaHolder.getStorage();
        storage.delete(REQUEST_KEY);
        StpLogic logic = StpUtil.stpLogic;
        String token = logic.getTokenValue();
        SaTokenDataIdentity identity = SaTokenDataSubjectProvider.identityForToken(logic, token);
        if (Objects.nonNull(identity)) {
            storage.set(REQUEST_KEY, new RequestBinding(logic, token, identity));
        }
        return identity;
    }

    /**
     * 读取仍与当前有效令牌及其会话一致的请求身份。
     *
     * @return 可信数据身份
     */
    public static SaTokenDataIdentity current() {
        if (!SaManager.getSaTokenContext().isValid()) {
            return null;
        }
        SaStorage storage = SaHolder.getStorage();
        Object stored = storage.get(REQUEST_KEY);
        if (stored instanceof RequestBinding binding) {
            try {
                if (binding.logic == StpUtil.stpLogic && Objects.equals(binding.token, binding.logic.getTokenValue())
                        && binding.identity.sameScope(SaTokenDataSubjectProvider.identityForToken(binding.logic, binding.token))) {
                    return binding.identity;
                }
            } catch (RuntimeException failure) {
                log.warn("Sa-Token request data identity verification failed: {}", failure.getClass().getSimpleName());
            }
            storage.delete(REQUEST_KEY);
        }
        return null;
    }

    /**
     * 检查数据标识是否属于当前会话，仅允许 tenant、application 和 user 三种类型。
     *
     * @param type  数据权限类型
     * @param value 待授权的标识
     * @return 当前请求具有对应权限时返回 true
     */
    public static boolean permits(String type, Object value) {
        SaTokenDataIdentity identity = current();
        String target = identifier(value);
        if (Objects.isNull(identity) || Objects.isNull(type) || Objects.isNull(target)) {
            return false;
        }
        return switch (type) {
            case "tenant" -> identity.tenantId.equals(target);
            case "application" -> identity.appId.equals(target);
            case "user" -> identity.userId.equals(target);
            default -> false;
        };
    }

    /**
     * \u68c0\u67e5\u8bf7\u6c42\u662f\u5426\u7701\u7565\u8303\u56f4\u6807\u8bc6\u6216\u53ea\u63d0\u4f9b\u4e86\u4e0e\u8ba4\u8bc1\u8eab\u4efd\u4e00\u81f4\u7684\u552f\u4e00\u503c\u3002
     * @param values \u8bf7\u6c42\u5b9e\u9645\u63d0\u4f9b\u7684\u8303\u56f4\u6807\u8bc6\u96c6\u5408
     * @param expected \u5df2\u9a8c\u8bc1\u7684\u6570\u636e\u8303\u56f4\u6807\u8bc6
     * @return \u65e0\u9700\u8986\u76d6\u8303\u56f4\u6216\u8bf7\u6c42\u8303\u56f4\u4e00\u81f4\u65f6\u8fd4\u56de true
     */
    private static boolean matches(List<String> values, String expected) {
        return values.isEmpty() || (values.size() == 1 && expected.equals(values.get(0)));
    }

    /**
     * \u4ece\u8ba4\u8bc1\u4e3b\u4f53\u7684\u5c5e\u6027\u548c\u8d26\u53f7\u4fe1\u606f\u63d0\u53d6\u6570\u636e\u8303\u56f4\u3002
     * @param principal \u539f\u751f\u4f1a\u8bdd\u5173\u8054\u7684\u8ba4\u8bc1\u4e3b\u4f53
     * @return \u5b8c\u6574\u7684\u79df\u6237\u3001\u5e94\u7528\u548c\u7528\u6237\u7ec4\u5408\uff0c\u4fe1\u606f\u7f3a\u5931\u65f6\u4e3a\u7a7a
     */
    static SaTokenDataIdentity fromPrincipal(AuthPrincipal principal) {
        if (Objects.isNull(principal.getProfile())) {
            return null;
        }
        return create(identifier(principal.getProfile().get(AuthConstants.FIELD_TENANT_ID)),
                identifier(principal.getAppId()), identifier(principal.getUserId()));
    }

    /**
     * \u4ece\u539f\u751f\u4ee4\u724c\u4f1a\u8bdd\u63d0\u53d6\u79df\u6237\u3001\u5e94\u7528\u548c\u7528\u6237\u6807\u8bc6\u3002
     * @param session \u4fdd\u5b58\u8ba4\u8bc1\u8303\u56f4\u5b57\u6bb5\u7684\u4ee4\u724c\u4f1a\u8bdd\u4e0a\u4e0b\u6587
     * @return \u5408\u6cd5\u4e14\u5b8c\u6574\u7684\u6570\u636e\u8eab\u4efd\uff0c\u7f3a\u5c11\u5b57\u6bb5\u65f6\u4e3a\u7a7a
     */
    static SaTokenDataIdentity fromSession(Map<?, ?> session) {
        return create(identifier(session.get("tenantId")), identifier(session.get("appId")),
                identifier(session.get("userId")));
    }

    /**
     * \u4ec5\u5f53\u4e09\u9879\u8303\u56f4\u6807\u8bc6\u90fd\u6709\u503c\u65f6\u521b\u5efa\u6570\u636e\u8eab\u4efd\u3002
     * @param tenant \u6570\u636e\u8eab\u4efd\u5bf9\u5e94\u7684\u79df\u6237\u6807\u8bc6
     * @param app \u6570\u636e\u8eab\u4efd\u5bf9\u5e94\u7684\u5e94\u7528\u6807\u8bc6
     * @param user \u6570\u636e\u8eab\u4efd\u5bf9\u5e94\u7684\u7528\u6237\u6807\u8bc6
     * @return \u6570\u636e\u8eab\u4efd\u6216\u7a7a\u5f15\u7528
     */
    private static SaTokenDataIdentity create(String tenant, String app, String user) {
        return Objects.nonNull(tenant) && Objects.nonNull(app) && Objects.nonNull(user)
                ? new SaTokenDataIdentity(tenant, app, user) : null;
    }

    /**
     * \u53ea\u63a5\u53d7\u5b57\u7b26\u4e32\u3001\u6570\u5b57\u548c UUID \u4f5c\u4e3a\u6807\u8bc6\uff0c\u6392\u9664\u7a7a\u767d\u6216\u63a7\u5236\u5b57\u7b26\u3002
     * @param value \u5f53\u524d\u64cd\u4f5c\u51c6\u5907\u5199\u5165\u3001\u590d\u7528\u6216\u89c4\u8303\u5316\u7684\u503c
     * @return \u89c4\u8303\u5316\u540e\u7684\u6587\u672c\u6807\u8bc6\uff0c\u503c\u4e0d\u5408\u6cd5\u65f6\u8fd4\u56de\u7a7a
     */
    private static String identifier(Object value) {
        if (!(value instanceof String || value instanceof Number || value instanceof UUID)) {
            return null;
        }
        String text = value.toString();
        return StrKit.hasText(text) && text.chars().noneMatch(Character::isISOControl) ? text : null;
    }

    /**
     * \u6bd4\u8f83\u79df\u6237\u3001\u5e94\u7528\u53ca\u7528\u6237\u4e09\u9879\u8303\u56f4\u662f\u5426\u5b8c\u5168\u4e00\u81f4\u3002
     * @param identity \u5f53\u524d\u5df2\u7ecf\u9a8c\u8bc1\u7684\u79df\u6237\u3001\u5e94\u7528\u548c\u7528\u6237\u8eab\u4efd
     * @return \u4e09\u9879\u5747\u4e00\u81f4\u65f6\u8fd4\u56de true
     */
    private boolean sameScope(SaTokenDataIdentity identity) {
        return Objects.nonNull(identity) && tenantId.equals(identity.tenantId)
                && appId.equals(identity.appId) && userId.equals(identity.userId);
    }

    /**
     * 检查客户端声明的访问目标。请求头可省略，提供时必须与可信身份一致。
     *
     * @param headers 按名称返回请求头的所有值，缺失时返回空列表
     * @throws AuthorizationException 请求目标与会话身份不一致
     */
    public void requireMatchingTargets(Function<String, List<String>> headers) {
        if (!matches(headers.apply("X-Tenant-Id"), tenantId)
                || !matches(headers.apply(XHeaders.X_APP_ID), appId)
                || !matches(headers.apply("X-User-Id"), userId)) {
            throw new AuthorizationException("Request target does not match the authenticated data scope");
        }
    }

    /** \u5c06\u5f53\u524d\u8bf7\u6c42\u4f7f\u7528\u7684 Sa-Token \u767b\u5f55\u903b\u8f91\u3001\u4ee4\u724c\u548c\u6570\u636e\u8eab\u4efd\u7ed1\u5b9a\u5728\u4e00\u8d77\u3002 */
    private static final class RequestBinding {

        /** \u5f53\u524d\u8bf7\u6c42\u5df2\u9009\u5b9a\u7684\u767b\u5f55\u903b\u8f91\u3002 */
        private final StpLogic logic;
        /** \u5f53\u524d\u8bf7\u6c42\u643a\u5e26\u4e14\u7ed1\u5b9a\u5230\u6570\u636e\u8eab\u4efd\u7684\u4ee4\u724c\u3002 */
        private final String token;
        /** \u4ece\u5f53\u524d\u7ecf\u8fc7\u6821\u9a8c\u7684\u4ee4\u724c\u4f1a\u8bdd\u53d6\u5f97\u7684\u6570\u636e\u8eab\u4efd\u3002 */
        private final SaTokenDataIdentity identity;

        /**
         * \u4fdd\u5b58\u5f53\u524d\u8bf7\u6c42\u7ed1\u5b9a\u7684\u767b\u5f55\u903b\u8f91\u3001\u4ee4\u724c\u548c\u6570\u636e\u8eab\u4efd\u3002
         * @param logic \u5bf9\u5e94\u8d26\u53f7\u57df\u7684\u539f\u751f\u767b\u5f55\u903b\u8f91
         * @param token \u5f85\u68c0\u67e5\u6216\u8bfb\u53d6\u4e0a\u4e0b\u6587\u7684\u8bbf\u95ee\u4ee4\u724c
         * @param identity \u5f53\u524d\u5df2\u7ecf\u9a8c\u8bc1\u7684\u79df\u6237\u3001\u5e94\u7528\u548c\u7528\u6237\u8eab\u4efd
         */
        private RequestBinding(StpLogic logic, String token, SaTokenDataIdentity identity) {
            this.logic = logic;
            this.token = token;
            this.identity = identity;
        }
    }
}
