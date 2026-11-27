package io.ddd4j.core.cqrs.readmodel;

/**
 * 单一事件类型处理器。
 *
 * @param <E> 事件类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public interface TypedEventHandler<E> {

    /**
     * \u652f\u6301\u7684\u4e8b\u4ef6\u7c7b\u578b\u540d\u79f0\u3002
     *
     * @return \u4e8b\u4ef6\u7c7b\u578b\u540d\u79f0
     */
    String getEventType();

    /**
     * \u652f\u6301\u7684\u4e8b\u4ef6 Java \u7c7b\u578b\u3002
     *
     * @return \u4e8b\u4ef6 Java \u7c7b\u578b
     */
    Class<E> getEventClass();

    /**
     * 处理事件。
     *
     * @param event 事件
     */
    void handle(E event);
}
