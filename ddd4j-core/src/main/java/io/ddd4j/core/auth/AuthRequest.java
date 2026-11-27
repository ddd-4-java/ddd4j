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
     * \u4ee5\u5b57\u7b26\u4e32\u8bfb\u53d6\u767b\u5f55\u6807\u8bc6\u3002
     * @return \u767b\u5f55\u6807\u8bc6\u7684\u5b57\u7b26\u4e32\u5f62\u5f0f\uff0c\u672a\u8bbe\u7f6e\u65f6\u4e3a null
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
     * \u8bbe\u7f6e Token \u6709\u6548\u671f\u3002
     * @param timeout \u6709\u6548\u671f\u79d2\u6570\uff0c-1 \u8868\u793a\u6c38\u4e45
     * @return \u5f53\u524d\u767b\u5f55\u8bf7\u6c42
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
     * \u8bbe\u7f6e\u591a\u7aef\u767b\u5f55\u9694\u79bb\u4f7f\u7528\u7684\u8bbe\u5907\u7c7b\u578b\u3002
     * @param deviceType \u8bbe\u5907\u7c7b\u578b
     * @return \u5f53\u524d\u767b\u5f55\u8bf7\u6c42
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
     * 根据<code>loginId</code>比较对象的值是否相等。
     *
     * <p>只比较运行时类型相同的对象。</p>
     *
     * @param o 用来比较的另一个对象，允许为空
     * @return 对象满足此实现的相等条件时为真
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
     * 用<code>loginId</code>计算哈希值。
     *
     * @return 由参与相等比较的状态计算出的哈希值
     */
    @Override
    public int hashCode() {
        return Objects.hash(loginId);
    }

    /**
     * 生成用于调试和日志的对象描述，包含<code>loginId</code>、<code>realm</code>、<code>sessionConfig</code>。
     *
     * @return 表示当前对象状态的字符串
     */
    @Override
    public String toString() {
        return "AuthRequest{loginId=" + loginId
                + ", realm='" + realm + '\''
                + ", sessionConfig=" + sessionConfig
                + '}';
    }
}