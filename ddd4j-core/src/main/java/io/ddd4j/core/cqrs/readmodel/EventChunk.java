package io.ddd4j.core.cqrs.readmodel;

import io.ddd4j.kit.lang.CollKit;
import lombok.Getter;

import java.util.List;
import java.util.Objects;

/**
 * 一批从事件流读取出的事件。
 *
 * @param <E> 事件类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Getter
public class EventChunk<E> {

    private final List<E> events;

    private final long nextEventNumber;

    public EventChunk(List<E> events, long nextEventNumber) {
        this.events = List.copyOf(Objects.requireNonNull(events, "events must not be null"));
        if (nextEventNumber < 0) {
            throw new IllegalArgumentException("nextEventNumber must not be negative");
        }
        this.nextEventNumber = nextEventNumber;
    }

    public static <E> EventChunk<E> empty(long nextEventNumber) {
        return new EventChunk<>(List.of(), nextEventNumber);
    }

    public boolean hasEvents() {
        return CollKit.isNotEmpty(events);
    }
}
