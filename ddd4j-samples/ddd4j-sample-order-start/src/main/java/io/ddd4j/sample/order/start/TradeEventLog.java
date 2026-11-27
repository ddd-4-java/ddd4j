package io.ddd4j.sample.order.start;

import io.ddd4j.core.ddd.event.DomainEvent;
import lombok.extern.slf4j.Slf4j;

/** 控制台观察器，用于展示本地交易事件的订阅。 */
@Slf4j
public final class TradeEventLog {
    /** \u9650\u5236\u5de5\u5177\u7c7b\u5b9e\u4f8b\u5316\u3002 */
    private TradeEventLog() {
    }

    /**
     * 记录接收到的领域事件类型。
     *
     * @param event 框架发出的事件
     */
    public static void accept(Object event) {
        if (event instanceof DomainEvent<?> domainEvent) {
            log.debug("Catalog event {}", domainEvent.getClass().getSimpleName());
        }
    }
}
