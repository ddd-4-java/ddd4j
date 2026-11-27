package io.ddd4j.spring.event;

import io.ddd4j.spring.context.SpringContext;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEvent;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.*;

/**
 * Spring 领域事件基类（继承 ApplicationEvent）
 * <p>
 * 仅在 Spring 环境下使用，Quarkus/Javalin 环境使用纯 Java DomainEvent 接口。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public abstract class SpringDomainEvent<T> extends ApplicationEvent {

    /**
     * 事件支持的策略键集合（策略模式）
     */
    @Setter
    @Getter
    private Set<String> supportKeys;
    /**
     * 事件处理结果
     */
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
     * \u9886\u57df\u4e8b\u4ef6\u6784\u9020\u5668
     * @param source \u4e8b\u4ef6\u5185\u5bb9
     * @param supportKeys \u652f\u6301\u6267\u884c\u7684\u6761\u4ef6\uff0c\u914d\u5408 supports \u65b9\u6cd5\u4f7f\u7528
     */
    public SpringDomainEvent(T source, Collection<?> supportKeys) {
        super(source);
        this.supportKeys = new HashSet<>();
        for (Object key : supportKeys) {
            this.supportKeys.add(String.valueOf(key));
        }
    }

    /**
     * \u9886\u57df\u4e8b\u4ef6\u6784\u9020\u5668
     * @param source \u4e8b\u4ef6\u5185\u5bb9
     * @param supportKey \u652f\u6301\u6267\u884c\u7684\u6761\u4ef6\uff0c\u914d\u5408 supports \u65b9\u6cd5\u4f7f\u7528
     */
    public SpringDomainEvent(T source, Object supportKey) {
        super(source);
        this.supportKeys = Collections.singleton(String.valueOf(supportKey));
    }

    /**
     * \u83b7\u53d6\u4e8b\u4ef6\u6e90
     * @return \u4e8b\u4ef6\u5185\u5bb9
     *
     * @param <S> \u4e8b\u4ef6\u5185\u5bb9\u7c7b\u578b
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

    /**
     * \u83b7\u53d6\u4e8b\u4ef6\u5904\u7406\u7ed3\u679c\u3002
     * @param <R> \u7ed3\u679c\u7c7b\u578b
     * @return \u5df2\u8bb0\u5f55\u7684\u5904\u7406\u7ed3\u679c
     */
    @SuppressWarnings("unchecked")
    public <R> R result() {
        return (R) this.result;
    }

    /**
     * \u7acb\u5373\u53d1\u5e03\u4e8b\u4ef6
     *
     * @param <R> \u7ed3\u679c\u7c7b\u578b
     * @return \u53d1\u5e03\u540e\u7684\u5904\u7406\u7ed3\u679c
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
