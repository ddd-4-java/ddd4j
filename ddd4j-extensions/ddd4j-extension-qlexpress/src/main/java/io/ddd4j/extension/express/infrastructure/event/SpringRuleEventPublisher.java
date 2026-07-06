package io.ddd4j.extension.express.infrastructure.event;

import io.ddd4j.extension.express.domain.event.RuleEventPublisher;
import io.ddd4j.extension.express.domain.event.RuleCreatedEvent;
import io.ddd4j.extension.express.domain.event.RuleDeletedEvent;
import io.ddd4j.extension.express.domain.event.RuleUpdatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Spring规则事件发布者实现
 *
 * <p>基础设施层：使用Spring的ApplicationEventPublisher实现事件发布。
 * 负责将规则事件发布到Spring事件系统中。
 *
 * <p>注意：此类是可选的，只有在使用Spring事件机制时才需要。
 * 如果不需要事件机制，可以不使用此类。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @version 1.0
 * @since 1.0
 */
@Component
public class SpringRuleEventPublisher implements RuleEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    /**
     * 构造函数
     *
     * @param eventPublisher Spring事件发布器
     */
    public SpringRuleEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * 发布规则创建事件
     *
     * @param event 规则创建事件，不能为null
     */
    @Override
    public void publishRuleCreated(RuleCreatedEvent event) {
        // Spring的ApplicationEventPublisher可以发布任何对象，不一定是ApplicationEvent的子类
        if (Objects.nonNull(event)) {
            eventPublisher.publishEvent(event);
        }
    }

    /**
     * 发布规则更新事件
     *
     * @param event 规则更新事件，不能为null
     */
    @Override
    public void publishRuleUpdated(RuleUpdatedEvent event) {
        if (Objects.nonNull(event)) {
            eventPublisher.publishEvent(event);
        }
    }

    /**
     * 发布规则删除事件
     *
     * @param event 规则删除事件，不能为null
     */
    @Override
    public void publishRuleDeleted(RuleDeletedEvent event) {
        if (Objects.nonNull(event)) {
            eventPublisher.publishEvent(event);
        }
    }
}

