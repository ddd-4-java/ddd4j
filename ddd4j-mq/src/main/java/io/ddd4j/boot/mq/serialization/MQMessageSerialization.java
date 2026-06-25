package io.ddd4j.boot.mq.serialization;

import io.ddd4j.boot.mq.core.MQEventSerialization;

/**
 * 消息序列化契约（与 {@link MQEventSerialization} 对齐，新代码优先引用本接口）。
 */
public interface MQMessageSerialization extends MQEventSerialization {
}
