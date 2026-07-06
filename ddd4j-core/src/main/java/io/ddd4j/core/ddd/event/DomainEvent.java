package io.ddd4j.core.ddd.event;

import io.ddd4j.core.context.Contexts;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.constant.ContextConstants;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.fuin.ddd4j.core.EntityId;
import org.fuin.ddd4j.core.EntityIdPath;
import org.fuin.ddd4j.jackson.AbstractDomainEvent;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;

/**
 * 领域事件基类（纯 Java，零 Spring 依赖）。
 *
 * <p>融合了进程内事件和事件溯源（ES）两条轨道：
 * <ul>
 *   <li><b>进程内事件</b> —— 通过 {@link #publish()} 发布到 {@link DomainEventPublisher}，
 *       支持 {@link #tenantIn(String...)} 租户过滤和 {@link #supports(Object...)} 策略过滤</li>
 *   <li><b>事件溯源（ES）</b> —— 继承 fuinorg {@link AbstractDomainEvent}，
 *       包含 {@code eventId} / {@code entityIdPath} / {@code aggregateVersion} /
 *       {@code correlationId} / {@code causationId} 完整元数据，可序列化持久化到 EventStore</li>
 * </ul>
 *
 * <h3>框架适配层注入 publisher</h3>
 * <pre>{@code
 * Contexts.register(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, publisher);
 * }</pre>
 *
 * <h3>业务方发布事件</h3>
 * <pre>{@code
 * new OrderCreatedEvent(orderId, amount).publish();
 * }</pre>
 *
 * @param <ID> 聚合根标识类型（必须是 fuinorg {@link org.fuin.ddd4j.core.AggregateRootId} 子类型）
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
@Slf4j
public abstract class DomainEvent<ID extends EntityId> extends AbstractDomainEvent<ID> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 默认主题（可通过系统属性 {@code ddd4j.mq.default-topic} 覆盖）。
     */
    @Setter
    @Getter
    private static volatile String defaultTopic = System.getProperty("ddd4j.mq.default-topic", "DEFAULT");

    /**
     * 监听者能否执行的条件，用于控制事件监听器能否执行（策略模式）。
     */
    @Setter
    private Collection supports;

    /**
     * 事件处理结果（由 publisher 写回）。
     */
    @Setter
    private Object result;

    /**
     * 默认构造器（Jackson 反序列化 + 事件回放时使用，子类必须保留无参构造）。
     */
    protected DomainEvent() {
        super();
    }

    /**
     * 构造领域事件。
     *
     * @param entityIdPath 从聚合根到事件源的路径
     */
    protected DomainEvent(EntityIdPath entityIdPath) {
        super(entityIdPath);
    }

    /**
     * 构造领域事件（带因果关联）。
     *
     * @param entityIdPath   从聚合根到事件源的路径
     * @param causationEvent 导致本事件的前置事件
     */
    protected DomainEvent(EntityIdPath entityIdPath, org.fuin.ddd4j.core.Event causationEvent) {
        super(entityIdPath, causationEvent);
    }

    /**
     * 获取事件处理结果。
     *
     * @param <R> 结果类型
     * @return 处理结果
     */
    @SuppressWarnings("unchecked")
    public <R> R result() {
        return (R) this.result;
    }

    /**
     * 租户判断。
     *
     * <p>使用方式：监听方法标注
     * {@code @EventListener(condition = "#event.tenantIn('xxx', 'xxx')")}
     *
     * @param tenantIds 指定租户 ID 才能订阅
     * @return 该租户能否监听
     */
    public boolean tenantIn(String... tenantIds) {
        if (Objects.isNull(tenantIds)) return false;
        return Arrays.asList(tenantIds).contains(ThreadContext.get(ContextConstants.TENANT_ID));
    }

    /**
     * 条件判断（策略模式）。
     *
     * <p>使用方式：监听方法标注
     * {@code @EventListener(condition = "#event.supports('xxx', 'xxx')")}
     *
     * @param supports 支持的类型
     * @return 该条件下能否监听
     */
    @SuppressWarnings("unchecked")
    public <S> boolean supports(S... supports) {
        if (Objects.isNull(this.supports) || Objects.isNull(supports)) return false;
        List<S> supportList = Arrays.asList(supports);
        for (Object support : this.supports) {
            if (supportList.contains(support)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 发布事件。
     *
     * <p>通过 {@link Contexts} 按「线程优先 → 全局兜底」策略查找 {@link DomainEventPublisher}。
     * 框架适配层应在启动期通过 {@link Contexts#register(String, Class, Object)} 注入 publisher。
     *
     * @param <R> 返回类型
     * @return 发布结果
     * @throws IllegalStateException 未找到 DomainEventPublisher
     */
    @SuppressWarnings("unchecked")
    public <R> R publish() {
        DomainEventPublisher publisher = Contexts.injectOrThrow(
                SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class);
        publisher.publish(this);
        return (R) result;
    }
}
