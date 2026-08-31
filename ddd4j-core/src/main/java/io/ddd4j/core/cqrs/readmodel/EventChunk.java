package io.ddd4j.core.cqrs.readmodel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 一次增量读取的事件批次及下一读取位置。 */
public final class EventChunk<E> {
    private final List<E> events; private final long nextEventNumber;
    public EventChunk(List<E> events, long nextEventNumber) {
        if (events == null) throw new NullPointerException("events");
        if (nextEventNumber < 0) throw new IllegalArgumentException("nextEventNumber must not be negative");
        this.events = Collections.unmodifiableList(new ArrayList<E>(events)); this.nextEventNumber = nextEventNumber;
    }
    public static <E> EventChunk<E> empty(long nextEventNumber) { return new EventChunk<E>(Collections.<E>emptyList(), nextEventNumber); }
    public List<E> getEvents() { return events; }
    public long getNextEventNumber() { return nextEventNumber; }
    public boolean hasEvents() { return !events.isEmpty(); }
}
