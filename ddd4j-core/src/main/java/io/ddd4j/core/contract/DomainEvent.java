package io.ddd4j.core.contract;

import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.contract.constant.ContextConstants;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

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
 * 各框架适配层通过 {@link DomainEventPublisher} 发布事件。
 * 便捷方法 {@link #publish()} / {@link #publishAt(Date)} / {@link #publishIn(long)}
 * 使用静态注册的 {@link DomainEventPublisher} 实现。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public abstract class DomainEvent<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 默认主题（可通过系统属性 ddd4j.mq.default-topic 覆盖）
     */
    private static volatile String defaultTopic = System.getProperty("ddd4j.mq.default-topic", "DEFAULT");

    /**
     * 静态事件发布者注册（由框架适配层注入）
     */
    private static volatile DomainEventPublisher eventPublisher;

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

    /**
     * 注册事件发布者（由框架适配层调用）
     */
    public static void registerPublisher(DomainEventPublisher publisher) {
        eventPublisher = publisher;
    }

    /**
     * 获取当前注册的事件发布者
     */
    public static DomainEventPublisher getPublisher() {
        return eventPublisher;
    }

    public static String getDefaultTopic() {
        return defaultTopic;
    }

    /**
     * 设置默认主题
     */
    public static void setDefaultTopic(String topic) {
        defaultTopic = topic;
    }

    private static ScheduledExecutorService getScheduler() {
        if (java.util.Objects.isNull(scheduler)) {
            synchronized (DomainEvent.class) {
                if (java.util.Objects.isNull(scheduler)) {
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
     * 使用方式：监听方法标注 @EventListener(condition = "#event.tenantIn('xxx', 'xxx')")
     *
     * @param tenantIds 指定租户ID才能订阅
     * @return 该租户能否监听
     */
    public boolean tenantIn(String... tenantIds) {
        if (java.util.Objects.isNull(tenantIds)) {
            return false;
        }
        return Arrays.asList(tenantIds).contains(ThreadContext.get(ContextConstants.TENANT_ID));
    }

    /**
     * 条件判断（策略模式）
     * 使用方式：监听方法标注 @EventListener(condition = "#event.supports('xxx', 'xxx')")
     *
     * @param supports 支持的类型
     * @return 该条件下能否监听
     */
    public <S> boolean supports(S... supports) {
        if (java.util.Objects.isNull(this.supports) || java.util.Objects.isNull(supports)) {
            return false;
        }
        List<S> supportList = Arrays.asList(supports);
        for (Object support : this.supports) {
            if (supportList.contains(support)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 通过已注册的 DomainEventPublisher 发布事件
     *
     * @return 发布结果
     */
    @SuppressWarnings("unchecked")
    public <R> R publish() {
        if (java.util.Objects.isNull(eventPublisher)) {
            throw new IllegalStateException(
                    "DomainEventPublisher not registered. Call DomainEvent.registerPublisher() or use framework adapter.");
        }
        eventPublisher.publish(this);
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
