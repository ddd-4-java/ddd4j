package io.ddd4j.mq.test;

import io.ddd4j.core.contract.MQEvent;

/**
 * MQ 集成测试统一冒烟事件（空载荷，仅携带 topic/tag/tenantId）。
 * <p>
 * 取代各 mq-* 模块 ContainerIT 中重复声明的 {@code DemoPublishEvent} 嵌套类。
 */
public class SmokePublishEvent extends MQEvent {
}
