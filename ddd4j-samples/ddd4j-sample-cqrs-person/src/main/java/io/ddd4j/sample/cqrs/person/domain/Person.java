package io.ddd4j.sample.cqrs.person.domain;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Getter
public class Person {

    private final List<PersonEvent> changes = new ArrayList<>();
    private PersonId id;
    private PersonName name;
    private boolean deleted;

    public static Person create(PersonId id, PersonName name) {
        Person person = new Person();
        person.apply(new PersonCreatedEvent(id, name));
        return person;
    }

    public void delete() {
        if (!deleted) {
            apply(new PersonDeletedEvent(id));
        }
    }

    public void loadFromHistory(List<PersonEvent> events) {
        for (PersonEvent event : events) {
            mutate(event);
        }
    }

    public List<PersonEvent> pullChanges() {
        List<PersonEvent> events = List.copyOf(changes);
        changes.clear();
        return events;
    }

    private void apply(PersonEvent event) {
        PersonEvent personEvent = Objects.requireNonNull(event, "event must not be null");
        mutate(personEvent);
        changes.add(personEvent);
    }

    private void mutate(PersonEvent event) {
        if (event instanceof PersonCreatedEvent createdEvent) {
            id = createdEvent.getPersonId();
            name = createdEvent.getName();
            deleted = false;
        }
        if (event instanceof PersonDeletedEvent) {
            deleted = true;
        }
    }
}
