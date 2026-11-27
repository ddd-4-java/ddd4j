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

    /** \u6807\u8bc6\u4e00\u6b21\u6570\u636e\u8bbf\u95ee\u5ba1\u8ba1\u7684\u8bf7\u6c42\u5c5e\u6027\u952e\u3002 */
    private static final Marker ACCESS_MARKER = MarkerFactory.getMarker(Constants.ACCESS_MARKER);

    /** \u5f53\u524d\u8bbf\u95ee\u5df2\u7ecf\u901a\u8fc7\u8ba4\u8bc1\u7684\u6570\u636e\u8eab\u4efd\u3002 */
    private final SaTokenDataIdentity identity;
    /** \u672c\u6b21\u5ba1\u8ba1\u8bb0\u5f55\u7684 HTTP \u65b9\u6cd5\u3002 */
    private final String method;
    /** \u7528\u4e8e\u63cf\u8ff0\u5f53\u524d\u6570\u636e\u8bbf\u95ee\u7528\u9014\u7684\u64cd\u4f5c\u6807\u8bc6\u3002 */
    private final String operation;
    /** \u672c\u6b21\u6570\u636e\u8bbf\u95ee\u7ed3\u675f\u65f6\u7684\u54cd\u5e94\u72b6\u6001\u7801\u3002 */
    private final int status;

    /**
     * \u4fdd\u5b58\u4e00\u6b21\u6570\u636e\u8303\u56f4\u8bbf\u95ee\u7684\u8ba4\u8bc1\u8eab\u4efd\u3001\u64cd\u4f5c\u548c\u72b6\u6001\u3002
     * @param identity \u5f53\u524d\u5df2\u7ecf\u9a8c\u8bc1\u7684\u79df\u6237\u3001\u5e94\u7528\u548c\u7528\u6237\u8eab\u4efd
     * @param method \u5f53\u524d\u8bbf\u95ee\u4f7f\u7528\u7684 HTTP \u8bf7\u6c42\u65b9\u6cd5
     * @param operation \u5f53\u524d\u8bbf\u95ee\u5bf9\u5e94\u7684\u4e1a\u52a1\u64cd\u4f5c\u6807\u8bc6
     * @param status \u5f53\u524d\u8bbf\u95ee\u7684 HTTP \u54cd\u5e94\u72b6\u6001\u7801
     */
    private SaTokenDataAccessAudit(SaTokenDataIdentity identity, String method, String operation, int status) {
        this.identity = identity;
        this.method = method;
        this.operation = operation;
        this.status = status;
    }

    /**
     * 写入访问日志，并将脱敏投影交给应用审计接收器。
     *
     * @param identity  已验证的会话数据身份
     * @param method    HTTP 请求方法
     * @param operation 注册的路由模板或固定处理方法名
     * @param status    响应状态码
     * @param sink      应用扩展，未配置时仅记录访问日志
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
