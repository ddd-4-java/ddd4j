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
package io.ddd4j.core.auth;

import io.ddd4j.core.auth.session.AuthSessionConfig;
import io.ddd4j.core.subject.Subject;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 登录请求载体（纯 Java 值对象）。
 *
 * <p>承载登录所需的全部信息，由业务构造，由 {@link Subject} 实现消费。
 * 对齐 Sa-Token 的 {@code SaLoginParameter}，覆盖完整登录场景（多端挤占、Cookie 配置、活跃检查等）。
 *
 * <h3>结构</h3>
 * <ul>
 *   <li>身份：{@link #loginId} / {@link #principal} / {@link #realm}</li>
 *   <li>会话策略：{@link #sessionConfig}（嵌入 {@link AuthSessionConfig}，含全部高级参数）</li>
 *   <li>扩展数据：{@link #extra}（写入 Token Claim）</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Data
@Accessors(chain = true)
public class AuthRequest {

    /**
     * 账号 ID（必填）。
     */
    private Object loginId;

    /**
     * 认证主体（可选，登录后通过 {@link Subject#getPrincipal()} 取回）。
     */
    private AuthPrincipal principal;

    /**
     * 多账号体系标识（如 sa-token 的 "admin"/"user"）。
     */
    private String realm;

    /**
     * 登录会话策略（设备/超时/挤占/Cookie 等完整配置）。
     * <p>如未设置，自动使用 {@code AuthSessionConfig} 默认值。
     */
    private AuthSessionConfig sessionConfig = new AuthSessionConfig();

    /**
     * 扩展信息（写入 Token Claim 或 Session）。
     */
    private Map<String, Object> extra = new HashMap<>();

    public AuthRequest() {
    }

    public AuthRequest(Object loginId) {
        this.loginId = loginId;
    }

    /**
     * 快速构造登录请求。
     */
    public static AuthRequest of(Object loginId) {
        return new AuthRequest(loginId);
    }

    public String getLoginIdAsString() {
        return Objects.isNull(loginId) ? null : String.valueOf(loginId);
    }

    /**
     * Token 有效期（秒），-1 表示永久。
     * <p>便捷访问器，转发到 {@link #sessionConfig}。
     */
    public long getTimeout() {
        return sessionConfig.getTimeout();
    }

    public AuthRequest setTimeout(long timeout) {
        sessionConfig.setTimeout(timeout);
        return this;
    }

    /**
     * 设备类型（多端登录隔离用）。
     * <p>便捷访问器，转发到 {@code sessionConfig.deviceType}。
     */
    public String getDeviceType() {
        return sessionConfig.getDeviceType();
    }

    public AuthRequest setDeviceType(String deviceType) {
        sessionConfig.setDeviceType(deviceType);
        return this;
    }

    /**
     * 添加扩展信息。
     */
    public AuthRequest extra(String key, Object value) {
        if (Objects.isNull(extra)) {
            extra = new HashMap<>();
        }
        extra.put(Objects.requireNonNull(key, "key must not be null"), value);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (Objects.isNull(o) || getClass() != o.getClass()) {
            return false;
        }
        AuthRequest that = (AuthRequest) o;
        return Objects.equals(loginId, that.loginId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(loginId);
    }

    @Override
    public String toString() {
        return "AuthRequest{loginId=" + loginId
                + ", realm='" + realm + '\''
                + ", sessionConfig=" + sessionConfig
                + '}';
    }
}