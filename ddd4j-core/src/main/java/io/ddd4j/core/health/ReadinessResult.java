package io.ddd4j.core.health;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.ddd4j.kit.lang.StrKit;

import java.util.Map;
import java.util.Objects;

/**
 * 单个依赖的就绪检查结果。
 */

public final class ReadinessResult {

    /** Java 序列化版本标识。 */
    private static final long serialVersionUID = 0L;

    /** \u5f53\u524d\u5c31\u7eea\u68c0\u67e5\u7684\u540d\u79f0\u3002 */
    private final String name;

    /** \u5f53\u524d\u88ab\u68c0\u67e5\u7ec4\u4ef6\u662f\u5426\u5c31\u7eea\u3002 */
    private final boolean ready;

    /** \u89e3\u91ca\u68c0\u67e5\u7ed3\u679c\u7684\u9644\u52a0\u8bca\u65ad\u4fe1\u606f\u3002 */
    private final Map<String, String> details;

    /**
     * 初始化 <code>ReadinessResult</code> 使用的 <code>name</code>、<code>ready</code>、<code>details</code>。
     *
     * @param name \u7a33\u5b9a\u7684\u4f9d\u8d56\u6807\u8bc6\uff0c\u4f8b\u5982 {@code postgresql} \u6216 {@code redis}
     * @param ready \u662f\u5426\u53ef\u63a5\u53d7\u6d41\u91cf
     * @param details \u53ef\u5b89\u5168\u66b4\u9732\u7684\u8bca\u65ad\u4fe1\u606f\uff0c\u7981\u6b62\u653e\u5165\u5bc6\u7801\u3001\u4ee4\u724c\u6216\u8fde\u63a5\u4e32
     */

    @JsonCreator()
    public ReadinessResult(@JsonProperty("name") String name, @JsonProperty("ready") boolean ready, @JsonProperty("details") Map<String, String> details) {
        if (StrKit.isBlank(name)) {
            throw new IllegalArgumentException("readiness contributor name must not be blank");
        }
        details = Map.copyOf(Objects.requireNonNullElse(details, Map.of()));
        this.name = name;
        this.ready = ready;
        this.details = details;
    }

    /**
     * 创建可接收流量的结果。
     *
     * @param name 依赖标识
     * @return 就绪结果
     */
    public static ReadinessResult ready(String name) {
        return new ReadinessResult(name, true, Map.of());
    }

    /**
     * 创建不可接收流量的结果。
     *
     * @param name   依赖标识
     * @param reason 可安全暴露的失败原因
     * @return 未就绪结果
     */
    public static ReadinessResult unavailable(String name, String reason) {
        return new ReadinessResult(name, false, StrKit.isBlank(reason) ? Map.of() : Map.of("reason", reason));
    }

    /**
     * \u83b7\u53d6\u4f9d\u8d56\u540d\u79f0\uff0c\u4f9b\u4e1a\u52a1\u8c03\u7528\u53ca JSON \u5e8f\u5217\u5316\u4f7f\u7528\u3002
     * @return \u7a33\u5b9a\u7684\u4f9d\u8d56\u540d\u79f0
     */
    @JsonProperty("name")
    public String name() {
        return name;
    }

    /**
     * \u83b7\u53d6\u5c31\u7eea\u72b6\u6001\uff0c\u4f9b\u4e1a\u52a1\u8c03\u7528\u53ca JSON \u5e8f\u5217\u5316\u4f7f\u7528\u3002
     * @return \u8be5\u4f9d\u8d56\u5c31\u7eea\u65f6\u4e3a true
     */
    @JsonProperty("ready")
    public boolean ready() {
        return ready;
    }

    /**
     * \u83b7\u53d6\u8bca\u65ad\u8be6\u60c5\uff0c\u4f9b\u4e1a\u52a1\u8c03\u7528\u53ca JSON \u5e8f\u5217\u5316\u4f7f\u7528\u3002
     * @return \u4e0d\u53ef\u53d8\u7684\u8bca\u65ad\u8be6\u60c5
     */
    @JsonProperty("details")
    public Map<String, String> details() {
        return details;
    }

    /**
     * 根据<code>name</code>、<code>details</code>、<code>ready</code>比较对象的值是否相等。
     *
     * <p>只比较运行时类型相同的对象。</p>
     *
     * @param obj 用来比较或转换的输入对象
     * @return 对象满足此实现的相等条件时为真
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (Objects.isNull(obj) || getClass() != obj.getClass()) {
            return false;
        }
        ReadinessResult other = (ReadinessResult) obj;
        return Objects.equals(this.name, other.name) && this.ready == other.ready && Objects.equals(this.details, other.details);
    }

    /**
     * 计算与对象相等条件一致的哈希值。
     *
     * @return 由参与相等比较的状态计算出的哈希值
     */
    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Objects.hashCode(name);
        result = 31 * result + Boolean.hashCode(ready);
        result = 31 * result + Objects.hashCode(details);
        return result;
    }

    /**
     * 生成用于调试和日志的对象描述，包含<code>name</code>、<code>ready</code>、<code>details</code>。
     *
     * @return 表示当前对象状态的字符串
     */
    @Override
    public String toString() {
        return "ReadinessResult[name=" + name + ", ready=" + ready + ", details=" + details + "]";
    }
}
