package io.ddd4j.extension.express.infrastructure.event;

import io.ddd4j.extension.express.domain.event.RuleCreatedEvent;
import io.ddd4j.extension.express.domain.event.RuleDeletedEvent;
import io.ddd4j.extension.express.domain.event.RuleEventPublisher;
import io.ddd4j.extension.express.domain.event.RuleUpdatedEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * 规则事件发布者实现
 *
 * <p>基础设施层：基于 {@link Consumer} 的纯 Java 事件发布实现。
 * 通过构造函数注入事件处理器，负责将规则事件分发给注册的监听器。
 *
 * <p>注意：该实现为纯 Java 版本，去除了对 Spring {@code ApplicationEventPublisher} 的依赖。
 * 如果需要接入 Spring 事件机制，可在上层使用
 * {@code new SimpleRuleEventPublisher(eventPublisher::publishEvent)} 进行适配。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @version 1.0
 * @since 1.0
 */
@Slf4j
public class SpringRuleEventPublisher implements RuleEventPublisher {

    private final Consumer<Object> eventPublisher;

    /**
     * 默认构造函数：使用日志记录作为事件发布的默认行为。
     * 事件仅记录日志，不进行实际分发。
     */
    public SpringRuleEventPublisher() {
        this.eventPublisher = event -> log.debug("发布规则事件: {}", event);
    }

    /**
     * 构造函数：注入自定义事件处理器。
     *
     * @param eventPublisher 事件处理器，接收任意事件对象
     */
    public SpringRuleEventPublisher(Consumer<Object> eventPublisher) {
        this.eventPublisher = Objects.nonNull(eventPublisher)
                ? eventPublisher
                : event -> log.debug("发布规则事件: {}", event);
    }

    /**
     * 发布规则创建事件
     *
     * @param event 规则创建事件，不能为null
     */
    @Override
    public void publishRuleCreated(RuleCreatedEvent event) {
        if (Objects.nonNull(event)) {
            eventPublisher.accept(event);
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
            eventPublisher.accept(event);
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
            eventPublisher.accept(event);
        }
    }
}
