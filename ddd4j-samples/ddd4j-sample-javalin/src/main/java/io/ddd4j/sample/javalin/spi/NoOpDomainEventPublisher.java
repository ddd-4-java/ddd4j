package io.ddd4j.sample.javalin.spi;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.ddd.event.EntityId;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Objects;

/**
 * 进程内领域事件发布者：No-Op 示例实现（仅打印）。
 *
 * <p>真实应用应基于 Guava EventBus / Reactor Sinks / Akka / 自研总线等实现。
 * 这里用最简化的方式演示 SPI 接口的契约。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
@Slf4j
public class NoOpDomainEventPublisher implements DomainEventPublisher {

    @Override
    public <ID extends EntityId> void publish(DomainEvent<ID> event) {
        if (Objects.isNull(event)) {
            return;
        }
        log.info("[DomainEvent] {}", event.getClass().getSimpleName());
    }

    @Override
    public <ID extends EntityId> void publishAll(Collection<DomainEvent<ID>> events) {
        if (Objects.nonNull(events)) {
            events.forEach(this::publish);
        }
    }
}
