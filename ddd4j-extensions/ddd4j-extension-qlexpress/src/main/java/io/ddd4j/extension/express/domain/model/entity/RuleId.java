package io.ddd4j.extension.express.domain.model.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * 规则ID值对象
 * 
 * <p>领域模型：值对象，不可变，通过值相等性判断。
 * 封装规则ID的创建和转换逻辑，提供类型安全。
 * 
 * <p>支持从String和Long类型创建，可以转换为Long类型。
 * 
 * @author ddd4j
 * @version 1.0
 * @since 1.0
 */
public final class RuleId implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String value;

    /**
     * 构造函数
     * 
     * @param value ID值，不能为null
     * @throws IllegalArgumentException 如果value为null
     */
    public RuleId(final String value) {
        if (value == null) {
            throw new IllegalArgumentException("RuleId value cannot be null");
        }
        this.value = value;
    }

    /**
     * 从字符串创建RuleId
     * 
     * @param value 字符串值，不能为null
     * @return RuleId实例
     * @throws IllegalArgumentException 如果value为null
     */
    public static RuleId valueOf(final String value) {
        return new RuleId(value);
    }

    /**
     * 从Long创建RuleId
     * 
     * @param value Long值，不能为null
     * @return RuleId实例
     * @throws IllegalArgumentException 如果value为null
     */
    public static RuleId valueOf(final Long value) {
        if (value == null) {
            throw new IllegalArgumentException("RuleId value cannot be null");
        }
        return new RuleId(String.valueOf(value));
    }

    /**
     * 获取ID值
     * 
     * @return ID的字符串值
     */
    public String getValue() {
        return value;
    }

    /**
     * 转换为Long类型
     * 
     * <p>如果ID值可以解析为Long，返回Long值；否则返回null。
     * 
     * @return Long值，如果无法解析返回null
     */
    public Long toLong() {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RuleId ruleId = (RuleId) o;
        return Objects.equals(value, ruleId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
