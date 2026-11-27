package io.ddd4j.core.cqrs.readmodel;

/**
 * 可被类型分发器识别的事件。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public interface TypedEvent {

    /**
     * \u4e8b\u4ef6\u7c7b\u578b\u540d\u79f0\u3002
     *
     * @return \u4e8b\u4ef6\u7c7b\u578b\u540d\u79f0
     */
    String getEventType();
}
