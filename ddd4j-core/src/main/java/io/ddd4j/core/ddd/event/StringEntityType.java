package io.ddd4j.core.ddd.event;

import com.fasterxml.jackson.annotation.JsonValue;
import io.ddd4j.kit.lang.StrKit;

import java.io.Serial;
import java.util.Objects;

/**
 * 基于字符串的实体类型值对象。
 */
public final class StringEntityType implements EntityType {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String value;

    /**
     * 创建实体类型。
     *
     * @param value 非空白类型文本
     */
    public StringEntityType(String value) {
        if (StrKit.isBlank(value)) {
            throw new IllegalArgumentException("Entity type must not be blank");
        }
        this.value = value;
    }

    @Override
    @JsonValue
    public String asString() {
        return value;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof StringEntityType that)) {
            return false;
        }
        return Objects.equals(value, that.value);
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
