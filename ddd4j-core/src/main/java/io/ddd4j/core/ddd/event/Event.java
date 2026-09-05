package io.ddd4j.core.ddd.event;
import java.io.Serializable;
import java.time.ZonedDateTime;
/**
 * 事件共有元数据契约。
 */
public interface Event extends Serializable {
    EventId getEventId();
    EventType getEventType();
    ZonedDateTime getEventTimestamp();
    EventId getCorrelationId();
    EventId getCausationId();
}
