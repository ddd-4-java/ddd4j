package io.ddd4j.sample.cqrs.person.query;

import io.ddd4j.core.cqrs.readmodel.ProjectionView;
import io.ddd4j.core.cqrs.readmodel.TypedEventDispatcher;
import io.ddd4j.sample.cqrs.person.domain.PersonCreatedEvent;
import io.ddd4j.sample.cqrs.person.domain.PersonDeletedEvent;
import io.ddd4j.sample.cqrs.person.domain.PersonEvent;
import io.ddd4j.sample.cqrs.person.infrastructure.InMemoryPersonEventStore;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class PersonListView implements ProjectionView<PersonEvent> {

    private final ConcurrentMap<String, PersonListEntry> entries = new ConcurrentHashMap<>();

    private final TypedEventDispatcher dispatcher = new TypedEventDispatcher(List.of(
            new PersonCreatedEventHandler(entries),
            new PersonDeletedEventHandler(entries)
    ));

    @Override
    public String getName() {
        return "person-list-view";
    }

    @Override
    public String getStreamId() {
        return InMemoryPersonEventStore.STREAM_ID;
    }

    @Override
    public String getCron() {
        return "0/5 * * * * ?";
    }

    @Override
    public Collection<String> getEventTypes() {
        return List.of(PersonCreatedEvent.TYPE, PersonDeletedEvent.TYPE);
    }

    @Override
    public void handleEvents(Collection<PersonEvent> events) {
        for (PersonEvent event : events) {
            dispatcher.dispatch(event);
        }
    }

    public Optional<PersonListEntry> findById(String personId) {
        return Optional.ofNullable(entries.get(personId));
    }

    public List<PersonListEntry> findAll() {
        return List.copyOf(entries.values());
    }

    public Map<String, PersonListEntry> snapshot() {
        return Map.copyOf(entries);
    }
}
