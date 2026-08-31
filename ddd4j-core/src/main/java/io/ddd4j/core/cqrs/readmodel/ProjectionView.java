package io.ddd4j.core.cqrs.readmodel;

import java.util.Collection;

/** 框架无关的增量投影视图。 */
public interface ProjectionView<E> {
    String getName();
    default String getStreamId() { return getName(); }
    String getCron();
    default int getChunkSize() { return 100; }
    Collection<String> getEventTypes();
    void handleEvents(Collection<E> events);
}
