package io.ddd4j.core.ddd.event;

import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.Contexts;
import io.ddd4j.core.context.ThreadContext;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.fuin.ddd4j.core.EntityId;
import org.fuin.ddd4j.core.EntityIdPath;
import org.fuin.ddd4j.core.EventType;
import org.fuin.ddd4j.jackson.AbstractDomainEvent;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

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
@SuppressWarnings("unchecked")
public abstract class DomainEvent<ID extends EntityId> extends AbstractDomainEvent<ID> implements Serializable {

    private static final ClassValue<EventType> EVENT_TYPES = new ClassValue<>() {
        @Override
        protected EventType computeValue(Class<?> type) {
            return new EventType(type.getSimpleName());
        }
    };

    /**
     * 默认主题（可通过系统属性 {@code ddd4j.mq.default-topic} 覆盖）。
     */
    @Setter
    @Getter
    private static volatile String defaultTopic = System.getProperty("ddd4j.mq.default-topic", "DEFAULT");

    /**
     * 事件支持的策略键集合（策略模式）。
     *
     * <p>监听器通过 {@link @EventListener#supports()} 声明它处理哪些策略键，
     * 消费时框架检查事件持有的 {@code supportKeys} 与监听器声明的键是否有交集。
     * 为 {@code null} 或空时不做策略过滤。
     */
    @Setter
    @Getter
    private Set<String> supportKeys;

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
     * 使用字符串实体标识构造领域事件。
     *
     * <p>适用于尚未定义专用实体标识值对象的轻量业务；复杂领域应优先传入
     * {@link EntityIdPath}，保留完整聚合路径语义。</p>
     *
     * @param entityId 非空字符串实体标识
     */
    protected DomainEvent(String entityId) {
        this(new EntityIdPath(new StringEntityId(entityId)));
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
     * @param entityIdPath 从聚合根到事件源的路径
     * @param respondTo    导致本事件的前置事件
     */
    protected DomainEvent(EntityIdPath entityIdPath, org.fuin.ddd4j.core.Event respondTo) {
        super(entityIdPath, respondTo);
    }

    /**
     * 获取面向业务日志和兼容监听器的字符串事件源。
     *
     * @return 当前事件实体标识的字符串形式
     */
    public String source() {
        return getEntityId().asString();
    }

    /**
     * 默认以事件类简单名称作为稳定事件类型。
     *
     * @return 事件类型
     */
    @Override
    public EventType getEventType() {
        return EVENT_TYPES.get(getClass());
    }

    /**
     * 获取事件处理结果。
     *
     * @param <R> 结果类型
     * @return 处理结果
     */
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
        if (Objects.isNull(tenantIds)) {
            return Boolean.FALSE;
        }
        String tenantId = ThreadContext.get(ContextConstants.TENANT_ID);
        return Arrays.asList(tenantIds).contains(tenantId);
    }

    /**
     * 策略匹配：检查事件持有的 {@link #supportKeys} 是否包含给定的任意一个键。
     *
     * <p>使用方式：监听方法标注
     * {@code @EventListener(condition = "#event.supports('paid', 'shipped')")}
     *
     * @param keys 监听器声明的策略键
     * @return 事件支持任一键时 {@code true}；事件未设置策略或无交集时 {@code false}
     */
    public boolean supports(String... keys) {
        if (Objects.isNull(supportKeys) || supportKeys.isEmpty() || Objects.isNull(keys) || keys.length == 0) {
            return false;
        }
        for (String key : keys) {
            if (supportKeys.contains(key)) {
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
    public <R> R publish() {
        DomainEventPublisher publisher = Contexts.getOrThrow(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class);
        publisher.publish(this);
        return (R) result;
    }

}
