package io.ddd4j.sample.cqrs.person.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.util.Objects;

@Getter
@ToString
@NoArgsConstructor
public class PersonDeletedEvent implements PersonEvent {

    public static final String TYPE = "person.deleted";

    private PersonId personId;

    private Instant occurredAt;

    public PersonDeletedEvent(PersonId personId) {
        this.personId = Objects.requireNonNull(personId, "personId must not be null");
        this.occurredAt = Instant.now();
    }

    @Override
    public String getEventType() {
        return TYPE;
    }
}
