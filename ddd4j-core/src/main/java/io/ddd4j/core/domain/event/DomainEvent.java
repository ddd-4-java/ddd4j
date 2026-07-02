package io.ddd4j.core.domain.event;

import io.ddd4j.core.context.Contexts;
import io.ddd4j.core.context.SpiKeys;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.constant.ContextConstants;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 领域事件（纯 Java，零 Spring 依赖）
 * <p>
 * 1. 异步事件透传线程变量
 * 2. 租户策略
 * 3. 条件策略
 * <p>
 * 各框架适配层在启动期通过 {@link Contexts#register} 或 {@link ThreadContext#service(String, Class, Object)}
 * 将 {@link DomainEventPublisher} 注入到上下文（约定 key 参见 {@link SpiKeys#DOMAIN_EVENT_PUBLISHER}）。
 * 业务方调用 {@link #publish()} / {@link #publishAt(Date)} / {@link #publishIn(long)} 时，
 * 框架无关地按「线程优先 → 全局兜底」策略从 {@link Contexts} 查找 publisher。
 *
 * <h3>框架适配层注入示例</h3>
 * <pre>{@code
 * // Spring 适配层：在 ApplicationListener<ContextRefreshedEvent> 中
 * Contexts.register(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, springPublisher);
 *
 * // Quarkus 适配层：在 @ApplicationScoped Bean 的 @PostConstruct 中
 * Contexts.register(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, cdiPublisher);
 *
 * // Guice 适配层：在 Provider<DomainEventPublisher> 中
 * Contexts.register(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, guicePublisher);
 * }</pre>
 *
 * <h3>业务方调用（零框架耦合）</h3>
 * <pre>{@code
 * new OrderCreatedEvent(orderId, amount).publish();  // 自动从上下文查找 publisher
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
@Slf4j
public abstract class DomainEvent<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 默认主题（可通过系统属性 ddd4j.mq.default-topic 覆盖）
     * -- SETTER --
     *  设置默认主题

     */
    @Setter
    @Getter
    private static volatile String defaultTopic = System.getProperty("ddd4j.mq.default-topic", "DEFAULT");

    /**
     * 延迟发布调度器（懒初始化）
     */
    private static volatile ScheduledExecutorService scheduler;

    // 事件源
    private final T source;
    // 监听者能否执行的条件，用于控制事件监听器能否执行（策略模式）
    private Collection supports;
    // 结果
    @Setter
    private Object result;

    /**
     * 领域事件构造器
     *
     * @param source 事件内容
     */
    public DomainEvent(T source) {
        this.source = source;
    }

    /**
     * 领域事件构造器（接受 Object source，用于子类传入 Object 类型的 source）
     *
     * @param source   事件内容
     * @param supports 支持执行的条件
     */
    @SuppressWarnings("unchecked")
    public DomainEvent(Object source, Collection supports) {
        this.source = (T) source;
        this.supports = supports;
    }

    /**
     * 领域事件构造器（接受 Object source）
     *
     * @param source  事件内容
     * @param support 支持执行的条件
     */
    @SuppressWarnings("unchecked")
    public DomainEvent(Object source, Object support) {
        this.source = (T) source;
        this.supports = Collections.singleton(support);
    }

    private static ScheduledExecutorService getScheduler() {
        if (Objects.isNull(scheduler)) {
            synchronized (DomainEvent.class) {
                if (Objects.isNull(scheduler)) {
                    scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                        Thread t = new Thread(r, "ddd4j-domain-event-scheduler");
                        t.setDaemon(true);
                        return t;
                    });
                }
            }
        }
        return scheduler;
    }

    /**
     * 获取事件源
     *
     * @return 事件内容
     */
    @Deprecated
    public <S> S get() {
        return (S) source();
    }

    /**
     * 获取事件源
     *
     * @return 事件内容
     */
    public T source() {
        return source;
    }

    public <R> R result() {
        return (R) this.result;
    }

    /**
     * 租户判断
     * 使用方式：监听方法标注@EventListener(condition = "#event.tenantIn('xxx', 'xxx')")
     *
     * @param tenantIds 指定租户ID才能订阅
     * @return 该租户能否监听
     */
    public boolean tenantIn(String... tenantIds) {
        if (Objects.isNull(tenantIds)) return false;
        return Arrays.asList(tenantIds).contains(ThreadContext.get(ContextConstants.TENANT_ID));
    }

    /**
     * 条件判断（策略模式）
     * 使用方式：监听方法标注@EventListener(condition = "#event.supports('xxx', 'xxx')")
     *
     * @param supports 支持的类型
     * @return 该条件下能否监听
     */
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
     * 通过上下文查找的 DomainEventPublisher 发布事件。
     * <p>
     * 查找顺序：{@link ThreadContext}（线程级，请求级覆盖） → {@link BaseContext}（JVM 级全局默认）。
     * 框架适配层应在启动期通过 {@link Contexts#register(String, Class, Object)} 注入 publisher。
     *
     * @return 发布结果
     * @throws IllegalStateException 未找到匹配的 DomainEventPublisher
     */
    @SuppressWarnings("unchecked")
    public <R> R publish() {
        DomainEventPublisher publisher = Contexts.injectOrThrow(
                SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class);
        publisher.publish(this);
        return (R) result;
    }

    /**
     * 定时发布事件
     *
     * @param sendTime 发送时间
     */
    public void publishAt(Date sendTime) {
        long delay = sendTime.getTime() - System.currentTimeMillis();
        if (delay <= 0) {
            publish();
        } else {
            getScheduler().schedule((Runnable) this::publish, delay, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 延迟发布事件
     *
     * @param delayMillis 延迟毫秒数
     */
    public void publishIn(long delayMillis) {
        if (delayMillis <= 0) {
            publish();
        } else {
            getScheduler().schedule((Runnable) this::publish, delayMillis, TimeUnit.MILLISECONDS);
        }
    }
}