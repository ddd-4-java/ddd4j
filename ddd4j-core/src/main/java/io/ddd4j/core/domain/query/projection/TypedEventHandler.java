package io.ddd4j.core.domain.query.projection;

/**
 * 单一事件类型处理器。
 *
 * @param <E> 事件类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public interface TypedEventHandler<E> {

    /**
     * 支持的事件类型名称。
     */
    String getEventType();

    /**
     * 支持的事件 Java 类型。
     */
    Class<E> getEventClass();

    /**
     * 处理事件。
     *
     * @param event 事件
     */
    void handle(E event);
}
