package io.ddd4j.core.cqrs.readmodel;

import java.util.Collection;

/** 从具体事件存储读取投影事件块的端口。 */
public interface EventChunkReader<E> {
    EventChunk<E> read(String streamId, long fromEventNumber, int chunkSize, Collection<String> eventTypes);
}
