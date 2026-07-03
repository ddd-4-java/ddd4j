package io.ddd4j.sample.quarkus.spi;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Collection;

/**
 * 进程内领域事件发布者：No-Op 示例实现（仅打印）。
 *
 * <p>本类为 Quarkus 风格实现：
 * <ul>
 *   <li>标注 {@link ApplicationScoped}，由 CDI 容器管理生命周期。</li>
 *   <li>ddd4j-runtime-quarkus 在启动期会扫描所有 {@link DomainEventPublisher} Bean，
 *       自动注入到 {@code BaseContext} 的 {@code SpiKeys.DOMAIN_EVENT_PUBLISHER} key 下。</li>
 * </ul>
 *
 * <p>真实应用应基于 Guava EventBus / Reactor Sinks / Akka / 自研总线等实现。
 * 这里用最简化的方式演示 SPI 接口的契约与 CDI 注入流程。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@ApplicationScoped
public class NoOpDomainEventPublisher implements DomainEventPublisher {

    @Override
    public <T> void publish(DomainEvent<T> event) {
        if (event == null) {
            return;
        }
        System.out.println("[DomainEvent] " + event.getClass().getSimpleName() + " -> " + event.source());
    }

    @Override
    public <T> void publishAll(Collection<DomainEvent<T>> events) {
        if (events != null) {
            events.forEach(this::publish);
        }
    }
}