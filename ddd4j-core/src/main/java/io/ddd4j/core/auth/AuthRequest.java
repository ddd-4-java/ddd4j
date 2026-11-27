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
 * <h2>结构</h2>
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

    /** \u521b\u5efa\u4f7f\u7528\u9ed8\u8ba4\u4f1a\u8bdd\u914d\u7f6e\u7684\u767b\u5f55\u8bf7\u6c42\u3002 */
    public AuthRequest() {
    }

    /**
     * \u521b\u5efa\u6307\u5b9a\u767b\u5f55\u6807\u8bc6\u7684\u8bf7\u6c42\u3002
     * @param loginId \u767b\u5f55\u6807\u8bc6
     */
    public AuthRequest(Object loginId) {
        this.loginId = loginId;
    }

    /**
     * \u5feb\u901f\u6784\u9020\u767b\u5f55\u8bf7\u6c42\u3002
     *
     * @param loginId \u767b\u5f55\u6807\u8bc6
     * @return \u767b\u5f55\u8bf7\u6c42
     */
    public static AuthRequest of(Object loginId) {
        return new AuthRequest(loginId);
    }

    /**
     * 获取值。
     * @return 结果字符串。
     */
    public String getLoginIdAsString() {
        return Objects.isNull(loginId) ? null : String.valueOf(loginId);
    }

    /**
     * Token \u6709\u6548\u671f\uff08\u79d2\uff09\uff0c-1 \u8868\u793a\u6c38\u4e45\u3002
     *  <p>\u4fbf\u6377\u8bbf\u95ee\u5668\uff0c\u8f6c\u53d1\u5230 {@link #sessionConfig}\u3002
     *
     * @return Token \u6709\u6548\u671f\uff0c\u5355\u4f4d\u4e3a\u79d2
     */
    public long getTimeout() {
        return sessionConfig.getTimeout();
    }

    /**
     * 设置值。
     * @param timeout 超时时间。
     * @return 当前对象自身，支持链式调用。
     */
    public AuthRequest setTimeout(long timeout) {
        sessionConfig.setTimeout(timeout);
        return this;
    }

    /**
     * \u8bbe\u5907\u7c7b\u578b\uff08\u591a\u7aef\u767b\u5f55\u9694\u79bb\u7528\uff09\u3002
     *  <p>\u4fbf\u6377\u8bbf\u95ee\u5668\uff0c\u8f6c\u53d1\u5230 {@code sessionConfig.deviceType}\u3002
     *
     * @return \u8bbe\u5907\u7c7b\u578b
     */
    public String getDeviceType() {
        return sessionConfig.getDeviceType();
    }

    /**
     * 设置值。
     * @param deviceType 字符串参数。
     * @return 当前对象自身，支持链式调用。
     */
    public AuthRequest setDeviceType(String deviceType) {
        sessionConfig.setDeviceType(deviceType);
        return this;
    }

    /**
     * \u6dfb\u52a0\u6269\u5c55\u4fe1\u606f\u3002
     *
     * @param key \u6269\u5c55\u4fe1\u606f\u952e\uff0c\u4e0d\u53ef\u4e3a null
     * @param value \u6269\u5c55\u4fe1\u606f\u503c
     * @return \u5f53\u524d\u767b\u5f55\u8bf7\u6c42
     */
    public AuthRequest extra(String key, Object value) {
        if (Objects.isNull(extra)) {
            extra = new HashMap<>();
        }
        extra.put(Objects.requireNonNull(key, "key must not be null"), value);
        return this;
    }

    /**
     * 判断与另一对象是否相等。
     * @param o 比较对象。
     * @return 相等返回 {@code true}，否则返回 {@code false}。
     */
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

    /**
     * 返回对象哈希码。
     * @return 哈希码。
     */
    @Override
    public int hashCode() {
        return Objects.hash(loginId);
    }

    /**
     * 返回对象的字符串表示。
     * @return 对象的字符串表示。
     */
    @Override
    public String toString() {
        return "AuthRequest{loginId=" + loginId
                + ", realm='" + realm + '\''
                + ", sessionConfig=" + sessionConfig
                + '}';
    }
}
