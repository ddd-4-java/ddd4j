package io.ddd4j.core.ddd.event;

import java.io.Serializable;
import java.time.ZonedDateTime;

/**
 * 事件共有元数据契约。
 */
public interface Event extends Serializable {

    /**
     * 读取用于全局标识事件的编号。
     *
     * @return \u5168\u5c40\u4e8b\u4ef6\u6807\u8bc6
     */
    EventId getEventId();

    /**
     * 读取事件在处理器注册和路由时使用的类型。
     *
     * @return \u4e8b\u4ef6\u7c7b\u578b
     */
    EventType getEventType();

    /**
     * 读取\u4e8b\u4ef6\u4ea7\u751f\u65f6\u95f4。
     *
     * @return \u4e8b\u4ef6\u4ea7\u751f\u65f6\u95f4
     */
    ZonedDateTime getEventTimestamp();

    /**
     * 读取当前事件所属流程的关联标识。
     *
     * @return \u5173\u8054\u4e8b\u4ef6\u6807\u8bc6\uff1b\u6ca1\u6709\u65f6\u8fd4\u56de {@code null}
     */
    EventId getCorrelationId();

    /**
     * 读取直接触发当前事件的原因标识。
     *
     * @return \u76f4\u63a5\u56e0\u679c\u4e8b\u4ef6\u6807\u8bc6\uff1b\u6ca1\u6709\u65f6\u8fd4\u56de {@code null}
     */
    EventId getCausationId();

}
