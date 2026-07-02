package io.ddd4j.sample.cqrs.person.application;

import io.ddd4j.sample.cqrs.person.domain.*;

import java.util.Objects;

public class PersonCommandService {

    private final PersonRepository repository;

    public PersonCommandService(PersonRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    public PersonId create(CreatePersonCommand command) {
        CreatePersonCommand createCommand = Objects.requireNonNull(command, "command must not be null");
        PersonId personId = new PersonId(createCommand.getPersonId());
        PersonName name = new PersonName(createCommand.getName());
        Person person = Person.create(personId, name);
        repository.save(person);
        return personId;
    }

    public void delete(DeletePersonCommand command) {
        DeletePersonCommand deleteCommand = Objects.requireNonNull(command, "command must not be null");
        PersonId personId = new PersonId(deleteCommand.getPersonId());
        Person person = repository.findById(personId)
                .orElseThrow(() -> new IllegalArgumentException("person not found: " + personId.getValue()));
        person.delete();
        repository.save(person);
    }
}
