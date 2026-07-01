package io.ddd4j.sample.cqrs.person.domain;

import java.util.Optional;

public interface PersonRepository {

    void save(Person person);

    Optional<Person> findById(PersonId id);
}
