package io.ddd4j.mq.spi;

import io.ddd4j.core.contract.MQEvent;
import io.ddd4j.mq.contract.MQDestination;

/**
 * 抽象事件发布契约。
 * <p>
 * 通过下沉发布器签名到 SPI，解除 ddd4j-extensions（集成测试）反向依赖
 * ddd4j-mq（核心发布器）造成的跨社区耦合。
 * </p>
 */
public interface MQEventPublisherContract {

	<T extends MQEvent> void publish(T event, MQDestination destination);

	default void publish(MQEvent event) {
		publish(event, MQDestination.from(event));
	}
}