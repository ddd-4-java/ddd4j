package io.ddd4j.guice.cqrs;

import io.ddd4j.core.cqrs.projection.ProjectionPosition;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

/**
 * Guice 环境默认的内存投影位置对象。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GuiceProjectionPosition implements ProjectionPosition, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String streamId;
    private long nextEventNumber;

    @Override
    public ProjectionPosition withNextEventNumber(long nextEventNumber) {
        this.nextEventNumber = nextEventNumber;
        return this;
    }
}
