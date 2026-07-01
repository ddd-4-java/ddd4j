package io.ddd4j.sample.cqrs.person.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.util.Objects;

@Getter
@ToString
@NoArgsConstructor
public class PersonCreatedEvent implements PersonEvent {

    public static final String TYPE = "person.created";

    private PersonId personId;

    private PersonName name;

    private Instant occurredAt;

    public PersonCreatedEvent(PersonId personId, PersonName name) {
        this.personId = Objects.requireNonNull(personId, "personId must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.occurredAt = Instant.now();
    }

    @Override
    public String getEventType() {
        return TYPE;
    }
}
