package io.ddd4j.sample.cqrs.person;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.ddd4j.sample.cqrs.person.domain.CreatePersonCommand;
import io.ddd4j.sample.cqrs.person.domain.PersonCreatedEvent;
import io.ddd4j.sample.cqrs.person.domain.PersonId;
import io.ddd4j.sample.cqrs.person.domain.PersonName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PersonSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Test
    void shouldRoundTripCreatePersonCommand() throws Exception {
        CreatePersonCommand command = CreatePersonCommand.builder()
                .personId("p-100")
                .name("Ada")
                .build();

        String json = objectMapper.writeValueAsString(command);
        CreatePersonCommand actual = objectMapper.readValue(json, CreatePersonCommand.class);

        assertThat(actual).isEqualTo(command);
    }

    @Test
    void shouldRoundTripPersonCreatedEvent() throws Exception {
        PersonCreatedEvent event = new PersonCreatedEvent(new PersonId("p-100"), new PersonName("Ada"));

        String json = objectMapper.writeValueAsString(event);
        PersonCreatedEvent actual = objectMapper.readValue(json, PersonCreatedEvent.class);

        assertThat(actual.getEventType()).isEqualTo(PersonCreatedEvent.TYPE);
        assertThat(actual.getPersonId()).isEqualTo(event.getPersonId());
        assertThat(actual.getName()).isEqualTo(event.getName());
    }
}
