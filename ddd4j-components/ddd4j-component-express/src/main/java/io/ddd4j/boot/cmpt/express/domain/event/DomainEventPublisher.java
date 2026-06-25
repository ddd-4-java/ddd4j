package io.ddd4j.boot.cmpt.express.domain.event;

/**
 * 领域事件发布者接口
 * 
 * <p>领域层接口：定义事件发布的抽象。
 * 实现应该在基础设施层或应用层，使用Spring的事件机制或其他消息中间件。
 * 
 * <p>实现类：
 * <ul>
 *   <li>SpringDomainEventPublisher - 使用Spring的ApplicationEventPublisher</li>
 * </ul>
 * 
 * @author ddd4j
 * @version 1.0
 * @since 1.0
 */
public interface DomainEventPublisher {

    /**
     * 发布规则创建事件
     * 
     * @param event 规则创建事件，不能为null
     */
    void publishRuleCreated(RuleCreatedEvent event);

    /**
     * 发布规则更新事件
     * 
     * @param event 规则更新事件，不能为null
     */
    void publishRuleUpdated(RuleUpdatedEvent event);

    /**
     * 发布规则删除事件
     * 
     * @param event 规则删除事件，不能为null
     */
    void publishRuleDeleted(RuleDeletedEvent event);
}

