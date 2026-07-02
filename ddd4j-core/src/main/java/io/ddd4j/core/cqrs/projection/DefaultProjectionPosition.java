package io.ddd4j.core.cqrs.projection;

import io.ddd4j.kit.lang.StrKit;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Objects;

/**
 * 默认不可变投影位置。
 *
 * <p>用于纯 Java 场景、测试场景，以及框架适配层没有自己的持久化实体时的
 * 最小实现。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Getter
@ToString
@EqualsAndHashCode
public final class DefaultProjectionPosition implements ProjectionPosition {

    private static final long serialVersionUID = 1L;

    private final String streamId;

    private final long nextEventNumber;

    public DefaultProjectionPosition(String streamId, long nextEventNumber) {
        if (StrKit.isBlank(streamId)) {
            throw new IllegalArgumentException("streamId must not be blank");
        }
        if (nextEventNumber < 0) {
            throw new IllegalArgumentException("nextEventNumber must not be negative");
        }
        this.streamId = streamId;
        this.nextEventNumber = nextEventNumber;
    }

    public static DefaultProjectionPosition zero(String streamId) {
        return new DefaultProjectionPosition(streamId, 0);
    }

    @Override
    public ProjectionPosition withNextEventNumber(long nextEventNumber) {
        if (nextEventNumber == this.nextEventNumber) {
            return this;
        }
        return new DefaultProjectionPosition(streamId, nextEventNumber);
    }

    public boolean isSameStream(ProjectionPosition position) {
        return Objects.nonNull(position) && Objects.equals(streamId, position.getStreamId());
    }
}
