package io.ddd4j.core.cqrs.readmodel;

import java.io.Serializable;

/** 增量投影的下一个待处理事件位置。 */
public interface ProjectionPosition extends Serializable {
    String getStreamId();
    long getNextEventNumber();
    ProjectionPosition withNextEventNumber(long nextEventNumber);
}
