package io.ddd4j.sample.cqrs.person.query;

import io.ddd4j.core.cqrs.readmodel.TypedEventHandler;
import io.ddd4j.sample.cqrs.person.domain.PersonDeletedEvent;

import java.util.Objects;
import java.util.concurrent.ConcurrentMap;

public class PersonDeletedEventHandler implements TypedEventHandler<PersonDeletedEvent> {

    private final ConcurrentMap<String, PersonListEntry> entries;

    public PersonDeletedEventHandler(ConcurrentMap<String, PersonListEntry> entries) {
        this.entries = Objects.requireNonNull(entries, "entries must not be null");
    }

    @Override
    public String getEventType() {
        return PersonDeletedEvent.TYPE;
    }

    @Override
    public Class<PersonDeletedEvent> getEventClass() {
        return PersonDeletedEvent.class;
    }

    @Override
    public void handle(PersonDeletedEvent event) {
        entries.remove(event.getPersonId().getValue());
    }
}
