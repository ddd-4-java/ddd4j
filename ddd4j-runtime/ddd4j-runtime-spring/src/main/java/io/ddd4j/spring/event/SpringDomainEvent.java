package io.ddd4j.spring.event;

import io.ddd4j.spring.context.SpringContext;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEvent;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.Collections;
import java.util.Date;

/**
 * Spring 领域事件基类（继承 ApplicationEvent）
 * <p>
 * 仅在 Spring 环境下使用，Quarkus/Javalin 环境使用纯 Java DomainEvent 接口。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public abstract class SpringDomainEvent<T> extends ApplicationEvent {

    /** 事件支持的策略键集合（策略模式） */
    @Setter
    @Getter
    private Set<String> supportKeys;
    /** 事件处理结果 */
    @Setter
    private Object result;

    /**
     * 领域事件构造器
     *
     * @param source 事件内容
     */
    public SpringDomainEvent(T source) {
        super(source);
    }

    /**
     * 领域事件构造器
     *
     * @param source   事件内容
     * @param supports 支持执行的条件，配合supports方法使用
     */
    public SpringDomainEvent(T source, Collection<?> supportKeys) {
        super(source);
        this.supportKeys = new HashSet<>();
        for (Object key : supportKeys) {
            this.supportKeys.add(String.valueOf(key));
        }
    }

    /**
     * 领域事件构造器
     *
     * @param source  事件内容
     * @param support 支持执行的条件，配合supports方法使用
     */
    public SpringDomainEvent(T source, Object supportKey) {
        super(source);
        this.supportKeys = Collections.singleton(String.valueOf(supportKey));
    }

    /**
     * 获取事件源
     *
     * @return 事件内容
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    public <S> S get() {
        return (S) source();
    }

    /**
     * 获取事件源
     *
     * @return 事件内容
     */
    @SuppressWarnings("unchecked")
    public T source() {
        return (T) super.getSource();
    }

    @SuppressWarnings("unchecked")
    public <R> R result() {
        return (R) this.result;
    }

    /**
     * 立即发布事件
     */
    @SuppressWarnings("unchecked")
    public <R> R publish() {
        SpringContext.getApplicationContext().publishEvent(this);
        return (R) result;
    }

    /**
     * 定时发布事件
     *
     * @param sendTime 发送时间
     */
    public void publishAt(Date sendTime) {
        ThreadPoolTaskScheduler taskScheduler = SpringContext.getBean("taskScheduler", ThreadPoolTaskScheduler.class);
        taskScheduler.schedule(this::publish, sendTime.toInstant());
    }

    /**
     * 延迟发布事件
     *
     * @param delayMillis 延迟毫秒数
     */
    public void publishIn(long delayMillis) {
        publishAt(new Date(System.currentTimeMillis() + delayMillis));
    }
}
