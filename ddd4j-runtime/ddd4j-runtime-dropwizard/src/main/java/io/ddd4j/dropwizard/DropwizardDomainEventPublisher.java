package io.ddd4j.dropwizard;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.ddd.event.EntityId;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Dropwizard 显式监听器领域事件发布器。
 */
@Slf4j
public final class DropwizardDomainEventPublisher implements DomainEventPublisher {

    /** \u63a5\u6536\u8fd0\u884c\u65f6\u53d1\u5e03\u9886\u57df\u4e8b\u4ef6\u7684\u76d1\u542c\u56de\u8c03\u3002 */
    private final List<Consumer<Object>> listeners;

    /**
     * \u4f7f\u7528\u6307\u5b9a\u4e8b\u4ef6\u76d1\u542c\u5668\u521b\u5efa\u53d1\u5e03\u5668\u3002
     * @param listeners \u4e8b\u4ef6\u76d1\u542c\u5668
     * @throws NullPointerException \u76d1\u542c\u5668\u96c6\u5408\u6216\u5176\u4e2d\u4efb\u4e00\u5143\u7d20\u4e3a null
     */
    public DropwizardDomainEventPublisher(Collection<Consumer<Object>> listeners) {
        this.listeners = List.copyOf(Objects.requireNonNull(listeners, "listeners must not be null"));
    }

    /**
     * 将传入的事件交给当前发布通道。
     *
     * <p>Dropwizard 实现调用 <code>publish((Object)event)</code>。</p>
     *
     * @param <ID> 实体标识类型
     * @param event 领域事件
     */
    @Override
    public <ID extends EntityId> void publish(DomainEvent<ID> event) {
        publish((Object) event);
    }

    /**
     * 将传入的事件交给当前发布通道。
     *
     * <p>Dropwizard 实现调用 <code>log.debug("Publishing Dropwizard event: {}", event.getClass().getName())</code>。</p>
     *
     * @param event 任意事件对象
     */
    @Override
    public void publish(Object event) {
        if (Objects.nonNull(event)) {
            log.debug("Publishing Dropwizard event: {}", event.getClass().getName());
            listeners.forEach(listener -> listener.accept(event));
        }
    }
}
