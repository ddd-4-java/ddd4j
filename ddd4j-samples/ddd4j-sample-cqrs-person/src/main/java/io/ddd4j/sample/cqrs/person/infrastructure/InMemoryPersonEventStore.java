package io.ddd4j.sample.cqrs.person.infrastructure;

import io.ddd4j.core.cqrs.readmodel.EventChunk;
import io.ddd4j.core.cqrs.readmodel.EventChunkReader;
import io.ddd4j.kit.lang.CollKit;
import io.ddd4j.sample.cqrs.person.domain.PersonEvent;
import io.ddd4j.sample.cqrs.person.domain.PersonId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public class InMemoryPersonEventStore implements EventChunkReader<PersonEvent> {

    public static final String STREAM_ID = "person-stream";

    private final List<PersonEvent> events = new CopyOnWriteArrayList<>();

    public void append(List<PersonEvent> newEvents) {
        if (CollKit.isEmpty(newEvents)) {
            return;
        }
        events.addAll(newEvents);
    }

    public List<PersonEvent> readByPersonId(PersonId personId) {
        PersonId id = Objects.requireNonNull(personId, "personId must not be null");
        return events.stream()
                .filter(event -> Objects.equals(id, event.getPersonId()))
                .toList();
    }

    @Override
    public EventChunk<PersonEvent> read(String streamId, long fromEventNumber, int chunkSize, Collection<String> eventTypes) {
        if (fromEventNumber >= events.size()) {
            return EventChunk.empty(fromEventNumber);
        }
        int fromIndex = Math.toIntExact(fromEventNumber);
        int toIndex = Math.min(events.size(), fromIndex + chunkSize);
        List<PersonEvent> selectedEvents = new ArrayList<>();
        for (PersonEvent event : events.subList(fromIndex, toIndex)) {
            if (CollKit.isEmpty(eventTypes) || eventTypes.contains(event.getEventType())) {
                selectedEvents.add(event);
            }
        }
        return new EventChunk<>(selectedEvents, toIndex);
    }
}
