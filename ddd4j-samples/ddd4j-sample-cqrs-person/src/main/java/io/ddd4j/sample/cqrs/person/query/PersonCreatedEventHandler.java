package io.ddd4j.sample.cqrs.person.query;

import io.ddd4j.core.cqrs.projection.TypedEventHandler;
import io.ddd4j.sample.cqrs.person.domain.PersonCreatedEvent;

import java.util.Objects;
import java.util.concurrent.ConcurrentMap;

public class PersonCreatedEventHandler implements TypedEventHandler<PersonCreatedEvent> {

    private final ConcurrentMap<String, PersonListEntry> entries;

    public PersonCreatedEventHandler(ConcurrentMap<String, PersonListEntry> entries) {
        this.entries = Objects.requireNonNull(entries, "entries must not be null");
    }

    @Override
    public String getEventType() {
        return PersonCreatedEvent.TYPE;
    }

    @Override
    public Class<PersonCreatedEvent> getEventClass() {
        return PersonCreatedEvent.class;
    }

    @Override
    public void handle(PersonCreatedEvent event) {
        entries.put(
                event.getPersonId().getValue(),
                new PersonListEntry(event.getPersonId().getValue(), event.getName().getValue())
        );
    }
}
