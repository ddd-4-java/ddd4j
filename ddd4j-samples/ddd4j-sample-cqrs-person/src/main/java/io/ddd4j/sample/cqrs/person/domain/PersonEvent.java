package io.ddd4j.sample.cqrs.person.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.ddd4j.core.cqrs.readmodel.TypedEvent;

import java.time.Instant;

public interface PersonEvent extends TypedEvent {

    @Override
    @JsonIgnore
    String getEventType();

    PersonId getPersonId();

    Instant getOccurredAt();
}
