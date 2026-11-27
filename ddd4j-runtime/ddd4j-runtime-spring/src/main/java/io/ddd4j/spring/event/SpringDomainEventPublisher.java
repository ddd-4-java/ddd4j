package io.ddd4j.spring.event;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.ddd.event.EntityId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Spring 实现的领域事件发布者
 * <p>
 * 使用 Spring ApplicationEventPublisher 发布领域事件。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@Component
public class SpringDomainEventPublisher implements DomainEventPublisher {

    /**
     * Spring 应用事件发布器
     */
    private final ApplicationEventPublisher publisher;

    /**
     * \u521b\u5efa\u57fa\u4e8e Spring \u5e94\u7528\u4e8b\u4ef6\u7684\u9886\u57df\u4e8b\u4ef6\u53d1\u5e03\u5668\u3002
     * @param publisher Spring \u5e94\u7528\u4e8b\u4ef6\u53d1\u5e03\u5668
     */
    public SpringDomainEventPublisher(ApplicationEventPublisher publisher) {
        log.debug("Initializing SpringDomainEventPublisher");
        Objects.requireNonNull(publisher, "ApplicationEventPublisher cannot be null");
        this.publisher = publisher;
    }

    /**
     * 将传入的事件交给当前发布通道。
     *
     * <p>Spring 实现调用 <code>log.warn("Attempted to publish null domain event")</code>。</p>
     *
     * @param <ID> 实体标识类型
     * @param event 领域事件
     */
    @Override
    public <ID extends EntityId> void publish(DomainEvent<ID> event) {
        if (Objects.isNull(event)) {
            log.warn("Attempted to publish null domain event");
            return;
        }
        log.debug("Publishing domain event: {}", event.getClass().getSimpleName());
        publisher.publishEvent(event);
    }
}
