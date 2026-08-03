package io.ddd4j.core.ddd.event;

import com.fasterxml.jackson.annotation.JsonValue;
import io.ddd4j.kit.lang.StrKit;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * 全局唯一事件标识。
 */
public final class EventId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID value;

    /**
     * 创建随机事件标识。
     */
    public EventId() {
        this(UUID.randomUUID());
    }

    /**
     * 使用 UUID 创建事件标识。
     *
     * @param value UUID 值
     */
    public EventId(UUID value) {
        this.value = Objects.requireNonNull(value, "value must not be null");
    }

    /**
     * 解析事件标识。
     *
     * @param value UUID 文本；{@code null} 返回 {@code null}
     * @return 事件标识或 {@code null}
     */
    public static EventId valueOf(String value) {
        if (StrKit.isBlank(value)) {
            return null;
        }
        return new EventId(UUID.fromString(value));
    }

    /**
     * 返回 UUID 值。
     *
     * @return UUID 值
     */
    public UUID asUuid() {
        return value;
    }

    /**
     * 返回 UUID 文本。
     *
     * @return UUID 文本
     */
    @JsonValue
    public String asString() {
        return value.toString();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof EventId that)) {
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
        return asString();
    }

}
