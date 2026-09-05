package io.ddd4j.core.cqrs.readmodel;

import java.util.Collection;

/**
 * 框架无关的增量投影视图。
 *
 * @param <E> 事件类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public interface ProjectionView<E> {
    String getName();
    default String getStreamId() { return getName(); }
    String getCron();
    default int getChunkSize() { return 100; }
    Collection<String> getEventTypes();
    void handleEvents(Collection<E> events);
}
