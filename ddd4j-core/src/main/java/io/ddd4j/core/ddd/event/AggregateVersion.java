package io.ddd4j.core.ddd.event;

import com.fasterxml.jackson.annotation.JsonValue;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * 聚合根的单调版本号。
 */
public final class AggregateVersion implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int value;

    /**
     * 创建版本号。
     *
     * @param value 大于等于零的版本号
     */
    public AggregateVersion(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("Aggregate version must not be negative");
        }
        this.value = value;
    }

    /**
     * 返回版本号。
     *
     * @return 版本号
     */
    @JsonValue
    public int asInt() {
        return value;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof AggregateVersion that)) {
            return false;
        }
        return value == that.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }

}
