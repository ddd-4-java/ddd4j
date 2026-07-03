package io.ddd4j.sample.cqrs.person;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.ddd4j.sample.cqrs.person.domain.CreatePersonCommand;
import io.ddd4j.sample.cqrs.person.domain.PersonCreatedEvent;
import io.ddd4j.sample.cqrs.person.domain.PersonId;
import io.ddd4j.sample.cqrs.person.domain.PersonName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Person 领域对象序列化测试。
 *
 * <p>验证 {@link CreatePersonCommand} 和 {@link PersonCreatedEvent} 的
 * JSON 序列化与反序列化正确性。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class PersonSerializationTest {

    /**
     * Jackson ObjectMapper（注册 JavaTimeModule 支持 Java 8 时间类型）
     */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    /**
     * 验证 {@link CreatePersonCommand} 的 JSON 序列化与反序列化。
     */
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

    /**
     * 验证 {@link PersonCreatedEvent} 的 JSON 序列化与反序列化。
     */
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
