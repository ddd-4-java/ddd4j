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

import io.ddd4j.core.constant.Constants;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * 数据访问审计投影。操作由框架提供注册路由或处理方法，避免带入原始请求数据。
 */
@Getter
@Slf4j
public final class SaTokenDataAccessAudit {

    private static final Marker ACCESS_MARKER = MarkerFactory.getMarker(Constants.ACCESS_MARKER);

    private final SaTokenDataIdentity identity;
    private final String method;
    private final String operation;
    private final int status;

    private SaTokenDataAccessAudit(SaTokenDataIdentity identity, String method, String operation, int status) {
        this.identity = identity;
        this.method = method;
        this.operation = operation;
        this.status = status;
    }

    /**
     * 写入访问日志，并将脱敏投影交给应用审计接收器。
     *
     * @param identity 已验证的会话数据身份
     * @param method HTTP 请求方法
     * @param operation 注册的路由模板或固定处理方法名
     * @param status 响应状态码
     * @param sink 应用扩展，未配置时仅记录访问日志
     */
    public static void publish(SaTokenDataIdentity identity, String method, String operation, int status,
                               Consumer<SaTokenDataAccessAudit> sink) {
        log.info(ACCESS_MARKER, "Sa-Token data access method={} status={} tenantId={} appId={} userId={} operation={}",
                method, status, identity.getTenantId(), identity.getAppId(), identity.getUserId(), operation);
        if (Objects.nonNull(sink)) {
            try {
                sink.accept(new SaTokenDataAccessAudit(identity, method, operation, status));
            } catch (RuntimeException exception) {
                log.warn("Sa-Token data audit listener failed: {}", exception.getClass().getSimpleName());
            }
        }
    }
}
