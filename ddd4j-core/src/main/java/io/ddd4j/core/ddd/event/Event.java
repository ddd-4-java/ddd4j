package io.ddd4j.core.ddd.event;

import java.io.Serializable;
import java.time.ZonedDateTime;

/**
 * 事件共有元数据契约。
 */
public interface Event extends Serializable {

    /**
     * @return 全局事件标识
     */
    EventId getEventId();

    /**
     * @return 事件类型
     */
    EventType getEventType();

    /**
     * @return 事件产生时间
     */
    ZonedDateTime getEventTimestamp();

    /**
     * @return 关联事件标识；没有时返回 {@code null}
     */
    EventId getCorrelationId();

    /**
     * @return 直接因果事件标识；没有时返回 {@code null}
     */
    EventId getCausationId();

}
