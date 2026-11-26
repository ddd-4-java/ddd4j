package io.ddd4j.sample.order.start;

import io.ddd4j.core.ddd.event.DomainEvent;
import lombok.extern.slf4j.Slf4j;

/** 控制台观察器，用于展示本地交易事件的订阅。 */
@Slf4j
public final class TradeEventLog {
    private TradeEventLog() {
    }

    public static void accept(Object event) {
        if (event instanceof DomainEvent<?>) {
            DomainEvent<?> domainEvent = (DomainEvent<?>) event;
            log.debug("Catalog event {}", domainEvent.getClass().getSimpleName());
        }
    }
}
