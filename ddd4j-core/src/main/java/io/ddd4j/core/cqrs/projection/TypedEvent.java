package io.ddd4j.core.cqrs.projection;

/**
 * 可被类型分发器识别的事件。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.4.x
 */
public interface TypedEvent {

    /**
     * 事件类型名称。
     */
    String getEventType();
}
