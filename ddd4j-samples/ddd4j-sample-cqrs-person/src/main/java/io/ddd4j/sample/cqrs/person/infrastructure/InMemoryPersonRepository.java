package io.ddd4j.sample.cqrs.person.infrastructure;

import io.ddd4j.sample.cqrs.person.domain.Person;
import io.ddd4j.sample.cqrs.person.domain.PersonEvent;
import io.ddd4j.sample.cqrs.person.domain.PersonId;
import io.ddd4j.sample.cqrs.person.domain.PersonRepository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class InMemoryPersonRepository implements PersonRepository {

    private final InMemoryPersonEventStore eventStore;

    public InMemoryPersonRepository(InMemoryPersonEventStore eventStore) {
        this.eventStore = Objects.requireNonNull(eventStore, "eventStore must not be null");
    }

    @Override
    public void save(Person person) {
        eventStore.append(person.pullChanges());
    }

    @Override
    public Optional<Person> findById(PersonId id) {
        List<PersonEvent> events = eventStore.readByPersonId(id);
        if (events.isEmpty()) {
            return Optional.empty();
        }
        Person person = new Person();
        person.loadFromHistory(events);
        return Optional.of(person);
    }
}
