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

    private static final String REQUEST_KEY = SaTokenDataIdentity.class.getName();
    private final String tenantId;
    private final String appId;
    private final String userId;

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

    private boolean sameScope(SaTokenDataIdentity identity) {
        return Objects.nonNull(identity) && tenantId.equals(identity.tenantId)
                && appId.equals(identity.appId) && userId.equals(identity.userId);
    }

    /**
     * 检查数据标识是否属于当前会话，仅允许 tenant、application 和 user 三种类型。
     *
     * @param type 数据权限类型
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

    private static boolean matches(List<String> values, String expected) {
        return values.isEmpty() || (values.size() == 1 && expected.equals(values.get(0)));
    }

    static SaTokenDataIdentity fromPrincipal(AuthPrincipal principal) {
        if (Objects.isNull(principal.getProfile())) {
            return null;
        }
        return create(identifier(principal.getProfile().get(AuthConstants.FIELD_TENANT_ID)),
                identifier(principal.getAppId()), identifier(principal.getUserId()));
    }

    static SaTokenDataIdentity fromSession(Map<?, ?> session) {
        return create(identifier(session.get("tenantId")), identifier(session.get("appId")),
                identifier(session.get("userId")));
    }

    private static SaTokenDataIdentity create(String tenant, String app, String user) {
        return Objects.nonNull(tenant) && Objects.nonNull(app) && Objects.nonNull(user)
                ? new SaTokenDataIdentity(tenant, app, user) : null;
    }

    private static String identifier(Object value) {
        if (!(value instanceof String || value instanceof Number || value instanceof UUID)) {
            return null;
        }
        String text = value.toString();
        return StrKit.hasText(text) && text.chars().noneMatch(Character::isISOControl) ? text : null;
    }
    private static final class RequestBinding {

        private final StpLogic logic;
        private final String token;
        private final SaTokenDataIdentity identity;

        private RequestBinding(StpLogic logic, String token, SaTokenDataIdentity identity) {
            this.logic = logic;
            this.token = token;
            this.identity = identity;
        }
    }
}
